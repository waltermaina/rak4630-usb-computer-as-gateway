/**
 * This package has a declaration of the CommPort class
 */
package com.walter.commports;

import com.fazecast.jSerialComm.*;
import java.util.Scanner;
import com.serialpundit.usb.SerialComUSB;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walter.http.HttpService;
import com.walter.rak4630data.CommandsResponses;
import com.walter.rak4630data.ResponseData;
import com.walter.rak4630data.SensorData;
import java.util.ArrayList;
import java.util.List;

/**
 * The CommPort class handles the setting up of the comport
 *
 * @author Walter
 */
public class CommPort {

    public static SerialComUSB scusb;
    public static String[] comPorts;
    public static SerialPort[] filteredPorts;
    public static String openedComPortNode;
    public static SerialPort sp;

    // RAK4630 device
    public static int PRODUCT_VID = 0x239A;
    public static int PRODUCT_PID = 0x8029;

    // Serial port settings
    //public static int BAUDRATE = 115200;
    public static int BAUDRATE = 9600;
    public static int DATA_BITS = 8;
    public static int STOP_BITS = 1;
    public static int PARITY = 0;

    /**
     * Sets up a com port based on the com.fazecast.jSerialComm package
     */
    public void setupCommPortJserial() {
        try {

            // List available ports
            SerialPort[] ports = SerialPort.getCommPorts();

            // Create a list to hold the matching ports
            List<SerialPort> matchingPorts = new ArrayList<>();

            // Filter the ports based on VID and PID
            for (SerialPort port : ports) {
                if (port.getVendorID() == PRODUCT_VID && port.getProductID() == PRODUCT_PID) {
                    // This port matches the RAK4630 VID and PID
                    matchingPorts.add(port);
                }
            }

            // Convert the list back to an array
            filteredPorts = matchingPorts.toArray(new SerialPort[0]);

            // Print the names of the matching ports
            for (SerialPort port : filteredPorts) {
                System.out.println("Matching Port: " + port.getSystemPortName());
            }

        } catch (Exception ex) {
            Logger.getLogger(CommPort.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error while getting serial ports");
        }

        if (filteredPorts.length > 0) {
            // Get the comport to be used.
            openedComPortNode = filteredPorts[0].getSystemPortName();
            System.out.println("Setting up serial port : " + openedComPortNode);

            try {
                // Configure the serial port
                sp = SerialPort.getCommPort(openedComPortNode);
                sp.setComPortParameters(BAUDRATE, DATA_BITS, STOP_BITS, PARITY); // default connection settings for RAK4630
                sp.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 100, 100);
                sp.addDataListener(new SerialPortDataListener() {
                    @Override
                    public int getListeningEvents() {
                        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                    }

                    @Override
                    public void serialEvent(SerialPortEvent event) {
                        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                            return;
                        }

                        try {
                            Scanner scanner = new Scanner(sp.getInputStream());
                            scanner.useDelimiter("\r\n");
                            StringBuffer allData = new StringBuffer();
                            //while(scanner.hasNext()){
                            allData.append(scanner.next());
                            //}
                            if (scanner.hasNextLine()) {
                                // Do what you want with the data
                                //System.out.println(allData.toString());
                                //parseReceivedData(allData.toString());
                                processReceivedDataInThread(allData.toString());
                                //clear the Stringbuffer content
                                allData.delete(0, allData.length());
                            }
                        } catch (Exception e) {
                        }
                    }
                });
                // Try to open the comport
                if (sp.openPort()) {
                    System.out.println("Port is open :)");
                    // Get the encryption key
                    String str = "RENCK";
                    //writeString(str);
                } else {
                    System.out.println("Failed to open port :(");
                }
            } catch (Exception Ex) {
                System.out.println("Failed to open port :(");
            }

        } else {
            System.out.println("No comports found :(");
        }
    }

    /**
     * Closes the com port
     */
    public void closeComPort() {
        try {
            sp.closePort();
        } catch (Exception ex) {
            System.out.println("Failed to close port :(");
        }
    }

    /**
     * Writes out the data that needs to be sent through the com port.
     *
     * @param stringToWrite is the string that needs to be sent out.
     */
    public void writeString(String stringToWrite) {
        byte[] b = new byte[500];
        try {
            //b = str.getBytes("UTF-8");
            b = stringToWrite.getBytes(StandardCharsets.US_ASCII);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            // Wait for mcu to start
            Thread.sleep(2000);
            sp.getOutputStream().write(b);
            sp.getOutputStream().flush();
            sp.getOutputStream().write('\n');
            sp.getOutputStream().flush();
        } catch (IOException ex) {
            Logger.getLogger(CommPort.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(CommPort.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Checks if a string has valid json data
     *
     * @param jsonString
     * @return true if the string has valid json
     */
    public boolean isValidJSON(String jsonString) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode jsonNode = mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            // An exception is thrown if the string is not valid JSON
            return false;
        }
    }

    /**
     * Parses every string received from the comport.
     *
     * @param newData is the data to parse.
     */
    public void parseReceivedData(String newData) {

        // log data and time data was received
        HttpService.log(newData);

        // Clean new data;
        newData = newData.trim();

        // Check if new data is valid json
        boolean isNewDataValidJSON = isValidJSON(newData);
        System.out.println("Is valid JSON: " + isNewDataValidJSON);

        if (isNewDataValidJSON == true) {
            try {
                // Try to get the data to send to server
                // Create ObjectMapper instance
                ObjectMapper objectMapper = new ObjectMapper();

                // Deserialize JSON to a Java object
                SensorData sensorData = objectMapper.readValue(newData, SensorData.class);

                // Get the command from the data
                int command = sensorData.getCommand();
                System.out.println("The command received is: " + command);

                if (command == CommandsResponses.CMD_SEND_DATA) {
                    // Send data to server
                    System.out.println("Received send data command");

                    HttpService httpService = new HttpService();
                    int responseCode = httpService.postRAK4630SensorData(sensorData);

                    // Send a response
                    //int responseCode = CommandsResponses.RES_SEND_DATA_CREATED;
                    ResponseData responseData = new ResponseData(responseCode);

                    // Serialize the ResponseData object to JSON
                    String jsonResponse = objectMapper.writeValueAsString(responseData);

                    // Print the JSON representation
                    System.out.println("Serialized JSON: " + jsonResponse);

                    writeString(jsonResponse);
                }
            } catch (Exception e) {
                // Handle the exception
                System.err.println("Data from RAK4630 cannot be processed: " + e.getMessage());
            }
        }
    }

    /**
     * Parses received data using a thread to allow for faster data reception.
     *
     * @param newData is the new data to parse.
     */
    public void processReceivedDataInThread(String newData) {
        if (newData != null) {
            Runnable runnable = () -> {
                try {
                    parseReceivedData(newData);
                } catch (Exception ex) {
                    System.out.println("Error while processing new data in thread: " + ex.getMessage());
                    Logger.getLogger(CommPort.class.getName()).log(Level.SEVERE, null, ex);
                }
            };

            Thread dataProcessingThread = new Thread(runnable);
            dataProcessingThread.start();
        }
    }
}
