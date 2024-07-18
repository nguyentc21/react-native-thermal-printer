package com.thermalprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.thermalprinter.adapter.BLEPrinterAdapter;
import com.thermalprinter.adapter.BLEPrinterDeviceId;
import com.thermalprinter.adapter.PrinterAdapter;
import com.thermalprinter.adapter.PrinterDevice;
//import com.thermalprinter.adapter.PrinterOption;

import java.util.List;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNBLEPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    protected ReactApplicationContext reactContext;

    protected PrinterAdapter adapter;

    public RNBLEPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }


    @ReactMethod
    @Override
    public void init(Promise promise) {
        this.adapter = BLEPrinterAdapter.getInstance();
        this.adapter.init(reactContext, promise);
    }

    @ReactMethod
    @Override
    public void closeConn(Promise promise) {
        adapter.closeConnectionIfExists(promise);
    }

    @ReactMethod
    @Override
    public void getDeviceList(Promise promise) {
        try {
            List<PrinterDevice> printerDevices = adapter.getDeviceList();
            WritableArray pairedDeviceList = Arguments.createArray();
            if (printerDevices.size() > 0) {
                for (PrinterDevice printerDevice : printerDevices) {
                    pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
                }
                promise.resolve(pairedDeviceList);
            } else {
                throw new Exception("No Device Found")
            }
        } catch(Exception e){
            promise.reject(e);
        }
    }

    @ReactMethod
    @Override
    public void printImageBase64(
        String base64,
        int imageWidth,
        int imageHeight,
        boolean cut,
        boolean beep,
        Promise promise
    ) {
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        adapter.printImageBase64(decodedByte, imageWidth, imageHeight, cut, beep, promise);
    }

    @ReactMethod
    public void connectPrinter(String innerAddress, Promise promise) {
        adapter.selectDevice(BLEPrinterDeviceId.valueOf(innerAddress), promise);
    }

    @Override
    public String getName() {
        return "RNBLEPrinter";
    }
}
