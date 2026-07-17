package com.elsapiens.backgroundlocation;

import android.content.Context;
import android.content.SharedPreferences;

/** {@link KeyValueStore} backed by SharedPreferences — the production implementation. */
public class SharedPrefsKeyValueStore implements KeyValueStore {
    private static final String PREFS_NAME = "com.elsapiens.backgroundlocation.state";

    private final SharedPreferences prefs;

    public SharedPrefsKeyValueStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    @Override
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    @Override
    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return prefs.getFloat(key, defaultValue);
    }

    @Override
    public void putFloat(String key, float value) {
        prefs.edit().putFloat(key, value).apply();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}
