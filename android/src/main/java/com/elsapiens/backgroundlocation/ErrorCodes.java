package com.elsapiens.backgroundlocation;

/**
 * Canonical error codes surfaced to the JavaScript layer.
 *
 * Every rejected plugin call and every "error" event carries one of these codes so the
 * consuming app can branch on them (e.g. show a permission prompt for
 * {@link #BACKGROUND_PERMISSION_DENIED}) instead of parsing free-form messages.
 * The string values are part of the public plugin API — never change them, only add.
 */
public final class ErrorCodes {

    /** Foreground (while-in-use) location permission has not been granted. */
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    /**
     * Background ("Allow all the time") location permission has not been granted.
     * Tracking started in the foreground still works, but the service cannot be
     * restarted while the app is in the background.
     */
    public static final String BACKGROUND_PERMISSION_DENIED = "BACKGROUND_PERMISSION_DENIED";

    /** Device location services (GPS/network) are switched off. */
    public static final String LOCATION_SERVICES_DISABLED = "LOCATION_SERVICES_DISABLED";

    /** A required call parameter is missing or empty. */
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";

    /** The Android foreground service could not be started. */
    public static final String SERVICE_START_FAILED = "SERVICE_START_FAILED";

    /** No location fix could be obtained (timeout or provider failure). */
    public static final String LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE";

    /** No stored data exists for the requested reference. */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** The request was superseded or cancelled by a newer request. */
    public static final String CANCELLED = "CANCELLED";

    /** An unexpected internal error occurred; message carries detail. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Sources for the "error" event so apps know which subsystem raised it.
    public static final String SOURCE_TASK_TRACKING = "taskTracking";
    public static final String SOURCE_WORK_HOUR_TRACKING = "workHourTracking";
    public static final String SOURCE_CURRENT_LOCATION = "currentLocation";
    public static final String SOURCE_PERMISSIONS = "permissions";

    private ErrorCodes() {
        // Constants holder — not instantiable.
    }
}
