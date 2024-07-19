package com.thermalprinter.adapter;

import static com.thermalprinter.adapter.UtilsImage.getPixelsSlow;
import static com.thermalprinter.adapter.UtilsImage.recollectSlice;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
// import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * Created by xiesubin on 2017/9/22.
 */
public class NetPrinterAdapter implements PrinterAdapter {

    private static NetPrinterAdapter mInstance;
    private ReactApplicationContext mContext;
    private final String LOG_TAG = "RNNetPrinter";
    private NetPrinterDevice mNetDevice;

    // {TODO- support other ports later}
    private final int[] PRINTER_ON_PORTS = {9100};
    private static final String EVENT_SCANNER_RESOLVED = "scannerResolved";
    private static final String EVENT_SCANNER_RUNNING = "scannerRunning";

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
    private final static byte[] SET_LINE_SPACE_24 = new byte[]{ESC_CHAR, 0x33, 24};
    private final static byte[] SET_LINE_SPACE_32 = new byte[]{ESC_CHAR, 0x33, 32};
    private final static byte[] LINE_FEED = new byte[]{0x0A};
    private static final byte[] CENTER_ALIGN = {0x1B, 0X61, 0X31};
    private static final byte[] CMD_CUT = {0x1D, 0x56, 0};
    private static final byte[] BEEP_SOUND = new byte[]{27, 66, 2, 1};

    private Socket mSocket;

    private boolean isRunning = false;

    private NetPrinterAdapter() {

    }

    public static NetPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new NetPrinterAdapter();

        }
        return mInstance;
    }

    @Override
    public void init(ReactApplicationContext reactContext, Promise promise) {
        this.mContext = reactContext;
        promise.resolve(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public List<PrinterDevice> getDeviceList() throws Exception {
        // promise.reject("do not need to invoke get device list for net
        // printer");
        // Use emitter instancee get devicelist to non block main thread
        // this.scan(promise);
        // return new ArrayList<>();
        try {
            WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            String ipAddress = ipToString(wifiManager.getConnectionInfo().getIpAddress());
            // WritableArray array = Arguments.createArray();
            List<PrinterDevice> array = new ArrayList<>();

            String prefix = ipAddress.substring(0, ipAddress.lastIndexOf('.') + 1);
            int suffix = Integer
                    .parseInt(ipAddress.substring(ipAddress.lastIndexOf('.') + 1, ipAddress.length()));

            for (int i = 0; i <= 255; i++) {
                if (i == suffix) {
                    continue;
                }
                ArrayList<Integer> ports = getAvailablePorts(prefix + i);
                if (!ports.isEmpty()) {
                    // WritableMap payload = Arguments.createMap();

                    // payload.putString("host", prefix + i);
                    // payload.putInt("port", 9100);

                    array.add(new NetPrinterDevice(prefix + i, 9100));
                }
            }
            return array;
        } catch (Exception ex) {
            Log.i(LOG_TAG, "No connection");
            throw (ex);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private ArrayList<Integer> getAvailablePorts(String address) {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : PRINTER_ON_PORTS) {
            if (crunchifyAddressReachable(address, port)) {
                ports.add(port);
            }
        }
        return ports;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static boolean crunchifyAddressReachable(String address, int port) {
        try {

            try (Socket crunchifySocket = new Socket()) {
                // Connects this socket to the server with a specified timeout value.
                crunchifySocket.connect(new InetSocketAddress(address, port), 100);
            }
            // Return true if connection successful
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private String ipToString(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise) {
        NetPrinterDeviceId netPrinterDeviceId = (NetPrinterDeviceId) printerDeviceId;

        if (this.mSocket != null && !this.mSocket.isClosed()
                && mNetDevice.getPrinterDeviceId().equals(netPrinterDeviceId)) {
            Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
            promise.resolve(this.mNetDevice.toRNWritableMap());
            return;
        }

        try {
            Socket socket = new Socket(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
            if (socket.isConnected()) {
                closeConnectionIfExists();
                this.mSocket = socket;
                this.mNetDevice = new NetPrinterDevice(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
                promise.resolve(this.mNetDevice.toRNWritableMap());
            } else {
                promise.reject("unable to build connection with host: " + netPrinterDeviceId.getHost()
                        + ", port: " + netPrinterDeviceId.getPort());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            promise.reject("failed to connect printer: " + e.getMessage());
        }
    }

    @Override
    public void closeConnectionIfExists() {
        if (this.mSocket != null) {
            if (!this.mSocket.isClosed()) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            this.mSocket = null;

        }
    }

    @Override
    public void closeConnectionIfExists(Promise promise) {
        boolean isError = false;
        if (this.mSocket != null) {
            if (!this.mSocket.isClosed()) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                    isError = true;
                    e.printStackTrace();
                    promise.reject(e);
                }
            }
            this.mSocket = null;
        }
        promise.resolve(true);
    }

    @Override
    public void printImageBase64(
            final Bitmap bitmapImage,
            int imageWidth,
            int imageHeight,
            boolean cut,
            boolean beep,
            Promise promise) {

        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mSocket == null) {
            promise.reject("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;

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
                printerOutputStream.write(new byte[]{(byte) (0x00ff & pixels[y].length),
                    (byte) ((0xff00 & pixels[y].length) >> 8)});
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
            // Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
            promise.reject(e);
        }
    }
}
