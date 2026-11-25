/*
 * mpu6050.h
 *
 *  Created on: 3 ott 2026
 *      Author: pietr
 */

#ifndef INC_MPU6050_H_
#define INC_MPU6050_H_

#include "stm32f4xx_hal.h"
#include "type_dati.h"	// Inclusion of the global data structures for the telemetry package

#define WINDOW_SIZE 32	// Window size for the moving average filter (smoothing)

/**
 * @brief Management structure (handler) for the MPU6050 sensor.
 * It encapsulates the device state, error counters for resilience,
 * and circular buffers for digital signal filtering.
 */
typedef struct {
    uint16_t active_addr;	// Active I2C address of the device
    uint8_t is_initialized;	// Initialization completion flag
    uint8_t i2c_error_count;	// Bus communication error counter

    // Buffer for mobile average
    int16_t ax_window[WINDOW_SIZE];
    int16_t ay_window[WINDOW_SIZE];
    int16_t az_window[WINDOW_SIZE];
    int16_t temp_window[WINDOW_SIZE];
    int window_index;
    uint8_t samples_collected;
} MPU6050_Handler_t;

#define MPU6050_ADDR      (0x68 << 1) // I2C address MPU6050
#define REG_PWR_MGMT_1    0x6B	// Power Management Register 1
#define REG_WHO_AM_I      0x75	// Device identification register (Who Am I)

// public function
/*@brief Initialization and status check of the MPU6050 sensor via I2C.*/
HAL_StatusTypeDef MPU6050_Init(I2C_HandleTypeDef *hi2c, MPU6050_Handler_t *dev);

/* @brief Raw data sampling from the accelerometer and buffer updating.*/
void MPU6050_SampleTask(I2C_HandleTypeDef *hi2c, MPU6050_Handler_t *dev);

/* @brief Extraction of data filtered using the moving average algorithm. */
uint8_t MPU6050_GetFilteredData(MPU6050_Handler_t *dev, MPU6050_Data_t *out_data);

#endif /* INC_MPU6050_H_ */
