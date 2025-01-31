package com.elsapiens.backgroundlocation;

public class LocationItem {
    public String reference;
    public int index;
    public double latitude;
    public double longitude;
    public float accuracy;
    public float speed;
    public long timestamp;
    public LocationItem(String reference, int index, double latitude, double longitude, float accuracy, float speed, long timestamp) {
        this.reference = reference;
        this.index = index;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setTime(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

    public float getAccuracy() {
        return this.accuracy;
    }

    public float getSpeed() {
        return this.speed;
    }

    public long getTime() {
        return this.timestamp;
    }

    public String getReference() {
        return this.reference;
    }

    public int getIndex() {
        return this.index;
    }

}
