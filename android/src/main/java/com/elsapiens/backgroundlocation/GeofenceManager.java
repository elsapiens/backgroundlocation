package com.elsapiens.backgroundlocation;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers circular regions with Play Services and remembers what they were for.
 *
 * The Android counterpart to iOS's {@code GeofenceMonitor}, and it exists for the
 * same reason: the OS keeps the watch and wakes a dead app to deliver the
 * crossing, which periodic sampling can never do for a phone in a pocket with
 * the app closed.
 *
 * Definitions are persisted rather than held in memory because the process that
 * receives the crossing is usually not the process that registered it — Android
 * starts a fresh one for the broadcast, and all it is handed is the geofence id.
 * Everything else, notification text above all, has to be read back from here.
 */
public class GeofenceManager {

    private static final String TAG = "GeofenceManager";
    /** Play Services' documented ceiling per app. */
    public static final int MAX_GEOFENCES = 100;

    static final String KEY_DEFINITIONS = "geofence.definitions";
    static final String KEY_PENDING = "geofence.pending";
    static final String ACTION_TRANSITION = "com.elsapiens.backgroundlocation.GEOFENCE_TRANSITION";

    private final Context context;
    private final KeyValueStore store;
    private final GeofencingClient client;

    public GeofenceManager(Context context, KeyValueStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.client = LocationServices.getGeofencingClient(this.context);
    }

    @SuppressLint("MissingPermission")
    public void add(JSONObject definition) throws JSONException, IllegalStateException {
        String id = definition.getString("id");

        List<JSONObject> definitions = definitions();
        removeById(definitions, id);
        if (definitions.size() >= MAX_GEOFENCES) {
            throw new IllegalStateException("at most " + MAX_GEOFENCES + " regions can be monitored");
        }

        int transitions = 0;
        if (definition.optBoolean("notifyOnEntry", true)) {
            transitions |= Geofence.GEOFENCE_TRANSITION_ENTER;
        }
        if (definition.optBoolean("notifyOnExit", false)) {
            transitions |= Geofence.GEOFENCE_TRANSITION_EXIT;
        }
        if (transitions == 0) {
            throw new IllegalStateException("a region must notify on entry, exit, or both");
        }

        Geofence geofence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(
                        definition.getDouble("latitude"),
                        definition.getDouble("longitude"),
                        (float) definition.getDouble("radius"))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transitions)
                // Someone already standing inside the office when the watch is
                // armed would never produce a crossing, and for an arrival
                // reminder that is the one case that must not be missed.
                // INITIAL_TRIGGER_ENTER makes Play Services report the current
                // state once, which is the same thing iOS's requestState does.
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        client.addGeofences(request, transitionPendingIntent());

        definitions.add(definition);
        save(definitions);
    }

    public void remove(String id) {
        client.removeGeofences(Collections.singletonList(id));
        List<JSONObject> definitions = definitions();
        removeById(definitions, id);
        save(definitions);
    }

    public void removeAll() {
        client.removeGeofences(transitionPendingIntent());
        save(new ArrayList<>());
    }

    public List<JSONObject> definitions() {
        return readArray(KEY_DEFINITIONS);
    }

    public JSONObject definitionFor(String id) {
        for (JSONObject definition : definitions()) {
            if (id.equals(definition.optString("id"))) {
                return definition;
            }
        }
        return null;
    }

    public List<JSONObject> pendingTransitions() {
        return readArray(KEY_PENDING);
    }

    public void clearPendingTransitions() {
        store.remove(KEY_PENDING);
    }

    /** Buffers a crossing that had no live JavaScript listener to go to. */
    public void bufferTransition(JSONObject transition) {
        List<JSONObject> pending = pendingTransitions();
        pending.add(transition);
        // Bounded, oldest dropped first: an unbounded buffer behind an app that
        // never launches grows without limit.
        while (pending.size() > 50) {
            pending.remove(0);
        }
        save(KEY_PENDING, pending);
    }

    private PendingIntent transitionPendingIntent() {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        intent.setAction(ACTION_TRANSITION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Play Services fills the Intent in with the transition detail, so
            // it must stay mutable — FLAG_IMMUTABLE here silently delivers an
            // empty broadcast.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static void removeById(List<JSONObject> definitions, String id) {
        for (int i = definitions.size() - 1; i >= 0; i--) {
            if (id.equals(definitions.get(i).optString("id"))) {
                definitions.remove(i);
            }
        }
    }

    private List<JSONObject> readArray(String key) {
        List<JSONObject> out = new ArrayList<>();
        String raw = store.getString(key, null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                out.add(array.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.w(TAG, "discarding unreadable " + key, e);
            store.remove(key);
        }
        return out;
    }

    private void save(List<JSONObject> definitions) {
        save(KEY_DEFINITIONS, definitions);
    }

    private void save(String key, List<JSONObject> values) {
        JSONArray array = new JSONArray();
        for (JSONObject value : values) {
            array.put(value);
        }
        store.putString(key, array.toString());
    }
}
