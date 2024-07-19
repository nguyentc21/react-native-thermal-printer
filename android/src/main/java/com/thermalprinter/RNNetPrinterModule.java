package com.thermalprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.thermalprinter.adapter.NetPrinterAdapter;
import com.thermalprinter.adapter.NetPrinterDeviceId;
import com.thermalprinter.adapter.PrinterAdapter;

import java.util.List;

public class RNNetPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    private PrinterAdapter adapter;
    private ReactApplicationContext reactContext;

    public RNNetPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Promise promise) {
        this.adapter = NetPrinterAdapter.getInstance();
        this.adapter.init(reactContext, promise);
    }

    @ReactMethod
    @Override
    public void closeConn(Promise promise) {
        this.adapter = NetPrinterAdapter.getInstance();
        this.adapter.closeConnectionIfExists(promise);
    }

    @ReactMethod
    @Override
    public void getDeviceList(Promise promise) {
        try {
            List<PrinterDevice> deviceList = this.adapter.getDeviceList();
            promise.resolve(deviceList);
        } catch (Exception ex) {
            promise.reject(ex);
        }
    }

    @ReactMethod
    public void connectPrinter(String host, Integer port, Promise promise) {
        adapter.selectDevice(NetPrinterDeviceId.valueOf(host, port), promise);
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
        // String imageBase64 = "data:image/png;base64," + imageUrl;
        // String base64ImageProcessed = imageUrl.split(",")[1];
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        adapter.printImageBase64(decodedByte, imageWidth, imageHeight, cut, beep, promise);
    }

    @Override
    public String getName() {
        return "RNNetPrinter";
    }
}
