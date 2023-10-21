/**
   @file RAK4630_Data_Logger.ino
   @author Walter Maina (waltermaina76@gmail.com)
   @brief Logs environment sensor data and sends it to a computer using USB serial.
   @version 1.0.0
   @date 21-10-2023

   @copyright Copyright (c) 2023

*/
#include <Arduino.h>
#include <SPI.h>
#include <Wire.h>
#include <MD_CirQueue.h>
#include <SX126x-RAK4630.h>
#include "bsec.h"
#include <ArduinoJson.h>


// Data sensor helper functions declarations
void checkIaqSensorStatus(void);
void errLeds(void);

// Create an object of the class Bsec
Bsec iaqSensor;

// General purpose output string
String output;

// Allocate the JSON document, for creating json data
StaticJsonDocument<1024> doc;

// Some commands
#define CMD_SEND_DATA         1                 // Instructs the computer to upload the data

// The amount of time that the device sleeps in milliseconds
#define SLEEP_TIME 10 * 1000                    // Ten seconds
//#define SLEEP_TIME 1000                       // One second

// Tracks the time to read the sensors
unsigned long sensorReadTimepoint = 0;

/** Semaphore used by events to wake up loop task */
SemaphoreHandle_t taskEvent = NULL;

/** Timer to wakeup task frequently and send message */
SoftwareTimer taskWakeupTimer;

/**
   @brief Flag for the event type
   -1 => no event
   0 => LoRaWan data received
   1 => Timer wakeup
   2 => tbd
   ...
*/
uint8_t eventType = -1;

/**
   @brief Timer event that wakes up the loop task frequently

   @param unused
*/
void periodicWakeup(TimerHandle_t unused)
{
  eventType = 1;
  // Give the semaphore, so the loop task will wake up
  xSemaphoreGiveFromISR(taskEvent, pdFALSE);
}

// RAK4630 supply two LED
#ifndef LED_BUILTIN
#define LED_BUILTIN 35          // Green LED
#endif

#ifndef LED_BUILTIN2
#define LED_BUILTIN2 36
#endif

#define SENSORS_BUFFER_SIZE 52                              /** Maximum size of sensors data buffer in bytes. */

// RAK1906 data queue variables
const uint8_t QUEUE_SIZE = 255; /** (255/24)= 10.625 days, 24 records per day */

struct
{
  unsigned long record_id = 0;
  unsigned long milliSec = 0;
  double temperature = 0.0;
  double pressure = 0.0;
  double humidity = 0.0;
  double gas_resistance = 0.0;
} pushdata, popdata, peekdata;   /** Data type of data stored every hour (40 bytes each) */

/** Define a queue that has 255 data items each with 320 bits and total is 10,200 bytes. */
MD_CirQueue Q(QUEUE_SIZE, sizeof(pushdata));

// A unique id for each data set created
unsigned long recordId = 0;

// Sensor read and queue peek variables
#define SENSOR_READ_INTERVAL 3600000 // Every hour
//#define DATA_SEND_INTERVAL 3600000 // Every hour
//#define SENSOR_READ_INTERVAL  60000 // Every minute
//#define SENSOR_READ_INTERVAL  2000 // Every 2 seconds
//#define DATA_SEND_INTERVAL    60000 // Every minute
#define DATA_SEND_INTERVAL    2000 // Every two seconds
#define SEND_DATA_TIMEOUT_INTERVAL    6*60000 // After six minutes, because server has a 5 minute timeout
#define PEEK_DATA_INTERVAL DATA_SEND_INTERVAL  /** Put data in buffer a few seconds befor sending */
unsigned long peekDataTimepoint = 0;
unsigned long sendDataTimeOutpoint = 0;

// Communication variables
String inputString = "";                // a String to hold incoming data
bool stringComplete = false;            // whether the string is complete

// Communication state machine variables
enum states {                           // Define enumerated type for state machine states
  NONE,
  SENDING_DATA,
  AWAITING_SEND_DATA_RESPONSE
};

// Holds the state of the state machine
enum states next_state = NONE;

// Communication state machine function prototypes
void initialState(void);
void sendingData(void);
void awaitingSendDataResponse(void);
void peekData(void);
void checkResponseInSerialPort();

