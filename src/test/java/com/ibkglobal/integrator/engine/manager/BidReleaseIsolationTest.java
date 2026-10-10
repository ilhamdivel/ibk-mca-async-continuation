package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import com.ibkglobal.integrator.engine.bean.mca.work.MCABidHandle;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

/**
 * Scope rule: the async change may only remove the dummy-ack wait. Every other flow keeps its
 * office behaviour; in particular the thread that delivers a release (Adapter-In executor for a
 * real response, the single BID JMS consumer for RCV_CONFIRM_BID) only signals the waiter, exactly
 * as the baseline did with CompletableFuture.complete(). The waiting transaction's continuation
 * must never run on, block, or throw into that thread.
 */
public class BidReleaseIsolationTest {

    private static BidInfo ticket(String key, DefaultCamelContext context) {
        BidInfo ticket = new BidInfo();
        ticket.setName(key);
        ticket.setBeforeExchange(new DefaultExchange(context));
        return ticket;
    }

    @Test
    public void releasingThreadOnlySignalsEvenWhileTheContinuationIsBlocked() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        CountDownLatch inContinuation = new CountDownLatch(1);
        CountDownLatch letGo = new CountDownLatch(1);
        AtomicReference<String> continuationThread = new AtomicReference<>();
        java.util.concurrent.ExecutorService releasingThread = java.util.concurrent.Executors.newSingleThreadExecutor(
            r -> new Thread(r, "test-releasing-thread"));
        try {
            manager.bidStartAsync(ticket("isolated", context), sync -> {
                continuationThread.set(Thread.currentThread().getName());
                inContinuation.countDown();
                try { letGo.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("slow and failing continuation");
            });
            Exchange response = new DefaultExchange(context);
            response.getIn().setBody("real-response");
            // Run the release on its own "releasing thread"; it must return although the continuation is blocked.
            java.util.concurrent.Future<BidManager.ReleaseResult> release = releasingThread.submit(
                () -> manager.bidResult("isolated", response));
            assertEquals(BidManager.ReleaseResult.DELIVERED, release.get(1, TimeUnit.SECONDS));
            assertTrue(inContinuation.await(3, TimeUnit.SECONDS));
            assertTrue(continuationThread.get(), continuationThread.get().startsWith("mca-bid-resume"));
            assertFalse(continuationThread.get().startsWith("test-releasing-thread"));
        } finally {
            letGo.countDown();
            releasingThread.shutdownNow();
            assertTrue(BidTestSupport.waitUntil(() -> manager.getAsyncPendingCount() == 0, 3000));
            manager.shutdownAsync();
        }
    }

    @Test
    public void rcvConfirmBidOnTheSingleJmsConsumerIsNotHeldByTheWaitingTransaction() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        StandardTelegram telegram = mock(StandardTelegram.class, RETURNS_DEEP_STUBS);
        when(telegram.getSttlSysCopt().getOtptTmgtDcd()).thenReturn("6");   // RCV_CONFIRM_BID
        when(telegram.getSttlSysCopt().getWhbnSttlWrtnYmd()).thenReturn("20261010");
        when(telegram.getSttlSysCopt().getWhbnSttlCretSysNm()).thenReturn("GCB");
        when(telegram.getSttlSysCopt().getWhbnSttlSrn()).thenReturn("SRN6");
        IBKMessage message = mock(IBKMessage.class);
        when(message.getStandardTelegram()).thenReturn(telegram);
        MCABidHandle handle = new MCABidHandle();
        java.lang.reflect.Field field = MCABidHandle.class.getDeclaredField("bidManager");
        field.setAccessible(true);
        field.set(handle, manager);

        CountDownLatch inContinuation = new CountDownLatch(1);
        CountDownLatch letGo = new CountDownLatch(1);
        java.util.concurrent.ExecutorService jmsConsumer = java.util.concurrent.Executors.newSingleThreadExecutor(
            r -> new Thread(r, "test-jms-consumer"));
        try {
            manager.bidStartAsync(ticket("20261010GCBSRN6", context), sync -> {
                inContinuation.countDown();
                try { letGo.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            Exchange rcvConfirm = new DefaultExchange(context);
            rcvConfirm.getIn().setBody(message);
            // The real MCABidHandle, as MCABidProcess calls it on the single JMS consumer thread.
            java.util.concurrent.Future<?> consumer = jmsConsumer.submit(() -> {
                try { handle.execute(rcvConfirm); } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            consumer.get(1, TimeUnit.SECONDS);   // TimeoutException = consumer held by the waiting transaction
            assertTrue(inContinuation.await(3, TimeUnit.SECONDS));
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally {
            letGo.countDown();
            jmsConsumer.shutdownNow();
            assertTrue(BidTestSupport.waitUntil(() -> manager.getAsyncPendingCount() == 0, 3000));
            manager.shutdownAsync();
        }
    }
}
