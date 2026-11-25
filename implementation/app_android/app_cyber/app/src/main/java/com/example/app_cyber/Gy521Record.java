package com.example.app_cyber;
/*
*@brief Gy521Record class for storing data from the GY-521 sensor.
 */
public class Gy521Record {
    public long timestampMs;
    public float accelX;
    public float accelY;
    public float accelZ;
    public String createdAt;

    public Gy521Record(long timestampMs, float accelX, float accelY, float accelZ, String createdAt) {
        this.timestampMs = timestampMs;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.createdAt = createdAt;
    }
}