//
//  ProgressOverlayView.m
//  Mitro
//
//  Created by Adam Hilss on 2/6/14.
//  Copyright (c) 2014 Lectorius, Inc. All rights reserved.
//

#import "MitroColor.h"
#import "ProgressOverlayView.h"

@implementation ProgressOverlayView

- (id)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        self.backgroundColor = [UIColor backgroundColor];

        self.titleLabel = [[UILabel alloc] init];
        self.titleLabel.frame = CGRectMake(20, 150, 280, 21);
        self.titleLabel.font = [UIFont boldSystemFontOfSize:18.0];
        self.titleLabel.textColor = [UIColor darkTextColor];
        self.titleLabel.textAlignment = NSTextAlignmentCenter;

        self.detailLabel = [[UILabel alloc] init];
        self.detailLabel.frame = CGRectMake(20, 185, 280, 21);
        self.detailLabel.font = [UIFont systemFontOfSize:15.0];
        self.detailLabel.textColor = [UIColor darkTextColor];
        self.detailLabel.textAlignment = NSTextAlignmentCenter;

        self.progressBar = [[UIProgressView alloc] init];
        self.progressBar.frame = CGRectMake(80, 214, 160, 2);

        self.activityIndicator = [[UIActivityIndicatorView alloc] init];
        self.activityIndicator.frame = CGRectMake(150, 199, 20, 20);
        self.activityIndicator.activityIndicatorViewStyle = UIActivityIndicatorViewStyleGray;

        [self addSubview:self.titleLabel];
        [self addSubview:self.detailLabel];
        [self addSubview:self.progressBar];
        [self addSubview:self.activityIndicator];
    }
    return self;
}

@end
