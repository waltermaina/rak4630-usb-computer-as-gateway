/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.walter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walter.rak4630data.SensorData;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.net.ssl.HttpsURLConnection;

/**
 * Facilitates HTTP communication between the application and the API.
 *
 * @author Walter
 */
public class HttpService {

    private final String USER_AGENT = "Mozilla/5.0";

    /**
     * Helps viewing messages with time stamps
     *
     * @param message is the message to display
     */
    public static void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    /**
     * Sends RAK4630 sensor data to the database for storage.
     *
     * @param newSensorData is the data to send.
     * @return HTTP response code e.g. 201
     * @throws Exception
     */
    public int postRAK4630SensorData(SensorData newSensorData) throws Exception {
        HttpsURLConnection con = null;
        //HttpURLConnection con = null;

        try {
            //String url = "http://localhost:8000/api/v2/";
            String url = "https://environment-sensors.onrender.com/api/v2/";
            URL obj = new URL(url);
            con = (HttpsURLConnection) obj.openConnection();
            //con = (HttpURLConnection) obj.openConnection();

            //add request header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            //con.setRequestProperty("Authorization", "Basic V2FsbGV0OnlLUktCREpjMldGdjxxdkhFZzw4TXdPdVkjISl1IyNJ");
            con.setConnectTimeout(300000); //set timeout to 5 minutes
            con.setReadTimeout(300000); //set timeout to 5 minutes

            // Serialize the SensorData object to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonSensorData = objectMapper.writeValueAsString(newSensorData);

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(jsonSensorData);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'POST' request to URL : " + url);
            //System.out.println("Post parameters : " + jsonSensorData);
            log("Post parameters : " + jsonSensorData);
            //System.out.println("Response Code : " + responseCode);
            log("Response Code : " + responseCode);

            // Print response body
            try {
                InputStream inputStream;

                if ((responseCode >= 200) && (responseCode <= 299)) {
                    inputStream = con.getInputStream();
                } else {
                    inputStream = con.getErrorStream();
                }

                String line;
                StringBuilder response = new StringBuilder();

                // Read the response body as a string using a BufferedReader.
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Close the BufferedReader.
                reader.close();

                // Print the response body.
                //System.out.println(response.toString());
                log("Response Body: " + response.toString());
            } catch (Exception e) {
            }

            // Return whichever response code was received
            return responseCode;

        } catch (java.net.SocketTimeoutException e) {
            System.out.println("Connection timeout occurred  ");
            return HttpURLConnection.HTTP_CLIENT_TIMEOUT;   // 408
        } catch (java.io.IOException e) {
            System.out.println("No internet");
            return HttpURLConnection.HTTP_UNAVAILABLE;      // 503
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return HttpURLConnection.HTTP_INTERNAL_ERROR;   // 500
        } finally {
            //close the connection, set all objects to null
            con.disconnect();
            con = null;
        }
    }
}
