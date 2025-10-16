package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.*;

import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * Capacitor plugin for background location tracking with task-based and work hour tracking capabilities.
 * 
 * This plugin provides comprehensive location tracking functionality including:
 * - Task-based location tracking with detailed route recording
 * - Work hour tracking with periodic server uploads
 * - Intelligent coordination between multiple tracking modes
 * - Comprehensive permission management
 * - Battery-optimized location updates
 * 
 * @version 1.0.0
 * @author Elsapiens Team
 */
@CapacitorPlugin(name = "BackgroundLocation", permissions = {
        @Permission(alias = "foregroundLocation", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }),
        @Permission(alias = "foregroundLocationNew", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }),
        @Permission(alias = "backgroundLocation", strings = {
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        })
})
public class BackgroundLocationPlugin extends Plugin {
    private static final String TAG = "BackgroundLocationPlugin";
    
    // Singleton instance for service access
    private static BackgroundLocationPlugin instance;
    
    // Core components
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCoordinator locationCoordinator;
    private LocationPermissionManager permissionManager;
    private LocationDataManager dataManager;
    private LocationTrackingManager trackingManager;
    
    // Database and receivers
    private SQLiteDatabaseHelper database;
    private LocationBroadcastReceiver locationReceiver;
    private LocationStateReceiver locationStateReceiver;
    
    // Work hour tracking state
    private List<BackgroundLocationPlugin.WorkHourLocationData> queuedWorkHourLocations = new ArrayList<>();
    
    public BackgroundLocationPlugin() {
        instance = this;
    }
    
    /**
     * Get the current plugin instance (for service access)
     * 
     * @return Current plugin instance or null if not initialized
     */
    public static BackgroundLocationPlugin getInstance() {
        return instance;
    }
    
    public static BackgroundLocationPlugin getCurrentInstance() {
        return instance;
    }
    
    @Override
    public void load() {
        super.load();
        
        try {
            Context context = getContext();
            Log.d(TAG, "Initializing BackgroundLocationPlugin");
            
            // Initialize core components
            initializeCoreComponents(context);
            
            // Register broadcast receiver
            registerLocationReceiver(context);
            
            Log.d(TAG, "BackgroundLocationPlugin initialization completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during plugin initialization", e);
        }
    }
    
    /**
     * Initialize all core components and dependencies
     */
    private void initializeCoreComponents(Context context) {
        // Initialize database
        database = new SQLiteDatabaseHelper(context);
        
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        locationCoordinator = LocationCoordinator.getInstance(fusedLocationClient);
        
        // Initialize managers
        permissionManager = new LocationPermissionManager(context);
        dataManager = new LocationDataManager(database);
        trackingManager = new LocationTrackingManager(context, locationCoordinator, dataManager, permissionManager);
        
        Log.d(TAG, "Core components initialized successfully");
    }
    
