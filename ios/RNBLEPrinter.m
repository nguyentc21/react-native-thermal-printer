#import <Foundation/Foundation.h>

#import "RNBLEPrinter.h"
#import "PrinterSDK.h"

@implementation RNBLEPrinter

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(init:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        _printerArray = [NSMutableArray new];
        m_printer = [[NSObject alloc] init];
        resolve(@[@"Init successful"]);
    } @catch (NSException *exception) {
        reject(@[exception.name], @[exception.reason], exception);
    }
}

RCT_EXPORT_METHOD(getDeviceList:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        !_printerArray ? [NSException raise:@"Null pointer exception" format:@"Must call init function first"] : nil;
        [[PrinterSDK defaultPrinterSDK] scanPrintersWithCompletion:^(Printer* printer){
            [self->_printerArray addObject:printer];
            NSMutableArray *mapped = [NSMutableArray arrayWithCapacity:[self->_printerArray count]];
            [self->_printerArray enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
                NSDictionary *dict = @{ @"device_name" : printer.name, @"inner_mac_address" : printer.UUIDString};
                [mapped addObject:dict];
            }];
            NSMutableArray*uniquearray = (NSMutableArray *)[[NSSet setWithArray:mapped] allObjects];
            [[PrinterSDK defaultPrinterSDK] stopScanPrinters];
            resolve(@[uniquearray]);
        }];
    } @catch (NSException *exception) {
        reject(@[exception.name], @[exception.reason], exception);
    }
}
RCT_EXPORT_METHOD(stopScan) {
    [[PrinterSDK defaultPrinterSDK] stopScanPrinters];
}

RCT_EXPORT_METHOD(connectPrinter:(NSString *)inner_mac_address
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        __block BOOL found = NO;
        __block Printer* selectedPrinter = nil;
        [_printerArray enumerateObjectsUsingBlock: ^(id obj, NSUInteger idx, BOOL *stop){
            selectedPrinter = (Printer *)obj;
            if ([inner_mac_address isEqualToString:(selectedPrinter.UUIDString)]) {
                found = YES;
                *stop = YES;
            }
        }];

        if (found) {
            [[PrinterSDK defaultPrinterSDK] connectBT:selectedPrinter];
            // [[NSNotificationCenter defaultCenter] postNotificationName:@"BLEPrinterConnected" object:nil];
            m_printer = selectedPrinter;
            resolve(@[[NSString stringWithFormat:@"Connected to printer %@", selectedPrinter.name]]);
        } else {
            [NSException raise:@"Invalid connection" format:@"connectPrinter: Can't connect to printer %@", inner_mac_address];
        }
    } @catch (NSException *exception) {
        reject(@[exception.name], @[exception.reason], exception);
    }
}

RCT_EXPORT_METHOD(printImageBase64:(NSString *)base64Qr
                  printerOptions:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        !m_printer ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        if(![base64Qr  isEqual: @""]){
            NSString *result = [@"data:image/png;base64," stringByAppendingString:base64Qr];
            NSURL *url = [NSURL URLWithString:result];
            NSData *imageData = [NSData dataWithContentsOfURL:url];
            NSString* printerWidth = [options valueForKey:@"printerWidth"];
            
            int printerWitdthInt = [printerWidth intValue];
            NSNumber* beepPtr = [options valueForKey:@"beep"];
            NSNumber* cutPtr = [options valueForKey:@"cut"];

            BOOL beep = (BOOL)[beepPtr intValue];
            BOOL cut = (BOOL)[cutPtr intValue];

            if(imageData != nil){
                UIImage* image = [UIImage imageWithData:imageData];
                UIImage* printImage = [self getPrintImage:image printerOptions:options];

                [[PrinterSDK defaultPrinterSDK] setPrintWidth:printerWitdthInt];
                [[PrinterSDK defaultPrinterSDK] printImage:printImage ];
                beep ? [[PrinterSDK defaultPrinterSDK] beep] : nil;
                cut ? [[PrinterSDK defaultPrinterSDK] cutPaper] : nil;
            }
        }
        resolve(@[@true]);
    } @catch (NSException *exception) {
        reject(@[exception.name], @[exception.reason], exception);
    }
}

