package com.elsapiens.backgroundlocation;

import android.location.Location;
import android.util.Log;
import com.getcapacitor.JSObject;

/**
 * Handles location data processing, validation, and database operations.
 * 
 * This class centralizes all location data-related operations including validation,
 * storage, retrieval, and conversion between different formats.
 */
public class LocationDataManager {
    private static final String TAG = "LocationDataManager";
    
    private final SQLiteDatabaseHelper database;
    private Location lastSavedLocation;
    
    // Default thresholds for location filtering
    private static final float DEFAULT_MIN_DISTANCE_METERS = 10.0f;
    private static final float DEFAULT_SIGNIFICANT_TURN_DEGREES = 30.0f;
    
    public LocationDataManager(SQLiteDatabaseHelper database) {
        this.database = database;
    }
    
    /**
     * Validate and process a new location update
     * 
     * @param location The new location to process
     * @param reference The tracking reference ID
     * @param minDistance Minimum distance threshold in meters
     * @return LocationProcessingResult indicating whether location should be saved
     */
    public LocationProcessingResult processLocationUpdate(Location location, String reference, float minDistance) {
        if (location == null) {
            return new LocationProcessingResult(false, "Location is null");
        }
        
        if (!isLocationValid(location)) {
            return new LocationProcessingResult(false, "Location failed validation checks");
        }
        
        if (!shouldSaveLocation(location, minDistance)) {
            return new LocationProcessingResult(false, "Location doesn't meet distance/movement criteria");
        }
        
        // Location is valid and should be saved
        int index = database.getNextIndexForReference(reference);
        saveLocationToDatabase(location, reference, index);
        updateLastSavedLocation(location);
        
        Log.d(TAG, String.format("Location saved: %.6f, %.6f (accuracy: %.1fm, reference: %s)", 
            location.getLatitude(), location.getLongitude(), location.getAccuracy(), reference));
            
        return new LocationProcessingResult(true, "Location saved successfully", index);
    }
    
    /**
     * Check if a location meets basic validation criteria
     * 
     * @param location The location to validate
     * @return true if location is valid, false otherwise
     */
    public boolean isLocationValid(Location location) {
        if (location == null) return false;
        
        // Check for valid coordinates
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        
        if (lat == 0.0 && lng == 0.0) return false;
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return false;
        
        // Check accuracy - reject locations with very poor accuracy
        if (location.hasAccuracy() && location.getAccuracy() > 100) {
            Log.w(TAG, "Location rejected due to poor accuracy: " + location.getAccuracy() + "m");
            return false;
        }
        
        // Check timestamp - reject very old locations
        long age = System.currentTimeMillis() - location.getTime();
        if (age > 30000) { // 30 seconds
            Log.w(TAG, "Location rejected due to age: " + age + "ms");
            return false;
        }
        
        return true;
    }
    
    /**
     * Determine if a location should be saved based on movement criteria
     * 
     * @param newLocation The new location to evaluate
     * @param minDistance Minimum distance threshold in meters
     * @return true if location should be saved, false otherwise
     */
    public boolean shouldSaveLocation(Location newLocation, float minDistance) {
        if (lastSavedLocation == null) {
            return true; // Always save first location
        }
        
        float distance = lastSavedLocation.distanceTo(newLocation);
        
        // Save if moved at least minimum distance or turned significantly
        return distance >= minDistance || hasSignificantDirectionChange(lastSavedLocation, newLocation);
    }
    
    /**
     * Check if there has been a significant change in direction
     * 
     * @param oldLocation Previous location
     * @param newLocation Current location
     * @return true if direction change is significant
     */
    private boolean hasSignificantDirectionChange(Location oldLocation, Location newLocation) {
        if (!oldLocation.hasBearing() || !newLocation.hasBearing()) {
            return false;
        }
        
        float bearingChange = Math.abs(oldLocation.getBearing() - newLocation.getBearing());
        
        // Handle bearing wrap-around (0-360 degrees)
        if (bearingChange > 180) {
            bearingChange = 360 - bearingChange;
        }
        
        return bearingChange > DEFAULT_SIGNIFICANT_TURN_DEGREES;
    }
    
