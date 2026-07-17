package com.elsapiens.backgroundlocation;

/**
 * Pure validation rules deciding whether a fix is trustworthy enough to record.
 *
 * Framework-free on purpose so the rules are unit-testable and shared by every
 * consumer (task tracking, work-hour tracking, current-location watch).
 */
public class LocationFilter {

    /** Fixes older than this are considered stale and rejected. */
    public static final long MAX_FIX_AGE_MS = 30_000L;

    /**
     * Basic sanity check on coordinates.
     *
     * @return true when the coordinates describe a plausible point on Earth.
     */
    public boolean hasValidCoordinates(double latitude, double longitude) {
        if (latitude == 0.0 && longitude == 0.0) {
            return false; // Null Island — the classic "no fix yet" value.
        }
        return Math.abs(latitude) <= 90.0 && Math.abs(longitude) <= 180.0;
    }

    /**
     * @param accuracyMeters reported horizontal accuracy of the fix
     * @param maxAccuracyMeters worst acceptable accuracy; fixes above it are dropped
     * @return true when the fix is precise enough to record
     */
    public boolean meetsAccuracy(float accuracyMeters, float maxAccuracyMeters) {
        return accuracyMeters > 0 && accuracyMeters <= maxAccuracyMeters;
    }

    /**
     * @param fixTimeMillis epoch time of the fix
     * @param nowMillis current epoch time
     * @return true when the fix is fresh enough to record
     */
    public boolean isFresh(long fixTimeMillis, long nowMillis) {
        return nowMillis - fixTimeMillis <= MAX_FIX_AGE_MS;
    }

    /**
     * Combined check used by the tracking services.
     */
    public boolean shouldRecord(double latitude, double longitude, float accuracyMeters, float maxAccuracyMeters,
            long fixTimeMillis, long nowMillis) {
        return hasValidCoordinates(latitude, longitude)
            && meetsAccuracy(accuracyMeters, maxAccuracyMeters)
            && isFresh(fixTimeMillis, nowMillis);
    }
}
