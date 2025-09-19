package com.elsapiens.backgroundlocation;

import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.location.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Coordinates multiple location services to prevent conflicts and optimize battery usage
 */
public class LocationCoordinator {
    private static final String TAG = "LocationCoordinator";
    private static LocationCoordinator instance;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback coordinatedLocationCallback;
    private Map<String, LocationServiceInfo> activeServices;
    private boolean isCoordinatedTrackingActive = false;
    
    // Current optimal location request parameters
    private long optimalInterval = Long.MAX_VALUE;
    private float optimalMinDistance = Float.MAX_VALUE;
    private int optimalPriority = Priority.PRIORITY_LOW_POWER;
    
    private LocationCoordinator(FusedLocationProviderClient client) {
        this.fusedLocationClient = client;
        this.activeServices = new HashMap<>();
        setupCoordinatedCallback();
    }
    
    public static synchronized LocationCoordinator getInstance(FusedLocationProviderClient client) {
        if (instance == null) {
            instance = new LocationCoordinator(client);
        }
        return instance;
    }
    
    /**
     * Register a location service with its requirements
     */
    public synchronized void registerService(String serviceId, LocationServiceInfo serviceInfo) {
        Log.d(TAG, "Registering location service: " + serviceId);
        activeServices.put(serviceId, serviceInfo);
        updateLocationRequest();
    }
    
    /**
     * Unregister a location service
     */
    public synchronized void unregisterService(String serviceId) {
        Log.d(TAG, "Unregistering location service: " + serviceId);
        activeServices.remove(serviceId);
        updateLocationRequest();
    }
    
    /**
     * Check if any services are active
     */
    public boolean hasActiveServices() {
        return !activeServices.isEmpty();
    }
    
    /**
     * Update the location request based on all active services' requirements
     */
    private void updateLocationRequest() {
        if (activeServices.isEmpty()) {
            stopCoordinatedTracking();
            return;
        }
        
        // Calculate optimal parameters from all active services
        calculateOptimalParameters();
        
        // Restart location tracking with new parameters
        if (isCoordinatedTrackingActive) {
            stopCoordinatedTracking();
        }
        startCoordinatedTracking();
    }
    
    private void calculateOptimalParameters() {
        optimalInterval = Long.MAX_VALUE;
        optimalMinDistance = Float.MAX_VALUE;
        optimalPriority = Priority.PRIORITY_LOW_POWER;
        
        for (LocationServiceInfo info : activeServices.values()) {
            // Use the most frequent interval (smallest value)
            if (info.interval < optimalInterval) {
                optimalInterval = info.interval;
            }
            
            // Use the smallest minimum distance
            if (info.minDistance < optimalMinDistance) {
                optimalMinDistance = info.minDistance;
            }
            
            // Use the highest priority (highest accuracy)
            if (info.priority > optimalPriority) {
                optimalPriority = info.priority;
            }
        }
        
        Log.d(TAG, "Optimal parameters - Interval: " + optimalInterval + 
              ", MinDistance: " + optimalMinDistance + 
              ", Priority: " + optimalPriority);
    }
    
    private void startCoordinatedTracking() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                optimalPriority,
                optimalInterval
            )
            .setMinUpdateDistanceMeters(optimalMinDistance)
            .setMaxUpdateAgeMillis(optimalInterval)
            .setWaitForAccurateLocation(optimalPriority == Priority.PRIORITY_HIGH_ACCURACY)
            .build();
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest, 
                coordinatedLocationCallback, 
                Looper.getMainLooper()
            );
            
            isCoordinatedTrackingActive = true;
            Log.d(TAG, "Coordinated location tracking started");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
    }
    
    private void stopCoordinatedTracking() {
        if (fusedLocationClient != null && coordinatedLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(coordinatedLocationCallback);
        }
        isCoordinatedTrackingActive = false;
        Log.d(TAG, "Coordinated location tracking stopped");
    }
    
    private void setupCoordinatedCallback() {
        coordinatedLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Distribute location to all registered services
                    distributeLocationToServices(location);
                }
            }
        };
    }
    
    /**
     * Distribute received location to all registered services based on their filters
     */
    private void distributeLocationToServices(Location location) {
        for (Map.Entry<String, LocationServiceInfo> entry : activeServices.entrySet()) {
            String serviceId = entry.getKey();
            LocationServiceInfo info = entry.getValue();
            
            // Check if this service should receive this location update
            if (shouldDeliverToService(location, info)) {
                try {
                    info.callback.onLocationReceived(location);
                    Log.d(TAG, "Location delivered to service: " + serviceId);
                } catch (Exception e) {
                    Log.e(TAG, "Error delivering location to service: " + serviceId, e);
                }
            }
        }
    }
    
    /**
     * Determine if a location should be delivered to a specific service
     */
    private boolean shouldDeliverToService(Location location, LocationServiceInfo info) {
        // Check if enough time has passed since last delivery
        long timeDiff = System.currentTimeMillis() - info.lastDeliveryTime;
        if (timeDiff < info.interval) {
            return false;
        }
        
        // Check if location has moved enough
        if (info.lastDeliveredLocation != null) {
            float distance = location.distanceTo(info.lastDeliveredLocation);
            if (distance < info.minDistance) {
                return false;
            }
        }
        
        // Update delivery tracking
        info.lastDeliveryTime = System.currentTimeMillis();
        info.lastDeliveredLocation = location;
        
        return true;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stopCoordinatedTracking();
        activeServices.clear();
        instance = null;
    }
    
    /**
     * Interface for services to receive location updates
     */
    public interface LocationServiceCallback {
        void onLocationReceived(Location location);
    }
    
    /**
     * Information about a registered location service
     */
    public static class LocationServiceInfo {
        public final long interval;
        public final float minDistance;
        public final int priority;
        public final LocationServiceCallback callback;
        
        // Tracking for delivery filtering
        public long lastDeliveryTime = 0;
        public Location lastDeliveredLocation = null;
        
        public LocationServiceInfo(long interval, float minDistance, int priority, LocationServiceCallback callback) {
            this.interval = interval;
            this.minDistance = minDistance;
            this.priority = priority;
            this.callback = callback;
        }
    }
}