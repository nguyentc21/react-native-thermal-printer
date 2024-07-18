//
//  RNNetPrinter.m
//  RNThermalReceiptPrinter
//
//  Created by MTT on 06/11/19.
//  Copyright © 2019 Facebook. All rights reserved.
//


#import "RNNetPrinter.h"
#import "PrinterSDK.h"
#include <ifaddrs.h>
#include <arpa/inet.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NSString *const EVENT_SCANNER_RESOLVED = @"scannerResolved";
NSString *const EVENT_SCANNER_RUNNING = @"scannerRunning";

@interface PrivateIP : NSObject

@end

@implementation PrivateIP

- (NSString *)getIPAddress {

    NSString *address = @"error";
    struct ifaddrs *interfaces = NULL;
    struct ifaddrs *temp_addr = NULL;
    int success = 0;
    // retrieve the current interfaces - returns 0 on success
    success = getifaddrs(&interfaces);
    if (success == 0) {
        // Loop through linked list of interfaces
        temp_addr = interfaces;
        while(temp_addr != NULL) {
            if(temp_addr->ifa_addr->sa_family == AF_INET) {
                // Check if interface is en0 which is the wifi connection on the iPhone
                if([[NSString stringWithUTF8String:temp_addr->ifa_name] isEqualToString:@"en0"]) {
                    // Get NSString from C String
                    address = [NSString stringWithUTF8String:inet_ntoa(((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr)];
                }
            }
            temp_addr = temp_addr->ifa_next;
        }
    }
    // Free memory
    freeifaddrs(interfaces);
    return address;
}

@end

@implementation RNNetPrinter

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
    return @[EVENT_SCANNER_RESOLVED, EVENT_SCANNER_RUNNING];
}

RCT_EXPORT_METHOD(init
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    connected_ip = nil;
    is_scanning = NO;
    is_need_stop_scanning = NO;
    _printerArray = [NSMutableArray new];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handlePrinterConnectedNotification:) name:PrinterConnectedNotification object:nil];
    // [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleBLEPrinterConnectedNotification:) name:@"BLEPrinterConnected" object:nil];
    resolver(@[@"Init successful"]);
}

RCT_EXPORT_METHOD(getDeviceList:(NSString *)prefixPrinterIp
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        [self scan:prefixPrinterIp success:resolver fail:reject];
    });
}

- (void) scan: (NSString *)prefixPrinterIp
                resolver:(RCTPromiseResolveBlock)resolve
                rejecter:(RCTPromiseRejectBlock)reject {
    @try {
        is_scanning = YES;
        is_need_stop_scanning = NO;
        [self sendEventWithName:EVENT_SCANNER_RUNNING body:@YES];
        _printerArray = [NSMutableArray new];

        NSString *prefix = !prefixPrinterIp ? @"192.168.1" : prefixPrinterIp;

        for (NSInteger i = 1; i < 255; i++) {
            NSString *testIP = [NSString stringWithFormat:@"%@.%ld", prefix, (long)i];
            current_scan_ip = testIP;
            [[PrinterSDK defaultPrinterSDK] connectIP:testIP];
            [NSThread sleepForTimeInterval:0.1];
            if (is_need_stop_scanning == YES) {
                break;
            }
        }

        NSOrderedSet *orderedSet = [NSOrderedSet orderedSetWithArray:_printerArray];
        NSArray *arrayWithoutDuplicates = [orderedSet array];
        _printerArray = (NSMutableArray *)arrayWithoutDuplicates;

        [self sendEventWithName:EVENT_SCANNER_RESOLVED body:_printerArray];

        resolve(@[_printerArray]);
    } @catch (NSException *exception) {
        // NSLog(@"No connection");
        reject(@[exception.reason]);
    }
    [[PrinterSDK defaultPrinterSDK] disconnect];
    is_scanning = NO;
    is_need_stop_scanning = NO;
    [self sendEventWithName:EVENT_SCANNER_RUNNING body:@NO];
}

- (void)stopScan {
    if (is_scanning) {
        is_need_stop_scanning = YES;
    }
}

RCT_EXPORT_METHOD(stopGetDeviceList) {
    [self stopScan];
}

- (void)handlePrinterConnectedNotification:(NSNotification*)notification
{
    if (is_scanning) {
        [_printerArray addObject:@{@"host": current_scan_ip, @"port": @9100}];
    }
}

RCT_EXPORT_METHOD(connectPrinter:(NSString *)host
                  withPort:(nonnull NSNumber *)port
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        BOOL isConnectSuccess = [[PrinterSDK defaultPrinterSDK] connectIP:host];
        !isConnectSuccess ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer %@", host] : nil;

        connected_ip = host;
        // [[NSNotificationCenter defaultCenter] postNotificationName:@"NetPrinterConnected" object:nil];
        resolve(@[[NSString stringWithFormat:@"Connecting to printer %@", host]]);

    } @catch (NSException *exception) {
        reject(@[exception.reason]);
    }
}

RCT_EXPORT_METHOD(printImageBase64:(NSString *)base64Qr
                  printerOptions:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
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
        reject(@[exception.reason]);
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

RCT_EXPORT_METHOD(closeConn
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSString * current_connected_ip = connected_ip;
        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        [[PrinterSDK defaultPrinterSDK] disconnect];
        connected_ip = nil;
        resolve(@[current_connected_ip]);
    } @catch (NSException *exception) {
        // NSLog(@"%@", exception.reason);
        reject(@[exception.reason]);
    }
}

RCT_EXPORT_METHOD(printTestPaper) {
    [[PrinterSDK defaultPrinterSDK] printTestPaper];
}
RCT_EXPORT_METHOD(selfTest) {
    [[PrinterSDK defaultPrinterSDK] selfTest];
}
RCT_EXPORT_METHOD(clear) {
    [self stopScan];
    connected_ip = nil;
    is_scanning = NO;
    is_need_stop_scanning = NO;
    _printerArray = nil;
    [[PrinterSDK defaultPrinterSDK] disconnect];
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end
