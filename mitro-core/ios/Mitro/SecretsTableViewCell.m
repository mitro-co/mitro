//
//  SecretsTableViewCell.m
//  Mitro
//
//  Created by Adam Hilss on 9/16/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "SecretsTableViewCell.h"

#import "MitroColor.h"

@implementation SecretsTableViewCell

static const NSUInteger kBackgroundColor = 0xf6f8f9;

- (id)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier {
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
    }
    return self;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    self.backgroundColor = [UIColor colorWithHex:kBackgroundColor];
    self.textLabel.textColor = [UIColor darkTextColor];
    self.detailTextLabel.textColor = [UIColor lightTextColor];
}

@end
