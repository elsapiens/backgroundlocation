package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

/**
 * Handles all location permission-related operations for the BackgroundLocation plugin.
 * 
 * This class centralizes permission checking, requesting, and state management to ensure
 * consistent permission handling across the plugin.
 */
public class LocationPermissionManager {
    private static final String TAG = "LocationPermissionManager";
    
    private final Context context;
    
    public LocationPermissionManager(Context context) {
        this.context = context;
    }
    
    /**
     * Check if all required location permissions are granted
     * 
     * @return true if all location permissions are available, false otherwise
     */
    public boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14+
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Check if background location permission is granted
     * 
     * @return true if background location permission is available, false otherwise
     */
    public boolean hasBackgroundLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Get the permission state for a specific permission
     * 
     * @param permission The permission to check (ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, FOREGROUND_SERVICE_LOCATION)
     * @return "granted", "denied", or "prompt"
     */
    public String getPermissionState(String permission) {
        String manifestPermission = getManifestPermission(permission);
        
        if (ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED) {
            return "granted";
        } else {
            // Note: This check requires Activity context, which might not be available in service context
            // For simplicity, we'll return "denied" if not granted
            return "denied";
        }
    }
    
    /**
     * Check if all permissions required for the given operation are available
     * 
     * @param operationType The type of operation (BASIC_TRACKING, BACKGROUND_TRACKING, WORK_HOUR_TRACKING)
     * @return true if all required permissions are available
     */
    public boolean hasPermissionsForOperation(OperationType operationType) {
        switch (operationType) {
            case BASIC_TRACKING:
                return hasLocationPermissions();
            case BACKGROUND_TRACKING:
            case WORK_HOUR_TRACKING:
                return hasLocationPermissions() && hasBackgroundLocationPermission();
            default:
                return false;
        }
    }
    
    /**
     * Get detailed permission status for all location-related permissions
     * 
     * @return PermissionStatus object with status of all permissions
     */
    public PermissionStatus getDetailedPermissionStatus() {
        PermissionStatus status = new PermissionStatus();
        status.location = getPermissionState("ACCESS_FINE_LOCATION");
        status.backgroundLocation = getPermissionState("ACCESS_BACKGROUND_LOCATION");
        status.foregroundService = getPermissionState("FOREGROUND_SERVICE_LOCATION");
        return status;
    }
    
    /**
     * Convert permission alias to actual Android manifest permission
     * 
     * @param permission The permission alias
     * @return The actual Android manifest permission string
     */
    private String getManifestPermission(String permission) {
        switch (permission) {
            case "ACCESS_FINE_LOCATION":
                return Manifest.permission.ACCESS_FINE_LOCATION;
            case "ACCESS_BACKGROUND_LOCATION":
                return Manifest.permission.ACCESS_BACKGROUND_LOCATION;
            case "FOREGROUND_SERVICE_LOCATION":
                return Manifest.permission.FOREGROUND_SERVICE_LOCATION;
            default:
                return Manifest.permission.ACCESS_FINE_LOCATION;
        }
    }
    
    /**
     * Log current permission status for debugging
     */
    public void logPermissionStatus() {
        Log.d(TAG, "Location permissions: " + hasLocationPermissions());
        Log.d(TAG, "Background location: " + hasBackgroundLocationPermission());
        Log.d(TAG, "Fine location: " + getPermissionState("ACCESS_FINE_LOCATION"));
        Log.d(TAG, "Background location: " + getPermissionState("ACCESS_BACKGROUND_LOCATION"));
        Log.d(TAG, "Foreground service: " + getPermissionState("FOREGROUND_SERVICE_LOCATION"));
    }
    
    /**
     * Types of operations that require different permission levels
     */
    public enum OperationType {
        BASIC_TRACKING,      // Requires foreground location permissions
        BACKGROUND_TRACKING, // Requires background location permissions
        WORK_HOUR_TRACKING   // Requires background location permissions
    }
    
    /**
     * Container for detailed permission status
     */
    public static class PermissionStatus {
        public String location;
        public String backgroundLocation;
        public String foregroundService;
        
        public PermissionStatus() {
            this.location = "denied";
            this.backgroundLocation = "denied";
            this.foregroundService = "denied";
        }
    }
}