/**
   Causes communication state to change
*/
void check_communication_state(void) {
  switch (next_state) {
    case NONE:
      initialState();
      break;
    case SENDING_DATA:
      sendingData();
      break;
    case AWAITING_SEND_DATA_RESPONSE:
      awaitingSendDataResponse();
      break;
  }
}

// Communication response variables
enum responseTypes {                         // Define enumerated type for response types
  OKAY = 200,
  CREATED = 201,
  BAD_REQUEST = 400
};

// Used to check if computer sent back a response
bool responseReceived = false;

//bool appDebugOn = true;    /** Used to decide if debug information can be printed */
bool appDebugOn = false;


/**@brief Handles initial state
*/
void initialState(void) {
  next_state = SENDING_DATA;
}

/**@brief Handles sending data state
*/
void sendingData(void) {
  if (millis() - peekDataTimepoint > PEEK_DATA_INTERVAL)
  {
    peekDataTimepoint = millis();
    peekData();
    sendDataTimeOutpoint = millis();
    next_state = AWAITING_SEND_DATA_RESPONSE;
  }
}

/**@brief Handles receiving of response
*/
void awaitingSendDataResponse(void) {
  // Check if response was received
  if (responseReceived == true)
  {
    responseReceived = false;
    peekDataTimepoint = millis();
    next_state = SENDING_DATA;
  }
  // Check for timeout
  else if (millis() - sendDataTimeOutpoint > SEND_DATA_TIMEOUT_INTERVAL)
  {
    sendDataTimeOutpoint = millis();
    responseReceived = false;
    next_state = SENDING_DATA;
  }
}

/**@brief Function for reading the BME680 sensor and putting the
   data in a queue.
*/
void bme680_get(void)
{
  if (iaqSensor.run()) { // If new data is available
    // Clear the JsonDocument
    doc.clear();

    // Calculate sensor values
    double temperature = iaqSensor.temperature;
    double pressure = iaqSensor.pressure;
    double humidity = iaqSensor.humidity;
    double gas_resistance = iaqSensor.gasResistance;

    if (appDebugOn == true) {
      Serial.print("Temperature = ");
      Serial.print(temperature);
      Serial.println(" *C");

      Serial.print("Pressure = ");
      Serial.print(pressure);
      Serial.println(" hPa");

      Serial.print("Humidity = ");
      Serial.print(humidity);
      Serial.println(" %");

      Serial.print("Gas = ");
      Serial.print(gas_resistance);
      Serial.println(" KOhms");

      Serial.println();
    }

    recordId = recordId + 1;
    pushdata.record_id = recordId;
    pushdata.milliSec = millis();
    pushdata.temperature = temperature;
    pushdata.pressure = pressure / 100.0;
    pushdata.humidity = humidity;
    pushdata.gas_resistance = gas_resistance / 1000.0;
    bool b;
    b = Q.push((uint8_t *)&pushdata);

    if (appDebugOn == true) {
      Serial.print("\nPush ");
      Serial.print("Record ID = ");
      Serial.print(pushdata.record_id);
      Serial.print(" Temperature = ");
      Serial.print(pushdata.temperature);
      Serial.print(" Pressure = ");
      Serial.print(pushdata.pressure);
      Serial.print(" Humidity = ");
      Serial.print(pushdata.humidity);
      Serial.print(" Gas Resistance = ");
      Serial.print(pushdata.gas_resistance);
      Serial.print(" Uptime = ");
      Serial.print(pushdata.milliSec);
      Serial.println(b ? " ok" : " fail");
    }
  } else {
    checkIaqSensorStatus();
  }
}

