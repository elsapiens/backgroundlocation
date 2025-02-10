package com.elsapiens.backgroundlocation;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

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
                + "id INTEGER PRIMARY KEY, "
                + "reference TEXT, "
                + "idx INTEGER, "
                + "latitude DOUBLE, "
                + "longitude DOUBLE, "
                + "accuracy FLOAT, " // Added accuracy column
                + "timestamp LONG)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) { // Upgrade logic for adding accuracy column
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN accuracy FLOAT DEFAULT 0");
        }
    }

    public void insertLocation(String reference, int index, double latitude, double longitude, long timestamp, float accuracy) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("reference", reference);
        values.put("idx", index);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("accuracy", accuracy);
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
                    cursor.getFloat(5), // Added accuracy
                    0, // Added speed
                    cursor.getLong(6)
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
                    location.put("idx", cursor.getInt(2));
                    location.put("latitude", cursor.getDouble(3));
                    location.put("longitude", cursor.getDouble(4));
                    location.put("accuracy", cursor.getFloat(5)); // Added accuracy
                    location.put("timestamp", cursor.getLong(6));
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
}