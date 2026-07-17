package com.elsapiens.backgroundlocation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationFilterTest {

    private final LocationFilter filter = new LocationFilter();

    @Test
    public void rejectsNullIsland() {
        assertFalse(filter.hasValidCoordinates(0.0, 0.0));
    }

    @Test
    public void rejectsOutOfRangeCoordinates() {
        assertFalse(filter.hasValidCoordinates(91.0, 10.0));
        assertFalse(filter.hasValidCoordinates(-91.0, 10.0));
        assertFalse(filter.hasValidCoordinates(45.0, 181.0));
        assertFalse(filter.hasValidCoordinates(45.0, -181.0));
    }

    @Test
    public void acceptsRealCoordinates() {
        assertTrue(filter.hasValidCoordinates(51.5074, -0.1278)); // London
        assertTrue(filter.hasValidCoordinates(-33.8688, 151.2093)); // Sydney
        assertTrue(filter.hasValidCoordinates(90.0, 180.0)); // Poles/antimeridian are valid
    }

    @Test
    public void accuracyWithinThresholdAccepted() {
        assertTrue(filter.meetsAccuracy(10f, 30f));
        assertTrue(filter.meetsAccuracy(30f, 30f)); // boundary inclusive
    }

    @Test
    public void accuracyBeyondThresholdRejected() {
        assertFalse(filter.meetsAccuracy(31f, 30f));
        assertFalse(filter.meetsAccuracy(100f, 30f));
    }

    @Test
    public void nonPositiveAccuracyRejected() {
        assertFalse(filter.meetsAccuracy(0f, 30f));
        assertFalse(filter.meetsAccuracy(-5f, 30f));
    }

    @Test
    public void freshFixAccepted() {
        long now = 1_000_000L;
        assertTrue(filter.isFresh(now - 1000, now));
        assertTrue(filter.isFresh(now - LocationFilter.MAX_FIX_AGE_MS, now)); // boundary inclusive
    }

    @Test
    public void staleFixRejected() {
        long now = 1_000_000L;
        assertFalse(filter.isFresh(now - LocationFilter.MAX_FIX_AGE_MS - 1, now));
    }

    @Test
    public void shouldRecordCombinesAllRules() {
        long now = 1_000_000L;
        assertTrue(filter.shouldRecord(51.5, -0.12, 10f, 30f, now - 1000, now));
        assertFalse(filter.shouldRecord(0.0, 0.0, 10f, 30f, now - 1000, now)); // bad coords
        assertFalse(filter.shouldRecord(51.5, -0.12, 50f, 30f, now - 1000, now)); // inaccurate
        assertFalse(filter.shouldRecord(51.5, -0.12, 10f, 30f, now - 60_000, now)); // stale
    }
}
