#ifndef INC_DS18B20_TEMP_H_
#define INC_DS18B20_TEMP_H_

#include "stm32f4xx_hal.h"
#include "FreeRTOS.h"
#include "task.h"

/* Definition of the GPIO port bank and the physical pin dedicated
   to One-Wire communication with the DS18B20 sensor */
#define DS18B20_PORT GPIOA
#define DS18B20_PIN GPIO_PIN_8

/*PROTOCOL COMMANDS (ONE-WIRE / ROM & SCRATCHPAD COMMANDS)*/
#define DS18B20_CMD_SKIP_ROM		0xCC	// Command to skip the ROM addressing phase (used for a single sensor on the bus)
#define DS18B20_CMD_CONVERT_T		0x44	// Command to initiate internal thermal conversion
#define DS18B20_CMD_READ_SCRATCH 	0xBE	// Command to read the temporary memory registers

//declarations of the functions used
/*@brief Initialization and verification of the sensor's presence on the 1-Wire bus.*/
void DS18B20_init(void);
/* @brief Avvia una nuova conversione di temperatura in modo asincrono.*/
uint8_t DS18B20_StartConversion(void);
/* @brief Reads and decodes the temperature from the sensor's Scratchpad registers.*/
float DS18B20_ReadTemperature(void);

#endif /* INC_DS18B20_TEMP_H_ */
