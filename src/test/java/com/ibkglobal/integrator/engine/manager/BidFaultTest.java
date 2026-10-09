package com.ibkglobal.integrator.engine.manager;

import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;
import org.slf4j.MDC;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BidFaultTest {
    private BidManager manager() {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        return manager;
    }
    private BidInfo ticket(String key, DefaultCamelContext context) {
        BidInfo ticket = new BidInfo();
        ticket.setName(key);
        ticket.setBeforeExchange(new DefaultExchange(context));
        return ticket;
    }

    @Test public void saturationRejectsAndReleasedCapacityIsReusable() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger calls = new AtomicInteger();
        try {
            for (int i = 0; i < 512; i++) {
                assertFalse(manager.bidStartAsync(ticket("capacity-" + i, context), sync -> calls.incrementAndGet()));
            }
            try {
                manager.bidStartAsync(ticket("overflow", context), sync -> fail("Rejected ticket must not complete"));
                fail("Capacity must be bounded");
            } catch (IBKExceptionMCA expected) { }
            assertEquals(512, manager.getBidInfoList().size());
            manager.bidResult("capacity-0", new DefaultExchange(context));
            assertFalse(manager.bidStartAsync(ticket("replacement", context), sync -> calls.incrementAndGet()));
        } finally { manager.shutdownAsync(); }
        assertEquals(513, calls.get());
        assertTrue(manager.getBidInfoList().isEmpty());
    }

    @Test public void callbackFailureRestoresMdcAndDoesNotLeakCapacity() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        MDC.put("correlation", "request");
        BidInfo ticket = ticket("throwing", context);
        manager.bidStartAsync(ticket, sync -> {
            assertEquals("request", MDC.get("correlation"));
            throw new IllegalStateException("Injected callback failure");
        });
        MDC.put("correlation", "release-thread");
        try {
            manager.bidResult("throwing", new DefaultExchange(context));
            fail("Injected failure must remain visible");
        } catch (IllegalStateException expected) {
            assertEquals("Injected callback failure", expected.getMessage());
        } finally {
            assertEquals("release-thread", MDC.get("correlation"));
            MDC.clear(); manager.shutdownAsync();
        }
        assertTrue(ticket.getContinuationCompleted().get());
        assertFalse(ticket.isAsyncPermitOwned());
        assertTrue(ticket.getFallbackDeadline().isCancelled());
        assertTrue(manager.getBidInfoList().isEmpty());
    }

    @Test public void releaseDuringTimerInstallDoesNotLeaveTimer() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            manager.bidResult("install-race", new DefaultExchange(context));
            return null;
        }).when(manager.ibkTimeoutBid).put(anyString(), any(BidInfo.class), anyLong());
        BidInfo ticket = ticket("install-race", context);
        try {
            assertFalse(manager.bidStartAsync(ticket, sync -> calls.incrementAndGet()));
            assertEquals(1, calls.get());
            verify(manager.ibkTimeoutBid, atLeastOnce()).remove(ticket.getAsyncTimerKey());
            assertTrue(ticket.getFallbackDeadline().isCancelled());
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally { manager.shutdownAsync(); }
    }

    @Test public void registrationAndShutdownRaceLeavesNoWaiter() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 50; i++) {
                BidManager manager = manager();
                BidInfo ticket = ticket("shutdown-race", context);
                AtomicInteger calls = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> registered = executor.submit(() -> {
                    start.await();
                    try { manager.bidStartAsync(ticket, sync -> calls.incrementAndGet()); return true; }
                    catch (IBKExceptionMCA expected) { return false; }
                });
                Future<?> shutdown = executor.submit(() -> {
                    try { start.await(); manager.shutdownAsync(); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                });
                start.countDown();
                boolean accepted = registered.get(3, TimeUnit.SECONDS);
                shutdown.get(3, TimeUnit.SECONDS);
                assertTrue(manager.getBidInfoList().isEmpty());
                assertEquals(accepted ? 1 : 0, calls.get());
            }
        } finally { executor.shutdownNow(); }
    }
}
