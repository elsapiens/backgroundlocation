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
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "locations";

    public SQLiteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY, reference TEXT, idx INTEGER, latitude DOUBLE, longitude DOUBLE, timestamp LONG)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertLocation(String reference, int index, double latitude, double longitude, long timestamp, float accuracy) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("reference", reference);
        values.put("idx", index);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("timestamp", timestamp);
        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public int getNextIndexForReference(String reference) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(idx) FROM " + TABLE_NAME + " WHERE reference = ?", new String[]{reference});
        int index = 0;
        if (cursor.moveToFirst()) {
            index = cursor.getInt(0) + 1;
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
                    location.put("idx", cursor.getInt(2));
                    location.put("latitude", cursor.getDouble(3));
                    location.put("longitude", cursor.getDouble(4));
                    location.put("timestamp", cursor.getLong(5));
                    locations.put(location);
                } catch (Exception e) { e.printStackTrace(); }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return locations;
    }
}
