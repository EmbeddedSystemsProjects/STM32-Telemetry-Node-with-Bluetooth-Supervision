/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * File Name          : freertos.c
  * Description        : Code for freertos applications (Modular Architecture)
  ******************************************************************************
  */
/* USER CODE END Header */

/* Includes ------------------------------------------------------------------*/
#include "FreeRTOS.h" //header file of freertos.c
#include "task.h"  //header file that gives with FreeRTOS.h the primitives of time and thread
#include "queue.h"          // explicit inclusion of the queue
#include "main.h"		//inclusion of the main IMPORTANT
#include "cmsis_os.h"	//gives the definition CMSIS of the ARM for the uniformation of the callback of the os

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
#include "ds18b20_temp.h"        // Header driver DS18B20 temperature sensor
#include "mpu6050.h"             // Driver MPU6050 I2C accelerometer sensor
#include "bluetooth.h"     // Driver Bluetooth UART module HC-05
#include "type_dati.h"       // Structure TelemetryPacket_t useful for the definition of data packet sent through HC-05
/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
/* USER CODE BEGIN Variables */
/*External references to hardware peripherals initialized in the main.c file*/
extern I2C_HandleTypeDef hi2c1;
extern UART_HandleTypeDef huart1;

// Queue e Task Handles
QueueHandle_t telemetryQueue = NULL; //Thread-safe queue for telemetry packets
osThreadId sensorTaskHandle; //Sensor sampling task handle
osThreadId bluetoothTaskHandle; //Handle for the serial/Bluetooth transmission task

// Istance MPU6050
volatile MPU6050_Handler_t mpu_device;

/*Debug variables mapped for real-time monitoring (IDE Live Expressions).
 *The 'volatile' keyword prevents the compiler from optimizing the variables away,
 *forcing the CPU to always read them directly from RAM.
 */
volatile float g_debug_ds18b20_temp = 0.0f;
volatile float g_debug_ax = 0.0f;
volatile float g_debug_ay = 0.0f;
volatile float g_debug_az = 0.0f;
/* USER CODE END Variables */

/* Private function prototypes -----------------------------------------------*/
/* USER CODE BEGIN FunctionPrototypes */
void MX_FREERTOS_Init(void);
void StartSensorTask(void const * argument);
void StartBluetoothTask(void const * argument);
/* USER CODE END FunctionPrototypes */

/* Prototype for static memory allocation of the Idle task (FreeRTOS) */
void vApplicationGetIdleTaskMemory( StaticTask_t **ppxIdleTaskTCBBuffer, StackType_t **ppxIdleTaskStackBuffer, uint32_t *pulIdleTaskStackSize );

/* USER CODE BEGIN GET_IDLE_TASK_MEMORY */
static StaticTask_t xIdleTaskTCBBuffer;
static StackType_t xIdleStack[configMINIMAL_STACK_SIZE];

/**
 * @brief Provides the FreeRTOS kernel with static memory buffers for the Idle Task,
 * avoiding dynamic heap fragmentation and ensuring deterministic behavior.
 */
void vApplicationGetIdleTaskMemory( StaticTask_t **ppxIdleTaskTCBBuffer, StackType_t **ppxIdleTaskStackBuffer, uint32_t *pulIdleTaskStackSize )
{
  *ppxIdleTaskTCBBuffer = &xIdleTaskTCBBuffer;
  *ppxIdleTaskStackBuffer = &xIdleStack[0];
  *pulIdleTaskStackSize = configMINIMAL_STACK_SIZE;
}
/* USER CODE END GET_IDLE_TASK_MEMORY */

/**
  * @brief  FreeRTOS initialization (Create queue & Task)
  * @param  None
  * @retval None
  */
void MX_FREERTOS_Init(void) {
  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* USER CODE BEGIN RTOS_QUEUES */
  /* Creation of a thread-safe queue for telemetry packets.
  *Sized to hold up to 10 elements of type TelemetryPacket_t,
  *it acts as a decoupling buffer between the sensor task and the Bluetooth task. */
  telemetryQueue = xQueueCreate(10, sizeof(TelemetryPacket_t));
  /* USER CODE END RTOS_QUEUES */

  /* USER CODE BEGIN RTOS_TASKS */
  /* 1. Definition and creation of the Sensor Sampling Task
   *(Normal priority, stack allocated to 512 words to accommodate I2C and OneWire libraries). */
  osThreadDef(sensorTask, StartSensorTask, osPriorityNormal, 0, 512);
  sensorTaskHandle = osThreadCreate(osThread(sensorTask), NULL);

  /* 2. Definition and creation of the Bluetooth Transmission Task
   *(Normal priority, stack allocated to 512 words for serial communication handling). */
  osThreadDef(bluetoothTask, StartBluetoothTask, osPriorityNormal, 0, 512);
  bluetoothTaskHandle = osThreadCreate(osThread(bluetoothTask), NULL);
  /* USER CODE END RTOS_TASKS */
}

/* Private application code --------------------------------------------------*/
/* USER CODE BEGIN Application */

/**
 * @brief High-reliability sensor sampling task (DS18B20 + MPU6050).
 * Implements communication bus management, asynchronous non-blocking
 * conversion handling, and a software prescaler for packet decimation.
 */
