//
//  ProgressOverlayView.h
//  Mitro
//
//  Created by Adam Hilss on 2/6/14.
//  Copyright (c) 2014 Lectorius, Inc. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface ProgressOverlayView : UIView

@property (strong, nonatomic) UILabel *titleLabel;
@property (strong, nonatomic) UILabel *detailLabel;
@property (strong, nonatomic) UIProgressView *progressBar;
@property (strong, nonatomic) UIActivityIndicatorView *activityIndicator;

@end
