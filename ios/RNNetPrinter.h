

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNNetPrinter : RCTEventEmitter <RCTBridgeModule>{
    NSString *connected_ip;
    NSString *current_scan_ip;
    NSMutableArray* _printerArray;
    bool is_scanning;
    bool is_need_stop_scanning;
}

@end