/**@brief Function for putting data into the pstring_buffer
   so that it can be sent by the send_lora_frame() function.
*/
void peekData(void) {
  if (!Q.isEmpty()) {
    bool b = Q.peek((uint8_t *)&peekdata);

    // How long ago this data was recorded
    unsigned long milliSecAgo = millis() - peekdata.milliSec;

    if (appDebugOn == true) {
      Serial.print("\nPeek ");
      Serial.print("Record ID = ");
      Serial.print(peekdata.record_id);
      Serial.print(" Temperature = ");
      Serial.print(peekdata.temperature);
      Serial.print(" Pressure = ");
      Serial.print(peekdata.pressure);
      Serial.print(" Humidity = ");
      Serial.print(peekdata.humidity);
      Serial.print(" Gas Resistance = ");
      Serial.print(peekdata.gas_resistance);
      Serial.print(" Uptime = ");
      Serial.print(milliSecAgo);
      Serial.println(b ? " ok" : " fail");
    }

    // Clear the JsonDocument
    doc.clear();

    // Add values in the JSON document
    doc["command"] = CMD_SEND_DATA;
    doc["recordId"] = peekdata.record_id;
    doc["timeRecorded"] = milliSecAgo;
    doc["temperature"] = peekdata.temperature;
    doc["pressure"] = peekdata.pressure;
    doc["humidity"] = peekdata.humidity;
    doc["gasResistance"] = peekdata.gas_resistance;

    // Serialize JSON to a buffer and send it
    char buffer[512];  // Adjust the size as needed
    size_t bytesWritten = serializeJson(doc, buffer);
    Serial.println(buffer);
  }
}

/**@brief Function for removing sent data from queue.
*/
void popData(void) {
  if (!Q.isEmpty()) {
    bool b = Q.pop((uint8_t *)&popdata);

    if (appDebugOn == true) {
      Serial.print("\nPop ");
      Serial.print("Record ID = ");
      Serial.print(popdata.record_id);
      Serial.print(" Temperature = ");
      Serial.print(popdata.temperature);
      Serial.print(" Pressure = ");
      Serial.print(popdata.pressure);
      Serial.print(" Humidity = ");
      Serial.print(popdata.humidity);
      Serial.print(" Gas Resistance = ");
      Serial.print(popdata.gas_resistance);
      Serial.print(" Uptime = ");
      Serial.print(popdata.milliSec);
      Serial.println(b ? " ok" : " fail");
    }
  }
}

void setup()
{
  // Start serial
  Serial.begin(9600);

  // Wait max 5 seconds for a terminal to connect
  time_t timeout = millis();
  while (!Serial)
  {
    if ((millis() - timeout) < 15000)
    {
      delay(100);
      digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));
    }
    else
    {
      break;
    }
  }
  Serial.println("====================================");
  Serial.println("RAK4630 Data Logger example");
  Serial.println("====================================");

  // Initialize the sensor
  iaqSensor.begin(BME68X_I2C_ADDR_LOW, Wire);
  output = "\nBSEC library version " + String(iaqSensor.version.major) + "." + String(iaqSensor.version.minor) + "." + String(iaqSensor.version.major_bugfix) + "." + String(iaqSensor.version.minor_bugfix);
  Serial.println(output);
  checkIaqSensorStatus();

  bsec_virtual_sensor_t sensorList[13] = {
    BSEC_OUTPUT_IAQ,
    BSEC_OUTPUT_STATIC_IAQ,
    BSEC_OUTPUT_CO2_EQUIVALENT,
    BSEC_OUTPUT_BREATH_VOC_EQUIVALENT,
    BSEC_OUTPUT_RAW_TEMPERATURE,
    BSEC_OUTPUT_RAW_PRESSURE,
    BSEC_OUTPUT_RAW_HUMIDITY,
    BSEC_OUTPUT_RAW_GAS,
    BSEC_OUTPUT_STABILIZATION_STATUS,
    BSEC_OUTPUT_RUN_IN_STATUS,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_TEMPERATURE,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_HUMIDITY,
    BSEC_OUTPUT_GAS_PERCENTAGE
  };

  iaqSensor.updateSubscription(sensorList, 13, BSEC_SAMPLE_RATE_LP);
  checkIaqSensorStatus();

  // Print the header
  output = "Timestamp [ms], IAQ, IAQ accuracy, Static IAQ, CO2 equivalent, breath VOC equivalent, raw temp[°C], pressure [hPa], raw relative humidity [%], gas [Ohm], Stab Status, run in status, comp temp[°C], comp humidity [%], gas percentage";
  Serial.println(output);

  // Initialize the queue.
  Q.begin();

  // reserve 200 bytes for the inputString:
  inputString.reserve(200);

  // Create the semaphore
  Serial.println("Create task semaphore");
  delay(100); // Give Serial time to send
  taskEvent = xSemaphoreCreateBinary();

  // Give the semaphore, seems to be required to initialize it
  Serial.println("Initialize task Semaphore");
  delay(100); // Give Serial time to send
  xSemaphoreGive(taskEvent);

  // Take the semaphore, so loop will be stopped waiting to get it
  Serial.println("Take task Semaphore");
  delay(100); // Give Serial time to send
  xSemaphoreTake(taskEvent, 10);

  // Now we are connected, start the timer that will wakeup the loop frequently
  Serial.println("Start Wakeup Timer");
  taskWakeupTimer.begin(SLEEP_TIME, periodicWakeup);
  taskWakeupTimer.start();
}

