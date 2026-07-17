package com.elsapiens.backgroundlocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, thread-safe queue of work-hour fixes awaiting upload.
 *
 * The queue is owned by the uploader (not the plugin instance) so it keeps working
 * when the WebView/plugin is gone — e.g. after the app process was killed and only the
 * foreground service restarted. Bounded so a long offline stretch cannot grow memory
 * without limit: when full, the oldest entries are dropped first (the newest data is
 * the most valuable for "where is the engineer now" style dashboards).
 */
public class WorkHourLocationQueue {

    public static final int DEFAULT_CAPACITY = 500;

    private final int capacity;
    private final List<BackgroundLocationPlugin.WorkHourLocationData> items = new ArrayList<>();

    public WorkHourLocationQueue() {
        this(DEFAULT_CAPACITY);
    }

    public WorkHourLocationQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Add a fix, evicting the oldest entries when the queue is full. */
    public synchronized void add(BackgroundLocationPlugin.WorkHourLocationData data) {
        while (items.size() >= capacity) {
            items.remove(0);
        }
        items.add(data);
    }

    /** Copy of the current contents, oldest first. */
    public synchronized List<BackgroundLocationPlugin.WorkHourLocationData> snapshot() {
        return new ArrayList<>(items);
    }

    /** Remove entries that were uploaded successfully. */
    public synchronized void removeAll(List<BackgroundLocationPlugin.WorkHourLocationData> uploaded) {
        items.removeAll(uploaded);
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public synchronized void clear() {
        items.clear();
    }
}
