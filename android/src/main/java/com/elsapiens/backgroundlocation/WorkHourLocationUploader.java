package com.elsapiens.backgroundlocation;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles periodic location uploads for work hour tracking
 */
public class WorkHourLocationUploader {
    private static final String TAG = "WorkHourUploader";
    
    private final String engineerId;
    private final long uploadInterval;
    private final String serverUrl;
    private final String authToken;
    private final boolean enableOfflineQueue;
    private final BackgroundLocationPlugin plugin;
    
    private Handler handler;
    private Runnable uploadRunnable;
    private ExecutorService executor;
    private boolean isActive = false;
    
    public WorkHourLocationUploader(String engineerId, long uploadInterval, String serverUrl, 
                                   String authToken, boolean enableOfflineQueue, BackgroundLocationPlugin plugin) {
        this.engineerId = engineerId;
        this.uploadInterval = uploadInterval;
        this.serverUrl = serverUrl;
        this.authToken = authToken;
        this.enableOfflineQueue = enableOfflineQueue;
        this.plugin = plugin;
        
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        
        setupUploadRunnable();
        start();
    }
    
    private void setupUploadRunnable() {
        uploadRunnable = new Runnable() {
            @Override
            public void run() {
                if (isActive) {
                    // Get current location and add to queue
                    getCurrentLocationAndQueue();
                    
                    // Try to upload queued locations
                    uploadQueuedLocations();
                    
                    // Schedule next upload
                    handler.postDelayed(this, uploadInterval);
                }
            }
        };
    }
    
    private void start() {
        isActive = true;
        handler.post(uploadRunnable);
        Log.d(TAG, "Work hour location uploader started with interval: " + uploadInterval + "ms");
    }
    
    public void stop() {
        isActive = false;
        if (handler != null && uploadRunnable != null) {
            handler.removeCallbacks(uploadRunnable);
        }
        if (executor != null) {
            executor.shutdown();
        }
        Log.d(TAG, "Work hour location uploader stopped");
    }
    
    private void getCurrentLocationAndQueue() {
        executor.execute(() -> {
            try {
                // This would be called from the service that has access to location
                // For now, we'll let the service handle adding locations to the queue
                Log.d(TAG, "Location queuing handled by service");
            } catch (Exception e) {
                Log.e(TAG, "Error queuing location: " + e.getMessage());
            }
        });
    }
    
    public void addLocationToQueue(Location location) {
        BackgroundLocationPlugin.WorkHourLocationData locationData = 
            new BackgroundLocationPlugin.WorkHourLocationData(
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getTime(),
                engineerId
            );
        
        plugin.addToWorkHourQueue(locationData);
        Log.d(TAG, "Added location to work hour queue: " + location.getLatitude() + ", " + location.getLongitude());
    }
    
    private void uploadQueuedLocations() {
        executor.execute(() -> {
            try {
                List<BackgroundLocationPlugin.WorkHourLocationData> locationsToUpload = new ArrayList<>();
                
                // Get queued locations from plugin (this would need to be implemented)
                // For now, we'll assume the plugin provides access to queued locations
                
                if (locationsToUpload.isEmpty()) {
                    Log.d(TAG, "No locations to upload");
                    return;
                }
                
                boolean uploadSuccess = uploadLocationsToServer(locationsToUpload);
                
                if (uploadSuccess) {
                    // Remove uploaded locations from queue
                    plugin.removeFromWorkHourQueue(locationsToUpload);
                    Log.d(TAG, "Successfully uploaded " + locationsToUpload.size() + " locations");
                } else {
                    Log.w(TAG, "Failed to upload locations, keeping in queue for retry");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error uploading locations: " + e.getMessage());
            }
        });
    }
    
    private boolean uploadLocationsToServer(List<BackgroundLocationPlugin.WorkHourLocationData> locations) {
        try {
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Setup connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "SignalScout-WorkHourTracker/1.0");
            
            if (authToken != null && !authToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
            }
            
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds
            
            // Create JSON payload
            JSONObject payload = new JSONObject();
            payload.put("engineerId", engineerId);
            payload.put("timestamp", System.currentTimeMillis());
            
            JSONArray locationsArray = new JSONArray();
            for (BackgroundLocationPlugin.WorkHourLocationData location : locations) {
                JSONObject locationObj = new JSONObject();
                locationObj.put("latitude", location.latitude);
                locationObj.put("longitude", location.longitude);
                locationObj.put("accuracy", location.accuracy);
                locationObj.put("timestamp", location.timestamp);
                locationsArray.put(locationObj);
            }
            payload.put("locations", locationsArray);
            
            // Send request
            String jsonString = payload.toString();
            connection.getOutputStream().write(jsonString.getBytes("UTF-8"));
            
            // Check response
            int responseCode = connection.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "Upload successful, response code: " + responseCode);
                return true;
            } else {
                Log.w(TAG, "Upload failed, response code: " + responseCode);
                return false;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Network error during upload: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during upload: " + e.getMessage());
            return false;
        }
    }
}