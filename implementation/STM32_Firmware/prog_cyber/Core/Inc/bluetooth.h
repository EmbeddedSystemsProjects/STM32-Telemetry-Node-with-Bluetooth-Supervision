/*
 * bluetooth.h
 *
 *  Created on: 3 ott 2026
 *      Author: pietr
 */

#ifndef INC_BLUETOOTH_H_
#define INC_BLUETOOTH_H_

#include "stm32f4xx_hal.h"
#include "type_dati.h"

/**
 * @brief Inizializza il modulo Bluetooth associando la periferica UART.
 * @param huart Puntatore alla struttura UART (es. &huart1 o &huart2)
 */
void BT_Init(UART_HandleTypeDef *huart);

/**
 * @brief Formatta il pacchetto di telemetria e lo trasmette via UART.
 * @param packet Puntatore alla struttura contenente i dati filtrati (NTC + MPU)
 * @return HAL_StatusTypeDef Stato della trasmissione HAL
 */
HAL_StatusTypeDef BT_SendPacket(const TelemetryPacket_t *packet);

#endif /* INC_BLUETOOTH_H_ */
