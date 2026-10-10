package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;

/**
 * Re-audit R3: timeout answers run on mca-bid-completion workers. These tests pin the bound:
 * an answer starts at its deadline unless completionThreads timeout continuations are already
 * running; then it queues (never dropped), a late release can still win, and the lag is reported.
 */
public class BidCompletionTest {

    private static BidManager manager() {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        return manager;
    }

    private static BidInfo ticket(String key, DefaultCamelContext context, long timeoutMillis) {
        BidInfo ticket = new BidInfo();
        ticket.setName(key);
        ticket.setBeforeExchange(new DefaultExchange(context));
        ticket.setDefaultTimeOut(timeoutMillis);
        return ticket;
    }

    private static void await(AtomicInteger counter, int expected, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (counter.get() < expected && System.nanoTime() < deadline) Thread.sleep(5);
    }

    @Test
    public void timeoutsAnswerInParallelUpToCompletionThreads() throws Exception {
        BidManager manager = manager();
        assertEquals(8, manager.getCompletionThreads());
        DefaultCamelContext context = new DefaultCamelContext();
        CountDownLatch slow = new CountDownLatch(1);
        AtomicInteger blocked = new AtomicInteger();
        try {
            for (int i = 0; i < 7; i++) {   // seven slow timeout continuations occupy seven workers
                manager.bidStartAsync(ticket("slow-" + i, context, 10), sync -> {
                    blocked.incrementAndGet();
                    try { slow.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
            }
            await(blocked, 7, 3000);
            assertEquals(7, blocked.get());
            AtomicInteger answered = new AtomicInteger();
            BidInfo eighth = ticket("eighth", context, 30);
            manager.bidStartAsync(eighth, sync -> answered.incrementAndGet());
            await(answered, 1, 1000);
            assertEquals("the 8th worker must answer at its deadline", 1, answered.get());
            assertEquals(BidInfo.BidStatus.TIMEOUT, eighth.getStatus());
        } finally {
            slow.countDown();
            manager.shutdownAsync();
        }
    }

    @Test
    public void saturatedWorkersQueueTimeoutsNeverDropThemLetReleasesWinAndReportLag() throws Exception {
        BidManager manager = manager();
        manager.setCompletionThreads(2);
        DefaultCamelContext context = new DefaultCamelContext();
        CountDownLatch slow = new CountDownLatch(1);
        AtomicInteger blocked = new AtomicInteger();
        try {
            for (int i = 0; i < 2; i++) {
                manager.bidStartAsync(ticket("busy-" + i, context, 10), sync -> {
                    blocked.incrementAndGet();
                    try { slow.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
            }
            await(blocked, 2, 3000);
            AtomicInteger timedOutCalls = new AtomicInteger();
            AtomicInteger releasedCalls = new AtomicInteger();
            BidInfo timedOut = ticket("queued-timeout", context, 30);
            BidInfo released = ticket("queued-release", context, 30);
            manager.bidStartAsync(timedOut, sync -> timedOutCalls.incrementAndGet());
            manager.bidStartAsync(released, sync -> releasedCalls.incrementAndGet());
            Thread.sleep(300);
            // Both deadlines passed but no worker is free: queued, still WAIT, nothing lost.
            assertEquals(0, timedOutCalls.get());
            assertEquals(2, manager.getCompletionQueueDepth());
            assertEquals(BidInfo.BidStatus.WAIT, timedOut.getStatus());
            // A real response arriving now still wins over the queued timeout.
            Exchange response = new DefaultExchange(context);
            response.getIn().setBody("real-response");
            assertEquals(BidManager.ReleaseResult.DELIVERED, manager.bidResult("queued-release", response));
            // Resumed on mca-bid-resume, a separate pool: saturated timeout workers do not delay it.
            assertTrue(BidTestSupport.waitUntil(() -> releasedCalls.get() == 1, 1000));
            assertEquals("real-response", released.getBeforeExchange().getIn().getBody());

            slow.countDown();
            await(timedOutCalls, 1, 3000);
            assertEquals(1, timedOutCalls.get());
            assertEquals(BidInfo.BidStatus.TIMEOUT, timedOut.getStatus());
            Thread.sleep(100);
            assertEquals("the stale queued timeout must not answer the released ticket again", 1, releasedCalls.get());
            assertEquals(BidInfo.BidStatus.COMPLETE, released.getStatus());
            assertTrue("lag " + manager.getMaxTimeoutLagMillis(), manager.getMaxTimeoutLagMillis() >= 250);
            assertTrue(manager.getAsyncStats().contains("completionQueue=0"));
        } finally {
            slow.countDown();
            manager.shutdownAsync();
        }
    }

    @Test
    public void capacityAboveFormerFixedQueueTimesOutEveryTicketExactlyOnce() throws Exception {
        BidManager manager = manager();
        manager.setAsyncCapacity(600);   // the old completion queue was fixed at 512
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger[] calls = new AtomicInteger[600];
        AtomicInteger total = new AtomicInteger();
        try {
            for (int i = 0; i < 600; i++) {
                AtomicInteger mine = calls[i] = new AtomicInteger();
                assertFalse(manager.bidStartAsync(ticket("mass-" + i, context, 20), sync -> {
                    mine.incrementAndGet();
                    total.incrementAndGet();
                }));
            }
            await(total, 600, 15000);
            assertEquals(600, total.get());
            for (AtomicInteger call : calls) assertEquals(1, call.get());
            assertEquals(0, manager.getAsyncPendingCount());
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally {
            manager.shutdownAsync();
        }
    }

    @Test
    public void completionThreadsAreValidatedAndStartupOnly() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            try { manager.setCompletionThreads(0); fail("threads < 1 must be rejected"); }
            catch (IllegalArgumentException expected) { }
            manager.setCompletionThreads(3);
            assertEquals(3, manager.getCompletionThreads());
            manager.bidStartAsync(ticket("pending", context, 100000), sync -> { });
            try { manager.setCompletionThreads(4); fail("must not resize while continuations are pending"); }
            catch (IllegalStateException expected) { }
        } finally {
            manager.shutdownAsync();
        }
    }
}
