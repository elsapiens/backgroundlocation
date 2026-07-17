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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Periodically uploads queued work-hour fixes to the configured server.
 *
 * Owns its {@link WorkHourLocationQueue} so uploads keep working when the WebView and
 * plugin instance are gone (app killed, service restarted standalone). The plugin is
 * notified about queue activity when it is alive, but is never required — the previous
 * implementation NPE-crashed the service without it, and its upload loop shipped an
 * always-empty list so nothing was ever uploaded.
 */
public class WorkHourLocationUploader {
    private static final String TAG = "WorkHourUploader";

    private final String engineerId;
    private final long uploadInterval;
    private final String serverUrl;
    private final String authToken;
    private final boolean enableOfflineQueue;
    private final WorkHourLocationQueue queue;

    private final Handler handler;
    private final Runnable uploadRunnable;
    private ExecutorService executor;
    private boolean isActive = false;

    public WorkHourLocationUploader(String engineerId, long uploadInterval, String serverUrl,
            String authToken, boolean enableOfflineQueue) {
        this(engineerId, uploadInterval, serverUrl, authToken, enableOfflineQueue, new WorkHourLocationQueue());
    }

    public WorkHourLocationUploader(String engineerId, long uploadInterval, String serverUrl,
            String authToken, boolean enableOfflineQueue, WorkHourLocationQueue queue) {
        this.engineerId = engineerId;
        this.uploadInterval = uploadInterval > 0 ? uploadInterval : TrackingStateStore.DEFAULT_UPLOAD_INTERVAL_MS;
        this.serverUrl = serverUrl;
        this.authToken = authToken;
        this.enableOfflineQueue = enableOfflineQueue;
        this.queue = queue;

        this.handler = new Handler(Looper.getMainLooper());
        this.uploadRunnable = new Runnable() {
            @Override
            public void run() {
                if (isActive) {
                    uploadQueuedLocations();
                    handler.postDelayed(this, WorkHourLocationUploader.this.uploadInterval);
                }
            }
        };
    }

    public void start() {
        if (isActive) {
            return;
        }
        isActive = true;
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        handler.postDelayed(uploadRunnable, uploadInterval);
        Log.d(TAG, "Work hour location uploader started with interval: " + uploadInterval + "ms");
    }

    public void stop() {
        isActive = false;
        handler.removeCallbacks(uploadRunnable);
        // Best-effort final flush so a normal stop does not strand queued fixes.
        if (executor != null && !executor.isShutdown()) {
            uploadQueuedLocations();
            executor.shutdown();
        }
        Log.d(TAG, "Work hour location uploader stopped");
    }

    /** Queue a fix and notify the plugin (when alive) so JS listeners hear about it. */
    public void addLocationToQueue(Location location) {
        BackgroundLocationPlugin.WorkHourLocationData locationData =
            new BackgroundLocationPlugin.WorkHourLocationData(
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getTime(),
                engineerId
            );
        queue.add(locationData);

        BackgroundLocationPlugin plugin = BackgroundLocationPlugin.getInstance();
        if (plugin != null) {
            plugin.notifyWorkHourLocationQueued(locationData);
        }
        Log.d(TAG, "Queued work hour fix (" + queue.size() + " pending): "
            + location.getLatitude() + ", " + location.getLongitude());
    }

    public WorkHourLocationQueue getQueue() {
        return queue;
    }

    private void uploadQueuedLocations() {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> {
            List<BackgroundLocationPlugin.WorkHourLocationData> batch = queue.snapshot();
            if (batch.isEmpty()) {
                return;
            }

            boolean uploadSuccess = uploadLocationsToServer(batch);

            if (uploadSuccess) {
                queue.removeAll(batch);
                notifyUploadResult(batch, true, null);
                Log.d(TAG, "Uploaded " + batch.size() + " work hour locations");
            } else if (!enableOfflineQueue) {
                // Caller opted out of offline buffering — drop instead of retrying.
                queue.removeAll(batch);
                notifyUploadResult(batch, false, "Upload failed and offline queue is disabled");
            } else {
                Log.w(TAG, "Upload failed; keeping " + batch.size() + " locations queued for retry");
            }
        });
    }

    private void notifyUploadResult(List<BackgroundLocationPlugin.WorkHourLocationData> batch, boolean success,
            String error) {
        BackgroundLocationPlugin plugin = BackgroundLocationPlugin.getInstance();
        if (plugin != null) {
            plugin.notifyWorkHourUploadResult(batch, success, error);
        }
    }

    private boolean uploadLocationsToServer(List<BackgroundLocationPlugin.WorkHourLocationData> locations) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(serverUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "ElsapiensBackgroundLocation/1.0");

            if (authToken != null && !authToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

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

            connection.getOutputStream().write(payload.toString().getBytes("UTF-8"));

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return true;
            }
            Log.w(TAG, "Upload failed, response code: " + responseCode);
            return false;

        } catch (IOException e) {
            Log.e(TAG, "Network error during upload: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during upload: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
