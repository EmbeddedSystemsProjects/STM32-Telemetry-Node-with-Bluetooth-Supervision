package com.example.app_cyber;
/*
*@brief Ds18b20Record class for storing data from the DS18B20 sensor.
 */
public class Ds18b20Record {
    public long timestampMs;
    public float temperature;
    public String createdAt;

    public Ds18b20Record(long timestampMs, float temperature, String createdAt) {
        this.timestampMs = timestampMs;
        this.temperature = temperature;
        this.createdAt = createdAt;
    }
}
