package com.elsapiens.backgroundlocation;

/**
 * State machine for the progressive-accuracy "current location" request.
 *
 * The app asks for the current position with a target accuracy; every incoming fix is
 * streamed live to JavaScript until a fix meets the target (or the timeout expires),
 * at which point the best fix wins. This class holds the decision logic only — no
 * Android or Play Services types — so the accept/finish/timeout behaviour is fully
 * unit-testable. {@link BackgroundLocationPlugin} feeds it fixes from the fused
 * provider and acts on the returned {@link Decision}.
 */
public class CurrentLocationWatcher {

    /** What the caller should do with a fix that was just fed in. */
    public enum Decision {
        /** Stream the fix as a live (non-final) update; keep watching. */
        LIVE_UPDATE,
        /** Target accuracy met — deliver this fix as final and stop watching. */
        COMPLETE,
        /** The fix is unusable (invalid coordinates); ignore it. */
        IGNORE
    }

    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final float targetAccuracyMeters;
    private final long timeoutMs;
    private final long startedAtMs;
    private final LocationFilter filter;

    private Fix best = null;

    public CurrentLocationWatcher(float targetAccuracyMeters, long timeoutMs, long nowMs) {
        this(targetAccuracyMeters, timeoutMs, nowMs, new LocationFilter());
    }

    public CurrentLocationWatcher(float targetAccuracyMeters, long timeoutMs, long nowMs, LocationFilter filter) {
        this.targetAccuracyMeters = targetAccuracyMeters;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.startedAtMs = nowMs;
        this.filter = filter;
    }

    /**
     * Feed a fix into the watcher.
     *
     * Tracks the most accurate fix seen so far and decides whether the watch is done.
     */
    public Decision onFix(Fix fix) {
        if (!filter.hasValidCoordinates(fix.latitude, fix.longitude) || fix.accuracy <= 0) {
            return Decision.IGNORE;
        }
        if (best == null || fix.accuracy < best.accuracy) {
            best = fix;
        }
        if (fix.accuracy <= targetAccuracyMeters) {
            return Decision.COMPLETE;
        }
        return Decision.LIVE_UPDATE;
    }

    public boolean isTimedOut(long nowMs) {
        return nowMs - startedAtMs >= timeoutMs;
    }

    /** Best (most accurate) fix seen so far, or null when nothing usable arrived. */
    public Fix getBest() {
        return best;
    }

    public float getTargetAccuracyMeters() {
        return targetAccuracyMeters;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    /** Framework-free snapshot of a location fix. */
    public static class Fix {
        public final double latitude;
        public final double longitude;
        public final float accuracy;
        public final double altitude;
        public final float speed;
        public final float heading;
        public final long timestamp;

        public Fix(double latitude, double longitude, float accuracy, double altitude, float speed, float heading,
                long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.altitude = altitude;
            this.speed = speed;
            this.heading = heading;
            this.timestamp = timestamp;
        }
    }
}
