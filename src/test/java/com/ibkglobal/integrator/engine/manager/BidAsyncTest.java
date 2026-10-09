package com.ibkglobal.integrator.engine.manager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;
import static org.junit.Assert.*;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.model.BidInfo.BidStatus;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import static org.mockito.Mockito.*;

public class BidAsyncTest {
    @Test
    public void camelPipelineSuspendsAndResumesAfterRelease() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger downstream = new AtomicInteger();
        org.apache.camel.AsyncProcessor suspend = new org.apache.camel.AsyncProcessor() {
            public void process(Exchange exchange) throws Exception {
                org.apache.camel.util.AsyncProcessorHelper.process(this, exchange);
            }
            public boolean process(Exchange exchange, org.apache.camel.AsyncCallback callback) {
                BidInfo ticket = new BidInfo();
                ticket.setName("route-key");
                ticket.setBeforeExchange(exchange);
                try {
                    return manager.bidStartAsync(ticket, callback);
                } catch (Exception failure) {
                    exchange.setException(failure);
                    callback.done(true);
                    return true;
                }
            }
        };
        context.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            public void configure() {
                from("direct:proof").process(suspend)
                    .process(exchange -> downstream.incrementAndGet());
            }
        });
        context.start();
        org.apache.camel.Producer producer = context.getEndpoint("direct:proof").createProducer();
        producer.start();
        Exchange original = new DefaultExchange(context);
        AtomicInteger completions = new AtomicInteger();
        org.apache.camel.AsyncProcessor processor =
            org.apache.camel.util.AsyncProcessorConverterHelper.convert(producer);
        try {
            assertFalse(processor.process(original, sync -> completions.incrementAndGet()));
            assertEquals(0, downstream.get());
            assertEquals(0, completions.get());
            Exchange release = new DefaultExchange(context);
            release.getIn().setBody("final");
            manager.bidResult("route-key", release);
            assertEquals(1, downstream.get());
            assertEquals(1, completions.get());
            assertEquals("final", original.getIn().getBody());
        } finally {
            producer.stop();
            context.stop();
            manager.shutdownAsync();
        }
    }

    @Test
    public void concurrentReleaseAndTimeoutCompleteOnce() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            for (int i = 0; i < 100; i++) {
                String key = "race-" + i;
                BidInfo ticket = new BidInfo();
                ticket.setName(key);
                ticket.setBeforeExchange(new DefaultExchange(context));
                AtomicInteger calls = new AtomicInteger();
                CountDownLatch done = new CountDownLatch(1);
                manager.bidStartAsync(ticket, sync -> { calls.incrementAndGet(); done.countDown(); });
                Exchange release = new DefaultExchange(context);
                release.getIn().setBody("final");
                Thread releaser = new Thread(() -> {
                    try { manager.bidResult(key, release); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                });
                Thread timer = new Thread(() -> manager.bidTimeout(key, ticket));
                releaser.start(); timer.start(); releaser.join(); timer.join();
                assertTrue(done.await(3, TimeUnit.SECONDS));
                assertEquals(1, calls.get());
                assertFalse(manager.getBidInfoList().containsKey(key));
                assertTrue(ticket.getFallbackDeadline().isCancelled());
            }
        } finally { manager.shutdownAsync(); }
    }

    @Test
    public void shutdownResolvesPendingAndRejectsNewRequests() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        BidInfo ticket = new BidInfo();
        ticket.setName("shutdown");
        ticket.setBeforeExchange(new DefaultExchange(new DefaultCamelContext()));
        AtomicInteger calls = new AtomicInteger();
        manager.bidStartAsync(ticket, sync -> calls.incrementAndGet());
        manager.shutdownAsync();
        assertEquals(1, calls.get());
        assertTrue(manager.getBidInfoList().isEmpty());
        try { manager.bidStartAsync(ticket, sync -> calls.incrementAndGet()); fail("Must reject after shutdown"); }
        catch (com.ibkglobal.integrator.exception.IBKExceptionMCA expected) { }
    }

    @Test
    public void normalResponseRemainsSynchronous() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        com.ibkglobal.message.common.normal.StandardTelegram telegram = mock(
            com.ibkglobal.message.common.normal.StandardTelegram.class, RETURNS_DEEP_STUBS);
        when(telegram.getSttlSysCopt().getOtptTmgtDcd()).thenReturn("0");
        com.ibkglobal.message.IBKMessage message = mock(com.ibkglobal.message.IBKMessage.class);
        when(message.getStandardTelegram()).thenReturn(telegram);
        Exchange original = new DefaultExchange(new DefaultCamelContext());
        original.getIn().setBody(message);
        AtomicInteger calls = new AtomicInteger();
        com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync processor =
            new com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync(manager);
        assertTrue(processor.process(original, sync -> { assertTrue(sync); calls.incrementAndGet(); }));
        assertEquals(1, calls.get());
        assertSame(message, original.getIn().getBody());
        assertNull(original.getException());
        assertTrue(manager.getBidInfoList().isEmpty());
        verifyZeroInteractions(manager.ibkTimeoutBid);
        manager.shutdownAsync();
    }

    @Test
    public void closedChannelKeepsTicketUntilRelease() throws Exception {
        // Office baseline semantics: a client disconnect must not turn into an early
        // BID timeout, otherwise the later real release is reported as "bidInfo is null".
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
        DefaultCamelContext context = new DefaultCamelContext();
        BidInfo ticket = new BidInfo();
        ticket.setName("closed");
        ticket.setBeforeExchange(new DefaultExchange(context));
        ticket.getBeforeExchange().setProperty("MCA_BID_ORIGINAL_CHANNEL", channel);
        AtomicInteger calls = new AtomicInteger();
        assertFalse(manager.bidStartAsync(ticket, sync -> calls.incrementAndGet()));
        channel.close(); channel.runPendingTasks();
        Thread.sleep(200);
        assertEquals(0, calls.get());
        assertSame(ticket, manager.getBidInfoList().get("closed"));
        assertEquals(BidManager.ReleaseResult.DELIVERED, manager.bidResult("closed", new DefaultExchange(context)));
        assertEquals(1, calls.get());
        assertEquals(BidStatus.COMPLETE, ticket.getStatus());
        manager.shutdownAsync();
    }

    @Test
    public void staleTimerCannotCompleteNewGeneration() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        BidInfo old = new BidInfo(); old.setName("reused");
        old.setBeforeExchange(new DefaultExchange(context));
        manager.bidStartAsync(old, sync -> { });
        manager.bidResult("reused", new DefaultExchange(context));
        BidInfo current = new BidInfo(); current.setName("reused");
        current.setBeforeExchange(new DefaultExchange(context));
        AtomicInteger calls = new AtomicInteger();
        manager.bidStartAsync(current, sync -> calls.incrementAndGet());
        assertNotEquals(old.getAsyncTimerKey(), current.getAsyncTimerKey());
        manager.bidTimeout(old.getAsyncTimerKey(), old);
        assertSame(current, manager.getBidInfoList().get("reused"));
        assertEquals(0, calls.get());
        manager.bidResult("reused", new DefaultExchange(context));
        assertEquals(1, calls.get());
        manager.shutdownAsync();
    }

    @Test
    public void dummyReturnsBeforeRelease() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange original = new DefaultExchange(context);
        original.getIn().setBody("dummy");
        BidInfo ticket = new BidInfo();
        ticket.setName("key");
        ticket.setBeforeExchange(original);
        AtomicInteger calls = new AtomicInteger();
        assertFalse(manager.bidStartAsync(ticket, sync -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
        Exchange release = new DefaultExchange(context);
        release.getIn().setBody("final");
        assertEquals(BidManager.ReleaseResult.DELIVERED, manager.bidResult("key", release));
        assertEquals("final", original.getIn().getBody());
        assertEquals(1, calls.get());
        assertEquals(BidManager.ReleaseResult.NOT_FOUND, manager.bidResult("key", release));
        assertEquals(1, calls.get());
        manager.shutdownAsync();
    }

    @Test
    public void releaseFirstCompletesSynchronously() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        manager.bidPreRegister("key");
        Exchange release = new DefaultExchange(context);
        release.getIn().setBody("final");
        assertEquals(BidManager.ReleaseResult.PARKED, manager.bidResult("key", release));
        BidInfo dummy = new BidInfo();
        dummy.setName("key");
        dummy.setBeforeExchange(new DefaultExchange(context));
        AtomicInteger calls = new AtomicInteger();
        assertTrue(manager.bidStartAsync(dummy, sync -> { assertTrue(sync); calls.incrementAndGet(); }));
        assertEquals(1, calls.get());
        assertEquals("final", dummy.getBeforeExchange().getIn().getBody());
        manager.shutdownAsync();
    }

    @Test
    public void failedPrimaryTimerStillExpires() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        doThrow(new RuntimeException("Primary timer unavailable"))
            .when(manager.ibkTimeoutBid).put(anyString(), any(BidInfo.class), anyLong());
        BidInfo ticket = new BidInfo();
        ticket.setName("fallback");
        ticket.setDefaultTimeOut(20);
        ticket.setBeforeExchange(new DefaultExchange(new DefaultCamelContext()));
        CountDownLatch completed = new CountDownLatch(1);
        assertFalse(manager.bidStartAsync(ticket, sync -> completed.countDown()));
        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertEquals(BidStatus.TIMEOUT, ticket.getStatus());
        assertTrue(manager.getBidInfoList().isEmpty());
        manager.shutdownAsync();
    }
}
