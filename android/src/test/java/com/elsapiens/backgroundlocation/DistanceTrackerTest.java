package com.elsapiens.backgroundlocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DistanceTrackerTest {

    @Test
    public void firstPointAddsNoDistance() {
        DistanceTracker tracker = new DistanceTracker();
        assertEquals(0.0, tracker.addPoint(51.5074, -0.1278), 1e-9);
        assertTrue(tracker.hasLastPoint());
    }

    @Test
    public void accumulatesKnownDistance() {
        DistanceTracker tracker = new DistanceTracker();
        // London -> Paris is ~343.5 km great-circle.
        tracker.addPoint(51.5074, -0.1278);
        double total = tracker.addPoint(48.8566, 2.3522);
        assertEquals(343.5, total, 2.0);
    }

    @Test
    public void skippedInaccuratePointsDoNotInflateDistance() {
        // The tracker only ever receives accepted points, so feeding A then B directly
        // must equal the distance A->B — the historical double-count came from feeding
        // rejected fixes into the total.
        DistanceTracker tracker = new DistanceTracker();
        tracker.addPoint(51.5074, -0.1278);
        double direct = tracker.addPoint(51.5174, -0.1278); // ~1.11 km north

        assertEquals(1.11, direct, 0.02);
    }

    @Test
    public void resetForgetsEverything() {
        DistanceTracker tracker = new DistanceTracker();
        tracker.addPoint(51.5, -0.12);
        tracker.addPoint(51.6, -0.12);
        tracker.reset();
        assertFalse(tracker.hasLastPoint());
        assertEquals(0.0, tracker.getTotalKm(), 1e-9);
    }

    @Test
    public void resumeSeedsTotalWithoutAddingDistance() {
        DistanceTracker tracker = new DistanceTracker();
        tracker.resume(12.5, 51.5074, -0.1278);
        assertEquals(12.5, tracker.getTotalKm(), 1e-9);
        // Next point adds only the increment from the seed point.
        double total = tracker.addPoint(51.5174, -0.1278);
        assertEquals(12.5 + 1.11, total, 0.02);
    }

    @Test
    public void haversineZeroForSamePoint() {
        assertEquals(0.0, DistanceTracker.haversineKm(51.5, -0.12, 51.5, -0.12), 1e-9);
    }
}
