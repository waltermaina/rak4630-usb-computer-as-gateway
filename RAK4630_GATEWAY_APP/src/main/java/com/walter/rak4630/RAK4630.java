/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.walter.rak4630;

/**
 *
 * @author User
 */

import com.walter.commports.HotplugListener;
import javax.usb.*;

/**
 * This class contains the entry point of the application.
 * 
 * @author User
 */
public class RAK4630 {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws UsbException, InterruptedException {
        
        // Create usb listener
        UsbServices services = UsbHostManager.getUsbServices();
        services.addUsbServicesListener(new HotplugListener());
        
        // Keep this program from exiting
        while(true){}
    }
}
