package com.elsapiens.backgroundlocation;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import com.google.android.gms.location.Priority;

/**
 * Manages location tracking services and coordinates between different tracking modes.
 * 
 * This class handles the lifecycle of task tracking and work hour tracking,
 * ensuring proper coordination and resource management.
 */
public class LocationTrackingManager {
    private static final String TAG = "LocationTrackingManager";
    
    // Service IDs for the location coordinator
    public static final String TASK_TRACKING_SERVICE_ID = "task_tracking";
    public static final String WORK_HOUR_SERVICE_ID = "work_hour_tracking";
    
    private final Context context;
    private final LocationCoordinator locationCoordinator;
    private final LocationDataManager dataManager;
    private final LocationPermissionManager permissionManager;
    
    // Tracking state
    private boolean isTaskTrackingActive = false;
    private boolean isWorkHourTrackingActive = false;
    private String currentTaskReference = null;
    private WorkHourLocationUploader workHourUploader = null;
    
    // Default configuration
    private TrackingConfiguration defaultTaskConfig = new TrackingConfiguration(
        3000L,    // 3 second interval
        10.0f,    // 10 meter minimum distance
        Priority.PRIORITY_HIGH_ACCURACY
    );
    
    private TrackingConfiguration defaultWorkHourConfig = new TrackingConfiguration(
        300000L,  // 5 minute interval
        50.0f,    // 50 meter minimum distance
        Priority.PRIORITY_BALANCED_POWER_ACCURACY
    );
    
    public LocationTrackingManager(Context context, LocationCoordinator coordinator, 
                                 LocationDataManager dataManager, LocationPermissionManager permissionManager) {
        this.context = context;
        this.locationCoordinator = coordinator;
        this.dataManager = dataManager;
        this.permissionManager = permissionManager;
    }
    
    /**
     * Start task-based location tracking
     * 
     * @param reference The task reference ID
     * @param config Custom tracking configuration (optional)
     * @return TrackingStartResult indicating success or failure
     */
    public TrackingStartResult startTaskTracking(String reference, TrackingConfiguration config) {
        Log.d(TAG, "Starting task tracking for reference: " + reference);
        
        // Validate permissions
        if (!permissionManager.hasPermissionsForOperation(LocationPermissionManager.OperationType.BASIC_TRACKING)) {
            return new TrackingStartResult(false, "Insufficient permissions for task tracking");
        }
        
        // Check if location services are enabled
        if (!isLocationEnabled()) {
            return new TrackingStartResult(false, "Location services are disabled. Please enable location services to start tracking.");
        }
        
        // Stop existing task tracking if active
        if (isTaskTrackingActive) {
            stopTaskTracking();
        }
        
        // Use provided config or default
        TrackingConfiguration trackingConfig = config != null ? config : defaultTaskConfig;
        
        try {
            // Create location service callback for task tracking
            LocationCoordinator.LocationServiceCallback taskCallback = new LocationCoordinator.LocationServiceCallback() {
                @Override
                public void onLocationReceived(Location location) {
                    handleTaskLocationUpdate(location, reference);
                }
            };
            
            // Register with location coordinator
            LocationCoordinator.LocationServiceInfo serviceInfo = new LocationCoordinator.LocationServiceInfo(
                trackingConfig.interval,
                trackingConfig.minDistance,
                trackingConfig.priority,
                taskCallback
            );
            
            locationCoordinator.registerService(TASK_TRACKING_SERVICE_ID, serviceInfo);
            
            // Start background service
            Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
            serviceIntent.putExtra("reference", reference);
            serviceIntent.putExtra("interval", trackingConfig.interval);
            serviceIntent.putExtra("minDistance", trackingConfig.minDistance);
            serviceIntent.putExtra("highAccuracy", trackingConfig.priority == Priority.PRIORITY_HIGH_ACCURACY);
            context.startForegroundService(serviceIntent);
            
            // Update state
            isTaskTrackingActive = true;
            currentTaskReference = reference;
            
            Log.d(TAG, "Task tracking started successfully");
            return new TrackingStartResult(true, "Task tracking started");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting task tracking", e);
            return new TrackingStartResult(false, "Error starting task tracking: " + e.getMessage());
        }
    }
    
    /**
     * Stop task-based location tracking
     * 
     * @return true if stopped successfully, false otherwise
     */
    public boolean stopTaskTracking() {
        Log.d(TAG, "Stopping task tracking");
        
        try {
            // Unregister from location coordinator
            locationCoordinator.unregisterService(TASK_TRACKING_SERVICE_ID);
            
            // Stop background service
            Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
            context.stopService(serviceIntent);
            
            // Update state
            isTaskTrackingActive = false;
            currentTaskReference = null;
            
            Log.d(TAG, "Task tracking stopped successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping task tracking", e);
            return false;
        }
    }
    
