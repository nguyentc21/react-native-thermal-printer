import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

import { COMMANDS } from './utils/printer-commands';
import { connectToHost } from './utils/net-connect';

const RNBLEPrinter = NativeModules.RNBLEPrinter;
const RNNetPrinter = NativeModules.RNNetPrinter;

interface PrinterOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
}

interface PrinterImageOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
  imageWidth?: number;
  imageHeight?: number;
  printerWidthType?: PrinterWidth;
  // only ios
  paddingX?: number;
}

interface PrinterImageBase64Options {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
  /** should be set = 576 for 80mm paper; = 384 for 56mm paper;
   */
  imageWidth?: number;
  /** Set imageHeight = -1 to auto scale image height. */
  imageHeight?: number;
  /** Only iOS (required)
   * should be set = 576 for 80mm paper; = 384 for 56mm paper;
   */
  printerWidth?: number;
  paddingX?: number;
}

interface IUSBPrinter {
  device_name: string;
  vendor_id: string;
  product_id: string;
}

interface IBLEPrinter {
  device_name: string;
  inner_mac_address: string;
}

interface INetPrinter {
  host: string;
  port: number;
}

enum ColumnAliment {
  LEFT,
  CENTER,
  RIGHT,
}
enum PrinterWidth {
  '58mm' = 58,
  '80mm' = 80,
}
enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
  EVENT_NET_PRINTER_SCANNED_SUCCESS = 'scannerResolved',
  EVENT_NET_PRINTER_SCANNING = 'scannerRunning',
  EVENT_NET_PRINTER_SCANNED_ERROR = 'registerError',
}

const BLEPrinter = {
  init: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.init(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  clear: () => {
    RNBLEPrinter.clear();
  },

  getDeviceList: (): Promise<IBLEPrinter[]> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.getDeviceList(
        (printers: IBLEPrinter[]) => resolve(printers),
        (error: Error) => reject(error)
      )
    ),

  stopScan: (): void => {
    RNBLEPrinter.stopScan();
  },

  connectPrinter: (inner_mac_address: string): Promise<IBLEPrinter> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.connectPrinter(
        inner_mac_address,
        (printer: IBLEPrinter) => resolve(printer),
        (error: Error) => reject(error)
      )
    ),

  closeConn: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.closeConn(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (
    Base64: string,
    opts: PrinterImageBase64Options = {}
  ) {
    if (Platform.OS === 'ios') {
      return new Promise(function (resolve, reject) {
        return RNBLEPrinter.printImageBase64(
          Base64,
          opts,
          function (success: any) {
            return resolve(success);
          },
          function (error: Error) {
            return reject(error);
          }
        );
      });
    } else {
      return new Promise(function (resolve, reject) {
        return RNBLEPrinter.printImageBase64(
          Base64,
          opts?.imageWidth ?? 0,
          opts?.imageHeight ?? 0,
          function (success: any) {
            return resolve(success);
          },
          function (error: Error) {
            return reject(error);
          }
        );
      });
    }
  },

  printTestPaper: (): void => {
    RNBLEPrinter.printTestPaper();
  },
  selfTest: (): void => {
    RNBLEPrinter.selfTest();
  },
};

const NetPrinter = {
  init: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNNetPrinter.init(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  clear: () => {
    RNNetPrinter.clear();
  },

  getDeviceList: (prefixPrinterIp?: string): Promise<INetPrinter[]> =>
    new Promise((resolve, reject) =>
      RNNetPrinter.getDeviceList(
        prefixPrinterIp,
        (printers: INetPrinter[]) => resolve(printers),
        (error: Error) => reject(error)
      )
    ),

  stopGetDeviceList: (): void => {
    RNNetPrinter.stopGetDeviceList();
  },

  connectPrinter: (
    host: string,
    port: number,
    timeout?: number
  ): Promise<INetPrinter> =>
    new Promise(async (resolve, reject) => {
      try {
        await connectToHost(host, timeout);
        RNNetPrinter.connectPrinter(
          host,
          port,
          (printer: INetPrinter) => resolve(printer),
          (error: Error) => reject(error)
        );
      } catch (error: any) {
        reject(error?.message || `Connect to ${host} fail`);
      }
    }),

  closeConn: (): Promise<string> =>
    new Promise((resolve, reject) =>
      RNNetPrinter.closeConn(
        (connectedIp: string) => resolve(connectedIp),
        (error: Error) => reject(error)
      )
    ),
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (
    Base64: string,
    opts: PrinterImageBase64Options = {}
  ) {
    if (Platform.OS === 'ios') {
      return new Promise(function (resolve, reject) {
        return RNNetPrinter.printImageBase64(
          Base64,
          opts,
          function (success: any) {
            return resolve(success);
          },
          function (error: Error) {
            return reject(error);
          }
        );
      });
    } else {
      return new Promise(function (resolve, reject) {
        return RNNetPrinter.printImageBase64(
          Base64,
          opts?.imageWidth ?? 0,
          opts?.imageHeight ?? 0,
          function (success: any) {
            return resolve(success);
          },
          function (error: Error) {
            return reject(error);
          }
        );
      });
    }
  },

  printTestPaper: (): void => {
    RNNetPrinter.printTestPaper();
  },
  selfTest: (): void => {
    RNNetPrinter.selfTest();
  },
};

const NetPrinterEventEmitter =
  Platform.OS === 'ios'
    ? new NativeEventEmitter(RNNetPrinter)
    : new NativeEventEmitter();

export type {
  PrinterOptions,
  PrinterImageOptions,
  PrinterImageBase64Options,
  IUSBPrinter,
  IBLEPrinter,
  INetPrinter,
};

export {
  PrinterWidth,
  ColumnAliment,
  RN_THERMAL_RECEIPT_PRINTER_EVENTS,
  COMMANDS,
  NetPrinter,
  BLEPrinter,
  NetPrinterEventEmitter,
};
