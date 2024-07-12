
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNThermalPrinterSpec.h"

@interface ThermalPrinter : NSObject <NativeThermalPrinterSpec>
#else
#import <React/RCTBridgeModule.h>

@interface ThermalPrinter : NSObject <RCTBridgeModule>
#endif

@end
