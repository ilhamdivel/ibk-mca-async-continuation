package com.ibkglobal.integrator.engine.manager;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Shared wait helper: releases resume the waiting continuation on mca-bid-resume, not on the caller. */
final class BidTestSupport {
    private BidTestSupport() { }

    static boolean waitUntil(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline >= 0) return false;
            Thread.sleep(5);
        }
        return true;
    }
}
