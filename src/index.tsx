import { NativeModules, Platform } from 'react-native';

import { connectToHost } from './utils/net-connect';

const RNBLEPrinter = NativeModules.RNBLEPrinter;
const RNNetPrinter = NativeModules.RNNetPrinter;

interface PrinterImageBase64Options {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  /** should be set = 576 for 80mm paper; = 384 for 56mm paper;
   */
  imageWidth: number;
  /** Set imageHeight = -1 to auto scale image height. */
  imageHeight: number;
  /** Only iOS (required)
   * should be set = 576 for 80mm paper; = 384 for 56mm paper;
   */
  printerWidth: number;
  paddingX?: number;
}

interface IBLEPrinter {
  device_name: string;
  inner_mac_address: string;
}

interface INetPrinter {
  host: string;
  port: number;
}

enum PrinterWidth {
  '58mm' = 58,
  '80mm' = 80,
}

const BLEPrinter = {
  init: async (): Promise<void> => await RNBLEPrinter.init(),

  clear:
    Platform.OS === 'ios'
      ? (): void => {
          RNBLEPrinter.clear();
        }
      : undefined,

  getDeviceList: async (): Promise<IBLEPrinter[]> =>
    await RNBLEPrinter.getDeviceList(),

  stopScan:
    Platform.OS === 'ios'
      ? (): void => {
          RNBLEPrinter.stopScan();
        }
      : undefined,

  connectPrinter: async (inner_mac_address: string): Promise<IBLEPrinter> =>
    await RNBLEPrinter.connectPrinter(inner_mac_address),

  closeConn: async (): Promise<void> => await RNBLEPrinter.closeConn(),

  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: async (
    Base64: string,
    opts: PrinterImageBase64Options
  ): Promise<boolean> => {
    if (Platform.OS === 'ios') {
      return await RNBLEPrinter.printImageBase64(Base64, opts);
    } else {
      const { imageWidth, imageHeight, cut, beep } = opts;
      return await RNBLEPrinter.printImageBase64(
        Base64,
        imageWidth,
        imageHeight,
        cut,
        beep
      );
    }
  },

  printTestPaper:
    Platform.OS === 'ios'
      ? (): void => RNBLEPrinter.printTestPaper()
      : undefined,
  selfTest:
    Platform.OS === 'ios' ? (): void => RNBLEPrinter.selfTest() : undefined,
};

const NetPrinter = {
  init: async (): Promise<void> => await RNNetPrinter.init(),

  clear:
    Platform.OS === 'ios'
      ? (): void => {
          RNNetPrinter.clear();
        }
      : undefined,

  getDeviceList: async (prefixPrinterIp?: string): Promise<INetPrinter[]> =>
    await RNNetPrinter.getDeviceList(prefixPrinterIp),

  stopScan:
    Platform.OS === 'ios'
      ? (): void => {
          RNNetPrinter.stopGetDeviceList();
        }
      : undefined,

  connectPrinter: async (
    host: string,
    port: number,
    timeout?: number
  ): Promise<INetPrinter> => {
    await connectToHost(host, timeout);
    return await RNNetPrinter.connectPrinter(host, port);
  },

  closeConn: async (): Promise<string | undefined> =>
    await RNNetPrinter.closeConn(),

  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: async (
    Base64: string,
    opts: PrinterImageBase64Options
  ): Promise<boolean> => {
    if (Platform.OS === 'ios') {
      return await RNNetPrinter.printImageBase64(Base64, opts);
    } else {
      const { imageWidth, imageHeight, cut, beep } = opts;
      return await RNNetPrinter.printImageBase64(
        Base64,
        imageWidth,
        imageHeight,
        cut,
        beep
      );
    }
  },

  printTestPaper:
    Platform.OS === 'ios'
      ? (): void => RNNetPrinter.printTestPaper()
      : undefined,
  selfTest:
    Platform.OS === 'ios' ? (): void => RNNetPrinter.selfTest() : undefined,
};

export type { PrinterImageBase64Options, IBLEPrinter, INetPrinter };

export { PrinterWidth, NetPrinter, BLEPrinter };
