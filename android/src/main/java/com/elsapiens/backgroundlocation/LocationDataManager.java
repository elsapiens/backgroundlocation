package com.elsapiens.backgroundlocation;

import android.util.Log;
import com.getcapacitor.JSObject;

/**
 * Converts stored location rows for the Capacitor bridge and manages clearing them.
 *
 * Recording fixes is the tracking service's job alone — this class must never write
 * fixes itself. (An earlier version also processed and saved fixes in the app process,
 * running as a second writer against the same table as the service and duplicating
 * route points.)
 */
public class LocationDataManager {
    private static final String TAG = "LocationDataManager";

    private final SQLiteDatabaseHelper database;

    public LocationDataManager(SQLiteDatabaseHelper database) {
        this.database = database;
    }

    /** Convert database LocationItem to JSObject for the bridge. */
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

        try {
            data.put("totalDistance", database.getTotalDistanceForReference(locationItem.reference));
        } catch (Exception e) {
            Log.w(TAG, "Could not calculate total distance for item", e);
            data.put("totalDistance", 0.0);
        }
        return data;
    }

    /**
     * Clear stored locations.
     *
     * @param reference reserved for future per-reference clearing; currently all rows
     *                  are cleared regardless
     */
    public void clearStoredLocations(String reference) {
        try {
            database.clearStoredLocations();
            Log.d(TAG, "All stored locations cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing stored locations", e);
        }
    }
}
