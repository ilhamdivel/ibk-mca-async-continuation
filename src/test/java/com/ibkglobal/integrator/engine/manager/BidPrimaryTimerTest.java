package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;

/**
 * Re-audit R2 with the REAL IBKTimeout / Camel 2.21.1 DefaultTimeoutMap: while its lock is held
 * (it is also held by purge -> onEviction), a dummy ack must still return, a release must still
 * answer, the fallback deadline must still answer, and permits must come back. Primary-timer
 * put/remove only catch up once the lock is free.
 */
public class BidPrimaryTimerTest {

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        java.lang.reflect.Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static java.util.concurrent.locks.Lock timeoutMapLock(IBKTimeout<BidInfo> timer) throws Exception {
        Class<?> type = timer.getTimeout().getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("lock");
                field.setAccessible(true);
                return (java.util.concurrent.locks.Lock) field.get(timer.getTimeout());
            } catch (NoSuchFieldException notHere) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalStateException("DefaultTimeoutMap lock not found");
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
    public void stuckPrimaryTimerLockDelaysNeitherDummyAckNorAnswerNorPermit() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.start();   // IBKTimeout.onEviction shuts its executor down when the context is stopped
        BidManager manager = new BidManager();
        IBKTimeout<BidInfo> timer = new IBKTimeout<>();
        CamelConfig config = new CamelConfig();
        set(config, CamelConfig.class, "camelContext", context);
        set(timer, IBKTimeout.class, "camelConfig", config);
        set(timer, IBKTimeout.class, "bidManager", manager);
        timer.timeBid();
        timer.init();
        manager.ibkTimeoutBid = timer;

        java.util.concurrent.locks.Lock lock = timeoutMapLock(timer);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch unlock = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try { locked.countDown(); unlock.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { lock.unlock(); }
        }, "test-timeout-map-lock-holder");
        holder.start();
        assertTrue(locked.await(3, TimeUnit.SECONDS));
        try {
            // 1. Dummy ack while the lock is stuck: must not block (this runs on the Netty IO thread in prod).
            AtomicInteger expiredCalls = new AtomicInteger();
            BidInfo expiring = ticket("lock-timeout", context, 50);
            long started = System.nanoTime();
            assertFalse(manager.bidStartAsync(expiring, sync -> expiredCalls.incrementAndGet()));
            AtomicInteger releasedCalls = new AtomicInteger();
            BidInfo released = ticket("lock-release", context, 100000);
            assertFalse(manager.bidStartAsync(released, sync -> releasedCalls.incrementAndGet()));
            long registerMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue("dummy ack blocked on the timer lock for " + registerMs + " ms", registerMs < 500);

            // 2. Release while the lock is stuck: answered immediately.
            assertEquals(BidManager.ReleaseResult.DELIVERED,
                manager.bidResult("lock-release", new DefaultExchange(context)));
            assertEquals(1, releasedCalls.get());

            // 3. Fallback deadline while the lock is stuck: answered, permit returned.
            await(expiredCalls, 1, 2000);
            assertEquals(1, expiredCalls.get());
            assertEquals(BidInfo.BidStatus.TIMEOUT, expiring.getStatus());
            assertEquals(0, manager.getAsyncPendingCount());
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally {
            unlock.countDown();
            holder.join(3000);
        }
        // 4. Once the lock is free, the timer thread catches up: no primary entry is left behind.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (timer.getTimeout().size() > 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(0, timer.getTimeout().size());
        manager.shutdownAsync();
        timer.stop();
        context.stop();
    }
}
