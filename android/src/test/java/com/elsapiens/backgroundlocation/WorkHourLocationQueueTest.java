package com.elsapiens.backgroundlocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class WorkHourLocationQueueTest {

    private static BackgroundLocationPlugin.WorkHourLocationData data(long timestamp) {
        return new BackgroundLocationPlugin.WorkHourLocationData(51.5, -0.12, 10f, timestamp, "eng-1");
    }

    @Test
    public void addAndSnapshotPreservesOrder() {
        WorkHourLocationQueue queue = new WorkHourLocationQueue(10);
        queue.add(data(1));
        queue.add(data(2));
        queue.add(data(3));

        List<BackgroundLocationPlugin.WorkHourLocationData> snapshot = queue.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(1, snapshot.get(0).timestamp);
        assertEquals(3, snapshot.get(2).timestamp);
    }

    @Test
    public void evictsOldestWhenFull() {
        WorkHourLocationQueue queue = new WorkHourLocationQueue(3);
        for (long t = 1; t <= 5; t++) {
            queue.add(data(t));
        }
        List<BackgroundLocationPlugin.WorkHourLocationData> snapshot = queue.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(3, snapshot.get(0).timestamp); // 1 and 2 evicted
        assertEquals(5, snapshot.get(2).timestamp);
    }

    @Test
    public void removeAllRemovesOnlyUploadedBatch() {
        WorkHourLocationQueue queue = new WorkHourLocationQueue(10);
        queue.add(data(1));
        queue.add(data(2));

        List<BackgroundLocationPlugin.WorkHourLocationData> batch = queue.snapshot();
        queue.add(data(3)); // Arrives while the batch is uploading

        queue.removeAll(batch);
        assertEquals(1, queue.size());
        assertEquals(3, queue.snapshot().get(0).timestamp);
    }

    @Test
    public void snapshotIsACopy() {
        WorkHourLocationQueue queue = new WorkHourLocationQueue(10);
        queue.add(data(1));
        List<BackgroundLocationPlugin.WorkHourLocationData> snapshot = queue.snapshot();
        snapshot.clear();
        assertEquals(1, queue.size());
    }

    @Test
    public void clearEmptiesQueue() {
        WorkHourLocationQueue queue = new WorkHourLocationQueue(10);
        queue.add(data(1));
        queue.clear();
        assertTrue(queue.isEmpty());
    }
}
