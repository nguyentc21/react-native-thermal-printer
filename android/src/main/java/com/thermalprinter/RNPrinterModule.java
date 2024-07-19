package com.thermalprinter;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;

public interface RNPrinterModule {

    @ReactMethod
    public void init(Promise promise);

    @ReactMethod
    public void closeConn(Promise promise);

    @ReactMethod
    public void getDeviceList(Promise promise);

    @ReactMethod
    public void printImageBase64(
            String base64,
            int imageWidth,
            int imageHeight,
            boolean cut,
            boolean beep,
            Promise promise
    );
}
