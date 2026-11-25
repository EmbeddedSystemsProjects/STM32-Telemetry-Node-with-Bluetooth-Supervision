	/*
	 * @brief MPU6050 driver file – Implementation of management functions,
	 *        secure sampling, and digital filtering of inertial data.
	 */

	#include "mpu6050.h"

	/**
	 * @brief Initialization and auto-detection of the MPU6050 sensor's I2C address.
	 * Polls potential addresses (0x68 or 0x69) by reading the WHO_AM_I register,
	 * resets filter windows, and wakes the device from its low-power (sleep) state.
	 */
	HAL_StatusTypeDef MPU6050_Init(I2C_HandleTypeDef *hi2c, MPU6050_Handler_t *dev) {
		uint8_t check_id = 0;
		uint8_t pwr_data = 0x00;	// Value 0 in the PWR_MGMT_1 register to activate the chip

		// Logical reset of the handler's state variables and counters
		dev->is_initialized = 0;
		dev->window_index = 0;
		dev->samples_collected = 0;
		dev->i2c_error_count = 0;

		// 1. Check primary address (0x68 with read bit shifted)
		if (HAL_I2C_Mem_Read(hi2c, 0x68 << 1, 0x75, 1, &check_id, 1, 50) == HAL_OK && check_id == 0x68) {
			dev->active_addr = 0x68 << 1;
			dev->is_initialized = 1;
		}
		// 2. Secondary address check (0x69 if the AD0 pin is connected to VCC)
		else if (HAL_I2C_Mem_Read(hi2c, 0x69 << 1, 0x75, 1, &check_id, 1, 50) == HAL_OK && check_id == 0x68) {
			dev->active_addr = 0x69 << 1;
			dev->is_initialized = 1;
		}
		// If the sensor has been correctly identified, we complete the startup process
		if (dev->is_initialized) {
			// Wake-up from Power Down
			return HAL_I2C_Mem_Write(hi2c, dev->active_addr, 0x6B, 1, &pwr_data, 1, 100);
		}

		return HAL_ERROR;// Returns an error if no MPU6050 responds on the bus
	}

	/* @brief I2C sampling task with bus anti-stall management.
	 * Reads accelerometer and temperature registers (0x3B - 0x42) in a single burst transaction,
	 * saving them into circular buffers for subsequent filtering.
	 */
	void MPU6050_SampleTask(I2C_HandleTypeDef *hi2c, MPU6050_Handler_t *dev) {
		if (!dev->is_initialized) return;

		uint8_t data_buffer[8]; // Buffer to hold the 6 bytes for the axes (X, Y, Z) + 2 bytes for temperature

		// Sequential read starting from the ACCEL_XOUT_H register (0x3B)
		if (HAL_I2C_Mem_Read(hi2c, dev->active_addr, 0x3B, 1, data_buffer, 8, 20) == HAL_OK) {
			// Communication successful: resetting the consecutive error counter
			dev->i2c_error_count = 0;

			// Reconstruction of 16-bit values ​​by combining the high and low bytes (Big-Endian)
			dev->ax_window[dev->window_index]   = (int16_t)(data_buffer[0] << 8 | data_buffer[1]);
			dev->ay_window[dev->window_index]   = (int16_t)(data_buffer[2] << 8 | data_buffer[3]);
			dev->az_window[dev->window_index]   = (int16_t)(data_buffer[4] << 8 | data_buffer[5]);
			dev->temp_window[dev->window_index] = (int16_t)(data_buffer[6] << 8 | data_buffer[7]);
			// Circular advancement of the window index (Circular Buffer / Ring Buffer)
			dev->window_index = (dev->window_index + 1) % WINDOW_SIZE;
			if (dev->samples_collected < WINDOW_SIZE) {
				dev->samples_collected++;// Increment until the 32-sample window is filled
			}
		} else {
			// Handling of communication errors on the I2C bus
			dev->i2c_error_count++;
			//if more than 10 consecutive errors accumulate, reset the hardware device
			if (dev->i2c_error_count > 10) {
				HAL_I2C_DeInit(hi2c);
				HAL_I2C_Init(hi2c);
				dev->i2c_error_count = 0;
			}
		}
	}

	/* @brief Calculation of a Moving Average filter on the accumulated samples
	 *        and conversion of raw data into physical units of measurement (g and °C).
	 */
	uint8_t MPU6050_GetFilteredData(MPU6050_Handler_t *dev, MPU6050_Data_t *out_data) {
		// If no samples have been collected yet, it returns invalid data
		if (dev->samples_collected == 0) return 0;

		int32_t sum_ax = 0, sum_ay = 0, sum_az = 0, sum_temp = 0;
		// Sum of the values ​​present within the sliding window
		for (int i = 0; i < dev->samples_collected; i++) {
			sum_ax   += dev->ax_window[i];
			sum_ay   += dev->ay_window[i];
			sum_az   += dev->az_window[i];
			sum_temp += dev->temp_window[i];
		}

		// Calculation of the arithmetic mean based on available samples
		int16_t ax_raw   = (int16_t)(sum_ax / dev->samples_collected);
		int16_t ay_raw   = (int16_t)(sum_ay / dev->samples_collected);
		int16_t az_raw   = (int16_t)(sum_az / dev->samples_collected);
		int16_t temp_raw = (int16_t)(sum_temp / dev->samples_collected);

		// Conversion of raw accelerometer values ​​into standard gravity (g)
		// assuming a scale configured to ±2g (sensitivity 16384 LSB/g)
		out_data->accel_x = (float)ax_raw / 16384.0f;
		out_data->accel_y = (float)ay_raw / 16384.0f;
		out_data->accel_z = (float)az_raw / 16384.0f;
		// Conversion of the internal temperature according to the formula in the sensor's datasheet
		out_data->temperature = ((float)temp_raw / 340.0f) + 36.53f;

		return 1; //success
	}