    /**
     * Start work hour location tracking
     * 
     * @param options Work hour tracking configuration
     * @return TrackingStartResult indicating success or failure
     */
    public TrackingStartResult startWorkHourTracking(WorkHourTrackingOptions options) {
        Log.d(TAG, "Starting work hour tracking for engineer: " + options.engineerId);
        
        // Validate permissions
        if (!permissionManager.hasPermissionsForOperation(LocationPermissionManager.OperationType.WORK_HOUR_TRACKING)) {
            return new TrackingStartResult(false, "Insufficient permissions for work hour tracking");
        }
        
        // Check if location services are enabled
        if (!isLocationEnabled()) {
            return new TrackingStartResult(false, "Location services are disabled. Please enable location services to start tracking.");
        }
        
        // Validate required parameters
        if (options.engineerId == null || options.engineerId.isEmpty()) {
            return new TrackingStartResult(false, "Engineer ID is required");
        }
        
        if (options.serverUrl == null || options.serverUrl.isEmpty()) {
            return new TrackingStartResult(false, "Server URL is required");
        }
        
        // Stop existing work hour tracking if active
        if (isWorkHourTrackingActive) {
            stopWorkHourTracking();
        }
        
        try {
            // Create work hour uploader
            workHourUploader = new WorkHourLocationUploader(
                options.engineerId,
                options.uploadInterval,
                options.serverUrl,
                options.authToken,
                options.enableOfflineQueue,
                null // Will be set via callback
            );
            
            // Create location service callback for work hour tracking
            LocationCoordinator.LocationServiceCallback workHourCallback = new LocationCoordinator.LocationServiceCallback() {
                @Override
                public void onLocationReceived(Location location) {
                    handleWorkHourLocationUpdate(location, options.engineerId);
                }
            };
            
            // Register with location coordinator
            LocationCoordinator.LocationServiceInfo serviceInfo = new LocationCoordinator.LocationServiceInfo(
                options.uploadInterval,
                defaultWorkHourConfig.minDistance,
                defaultWorkHourConfig.priority,
                workHourCallback
            );
            
            locationCoordinator.registerService(WORK_HOUR_SERVICE_ID, serviceInfo);
            
            // Update state
            isWorkHourTrackingActive = true;
            
            Log.d(TAG, "Work hour tracking started successfully");
            return new TrackingStartResult(true, "Work hour tracking started");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting work hour tracking", e);
            return new TrackingStartResult(false, "Error starting work hour tracking: " + e.getMessage());
        }
    }
    
    /**
     * Stop work hour location tracking
     * 
     * @return true if stopped successfully, false otherwise
     */
    public boolean stopWorkHourTracking() {
        Log.d(TAG, "Stopping work hour tracking");
        
        try {
            // Stop uploader
            if (workHourUploader != null) {
                workHourUploader.stop();
                workHourUploader = null;
            }
            
            // Unregister from location coordinator
            locationCoordinator.unregisterService(WORK_HOUR_SERVICE_ID);
            
            // Update state
            isWorkHourTrackingActive = false;
            
            Log.d(TAG, "Work hour tracking stopped successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping work hour tracking", e);
            return false;
        }
    }
    
    /**
     * Handle location updates for task tracking
     */
    private void handleTaskLocationUpdate(Location location, String reference) {
        LocationDataManager.LocationProcessingResult result = dataManager.processLocationUpdate(
            location, reference, defaultTaskConfig.minDistance
        );
        
        if (result.shouldSave && result.index != null) {
            // Notify listeners about the location update
            // This would typically be handled by the main plugin class
            Log.d(TAG, "Task location processed: " + result.message);
        }
    }
    
    /**
     * Handle location updates for work hour tracking
     */
    private void handleWorkHourLocationUpdate(Location location, String engineerId) {
        if (workHourUploader != null) {
            workHourUploader.addLocationToQueue(location);
            Log.d(TAG, "Work hour location queued for engineer: " + engineerId);
        }
    }
    
    // Getters for current state
    public boolean isTaskTrackingActive() { return isTaskTrackingActive; }
    public boolean isWorkHourTrackingActive() { return isWorkHourTrackingActive; }
    public String getCurrentTaskReference() { return currentTaskReference; }
    public WorkHourLocationUploader getWorkHourUploader() { return workHourUploader; }
    
    /**
     * Configuration for location tracking
     */
    public static class TrackingConfiguration {
        public final long interval;
        public final float minDistance;
        public final int priority;
        
        public TrackingConfiguration(long interval, float minDistance, int priority) {
            this.interval = interval;
            this.minDistance = minDistance;
            this.priority = priority;
        }
    }
    
    /**
     * Options for work hour tracking
     */
    public static class WorkHourTrackingOptions {
        public final String engineerId;
        public final long uploadInterval;
        public final String serverUrl;
        public final String authToken;
        public final boolean enableOfflineQueue;
        
        public WorkHourTrackingOptions(String engineerId, long uploadInterval, String serverUrl, 
                                     String authToken, boolean enableOfflineQueue) {
            this.engineerId = engineerId;
            this.uploadInterval = uploadInterval;
            this.serverUrl = serverUrl;
            this.authToken = authToken;
            this.enableOfflineQueue = enableOfflineQueue;
        }
    }
    
    /**
     * Check if location services are enabled on the device
     * 
     * @return true if location services are enabled, false otherwise
     */
    private boolean isLocationEnabled() {
        android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null
            && (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));
    }
    
    
    /**
     * Result of starting a tracking operation
     */
    public static class TrackingStartResult {
        public final boolean success;
        public final String message;
        
        public TrackingStartResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}