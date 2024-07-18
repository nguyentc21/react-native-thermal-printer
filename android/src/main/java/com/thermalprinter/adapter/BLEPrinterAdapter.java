package com.thermalprinter.adapter;

import static com.thermalprinter.adapter.UtilsImage.getPixelsSlow;
import static com.thermalprinter.adapter.UtilsImage.recollectSlice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.ArrayList;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.graphics.BitmapFactory;
/**
 * Created by xiesubin on 2017/9/21.
 */

public class BLEPrinterAdapter implements PrinterAdapter{


    private static BLEPrinterAdapter mInstance;


    private final String LOG_TAG = "RNBLEPrinter";

    private BluetoothDevice mBluetoothDevice;
    private BluetoothSocket mBluetoothSocket;


    private ReactApplicationContext mContext;

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
    private final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 24 };
    private final static byte[] SET_LINE_SPACE_32 = new byte[] { ESC_CHAR, 0x33, 32 };
    private final static byte[] LINE_FEED = new byte[] { 0x0A };
    private static final byte[] CENTER_ALIGN = { 0x1B, 0X61, 0X31 };
    private static final byte[] CMD_CUT = {0x1D, 0x56, 0};
    private static final byte[] BEEP_SOUND = new byte[]{27, 66, 2, 1};



    private BLEPrinterAdapter(){}

    public static BLEPrinterAdapter getInstance() {
        if(mInstance == null) {
            mInstance = new BLEPrinterAdapter();
        }
        return mInstance;
    }

    @Override
    public void init(ReactApplicationContext reactContext, Promise promise) {
        this.mContext = reactContext;
        BluetoothAdapter bluetoothAdapter = getBTAdapter();
        if(bluetoothAdapter == null) {
            promise.reject("No bluetooth adapter available");
            return;
        }
        if(!bluetoothAdapter.isEnabled()) {
            promise.reject("bluetooth adapter is not enabled");
            return;
        }else{
            promise.resolve();
        }

    }

    private static BluetoothAdapter getBTAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public List<PrinterDevice> getDeviceList() {
        try {
            BluetoothAdapter bluetoothAdapter = getBTAdapter();
            List<PrinterDevice> printerDevices = new ArrayList<>();
            if(bluetoothAdapter == null) {
                throw new Exception("No bluetooth adapter available");
            }
            if (!bluetoothAdapter.isEnabled()) {
                throw new Exception("Bluetooth is not enabled");
            }
            Set<BluetoothDevice> pairedDevices = getBTAdapter().getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                printerDevices.add(new BLEPrinterDevice(device));
            }
            return printerDevices;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise) {
        BluetoothAdapter bluetoothAdapter = getBTAdapter();
        if(bluetoothAdapter == null) {
            promise.reject("No bluetooth adapter available");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            promise.reject("bluetooth is not enabled");
            return;
        }
        BLEPrinterDeviceId blePrinterDeviceId = (BLEPrinterDeviceId)printerDeviceId;
        if(this.mBluetoothDevice != null){
            if(this.mBluetoothDevice.getAddress().equals(blePrinterDeviceId.getInnerMacAddress()) && this.mBluetoothSocket != null){
                Log.v(LOG_TAG, "do not need to reconnect");
                promise.resolve(new BLEPrinterDevice(this.mBluetoothDevice).toRNWritableMap());
                return;
            }else{
                closeConnectionIfExists();
            }
        }
        Set<BluetoothDevice> pairedDevices = getBTAdapter().getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            if(device.getAddress().equals(blePrinterDeviceId.getInnerMacAddress())){

                try{
                    connectBluetoothDevice(device);
                    promise.resolve(new BLEPrinterDevice(this.mBluetoothDevice).toRNWritableMap());
                    return;
                }catch (IOException e){
                    e.printStackTrace();
                    promise.reject(e.getMessage());
                    return;
                }
            }
        }
        String errorText = "Can not find the specified printing device, please perform Bluetooth pairing in the system settings first.";
        Toast.makeText(this.mContext, errorText, Toast.LENGTH_LONG).show();
        promise.reject(errorText);
        return;
    }

    private void connectBluetoothDevice(BluetoothDevice device) throws IOException{
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        this.mBluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
        this.mBluetoothSocket.connect();
        this.mBluetoothDevice = device;//最后一步执行

    }

    @Override
    public void closeConnectionIfExists() {
        try{
            if(this.mBluetoothSocket != null){
                this.mBluetoothSocket.close();
                this.mBluetoothSocket = null;
            }
        }catch(IOException e){
            e.printStackTrace();
        }

        if(this.mBluetoothDevice != null) {
            this.mBluetoothDevice = null;
        }
    }
    @Override
    public void closeConnectionIfExists(Promise promise) {
        boolean isError = false;
        try{
            if(this.mBluetoothSocket != null){
                this.mBluetoothSocket.close();
                this.mBluetoothSocket = null;
            }
        }catch(IOException e){
            isError = true;
            e.printStackTrace();
        }

        if(this.mBluetoothDevice != null) {
            this.mBluetoothDevice = null;
        }
        if (!isError) {
            promise.resolve();
        } else {
            promise.reject();
        }
    }

    @Override
    public void printImageBase64(
        final Bitmap imageUrl,
        int imageWidth,
        int imageHeight, 
        boolean cut,
        boolean beep,
        Promise promise) {

        if(bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mBluetoothSocket == null) {
            promise.reject("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final BluetoothSocket socket = this.mBluetoothSocket;

        try {
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            OutputStream printerOutputStream = socket.getOutputStream();

            printerOutputStream.write(SET_LINE_SPACE_24);
            printerOutputStream.write(CENTER_ALIGN);

            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printerOutputStream.write(new byte[]{(byte)(0x00ff & pixels[y].length)
                        , (byte)((0xff00 & pixels[y].length) >> 8)});
                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printerOutputStream.write(recollectSlice(y, x, pixels));
                }

                // Do a line feed, if not the printing will resume on the same line
                printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            printerOutputStream.write(LINE_FEED);
            if (cut == true) {
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(LINE_FEED);
                printerOutputStream.write(CMD_CUT);
            }
            if (beep == true) {
                printerOutputStream.write(BEEP_SOUND);
            }

            printerOutputStream.flush();
            promise.resolve("Successful!");
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
        }
    }
}
