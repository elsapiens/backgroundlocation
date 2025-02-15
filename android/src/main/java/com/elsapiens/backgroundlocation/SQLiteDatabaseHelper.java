package com.elsapiens.backgroundlocation;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

public class SQLiteDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "location_tracking.db";
    private static final int DATABASE_VERSION = 2; // Incremented database version
    private static final String TABLE_NAME = "locations";

    public SQLiteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "reference TEXT,"
                + "idx INTEGER,"
                + "latitude REAL,"
                + "longitude REAL,"
                + "altitude REAL,"
                + "accuracy REAL,"
                + "speed REAL,"
                + "heading REAL,"
                + "altitudeAccuracy REAL,"
                + "timestamp INTEGER"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void insertLocation(String reference, int index, double latitude, double longitude, double altitude, float accuracy, float speed, float heading, float altitudeAccuracy, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("reference", reference);
        values.put("idx", index);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("altitude", altitude);
        values.put("accuracy", accuracy);
        values.put("speed", speed);
        values.put("heading", heading);
        values.put("altitudeAccuracy", altitudeAccuracy);
        values.put("timestamp", timestamp);
        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public LocationItem getLastLocation(String reference) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE reference = ? ORDER BY idx DESC LIMIT 1", new String[]{reference});
        LocationItem location = null;
        if (cursor.moveToFirst()) {
            location = new LocationItem(
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getDouble(3),
                    cursor.getDouble(4),
                    cursor.getFloat(5),
                    cursor.getLong(6),
                    cursor.getFloat(7),
                    cursor.getFloat(8),
                    cursor.getFloat(9),
                    cursor.getLong(10)
            );
        }
        cursor.close();
        db.close();
        return location;
    }

    public int getNextIndexForReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            reference = "default_reference"; // Ensure a non-null reference
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(idx) FROM " + TABLE_NAME + " WHERE reference = ?", new String[]{reference});

        int index = 1;
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            index = cursor.getInt(0) + 1; // Avoid NULL + 1 issue
        }
        cursor.close();
        db.close();
        return index;
    }

    public JSONArray getLocationsForReference(String reference) {
        JSONArray locations = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE reference = ? ORDER BY idx ASC", new String[]{reference});
        if (cursor.moveToFirst()) {
            do {
                JSONObject location = new JSONObject();
                try {
                    location.put("reference", cursor.getString(1));
                    location.put("index", cursor.getInt(2));
                    location.put("latitude", cursor.getDouble(3));
                    location.put("longitude", cursor.getDouble(4));
                    location.put("altitude", cursor.getDouble(5));
                    location.put("accuracy", cursor.getFloat(6));
                    location.put("speed", cursor.getFloat(7));
                    location.put("heading", cursor.getFloat(8));
                    location.put("altitudeAccuracy", cursor.getFloat(9));
                    location.put("timestamp", cursor.getLong(10));
                    locations.put(location);
                } catch (Exception ignored) {}
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return locations;
    }

    public void clearStoredLocations() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_NAME);
            db.close();
        } catch (Exception ignored) {}
    }

    public float getTotalDistanceForReference(String reference) {
        float distance = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT latitude, longitude" +
                " FROM " + TABLE_NAME + " WHERE reference = ? ORDER BY idx ASC", new String[]{reference});
        //use haversine formula to calculate distance between two points
        if (cursor.moveToFirst()) {
            double lat1 = cursor.getDouble(0);
            double lon1 = cursor.getDouble(1);
            while (cursor.moveToNext()) {
                double lat2 = cursor.getDouble(0);
                double lon2 = cursor.getDouble(1);
                double R = 6371; // Radius of the earth in km
                double dLat = Math.toRadians(lat2 - lat1);
                double dLon = Math.toRadians(lon2 - lon1);
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                distance += (float) (R * c);
                lat1 = lat2;
                lon1 = lon2;
            }
        }
        cursor.close();
        db.close();
        return distance;
    }
}