package com.elsapiens.backgroundlocation;

/**
 * Minimal key-value persistence abstraction.
 *
 * {@link TrackingStateStore} depends on this interface instead of Android's
 * SharedPreferences directly so its logic can be unit-tested with an in-memory
 * implementation (Dependency Inversion).
 */
public interface KeyValueStore {
    String getString(String key, String defaultValue);

    void putString(String key, String value);

    boolean getBoolean(String key, boolean defaultValue);

    void putBoolean(String key, boolean value);

    long getLong(String key, long defaultValue);

    void putLong(String key, long value);

    float getFloat(String key, float defaultValue);

    void putFloat(String key, float value);

    void remove(String key);
}
