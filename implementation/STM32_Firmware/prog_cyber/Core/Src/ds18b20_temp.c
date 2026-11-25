/*
 * ds18b20_temp.c - Driver for managing the DS18B20 temperature sensor
 * via the 1-Wire bit-banging protocol, optimized for STM32F4 and FreeRTOS.
 */
#include "ds18b20_temp.h"
/**
 * @brief Initialization of the DWT (Data Watchpoint and Trace) hardware counter
 *        found in ARM Cortex-M4 cores.
 * Enables the cycle counter (CYCCNT) to allow for highly precise microsecond-scale
 * delays, independent of operating system interrupts.
 */
void DS18B20_init(void){
    CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;
    DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
}

/**
 * @brief Delay function in microseconds (us) based on the DWT register.
 * Implements an active wait loop with a safety timeout to prevent
 * deadlocks in the event of hardware anomalies.
 */
static void delay_us(uint32_t us){
    uint32_t startTicks = DWT->CYCCNT;
    uint32_t usTicks = us * (SystemCoreClock / 1000000U);

    if (usTicks == 0) return;

    // Safety counter (timeout) to prevent the microcontroller from becoming permanently locked up
    uint32_t timeout = 100000;
    while ((DWT->CYCCNT - startTicks) < usTicks) {
        if (--timeout == 0) {
            break; // Exits the loop if the DWT counter does not advance
        }
    }
}

/**
 * @brief Performs the One-Wire bus reset sequence and verifies the presence of the sensor.
 * CRITICAL NOTE: Uses FreeRTOS critical section macros (taskENTER_CRITICAL)
 * to disable interrupts during critical timing (microsecond-scale),
 * preventing the operating system's context switch from interfering with the protocol timings.
 */
static uint8_t DS18B20_Reset(void){
    uint8_t presence = 0;

    taskENTER_CRITICAL(); // Start of critical section (disable interrupts)

    HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_RESET);
    delay_us(480);	// Reset pulse

    HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_SET);
    delay_us(80); //Presence Pulse

    if (HAL_GPIO_ReadPin(DS18B20_PORT, DS18B20_PIN) == GPIO_PIN_RESET) {
        presence = 1; // Sensor correctly detected on the bus
    }
    taskEXIT_CRITICAL();	// End of critical section (re-enables interrupts)

    delay_us(400);
    return presence;
}

/**
 * @brief Low-level writing of a single bit on the One-Wire bus
 * Protected by a critical section to maintain the strict timing required by the protocol.
 */
static void DS18B20_WriteBit(uint8_t bit) {
    taskENTER_CRITICAL();
    HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_RESET);
    if (bit) {
        delay_us(10);
        HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_SET);
        delay_us(55);
    } else {
        delay_us(65);
        HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_SET);
        delay_us(5);
    }
    taskEXIT_CRITICAL();
}

/*
 *  @brief Low-level reading of a single bit from the One-Wire bus
 */
static uint8_t DS18B20_ReadBit(void) {
    uint8_t bit = 0;
    taskENTER_CRITICAL();
    HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_RESET);
    delay_us(3);
    HAL_GPIO_WritePin(DS18B20_PORT, DS18B20_PIN, GPIO_PIN_SET);
    delay_us(10);
    if (HAL_GPIO_ReadPin(DS18B20_PORT, DS18B20_PIN) == GPIO_PIN_SET) {
        bit = 1;
    }
    delay_us(50);
    taskEXIT_CRITICAL();
    return bit;
}

// Writing an entire byte (8 bits) by iterating over each individual bit
static void DS18B20_WriteByte(uint8_t data) {
    for (uint8_t i = 0; i < 8; i++) {
        DS18B20_WriteBit(data & 0x01);
        data >>= 1;
    }
}

// Reading an entire byte (8 bits) from the bus
static uint8_t DS18B20_ReadByte(void) {
    uint8_t value = 0;
    for (uint8_t i = 0; i < 8; i++) {
        if (DS18B20_ReadBit()) {
            value |= (1 << i);
        }
    }
    return value;
}

/**
 * @brief Sends the command to start a temperature conversion to the sensor.
 * Uses the Skip ROM (0xCC) command, which is ideal for configurations with a single sensor on the bus.
 */
uint8_t DS18B20_StartConversion(void) {
    if (!DS18B20_Reset()) return 0;
    DS18B20_WriteByte(DS18B20_CMD_SKIP_ROM);
    DS18B20_WriteByte(DS18B20_CMD_CONVERT_T);
    return 1;
}

/**
 * @brief Legge i registri di memoria temporanea (Scratchpad) del sensore
 *        e converte il valore grezzo in gradi Celsius (°C).
 */
float DS18B20_ReadTemperature(void) {
    if (!DS18B20_Reset()) return -999.0f; // Error code for disconnected sensor

    DS18B20_WriteByte(DS18B20_CMD_SKIP_ROM);
    DS18B20_WriteByte(DS18B20_CMD_READ_SCRATCH);

    uint8_t temp_lsb = DS18B20_ReadByte();
    uint8_t temp_msb = DS18B20_ReadByte();

    // Reconstruction of the 16-bit signed value in two's complement
    int16_t raw_temp = (temp_msb << 8) | temp_lsb;
    // Multiplication by the sensor's native resolution (0.0625 °C per bit at 12-bit)
    return (float)raw_temp * 0.0625f;
}