void StartSensorTask(void const * argument)
{
// 1. Hardware initialization of the sensors on their respective buses
  DS18B20_init();
  MPU6050_Init(&hi2c1, (MPU6050_Handler_t *)&mpu_device);

  TelemetryPacket_t telemetry_packet = {0};
  MPU6050_Data_t mpu_data;

  uint16_t ds18b20_timer = 0;
  uint8_t ds18b20_converting = 0;

  // First attempt to initiate thermal conversion
  if (DS18B20_StartConversion()) {
      ds18b20_converting = 1;
  }
  uint32_t send_counter = 0; // Counter for temporal decimation of transmission, to avoid bottlenecks in packet transmission

  for(;;)
  {
	  // 2. MPU6050 sampling with a verifying protocol on the I2C bus (Address: 0x68 << 1 = 0xD0)
	  if (HAL_I2C_IsDeviceReady(&hi2c1, 0xD0, 1, 10) == HAL_OK)
	      {
	          MPU6050_SampleTask(&hi2c1, (MPU6050_Handler_t *)&mpu_device);

	          if (MPU6050_GetFilteredData((MPU6050_Handler_t *)&mpu_device, &mpu_data)) {
	        	  // Updating valid data in the telemetry packet
	              telemetry_packet.mpu_data.accel_x = mpu_data.accel_x;
	              telemetry_packet.mpu_data.accel_y = mpu_data.accel_y;
	              telemetry_packet.mpu_data.accel_z = mpu_data.accel_z;

	              // Updating debug variables for live monitoring via IDE
	              g_debug_ax = mpu_data.accel_x;
	              g_debug_ay = mpu_data.accel_y;
	              g_debug_az = mpu_data.accel_z;
	          }
	      }
	      else
	      {
	    	  // I2C fault handling: clearing data in the event of a disconnection or a locked bus
	    	  g_debug_ax = 0.0f;
	          g_debug_ay = 0.0f;
	          g_debug_az = 0.0f;
	          telemetry_packet.mpu_data.accel_x = 0.0f;
	          telemetry_packet.mpu_data.accel_y = 0.0f;
	          telemetry_packet.mpu_data.accel_z = 0.0f;

	          // Reset I2C hardware to recover the bus from any lock-ups
	          HAL_I2C_DeInit(&hi2c1);
	          osDelay(2);
	          HAL_I2C_Init(&hi2c1);
	          MPU6050_Init(&hi2c1, (MPU6050_Handler_t *)&mpu_device);
	      }
	  // 3. Asynchronous and non-blocking handling of the DS18B20 sensor (takes approximately 750ms) q
    if (ds18b20_converting) {
        ds18b20_timer++;
        if (ds18b20_timer >= 75) { // 75 cycles x 10ms = 750ms
            float current_temp = DS18B20_ReadTemperature();

            // Metric validation of the operating range (-55°C to +125°C)
            if (current_temp >= -55.0f && current_temp <= 125.0f) {
                telemetry_packet.ds18b20_data.temperature = current_temp;
                g_debug_ds18b20_temp = current_temp;
            } else {
                g_debug_ds18b20_temp = -999.0f;
                telemetry_packet.ds18b20_data.temperature = -999.0f;
            }

            // Attempt to restart the next conversion
            if (!DS18B20_StartConversion()) {
                ds18b20_converting = 0; // Sensor disconnected while driving
                g_debug_ds18b20_temp = -999.0f;
                telemetry_packet.ds18b20_data.temperature = -999.0f;
            }
            ds18b20_timer = 0;
        }
    } else {
    	// Sensor disconnected: attempts to reconnect every 100 cycles (1 second)
        ds18b20_timer++;
        if (ds18b20_timer >= 100) {
            if (DS18B20_StartConversion()) {
                ds18b20_converting = 1;
            } else {
                g_debug_ds18b20_temp = -999.0f;
                telemetry_packet.ds18b20_data.temperature = -999.0f;
            }
            ds18b20_timer = 0;
        }
    }

    // 4. Current timestamp in milliseconds
    telemetry_packet.timestamp_ms = HAL_GetTick();

    // 5. Transmission decimation: send to the Bluetooth queue only every 100 cycles (1 packet per second)
    send_counter++;
    if (send_counter >= 100) {
        if (telemetryQueue != NULL) {
            xQueueSend(telemetryQueue, &telemetry_packet, 0);
        }
        send_counter = 0;
    }

    // CRITICAL: Constant execution rhythm of 10 ms for the RTOS
    osDelay(10);
  }
}

/**
 * @brief Task for data transmission via Bluetooth (HC-05).
 * It remains in a blocked waiting state (without wasting CPU cycles)
 * until the sampling task places a new packet into the queue.
 */
void StartBluetoothTask(void const * argument)
{
// Initialization of the Bluetooth subsystem associated with UART1
  BT_Init(&huart1);

  TelemetryPacket_t rx_packet; // Structure for receiving a packet from the queue

  for(;;)
  {
    if (telemetryQueue != NULL) {
    	/* xQueueReceive with portMAX_DELAY:
    	 *Places the task into a zero-power-consumption suspended state (Blocked).
    	  *The kernel immediately wakes up the task as soon as the queue receives a new packet. */
      if (xQueueReceive(telemetryQueue, &rx_packet, portMAX_DELAY) == pdTRUE) {
        BT_SendPacket(&rx_packet);// Serialization and transmission of the packet via Bluetooth
      }
    } else {
    // Safety condition in case the queue has not been allocated correctly
      osDelay(100);
    }
  }
}

/* USER CODE END Application */