-(UIImage *)getPrintImage:(UIImage *)image
           printerOptions:(NSDictionary *)options {
   NSNumber* nWidth = [options valueForKey:@"imageWidth"];
   NSNumber* nHeight = [options valueForKey:@"imageHeight"];
   NSNumber* nPaddingX = [options valueForKey:@"paddingX"];

   CGFloat newWidth = 150;
   if(nWidth != nil) {
       newWidth = [nWidth floatValue];
   }

   CGFloat newHeight = image.size.height;
   if(nHeight != nil) {
        int iNHeight = [nHeight intValue];
        if (iNHeight == -1) {
            CGFloat _iWidth = [nWidth floatValue];
            CGFloat _width = image.size.width;
            CGFloat ratio = (_iWidth / _width);
            newHeight = ratio * image.size.height;
        } else {
            newHeight = [nHeight floatValue];
        }
   }

   CGFloat paddingX = 250;
   if(nPaddingX != nil) {
       paddingX = [nPaddingX floatValue];
   }

   CGFloat _newHeight = newHeight;
   CGSize newSize = CGSizeMake(newWidth, _newHeight);
   UIGraphicsBeginImageContextWithOptions(newSize, false, 0.0);
   CGContextRef context = UIGraphicsGetCurrentContext();
   CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
   CGImageRef immageRef = image.CGImage;
   CGContextDrawImage(context, CGRectMake(0, 0, newWidth, newHeight), immageRef);
   CGImageRef newImageRef = CGBitmapContextCreateImage(context);
   UIImage* newImage = [UIImage imageWithCGImage:newImageRef];

   CGImageRelease(newImageRef);
   UIGraphicsEndImageContext();

   UIImage* paddedImage = [self addImagePadding:newImage paddingX:paddingX paddingY:0];
   return paddedImage;
}

-(UIImage *)addImagePadding:(UIImage * )image
                   paddingX: (CGFloat) paddingX
                   paddingY: (CGFloat) paddingY
{
    CGFloat width = image.size.width + paddingX;
    CGFloat height = image.size.height + paddingY;

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 0.0);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context, [UIColor whiteColor].CGColor);
    CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
    CGContextFillRect(context, CGRectMake(0, 0, width, height));
    CGFloat originX = (width - image.size.width)/2;
    CGFloat originY = (height -  image.size.height)/2;
    CGImageRef immageRef = image.CGImage;
    CGContextDrawImage(context, CGRectMake(originX, originY, image.size.width, image.size.height), immageRef);
    CGImageRef newImageRef = CGBitmapContextCreateImage(context);
    UIImage* paddedImage = [UIImage imageWithCGImage:newImageRef];

    CGImageRelease(newImageRef);
    UIGraphicsEndImageContext();

    return paddedImage;
}

RCT_EXPORT_METHOD(closeConn:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        m_printer = nil;
        [[PrinterSDK defaultPrinterSDK] disconnect];
        resolve(@[@true]);
    } @catch (NSException *exception) {
        // NSLog(@"%@", exception.reason);
        reject(@[exception.name], @[exception.reason], exception);
    }
}

RCT_EXPORT_METHOD(printTestPaper) {
    [[PrinterSDK defaultPrinterSDK] printTestPaper];
}
RCT_EXPORT_METHOD(selfTest) {
    [[PrinterSDK defaultPrinterSDK] selfTest];
}
RCT_EXPORT_METHOD(clear) {
    [[PrinterSDK defaultPrinterSDK] stopScanPrinters];
    _printerArray = nil;
    m_printer = nil;
    [[PrinterSDK defaultPrinterSDK] disconnect];
}

@end