void loop()
{
  // Sleep until we are woken up by an event
  if (xSemaphoreTake(taskEvent, portMAX_DELAY) == pdTRUE)
  {
    // Check the wake up reason
    switch (eventType)
    {
      case 0: // Wakeup reason is package downlink arrived
        Serial.println("Received package over LoRaWan");
        break;
      case 1: // Wakeup reason is timer
        Serial.println("Timer wakeup");

        // Read the sensors.
        if (millis() - sensorReadTimepoint > SENSOR_READ_INTERVAL) {
          sensorReadTimepoint = millis();

          if (appDebugOn == true) {
            Serial.print("SENSOR_READ_INTERVAL: ");
            Serial.println(SENSOR_READ_INTERVAL);
          }

          // Toggle LED to show device is not frozen
          digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));

          // Read bme680 and put data in queue
          bme680_get();
        }

        // Check for response and process it
        checkResponseInSerialPort();

        // Run the communication state machine
        check_communication_state();
        
        break;
      default:
        Serial.println("This should never happen ;-)");
        break;
    }
    // Go back to sleep
    xSemaphoreTake(taskEvent, 10);
  }
}

// Helper function definitions

/**
 * @brief Checks the status of the environment data sensor.
 */
void checkIaqSensorStatus(void)
{
  if (iaqSensor.bsecStatus != BSEC_OK) {
    if (iaqSensor.bsecStatus < BSEC_OK) {
      output = "BSEC error code : " + String(iaqSensor.bsecStatus);
      Serial.println(output);
      for (;;)
        errLeds(); /* Halt in case of failure */
    } else {
      output = "BSEC warning code : " + String(iaqSensor.bsecStatus);
      Serial.println(output);
    }
  }

  if (iaqSensor.bme68xStatus != BME68X_OK) {
    if (iaqSensor.bme68xStatus < BME68X_OK) {
      output = "BME68X error code : " + String(iaqSensor.bme68xStatus);
      Serial.println(output);
      for (;;)
        errLeds(); /* Halt in case of failure */
    } else {
      output = "BME68X warning code : " + String(iaqSensor.bme68xStatus);
      Serial.println(output);
    }
  }
}

/**
 * @brief Blinks LEDs to show there is a problem with sensor.
 */
void errLeds(void)
{
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, HIGH);
  delay(100);
  digitalWrite(LED_BUILTIN, LOW);
  delay(100);
}

/**
 * @brief Checks whether a response from the computer was received.
 */
void checkResponseInSerialPort(void) {
  // Read data if available
  while (Serial.available()) {
    // get the new byte:
    char inChar = (char)Serial.read();
    // add it to the inputString:
    inputString += inChar;
    // if the incoming character is a newline, set a flag so the main loop can
    // do something about it:
    if (inChar == '\n') {
      stringComplete = true;
    }
  }

  // Process received data
  if (stringComplete) {
    if (appDebugOn == true) {
      Serial.println(inputString);
    }

    // Get the data from input string
    deserializeJson(doc, inputString);
    long response   = doc["response"];
    if (response == OKAY)
    {
      Serial.println("Received an okay from pc");
      responseReceived = true;
    }
    if (response == CREATED)
    {
      Serial.println("Received created from pc");

      // Remove sent item from queue, it was saved
      popData();

      responseReceived = true;
    }
    if (response == BAD_REQUEST)
    {
      Serial.println("Received bad request from pc");

      // Remove sent item from queue, it might be a duplicate
      popData();

      responseReceived = true;
    }

    // clear the string:
    inputString = "";
    stringComplete = false;

    // Clear the JsonDocument
    doc.clear();
  }
}
