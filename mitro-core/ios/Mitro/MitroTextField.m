//
//  MitroTextField.m
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "MitroTextField.h"

#import <QuartzCore/QuartzCore.h>

#import "MitroColor.h"

@interface MitroTextField()

@property (nonatomic) UIEdgeInsets edgeInsets;

@end

@implementation MitroTextField

static const CGFloat kBorderWidth = 1.0;
static const CGFloat kFontSize = 20.0;
static const CGFloat kPadding = 8.0;

static const NSUInteger kBackgroundColor = 0xffffff;
static const NSUInteger kTextColor = 0x4d5961;
static const NSUInteger kBorderColor = 0xcccccc;
static const NSUInteger kFocusBorderColor = 0x999999;
static const NSUInteger kPlaceholderColor = 0xcccccc;

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

    // Text field padding.
    self.edgeInsets = UIEdgeInsetsMake(0, kPadding, 0, kPadding);

    // Register for notifications so that we can change the border color when focus changes.
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(textFieldDidBeginEditing)
                                                 name:UITextFieldTextDidBeginEditingNotification
                                               object:self];
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(textFieldDidEndEditing)
                                                 name:UITextFieldTextDidEndEditingNotification
                                               object:self];
}

- (void)textFieldDidBeginEditing {
    self.layer.borderColor = [[UIColor colorWithHex:kFocusBorderColor] CGColor];
}

- (void)textFieldDidEndEditing {
    self.layer.borderColor = [[UIColor colorWithHex:kBorderColor] CGColor];
}

- (CGRect)textRectForBounds:(CGRect)bounds {
    return [super textRectForBounds:UIEdgeInsetsInsetRect(bounds, self.edgeInsets)];
}

- (CGRect)editingRectForBounds:(CGRect)bounds {
    return [super editingRectForBounds:UIEdgeInsetsInsetRect(bounds, self.edgeInsets)];
}

@end
