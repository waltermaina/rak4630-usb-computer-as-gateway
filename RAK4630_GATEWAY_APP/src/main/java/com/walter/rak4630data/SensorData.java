/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.walter.rak4630data;

/**
 *
 * @author User
 */
public class SensorData {
    private int command;
    private int recordId;
    private long timeRecorded;
    private double temperature;
    private double pressure;
    private double humidity;
    private double gasResistance;

    // Constructors, getters, and setters

    public SensorData() {
    }

    public SensorData(int command, int recordId, long timeRecorded, double temperature, double pressure, double humidity, double gasResistance) {
        this.command = command;
        this.recordId = recordId;
        this.timeRecorded = timeRecorded;
        this.temperature = temperature;
        this.pressure = pressure;
        this.humidity = humidity;
        this.gasResistance = gasResistance;
    }

    public int getCommand() {
        return command;
    }

    public void setCommand(int command) {
        this.command = command;
    }

    public int getRecordId() {
        return recordId;
    }

    public void setRecordId(int recordId) {
        this.recordId = recordId;
    }

    public long getTimeRecorded() {
        return timeRecorded;
    }

    public void setTimeRecorded(long timeRecorded) {
        this.timeRecorded = timeRecorded;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getPressure() {
        return pressure;
    }

    public void setPressure(double pressure) {
        this.pressure = pressure;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public double getGasResistance() {
        return gasResistance;
    }

    public void setGasResistance(double gasResistance) {
        this.gasResistance = gasResistance;
    }

    @Override
    public String toString() {
        return "SensorData{" +
                "command=" + command +
                ", recordId=" + recordId +
                ", timeRecorded=" + timeRecorded +
                ", temperature=" + temperature +
                ", pressure=" + pressure +
                ", humidity=" + humidity +
                ", gasResistance=" + gasResistance +
                '}';
    }
}

