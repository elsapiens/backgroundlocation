package com.elsapiens.backgroundlocation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Foreground service recording the route of the active task.
 *
 * Lifecycle contract (the source of the historical crash): any service started with
 * {@code startForegroundService()} MUST call {@code startForeground()} promptly or
 * Android kills the whole app with ForegroundServiceDidNotStartInTimeException.
 * Therefore this service promotes itself to the foreground FIRST, unconditionally,
 * and only then evaluates permissions — failures broadcast a typed error to the app
 * and stop the service gracefully instead of crashing.
 *
 * Runs with foreground (while-in-use) permission alone: Android allows a location
 * foreground service started while the app is visible to keep receiving fixes after
 * the app is backgrounded. Background permission is only needed for the restart
 * paths, which the restart receivers check before attempting a start.
 */
public class BackgroundLocationService extends Service {
    private static final String TAG = "BackgroundLocation";

    public static final String ACTION_LOCATION_UPDATE = "BackgroundLocationUpdate";
    public static final String ACTION_LOCATION_DISABLED = "BackgroundLocationDisabled";
    public static final String ACTION_ERROR = "BackgroundLocationError";

    public static final String EXTRA_REFERENCE = "reference";
    public static final String EXTRA_INTERVAL = "interval";
    public static final String EXTRA_MIN_DISTANCE = "minDistance";
    public static final String EXTRA_HIGH_ACCURACY = "highAccuracy";
    public static final String EXTRA_MAX_ACCURACY = "maxAccuracy";

    private static final int NOTIFICATION_ID = 1;
    private static final int RESTART_ALARM_ID = 1001;
    private static final long RESTART_CHECK_INTERVAL = 30 * 60 * 1000; // 30 minutes

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SQLiteDatabaseHelper db;
    private TrackingStateStore stateStore;
    private NotificationFactory notifications;
    private LocationPermissionManager permissionManager;
    private final LocationFilter filter = new LocationFilter();
    private final DistanceTracker distanceTracker = new DistanceTracker();

    private String reference;
    private long interval = TrackingStateStore.DEFAULT_INTERVAL_MS;
    private float minDistance = TrackingStateStore.DEFAULT_MIN_DISTANCE_METERS;
    private boolean highAccuracy = true;
    private float maxAccuracy = TrackingStateStore.DEFAULT_MAX_ACCURACY_METERS;

