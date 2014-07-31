//
//  MitroButton.m
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "MitroButton.h"

#import <QuartzCore/QuartzCore.h>

#import "MitroColor.h"

@implementation MitroButton

static const CGFloat kBorderRadius = 5.0;
static const CGFloat kBorderWidth = 1.0;
static const CGFloat kFontSize = 16.0;

static const NSUInteger kBackgroundColor = 0xffffff;
static const NSUInteger kHighlightBackgroundColor = 0xe8ebed;

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
    self.tintColor = [UIColor tintColor];
    self.backgroundColor = [UIColor colorWithHex:kBackgroundColor];
    self.layer.masksToBounds = YES;
    self.layer.cornerRadius = kBorderRadius;
    self.layer.borderColor = [self.tintColor CGColor];
    self.layer.borderWidth = kBorderWidth;
    self.titleLabel.font = [UIFont systemFontOfSize:kFontSize];
    [self setTitleColor:self.tintColor forState:UIControlStateNormal];
    [self setTitleColor:self.tintColor forState:UIControlStateHighlighted];
}

// Override to set background color when button is highlighted.
- (void)setHighlighted:(BOOL)highlighted {
    BOOL highlightChanged = highlighted != self.highlighted;
    super.highlighted = highlighted;

    if (highlightChanged) {
        if (highlighted) {
            self.backgroundColor = [UIColor colorWithHex:kHighlightBackgroundColor];
        } else {
            self.backgroundColor = [UIColor colorWithHex:kBackgroundColor];
        }
        [self setNeedsDisplay];
    }
}

@end