    /**
     * Register location broadcast receiver
     */
    private void registerLocationReceiver(Context context) {
        locationReceiver = new LocationBroadcastReceiver();
        IntentFilter filter = new IntentFilter("BackgroundLocationUpdate");
        ContextCompat.registerReceiver(context, locationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "LocationBroadcastReceiver registered");
    }
    
    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        
        try {
            Log.d(TAG, "Cleaning up BackgroundLocationPlugin");
            
            // Clean up location coordinator
            if (locationCoordinator != null) {
                locationCoordinator.cleanup();
            }
            
            // Stop all tracking
            if (trackingManager != null) {
                trackingManager.stopTaskTracking();
                trackingManager.stopWorkHourTracking();
            }
            
            // Unregister receivers
            Context context = getContext();
            if (locationReceiver != null) {
                context.unregisterReceiver(locationReceiver);
            }
            if (locationStateReceiver != null) {
                try {
                    context.unregisterReceiver(locationStateReceiver);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Location state receiver not registered", e);
                }
            }
            
            Log.d(TAG, "Plugin cleanup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during plugin cleanup", e);
        }
    }
    
    // =================================================================================
    // PERMISSION MANAGEMENT METHODS
    // =================================================================================
    
    /**
     * Check current permission status for all location-related permissions
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        try {
            LocationPermissionManager.PermissionStatus status = permissionManager.getDetailedPermissionStatus();
            
            JSObject result = new JSObject();
            result.put("location", status.location);
            result.put("backgroundLocation", status.backgroundLocation);
            result.put("foregroundService", status.foregroundService);
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
            call.reject("Error checking permissions: " + e.getMessage());
        }
    }
    
    /**
     * Request location permissions from the user
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        try {
            Log.d(TAG, "Requesting permissions...");
            
            if (!permissionManager.hasLocationPermissions()) {
                Log.d(TAG, "Requesting foreground location permissions");
                requestLocationPermissions(call);
            } else if (!permissionManager.hasBackgroundLocationPermission()) {
                Log.d(TAG, "Requesting background location permission");
                requestBackgroundPermission(call);
            } else {
                // All permissions already granted
                Log.d(TAG, "All permissions already granted");
                checkPermissions(call);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting permissions", e);
            call.reject("Error requesting permissions: " + e.getMessage());
        }
    }
    
    @PermissionCallback
    private void requestLocationPermissions(PluginCall call) {
        Log.d(TAG, "requestLocationPermissions called, SDK_INT: " + android.os.Build.VERSION.SDK_INT);
        if (android.os.Build.VERSION.SDK_INT >= 34) { // Android 14+
            Log.d(TAG, "Requesting foregroundLocationNew permissions");
            requestPermissionForAlias("foregroundLocationNew", call, "requestBackgroundPermission");
        } else {
            Log.d(TAG, "Requesting foregroundLocation permissions");
            requestPermissionForAlias("foregroundLocation", call, "requestBackgroundPermission");
        }
    }
    
    @PermissionCallback
    private void requestBackgroundPermission(PluginCall call) {
        Log.d(TAG, "requestBackgroundPermission called");
        requestPermissionForAlias("backgroundLocation", call, "checkPermissions");
    }
    
    /**
     * Check if device location services are enabled
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void isLocationServiceEnabled(PluginCall call) {
        try {
            JSObject result = isLocationEnabled();
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking location service status", e);
            call.reject("Error checking location service status: " + e.getMessage());
        }
    }
    
    /**
     * Open device location settings
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void openLocationSettings(PluginCall call) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening location settings", e);
            call.reject("Error opening location settings: " + e.getMessage());
        }
    }
    
    // =================================================================================
    // TASK TRACKING METHODS
    // =================================================================================
    
    /**
     * Start task-based location tracking
     * 
     * @param call Capacitor plugin call with parameters: reference, interval, minDistance, highAccuracy
     */
    @PluginMethod
    public void startTracking(PluginCall call) {
        try {
            // Validate required parameters
            if (!call.getData().has("reference")) {
                call.reject("Missing required 'reference' parameter");
                return;
            }
            
            // Check permissions before starting tracking
            if (!permissionManager.hasLocationPermissions()) {
                call.reject("Location permissions not granted. Please request permissions first.");
                return;
            }
            
            String reference = call.getString("reference");
            long interval = call.getLong("interval", 3000L);
            float minDistance = call.getFloat("minDistance", 10.0f);
            boolean highAccuracy = call.getBoolean("highAccuracy", true);
            
            // Create tracking configuration
            int priority = highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            LocationTrackingManager.TrackingConfiguration config = 
                new LocationTrackingManager.TrackingConfiguration(interval, minDistance, priority);
            
            // Start tracking
            LocationTrackingManager.TrackingStartResult result = trackingManager.startTaskTracking(reference, config);
            
            if (result.success) {
                call.resolve();
            } else {
                call.reject(result.message);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting task tracking", e);
            call.reject("Error starting task tracking: " + e.getMessage());
        }
    }
    
    /**
     * Stop task-based location tracking
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void stopTracking(PluginCall call) {
        try {
            boolean success = trackingManager.stopTaskTracking();
            
            if (success) {
                call.resolve();
            } else {
                call.reject("Failed to stop task tracking");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping task tracking", e);
            call.reject("Error stopping task tracking: " + e.getMessage());
        }
    }
    
    // =================================================================================
    // WORK HOUR TRACKING METHODS  
    // =================================================================================
    
    /**
     * Start work hour tracking with periodic server uploads
     * 
     * @param call Capacitor plugin call with parameters: engineerId, uploadInterval, serverUrl, authToken, enableOfflineQueue
     */
    @PluginMethod
    public void startWorkHourTracking(PluginCall call) {
        try {
            // Check permissions before starting tracking
            if (!permissionManager.hasLocationPermissions()) {
                call.reject("Location permissions not granted. Please request permissions first.");
                return;
            }
            
            // Validate required parameters
            String engineerId = call.getString("engineerId");
            if (engineerId == null || engineerId.isEmpty()) {
                call.reject("Missing required 'engineerId' parameter");
                return;
            }
            
            String serverUrl = call.getString("serverUrl");
            if (serverUrl == null || serverUrl.isEmpty()) {
                call.reject("Missing required 'serverUrl' parameter");
                return;
            }
            
            // Get optional parameters
            long uploadInterval = call.getLong("uploadInterval", 300000L); // 5 minutes default
            String authToken = call.getString("authToken");
            boolean enableOfflineQueue = call.getBoolean("enableOfflineQueue", true);
            
            // Create work hour tracking options
            LocationTrackingManager.WorkHourTrackingOptions options = 
                new LocationTrackingManager.WorkHourTrackingOptions(
                    engineerId, uploadInterval, serverUrl, authToken, enableOfflineQueue
                );
            
            // Start work hour tracking
            LocationTrackingManager.TrackingStartResult result = trackingManager.startWorkHourTracking(options);
            
            if (result.success) {
                call.resolve();
            } else {
                call.reject(result.message);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting work hour tracking", e);
            call.reject("Error starting work hour tracking: " + e.getMessage());
        }
    }
    
    /**
     * Stop work hour tracking
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void stopWorkHourTracking(PluginCall call) {
        try {
            boolean success = trackingManager.stopWorkHourTracking();
            
            if (success) {
                call.resolve();
            } else {
                call.reject("Failed to stop work hour tracking");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping work hour tracking", e);
            call.reject("Error stopping work hour tracking: " + e.getMessage());
        }
    }
    
    /**
     * Check if work hour tracking is currently active
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void isWorkHourTrackingActive(PluginCall call) {
        try {
            JSObject result = new JSObject();
            result.put("active", trackingManager.isWorkHourTrackingActive());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking work hour tracking status", e);
            call.reject("Error checking work hour tracking status: " + e.getMessage());
        }
    }
    
    /**
     * Get queued work hour locations that haven't been uploaded yet
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void getQueuedWorkHourLocations(PluginCall call) {
        try {
            JSArray locations = new JSArray();
            
            for (WorkHourLocationData location : queuedWorkHourLocations) {
                JSObject locationObj = new JSObject();
                locationObj.put("latitude", location.latitude);
                locationObj.put("longitude", location.longitude);
                locationObj.put("accuracy", location.accuracy);
                locationObj.put("timestamp", location.timestamp);
                locationObj.put("engineerId", location.engineerId);
                locations.put(locationObj);
            }
            
            JSObject result = new JSObject();
            result.put("locations", locations);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting queued work hour locations", e);
            call.reject("Error getting queued work hour locations: " + e.getMessage());
        }
    }
    
    /**
     * Clear all queued work hour locations
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void clearQueuedWorkHourLocations(PluginCall call) {
        try {
            queuedWorkHourLocations.clear();
            call.resolve();
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing queued work hour locations", e);
            call.reject("Error clearing queued work hour locations: " + e.getMessage());
        }
    }
    
    // =================================================================================
    // LOCATION DATA METHODS
    // =================================================================================
    
    /**
     * Get current device location
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void getCurrentLocation(PluginCall call) {
        try {
            if (!permissionManager.hasLocationPermissions()) {
                call.reject("Location permissions not granted");
                return;
            }
            
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        JSObject result = new JSObject();
                        result.put("latitude", location.getLatitude());
                        result.put("longitude", location.getLongitude());
                        result.put("accuracy", location.getAccuracy());
                        result.put("altitude", location.getAltitude());
                        result.put("speed", location.getSpeed());
                        result.put("heading", location.getBearing());
                        result.put("timestamp", location.getTime());
                        call.resolve(result);
                    } else {
                        call.reject("Failed to get current location");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting current location", e);
                    call.reject("Error getting current location: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error in getCurrentLocation", e);
            call.reject("Error getting current location: " + e.getMessage());
        }
    }
    
    /**
     * Get stored locations for a specific reference
     * 
     * @param call Capacitor plugin call with parameter: reference
     */
    @PluginMethod
    public void getStoredLocations(PluginCall call) {
        try {
            if (!call.getData().has("reference")) {
                call.reject("Missing required 'reference' parameter");
                return;
            }
            
            String reference = call.getString("reference");
            JSObject result = new JSObject();
            result.put("locations", database.getLocationsForReference(reference));
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting stored locations", e);
            call.reject("Error getting stored locations: " + e.getMessage());
        }
    }
    
    /**
     * Clear all stored locations
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void clearStoredLocations(PluginCall call) {
        try {
            dataManager.clearStoredLocations(null);
            call.resolve();
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing stored locations", e);
            call.reject("Error clearing stored locations: " + e.getMessage());
        }
    }
    
    /**
     * Get the last location for a specific reference
     * 
     * @param call Capacitor plugin call with parameter: reference
     */
    @PluginMethod
    public void getLastLocation(PluginCall call) {
        try {
            if (!call.getData().has("reference")) {
                call.reject("Missing required 'reference' parameter");
                return;
            }
            
            String reference = call.getString("reference");
            LocationItem location = database.getLastLocation(reference);
            
            if (location != null) {
                JSObject result = dataManager.locationItemToJSObject(location);
                notifyListeners("locationUpdate", result);
                call.resolve(result);
            } else {
                call.reject("No location found for reference: " + reference);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting last location", e);
            call.reject("Error getting last location: " + e.getMessage());
        }
    }
    
    // =================================================================================
    // LOCATION STATUS METHODS
    // =================================================================================
    
    /**
     * Start monitoring location service status changes
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void startLocationStatusTracking(PluginCall call) {
        try {
            if (!permissionManager.hasLocationPermissions()) {
                requestLocationStatusPermissions(call);
                return;
            }
            
            if (locationStateReceiver == null) {
                locationStateReceiver = new LocationStateReceiver();
                IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
                getContext().registerReceiver(locationStateReceiver, filter);
            }
            
            // Send initial status
            try {
                pushLocationStateToCapacitor(isLocationEnabled().getBoolean("enabled"));
            } catch (JSONException e) {
                pushLocationStateToCapacitor(false);
            }
            
            call.resolve();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting location status tracking", e);
            call.reject("Error starting location status tracking: " + e.getMessage());
        }
    }
    
    @PermissionCallback
    private void requestLocationStatusPermissions(PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= 34) { // Android 14+
            requestPermissionForAlias("foregroundLocationNew", call, "startLocationStatusTracking");
        } else {
            requestPermissionForAlias("foregroundLocation", call, "startLocationStatusTracking");
        }
    }
    
    /**
     * Stop monitoring location service status changes
     * 
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void stopLocationStatusTracking(PluginCall call) {
        try {
            if (locationStateReceiver != null) {
                getContext().unregisterReceiver(locationStateReceiver);
                locationStateReceiver = null;
            }
            call.resolve();
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping location status tracking", e);
            call.reject("Error stopping location status tracking: " + e.getMessage());
        }
    }
    
    // =================================================================================
    // UTILITY METHODS
    // =================================================================================
    
    /**
     * Check if device location services are enabled
     * 
     * @return JSObject with enabled status
     */
    public JSObject isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager != null && 
            (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
             locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        
        JSObject data = new JSObject();
        data.put("enabled", isLocationEnabled);
        return data;
    }
    
    /**
     * Notify Capacitor listeners about location status changes
     * 
     * @param status Location service enabled status
     */
    public void pushLocationStateToCapacitor(boolean status) {
        JSObject data = new JSObject();
        data.put("enabled", status);
        notifyListeners("locationStatus", data);
    }
    
    /**
     * Push location update to Capacitor (called by LocationBroadcastReceiver)
     *
     * @param locationItem Location item from background service
     */
    public void pushUpdateToCapacitor(LocationItem locationItem) {
        JSObject result = new JSObject();
        result.put("reference", locationItem.reference);
        result.put("index", locationItem.index);
        result.put("latitude", locationItem.latitude);
        result.put("longitude", locationItem.longitude);
        result.put("altitude", locationItem.altitude);
        result.put("accuracy", locationItem.accuracy);
        result.put("speed", locationItem.speed);
        result.put("heading", locationItem.heading);
        result.put("altitudeAccuracy", locationItem.altitudeAccuracy);
        result.put("totalDistance", locationItem.totalDistance);
        result.put("timestamp", locationItem.timestamp);

        notifyListeners("locationUpdate", result);
    }

    /**
     * Add work hour location to queue (called by WorkHourLocationUploader)
     * 
     * @param location Work hour location data
     */
    public void addToWorkHourQueue(WorkHourLocationData location) {
        queuedWorkHourLocations.add(location);
        
        // Notify Capacitor about new work hour location
        JSObject data = new JSObject();
        data.put("latitude", location.latitude);
        data.put("longitude", location.longitude);
        data.put("accuracy", location.accuracy);
        data.put("timestamp", location.timestamp);
        data.put("engineerId", location.engineerId);
        notifyListeners("workHourLocationUpdate", data);
    }
    
    /**
     * Remove uploaded locations from queue (called by WorkHourLocationUploader)
     * 
     * @param uploadedLocations Locations that were successfully uploaded
     */
    public void removeFromWorkHourQueue(List<WorkHourLocationData> uploadedLocations) {
        queuedWorkHourLocations.removeAll(uploadedLocations);
    }
    
    // =================================================================================
    // DATA CLASSES
    // =================================================================================
    
    /**
     * Work hour location data container
     */
    public static class WorkHourLocationData {
        public double latitude;
        public double longitude;
        public float accuracy;
        public long timestamp;
        public String engineerId;
        
        public WorkHourLocationData(double latitude, double longitude, float accuracy, long timestamp, String engineerId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.timestamp = timestamp;
            this.engineerId = engineerId;
        }
    }
}