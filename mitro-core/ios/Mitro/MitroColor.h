//
//  MitroColor.h
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface UIColor (Mitro)

+ (UIColor *)colorWithRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue;
+ (UIColor *)colorWithHex:(NSUInteger)rgb;

+ (UIColor *)backgroundColor;
+ (UIColor *)darkTextColor;
+ (UIColor *)lightTextColor;
+ (UIColor *)tintColor;

@end
