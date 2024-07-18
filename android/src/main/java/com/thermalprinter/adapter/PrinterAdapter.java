package com.thermalprinter.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.telecom.Call;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

public interface PrinterAdapter {

    public void init(ReactApplicationContext reactContext, Promise promise);

    public void getDeviceList(Promise promise);

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
