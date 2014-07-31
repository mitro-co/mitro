//
//  MitroTextView.m
//  Mitro
//
//  Created by Adam Hilss on 10/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "MitroTextView.h"

#import "MitroColor.h"

@implementation MitroTextView

static const CGFloat kBorderWidth = 1.0;
static const CGFloat kFontSize = 14.0;

static const NSUInteger kBackgroundColor = 0xffffff;
static const NSUInteger kTextColor = 0x4d5961;
static const NSUInteger kBorderColor = 0xcccccc;

- (id)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        [self initialize];
    }
    return self;
}

- (id) initWithCoder:(NSCoder *)aCoder {
    self = [super initWithCoder:aCoder];
    if (self) {
        [self initialize];
    }
    return self;
}

- (void)initialize {
    self.backgroundColor = [UIColor colorWithHex:kBackgroundColor];
    self.layer.masksToBounds = YES;
    self.layer.borderColor = [[UIColor colorWithHex:kBorderColor] CGColor];
    self.layer.borderWidth = kBorderWidth;
    self.font = [UIFont systemFontOfSize:kFontSize];
    self.textColor = [UIColor colorWithHex:kTextColor];
}

@end
