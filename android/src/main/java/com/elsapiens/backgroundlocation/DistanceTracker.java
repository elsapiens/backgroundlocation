package com.elsapiens.backgroundlocation;

/**
 * Accumulates travelled distance across accepted fixes only.
 *
 * The previous implementation added the distance to every incoming fix before the
 * accuracy filter ran, so rejected (inaccurate) fixes inflated the total and the next
 * accepted fix double-counted the same stretch. This class is only ever fed fixes that
 * passed {@link LocationFilter}, keeping the total honest. Pure math — unit-testable.
 */
public class DistanceTracker {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private double lastLatitude = Double.NaN;
    private double lastLongitude = Double.NaN;
    private double totalKm = 0.0;

    /**
     * Record an accepted fix and return the running total in kilometers.
     */
    public double addPoint(double latitude, double longitude) {
        if (!Double.isNaN(lastLatitude)) {
            totalKm += haversineKm(lastLatitude, lastLongitude, latitude, longitude);
        }
        lastLatitude = latitude;
        lastLongitude = longitude;
        return totalKm;
    }

    /** Running total in kilometers. */
    public double getTotalKm() {
        return totalKm;
    }

    /** True once at least one point has been recorded. */
    public boolean hasLastPoint() {
        return !Double.isNaN(lastLatitude);
    }

    /** Forget everything — used when a new tracking session starts. */
    public void reset() {
        lastLatitude = Double.NaN;
        lastLongitude = Double.NaN;
        totalKm = 0.0;
    }

    /**
     * Seed the running total (e.g. resuming a session from the database after a
     * service restart) without adding distance for the seed point itself.
     */
    public void resume(double totalKmSoFar, double latitude, double longitude) {
        this.totalKm = totalKmSoFar;
        this.lastLatitude = latitude;
        this.lastLongitude = longitude;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
