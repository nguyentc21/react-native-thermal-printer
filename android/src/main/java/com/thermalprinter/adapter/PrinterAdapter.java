package com.thermalprinter.adapter;

import android.graphics.Bitmap;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import java.util.List;

public interface PrinterAdapter {

    public void init(ReactApplicationContext reactContext, Promise promise);

    public List<PrinterDevice> getDeviceList() throws Exception;

    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise);

    public void closeConnectionIfExists();

    public void closeConnectionIfExists(Promise promise);

    public void printImageBase64(
            Bitmap imageUrl,
            int imageWidth,
            int imageHeight,
            boolean cut,
            boolean beep,
            Promise promise
    );
}
