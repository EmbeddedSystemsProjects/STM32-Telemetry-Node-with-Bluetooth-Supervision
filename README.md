# Distributed Monitoring System

STM32-based telemetry node with Bluetooth communication and an Android application for local sensor monitoring.

## Overview

The project uses an **STM32F401RE** running **FreeRTOS** to acquire data from an **MPU6050** accelerometer and a **DS18B20** temperature sensor. Measurements are sent through an **HC-05 Bluetooth module** to an Android application, which displays live data and stores records locally in **SQLite**. No cellular connection or cloud broker is required for local monitoring.

## Hardware

| Component | Function | STM32 connection |
|---|---|---|
| MPU6050 | 3-axis acceleration | I2C1: PB8 (SCL), PB9 (SDA) |
| DS18B20 | Digital temperature | PA8: software 1-Wire GPIO |
| HC-05 | Bluetooth Classic SPP | USART1: PA9 (TX), PA10 (RX) |

The HC-05 serial connection is configured for **9600 bit/s, 8-N-1**. Use a common ground and check the voltage requirements of each sensor breakout and Bluetooth board. The DS18B20 1-Wire data line requires a pull-up resistor (commonly 4.7 kΩ).

## Firmware and data flow

- FreeRTOS separates sensor acquisition and UART transmission into two tasks.
- MPU6050 acceleration data is smoothed with a moving average using circular buffers of up to 32 samples per axis.
- The DS18B20 is driven through a software 1-Wire implementation. DWT timing is used for microsecond-scale bus operations.
- A FreeRTOS queue stores up to 10 telemetry packets. A packet is generated every 100 sensor-task iterations.
- The HC-05 forwards the UART data to the Android application using Bluetooth Classic SPP.
- The Android app parses incoming records, displays live measurements, and stores them in a local SQLite database.

An offline analysis of the stored records found an average interval of approximately **1108.67 ms** between regular telemetry records (about **0.902 records/s**) in the analysed sessions. This describes record timing, not the raw sensor sampling frequency or Bluetooth latency.

## Possible improvements

CRC validation, explicit sensor configuration, improved queue/UART error handling, timing instrumentation, Bluetooth reconnection, and enhanced Android data management.
