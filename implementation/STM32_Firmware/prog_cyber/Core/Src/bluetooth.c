/*
 * bluetooth.c
 *
 *  Created on: 3 ott 2026
 *      Author: pietr
 */

/*
 * bluetooth.c
 *
 *  Created on: 3 ott 2026
 *      Author: pietr
 */

#include "bluetooth.h"
#include <stdio.h>

static UART_HandleTypeDef *bt_huart;
static char tx_buffer[120];

void BT_Init(UART_HandleTypeDef *huart) {
    bt_huart = huart;
}

HAL_StatusTypeDef BT_SendPacket(const TelemetryPacket_t *packet) {
    if (bt_huart == NULL) return HAL_ERROR;

    // Formato CSV a 5 campi: timestamp, temp_ntc, ax, ay, az
    int len = sprintf(tx_buffer, "%lu,%.3f,%.2f,%.2f,%.2f\r\n",
                      packet->timestamp_ms,
					  packet->ds18b20_data.temperature,  // DS18B20 Digitale su GPIO
                      packet->mpu_data.accel_x,
                      packet->mpu_data.accel_y,
                      packet->mpu_data.accel_z);

    if (len > 0) {
        return HAL_UART_Transmit(bt_huart, (uint8_t*)tx_buffer, len, 50);
    }
    return HAL_ERROR;
}
