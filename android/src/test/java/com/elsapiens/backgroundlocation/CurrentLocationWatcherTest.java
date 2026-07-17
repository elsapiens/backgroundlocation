package com.elsapiens.backgroundlocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CurrentLocationWatcherTest {

    private static CurrentLocationWatcher.Fix fix(double lat, double lng, float accuracy) {
        return new CurrentLocationWatcher.Fix(lat, lng, accuracy, 0, 0, 0, 0);
    }

    @Test
    public void liveUpdatesUntilTargetMet() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(10f, 30_000L, 0L);

        assertEquals(CurrentLocationWatcher.Decision.LIVE_UPDATE, watcher.onFix(fix(51.5, -0.12, 50f)));
        assertEquals(CurrentLocationWatcher.Decision.LIVE_UPDATE, watcher.onFix(fix(51.5, -0.12, 25f)));
        assertEquals(CurrentLocationWatcher.Decision.COMPLETE, watcher.onFix(fix(51.5, -0.12, 8f)));
    }

    @Test
    public void exactTargetAccuracyCompletes() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(10f, 30_000L, 0L);
        assertEquals(CurrentLocationWatcher.Decision.COMPLETE, watcher.onFix(fix(51.5, -0.12, 10f)));
    }

    @Test
    public void keepsBestFixAcrossUpdates() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(5f, 30_000L, 0L);
        watcher.onFix(fix(51.5, -0.12, 50f));
        watcher.onFix(fix(51.6, -0.13, 20f));
        watcher.onFix(fix(51.7, -0.14, 35f)); // worse — must not replace best

        assertEquals(20f, watcher.getBest().accuracy, 1e-6);
        assertEquals(51.6, watcher.getBest().latitude, 1e-9);
    }

    @Test
    public void ignoresInvalidFixes() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(10f, 30_000L, 0L);
        assertEquals(CurrentLocationWatcher.Decision.IGNORE, watcher.onFix(fix(0.0, 0.0, 5f)));
        assertEquals(CurrentLocationWatcher.Decision.IGNORE, watcher.onFix(fix(51.5, -0.12, 0f)));
        assertNull(watcher.getBest());
    }

    @Test
    public void timesOutAfterConfiguredDuration() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(10f, 5_000L, 1_000L);
        assertFalse(watcher.isTimedOut(5_999L));
        assertTrue(watcher.isTimedOut(6_000L));
    }

    @Test
    public void nonPositiveTimeoutFallsBackToDefault() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(10f, 0L, 0L);
        assertEquals(CurrentLocationWatcher.DEFAULT_TIMEOUT_MS, watcher.getTimeoutMs());
    }

    @Test
    public void bestFixAvailableAfterTimeoutEvenWithoutTargetMet() {
        CurrentLocationWatcher watcher = new CurrentLocationWatcher(5f, 1_000L, 0L);
        watcher.onFix(fix(51.5, -0.12, 40f));
        assertTrue(watcher.isTimedOut(2_000L));
        assertEquals(40f, watcher.getBest().accuracy, 1e-6);
    }
}
