package com.ibkglobal.integrator.engine.manager;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.integrator.exception.ErrorType;
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

    private static void assertAnsweredAsBidTimeout(BidInfo ticket) {
        assertEquals(BidInfo.BidStatus.TIMEOUT, ticket.getStatus());
        String errCode = ticket.getBeforeExchange().getIn().getHeader(ConstantCode.ERR_CODE, String.class);
        assertTrue("ERR_CODE " + errCode, errCode.endsWith(ErrorType.MCA_BID_TIMEOUT.getErrorCode()));
        assertEquals("Transaction processing is delayed. Please wait.",
            ticket.getBeforeExchange().getIn().getHeader(ConstantCode.ERR_MSG));
        assertNull(ticket.getBeforeExchange().getException());
    }

    @Test public void saturationAnswersAsBidTimeoutAndReleasedCapacityIsReusable() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger calls = new AtomicInteger();
        try {
            assertEquals(512, manager.getAsyncCapacityLimit());
            for (int i = 0; i < 512; i++) {
                assertFalse(manager.bidStartAsync(ticket("capacity-" + i, context), sync -> calls.incrementAndGet()));
            }
            // Dummy ack means the host already accepted the transaction: overload must
            // not become a hard MCA_BID error (channel would treat it as failed).
            BidInfo overflow = ticket("overflow", context);
            AtomicInteger overflowCalls = new AtomicInteger();
            assertTrue(manager.bidStartAsync(overflow, sync -> { assertTrue(sync); overflowCalls.incrementAndGet(); }));
            assertEquals(1, overflowCalls.get());
            assertAnsweredAsBidTimeout(overflow);
            assertEquals(512, manager.getBidInfoList().size());
            assertFalse(manager.getBidInfoList().containsKey("overflow"));
            manager.bidResult("capacity-0", new DefaultExchange(context));
            assertFalse(manager.bidStartAsync(ticket("replacement", context), sync -> calls.incrementAndGet()));
        } finally { manager.shutdownAsync(); }
        assertEquals(513, calls.get());
        assertTrue(manager.getBidInfoList().isEmpty());
    }

    @Test public void configuredCapacityIsHonouredAndValidated() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            try { manager.setAsyncCapacity(0); fail("capacity < 1 must be rejected"); }
            catch (IllegalArgumentException expected) { }
            manager.setAsyncCapacity(2);
            assertFalse(manager.bidStartAsync(ticket("a", context), sync -> { }));
            assertFalse(manager.bidStartAsync(ticket("b", context), sync -> { }));
            try { manager.setAsyncCapacity(10); fail("must not resize while continuations are pending"); }
            catch (IllegalStateException expected) { }
            BidInfo third = ticket("c", context);
            assertTrue(manager.bidStartAsync(third, sync -> { }));
            assertAnsweredAsBidTimeout(third);
        } finally { manager.shutdownAsync(); }
    }

    @Test public void parkedReleaseCompletesWithRealResponseEvenWhenSaturated() throws Exception {
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            manager.setAsyncCapacity(1);
            assertFalse(manager.bidStartAsync(ticket("occupied", context), sync -> { }));
            BidInfo owner = manager.bidPreRegister("early");
            Exchange release = new DefaultExchange(context);
            release.getIn().setBody("final");
            assertEquals(BidManager.ReleaseResult.PARKED, manager.bidResult("early", release));
            BidInfo dummy = ticket("early", context);
            dummy.getBeforeExchange().setProperty("MCA_BID_OWNER", owner);
            AtomicInteger calls = new AtomicInteger();
            assertTrue(manager.bidStartAsync(dummy, sync -> { assertTrue(sync); calls.incrementAndGet(); }));
            assertEquals(1, calls.get());
            assertEquals(BidInfo.BidStatus.COMPLETE, dummy.getStatus());
            assertEquals("final", dummy.getBeforeExchange().getIn().getBody());
            assertNull(dummy.getBeforeExchange().getIn().getHeader(ConstantCode.ERR_CODE));
        } finally { manager.shutdownAsync(); }
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
            // The primary timer is armed on the mca-bid-timer thread; the release fires from there.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (calls.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(1, calls.get());
            verify(manager.ibkTimeoutBid, timeout(3000).atLeastOnce()).remove(ticket.getAsyncTimerKey());
            assertTrue(ticket.getFallbackDeadline().isCancelled());
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally { manager.shutdownAsync(); }
    }

    static boolean ownedExecutorsStopped(BidManager manager) throws Exception {
        boolean found = false;
        for (java.lang.reflect.Field field : BidManager.class.getDeclaredFields()) {
            if (ExecutorService.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                found = true;
                if (!((ExecutorService) field.get(manager)).isShutdown()) return false;
            }
        }
        return found;
    }

    @Test public void shutdownIsolatesFailingCallbacksAndDrainsEveryTicket() throws Exception {
        // Re-audit R1: one throwing continuation used to abort the shutdown loop, leaving the
        // other tickets suspended, their permits held and the completion executor running.
        BidManager manager = manager();
        DefaultCamelContext context = new DefaultCamelContext();
        AtomicInteger calls = new AtomicInteger();
        java.util.List<BidInfo> tickets = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BidInfo ticket = ticket("drain-" + i, context);
            tickets.add(ticket);
            assertFalse(manager.bidStartAsync(ticket, sync -> {
                calls.incrementAndGet();
                throw new IllegalStateException("Injected continuation failure");
            }));
        }
        manager.shutdownAsync();
        assertEquals(3, calls.get());
        assertTrue(manager.getBidInfoList().isEmpty());
        assertEquals(0, manager.getAsyncPendingCount());
        for (BidInfo ticket : tickets) {
            assertEquals(BidInfo.BidStatus.TIMEOUT, ticket.getStatus());
            assertTrue(ticket.getContinuationCompleted().get());
            assertTrue(ticket.getFallbackDeadline().isCancelled());
        }
        assertTrue(ownedExecutorsStopped(manager));
    }

    @Test public void shutdownDrainIsBoundedWhenACallbackNeverReturns() throws Exception {
        BidManager manager = manager();
        manager.shutdownDrainMillis = 300;
        DefaultCamelContext context = new DefaultCamelContext();
        CountDownLatch never = new CountDownLatch(1);
        AtomicInteger others = new AtomicInteger();
        manager.bidStartAsync(ticket("hang", context), sync -> {
            try { never.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        manager.bidStartAsync(ticket("answered", context), sync -> others.incrementAndGet());
        long started = System.nanoTime();
        manager.shutdownAsync();
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue("shutdown must be bounded, took " + tookMs + " ms", tookMs < 3000);
        assertEquals(1, others.get());
        assertTrue(ownedExecutorsStopped(manager));
        never.countDown();
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
                    return manager.bidStartAsync(ticket, sync -> calls.incrementAndGet());
                });
                Future<?> shutdown = executor.submit(() -> {
                    try { start.await(); manager.shutdownAsync(); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                });
                start.countDown();
                registered.get(3, TimeUnit.SECONDS);
                shutdown.get(3, TimeUnit.SECONDS);
                // Either parked then expired by shutdown, or answered as BID timeout
                // because the node was already stopping: exactly one completion, no error.
                assertTrue(manager.getBidInfoList().isEmpty());
                assertEquals(1, calls.get());
                assertEquals(BidInfo.BidStatus.TIMEOUT, ticket.getStatus());
            }
        } finally { executor.shutdownNow(); }
    }
}
