package com.elsapiens.backgroundlocation;

public class LocationItem {
    public String reference;
    public int index;
    public double latitude;
    public double longitude;
    public double altitude;
    public float accuracy;
    public float speed;
    public float heading;
    public float altitudeAccuracy;

    public  float totalDistance;
    public long timestamp;
    public LocationItem(String reference, int index, double latitude, double longitude, double altitude,  float accuracy, float speed, float heading, float altitudeAccuracy, long timestamp) {
        this.reference = reference;
        this.index = index;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.heading = heading;
        this.altitudeAccuracy = altitudeAccuracy;
        this.timestamp = timestamp;
    }

}
