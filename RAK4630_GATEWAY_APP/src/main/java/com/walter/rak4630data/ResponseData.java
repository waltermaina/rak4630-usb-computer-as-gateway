/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.walter.rak4630data;

/**
 * This is the class that contains the response sent to the com port.
 * 
 * @author Walter
 */
public class ResponseData {
    private int response;

    public ResponseData() {
    }

    public ResponseData(int response) {
        this.response = response;
    }

    public int getResponse() {
        return response;
    }

    public void setResponse(int response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return "ResponseData{" +
                "response=" + response +
                '}';
    }
}

