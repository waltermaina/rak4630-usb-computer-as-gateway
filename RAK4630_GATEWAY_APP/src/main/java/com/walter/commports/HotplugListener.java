/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.walter.commports;

/**
 * A custom version of UsbServicesListener Detects usb hotplug events and takes
 * appropriate action
 *
 * @author Walter
 */
import javax.usb.*;
import javax.usb.event.*;


public class HotplugListener implements UsbServicesListener {

    // COM port variable
    CommPort rak4630CommPort;
    
    @Override
    public void usbDeviceAttached(UsbServicesEvent event) {
        try {
            UsbDevice device = event.getUsbDevice();
            UsbDeviceDescriptor descriptor = device.getUsbDeviceDescriptor();

            int idVendor = descriptor.idVendor();
            int idProduct = descriptor.idProduct();

            System.out.println("Vendor ID: 0x" + Integer.toHexString(idVendor));
            System.out.println("Product ID: 0x" + Integer.toHexString(idProduct));

            // RAK4630 device
            int rak4630VendorID = 0x239A;
            int rak4630ProductID = 0xFFFF8029;

            // Check if it is a RAK4630 device
            if (idVendor == rak4630VendorID && idProduct == rak4630ProductID) {
                System.out.println("RAK4630 device plugged in");

                // TODO; do something here;
                // Set up the comport first
                rak4630CommPort = new CommPort();
                //rak4630CommPort.setupCommPort();
                rak4630CommPort.setupCommPortJserial();
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public void usbDeviceDetached(UsbServicesEvent event) {
        try {
            UsbDevice device = event.getUsbDevice();
            UsbDeviceDescriptor descriptor = device.getUsbDeviceDescriptor();

            int idVendor = descriptor.idVendor();
            int idProduct = descriptor.idProduct();

            System.out.println("Vendor ID: " + Integer.toHexString(idVendor));
            System.out.println("Product ID: " + Integer.toHexString(idProduct));
            //System.out.println("Product ID: " + descriptor.toString());

            // RAK4630 device
            int rak4630VendorID = 0x239A;
            int rak4630ProductID = 0xFFFF8029;

            // Check if it is a RAK4630 device
            if (idVendor == rak4630VendorID && idProduct == rak4630ProductID) {
                System.out.println("RAK4630 device unplugged");

                // Close the com port;
                rak4630CommPort.closeComPort();
                rak4630CommPort = null;
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
}
