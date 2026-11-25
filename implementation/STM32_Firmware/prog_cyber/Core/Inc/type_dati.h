/*
 * type_dati.h
 *
 *  Created on: 3 ott 2026
 *      Author: pietr
 *
 * Data structure for the sensor utilized for the sensore node
 */

#ifndef INC_TYPE_DATI_H_
#define INC_TYPE_DATI_H_

#include <stdint.h>

// Data structure for MPU6050 (gyroscope and accelerometer)
typedef struct {
    float temperature;
    float accel_x;
    float accel_y;
    float accel_z;
} MPU6050_Data_t;

// Data structure for DS18B20 (Digital temperature sensor with GPIO)
typedef struct {
    float temperature;
} DS18B20_Data_t;

// Complete telemetry packet
typedef struct {
    uint32_t timestamp_ms;
    MPU6050_Data_t mpu_data;
    DS18B20_Data_t ds18b20_data;
} TelemetryPacket_t;

#endif /* INC_TYPE_DATI_H_ */