    private boolean isForeground = false;
    private BroadcastReceiver providerChangeReceiver;
    private AlarmManager alarmManager;
    private PendingIntent restartPendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new SQLiteDatabaseHelper(this);
        stateStore = new TrackingStateStore(new SharedPrefsKeyValueStore(this));
        notifications = new NotificationFactory(this);
        permissionManager = new LocationPermissionManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = createLocationCallback();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Promote to foreground before anything can fail — see class javadoc.
        promoteToForeground(null, null);
        registerProviderChangeReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!resolveParameters(intent)) {
            // No usable parameters (restart with no persisted session) — nothing to track.
            Log.w(TAG, "No tracking parameters available; stopping service");
            stopGracefully();
            return START_NOT_STICKY;
        }

        promoteToForeground(currentNotificationTitle(), currentNotificationText());
        if (!isForeground) {
            // Foreground promotion failed (e.g. permission revoked on Android 14+).
            broadcastError(ErrorCodes.SERVICE_START_FAILED,
                "Could not promote the tracking service to the foreground.", true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!permissionManager.hasForegroundLocationPermission()) {
            broadcastError(ErrorCodes.PERMISSION_DENIED,
                "Location permission is not granted. Request it before starting tracking.", true);
            stopGracefully();
            return START_NOT_STICKY;
        }

        if (!permissionManager.hasBackgroundLocationPermission()) {
            // Not fatal: tracking works while the service lives, but the system cannot
            // restart it from the background. Tell the app so it can ask the user.
            broadcastError(ErrorCodes.BACKGROUND_PERMISSION_DENIED,
                "Background location permission (\"Allow all the time\") is not granted. "
                    + "Tracking continues, but cannot recover if the system stops it while the app is in the background.",
                false);
        }

        if (!isLocationEnabled()) {
            // Stay alive and wait: the provider-change receiver resumes updates the
            // moment the user re-enables location services.
            broadcastLocationDisabled();
            broadcastError(ErrorCodes.LOCATION_SERVICES_DISABLED,
                "Device location services are disabled. Waiting for them to be re-enabled.", false);
        } else if (!startLocationUpdates()) {
            stopGracefully();
            return START_NOT_STICKY;
        }

        resumeDistanceFromDatabase();
        scheduleRestartAlarm();
        return START_STICKY;
    }

    /**
     * Populate tracking parameters from the start intent, falling back to the
     * persisted session for system restarts that deliver a null intent.
     *
     * @return true when a usable reference is available
     */
    private boolean resolveParameters(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_REFERENCE)) {
            String ref = intent.getStringExtra(EXTRA_REFERENCE);
            if (ref == null || ref.trim().isEmpty()) {
                return false;
            }
            if (reference != null && !reference.equals(ref)) {
                // New tracking session under a different reference — distance must
                // not carry over from the previous task.
                distanceTracker.reset();
            }
            reference = ref;
            interval = intent.getLongExtra(EXTRA_INTERVAL, TrackingStateStore.DEFAULT_INTERVAL_MS);
            minDistance = intent.getFloatExtra(EXTRA_MIN_DISTANCE, TrackingStateStore.DEFAULT_MIN_DISTANCE_METERS);
            highAccuracy = intent.getBooleanExtra(EXTRA_HIGH_ACCURACY, true);
            maxAccuracy = intent.getFloatExtra(EXTRA_MAX_ACCURACY, TrackingStateStore.DEFAULT_MAX_ACCURACY_METERS);
            return true;
        }

        TrackingStateStore.TaskTrackingState saved = stateStore.getTaskTracking();
        if (saved == null) {
            return false;
        }
        reference = saved.reference;
        interval = saved.interval;
        minDistance = saved.minDistance;
        highAccuracy = saved.highAccuracy;
        maxAccuracy = saved.maxAccuracy;
        return true;
    }

    private String currentNotificationTitle() {
        TrackingStateStore.TaskTrackingState saved = stateStore.getTaskTracking();
        return saved != null ? saved.notificationTitle : null;
    }

    private String currentNotificationText() {
        TrackingStateStore.TaskTrackingState saved = stateStore.getTaskTracking();
        return saved != null ? saved.notificationText : null;
    }

    /**
     * Promote to the foreground, guarding against the Android 14+ SecurityException
     * thrown when a location-type service starts without any location permission.
     * Safe to call repeatedly — later calls just refresh the notification content.
     */
    private void promoteToForeground(String title, String text) {
        if (Build.VERSION.SDK_INT >= 34 && !isForeground
                && !permissionManager.hasForegroundLocationPermission()) {
            // startForeground would throw for a location-type service; skip and let the
            // caller broadcast a typed error. All start sites pre-check permissions, so
            // this only guards revocation races.
            Log.e(TAG, "Cannot start foreground service without location permission on Android 14+");
            return;
        }
        try {
            notifications.createTaskChannel();
            startForeground(NOTIFICATION_ID, notifications.buildTaskNotification(title, text));
            isForeground = true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private LocationCallback createLocationCallback() {
        return new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                long now = System.currentTimeMillis();
                for (Location location : locationResult.getLocations()) {
                    handleLocation(location, now);
                }
            }
        };
    }

    private void handleLocation(Location location, long now) {
        if (!filter.shouldRecord(location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), maxAccuracy, location.getTime(), now)) {
            Log.d(TAG, "Fix dropped by filter (accuracy " + location.getAccuracy() + "m)");
            return;
        }
        double totalKm = distanceTracker.addPoint(location.getLatitude(), location.getLongitude());
        int index = db.getNextIndexForReference(reference);
        db.insertLocation(reference, index, location.getLatitude(), location.getLongitude(),
            location.getAltitude(), location.getAccuracy(), location.getSpeed(), location.getBearing(),
            location.getVerticalAccuracyMeters(), location.getTime());
        sendLocationUpdate(location, index, (float) totalKm);
    }

    /**
     * @return true when updates were requested successfully
     */
    private boolean startLocationUpdates() {
        if (!permissionManager.hasForegroundLocationPermission()) {
            broadcastError(ErrorCodes.PERMISSION_DENIED,
                "Location permission was revoked while tracking.", true);
            return false;
        }
        LocationRequest request = new LocationRequest.Builder(
                highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                interval)
            .setMinUpdateIntervalMillis(Math.max(interval / 2, 500L))
            .setMinUpdateDistanceMeters(minDistance)
            .setMaxUpdateAgeMillis(5000)
            .setWaitForAccurateLocation(highAccuracy)
            .build();
        try {
            // Idempotent: drop any previous registration before adding a new one.
            fusedLocationClient.removeLocationUpdates(locationCallback);
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing when requesting updates", e);
            broadcastError(ErrorCodes.PERMISSION_DENIED,
                "Location permission was revoked while tracking.", true);
            return false;
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    /** Seed the distance tracker from previously recorded points after a restart. */
    private void resumeDistanceFromDatabase() {
        if (distanceTracker.hasLastPoint()) {
            return; // Already tracking within this process — nothing to resume.
        }
        try {
            LocationItem last = db.getLastLocation(reference);
            if (last != null) {
                distanceTracker.resume(db.getTotalDistanceForReference(reference), last.latitude, last.longitude);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resume distance from database", e);
        }
    }

    /**
     * While the service is alive it reacts to the user toggling device location
     * services: pause updates when disabled, resume automatically when re-enabled.
     */
    private void registerProviderChangeReceiver() {
        providerChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    return;
                }
                if (isLocationEnabled()) {
                    Log.i(TAG, "Location services re-enabled; resuming updates");
                    startLocationUpdates();
                } else {
                    Log.w(TAG, "Location services disabled; pausing updates");
                    stopLocationUpdates();
                    broadcastLocationDisabled();
                    broadcastError(ErrorCodes.LOCATION_SERVICES_DISABLED,
                        "Device location services were disabled during tracking.", false);
                }
            }
        };
        registerReceiver(providerChangeReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    // ------------------------------------------------------------------
    // Broadcasts to the plugin (and through it, to JavaScript)
    // ------------------------------------------------------------------

    private void sendLocationUpdate(Location location, int index, float totalDistanceKm) {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.putExtra("reference", reference);
        intent.putExtra("index", index);
        intent.putExtra("latitude", location.getLatitude());
        intent.putExtra("longitude", location.getLongitude());
        intent.putExtra("altitude", location.getAltitude());
        intent.putExtra("speed", location.getSpeed());
        intent.putExtra("heading", location.getBearing());
        intent.putExtra("accuracy", location.getAccuracy());
        intent.putExtra("altitudeAccuracy", location.getVerticalAccuracyMeters());
        intent.putExtra("totalDistance", totalDistanceKm);
        intent.putExtra("timestamp", location.getTime());
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void broadcastLocationDisabled() {
        Intent intent = new Intent(ACTION_LOCATION_DISABLED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void broadcastError(String code, String message, boolean fatal) {
        Log.e(TAG, code + ": " + message);
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra("code", code);
        intent.putExtra("message", message);
        intent.putExtra("source", ErrorCodes.SOURCE_TASK_TRACKING);
        intent.putExtra("fatal", fatal);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    // ------------------------------------------------------------------
    // State helpers
    // ------------------------------------------------------------------

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null
            && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    /** Leave foreground state and stop without tripping the startForeground contract. */
    private void stopGracefully() {
        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            isForeground = false;
        }
        stopSelf();
    }

    // ------------------------------------------------------------------
    // Restart machinery
    // ------------------------------------------------------------------

    private void scheduleRestartAlarm() {
        try {
            Intent restartIntent = new Intent(this, ServiceRestartReceiver.class);
            restartIntent.setAction(ServiceRestartReceiver.ACTION_RESTART);
            restartPendingIntent = PendingIntent.getBroadcast(this, RESTART_ALARM_ID, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (alarmManager != null) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + RESTART_CHECK_INTERVAL,
                    RESTART_CHECK_INTERVAL, restartPendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling restart alarm", e);
        }
    }

    private void cancelRestartAlarm() {
        try {
            if (alarmManager != null && restartPendingIntent != null) {
                alarmManager.cancel(restartPendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling restart alarm", e);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // The user swiped the app away. Keep tracking only if a session is still
        // wanted; the short post-removal grace period allows this restart.
        if (stateStore.isTaskTrackingActive()) {
            try {
                Intent restart = new Intent(getApplicationContext(), BackgroundLocationService.class);
                startForegroundService(restart); // Parameters restored from the state store.
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart service after task removal", e);
                broadcastError(ErrorCodes.BACKGROUND_PERMISSION_DENIED,
                    "Tracking stopped when the app was closed. Grant \"Allow all the time\" "
                        + "location permission to keep tracking after the app is closed.", true);
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        if (providerChangeReceiver != null) {
            try {
                unregisterReceiver(providerChangeReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was not registered.
            }
            providerChangeReceiver = null;
        }
        // Keep the alarm when the session should survive (system killed us); the
        // receiver will restart tracking. Cancel it on an explicit stopTracking().
        if (!stateStore.isTaskTrackingActive()) {
            cancelRestartAlarm();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
