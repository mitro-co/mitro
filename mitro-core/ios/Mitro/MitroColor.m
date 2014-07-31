//
//  MitroColor.m
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "MitroColor.h"

@implementation UIColor (Mitro)

+ (UIColor *)colorWithRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue {
    return [UIColor colorWithRed:red / 255.0 green:green / 255.0 blue:blue / 255.0 alpha:1.0f];
}

+ (UIColor *)colorWithHex:(NSUInteger)rgb {
    return [UIColor colorWithRed:(CGFloat)((rgb >> 16) & 0xFF) green:(CGFloat)((rgb >> 8) & 0xFF) blue:(CGFloat)(rgb & 0xFF)];
}

+ (UIColor *)backgroundColor {
    return [UIColor colorWithHex:0xf6f8f9];
}

+ (UIColor *)darkTextColor {
    return [UIColor colorWithHex:0x4d5961];
}

+ (UIColor *)lightTextColor {
    return [UIColor colorWithHex:0xafafaf];
}

+ (UIColor *)tintColor {
    return [UIColor colorWithHex:0x44a9f8];
}

@end