    /**
     * Save location to database
     * 
     * @param location The location to save
     * @param reference The tracking reference ID
     * @param index The sequence index for this reference
     */
    private void saveLocationToDatabase(Location location, String reference, int index) {
        try {
            database.insertLocation(
                reference,
                index,
                location.getLatitude(),
                location.getLongitude(),
                (float) location.getAltitude(),
                location.getAccuracy(),
                location.getSpeed(),
                location.getBearing(),
                location.getVerticalAccuracyMeters(),
                location.getTime()
            );
        } catch (Exception e) {
            Log.e(TAG, "Error saving location to database", e);
        }
    }
    
    /**
     * Update the last saved location reference
     * 
     * @param location The location to set as last saved
     */
    private void updateLastSavedLocation(Location location) {
        this.lastSavedLocation = location;
    }
    
    /**
     * Convert Location object to JSObject for Capacitor
     * 
     * @param location The location to convert
     * @param reference The tracking reference ID
     * @param index The sequence index
     * @return JSObject representation of the location
     */
    public JSObject locationToJSObject(Location location, String reference, int index) {
        JSObject data = new JSObject();
        data.put("reference", reference);
        data.put("index", index);
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("altitude", location.getAltitude());
        data.put("accuracy", location.getAccuracy());
        data.put("speed", location.getSpeed());
        data.put("heading", location.getBearing());
        data.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        data.put("timestamp", location.getTime());
        
        // Add calculated total distance for this reference
        try {
            double totalDistance = database.getTotalDistanceForReference(reference);
            data.put("totalDistance", totalDistance);
        } catch (Exception e) {
            Log.w(TAG, "Could not calculate total distance", e);
            data.put("totalDistance", 0.0);
        }
        
        return data;
    }
    
    /**
     * Convert database LocationItem to JSObject
     * 
     * @param locationItem The location item from database
     * @return JSObject representation of the location
     */
    public JSObject locationItemToJSObject(LocationItem locationItem) {
        JSObject data = new JSObject();
        data.put("reference", locationItem.reference);
        data.put("index", locationItem.index);
        data.put("latitude", locationItem.latitude);
        data.put("longitude", locationItem.longitude);
        data.put("altitude", locationItem.altitude);
        data.put("accuracy", locationItem.accuracy);
        data.put("speed", locationItem.speed);
        data.put("heading", locationItem.heading);
        data.put("altitudeAccuracy", locationItem.altitudeAccuracy);
        data.put("timestamp", locationItem.timestamp);
        
        // Add calculated total distance
        try {
            double totalDistance = database.getTotalDistanceForReference(locationItem.reference);
            data.put("totalDistance", totalDistance);
        } catch (Exception e) {
            Log.w(TAG, "Could not calculate total distance for item", e);
            data.put("totalDistance", 0.0);
        }
        
        return data;
    }
    
    /**
     * Get the last saved location
     * 
     * @return The last location that was saved, or null if none
     */
    public Location getLastSavedLocation() {
        return lastSavedLocation;
    }
    
    /**
     * Clear stored locations for a specific reference
     * 
     * @param reference The reference ID to clear, or null to clear all
     */
    public void clearStoredLocations(String reference) {
        try {
            if (reference != null) {
                // Clear specific reference (method would need to be added to database helper)
                Log.d(TAG, "Clearing locations for reference: " + reference);
                // database.clearLocationsForReference(reference);
            } else {
                // Clear all locations
                database.clearStoredLocations();
                Log.d(TAG, "All stored locations cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing stored locations", e);
        }
    }
    
    /**
     * Result of location processing operation
     */
    public static class LocationProcessingResult {
        public final boolean shouldSave;
        public final String message;
        public final Integer index;
        
        public LocationProcessingResult(boolean shouldSave, String message) {
            this.shouldSave = shouldSave;
            this.message = message;
            this.index = null;
        }
        
        public LocationProcessingResult(boolean shouldSave, String message, int index) {
            this.shouldSave = shouldSave;
            this.message = message;
            this.index = index;
        }
    }
}