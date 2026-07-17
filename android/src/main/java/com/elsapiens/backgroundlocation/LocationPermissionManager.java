package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.util.Log;

/**
 * Single source of truth for location permission checks.
 *
 * Two independent tiers exist on Android 10+ and this class never conflates them:
 *  - Foreground ("while in use"): ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
 *    Either one is enough to track — on Android 12+ the user may grant only
 *    approximate (coarse) accuracy, which previously locked tracking out entirely
 *    because the check demanded FINE AND COARSE.
 *  - Background ("allow all the time"): ACCESS_BACKGROUND_LOCATION, needed only to
 *    (re)start tracking while the app is not visible.
 */
public class LocationPermissionManager {
    private static final String TAG = "LocationPermissionManager";

    public static final String ACCURACY_FINE = "fine";
    public static final String ACCURACY_COARSE = "coarse";
    public static final String ACCURACY_NONE = "none";

    private final Context context;

    public LocationPermissionManager(Context context) {
        this.context = context;
    }

    /** True when the app can access location while in use (fine OR coarse). */
    public boolean hasForegroundLocationPermission() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** True when the app can access location from the background. */
    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < 29) {
            return hasForegroundLocationPermission();
        }
        return isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * Granted location accuracy: {@link #ACCURACY_FINE}, {@link #ACCURACY_COARSE} or
     * {@link #ACCURACY_NONE}. Apps that need precise tracking should surface a prompt
     * when only coarse accuracy was granted.
     */
    public String getGrantedAccuracy() {
        if (isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return ACCURACY_FINE;
        }
        if (isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return ACCURACY_COARSE;
        }
        return ACCURACY_NONE;
    }

    /**
     * FOREGROUND_SERVICE_LOCATION is an install-time permission on Android 14+; it is
     * missing only when the host app forgot to declare it in the manifest — surfaced
     * here so integration mistakes show up in checkPermissions() instead of as a
     * runtime service failure.
     */
    public boolean hasForegroundServicePermission() {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        return isGranted(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
    }

    /**
     * Backwards-compatible alias used by older callers.
     *
     * @deprecated use {@link #hasForegroundLocationPermission()} which states the tier.
     */
    @Deprecated
    public boolean hasLocationPermissions() {
        return hasForegroundLocationPermission();
    }

    /** Check if all permissions required for the given operation are available. */
    public boolean hasPermissionsForOperation(OperationType operationType) {
        switch (operationType) {
            case BASIC_TRACKING:
                return hasForegroundLocationPermission();
            case BACKGROUND_TRACKING:
            case WORK_HOUR_TRACKING:
                return hasForegroundLocationPermission() && hasBackgroundLocationPermission();
            default:
                return false;
        }
    }

    public void logPermissionStatus() {
        Log.d(TAG, "Foreground location: " + hasForegroundLocationPermission()
            + " (" + getGrantedAccuracy() + ")"
            + ", background: " + hasBackgroundLocationPermission()
            + ", foreground service: " + hasForegroundServicePermission());
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Types of operations that require different permission levels. */
    public enum OperationType {
        BASIC_TRACKING,      // Requires foreground location permission
        BACKGROUND_TRACKING, // Requires background location permission
        WORK_HOUR_TRACKING   // Requires background location permission
    }
}
