//
//  DetailViewController.h
//  Mitro
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import <UIKit/UIKit.h>

#import "SecretManager.h"
#import "SessionManager.h"

enum {
    kTitleTag = 1,
    kUrlTag = 2
};

@interface SecretDetailViewController : UIViewController<SecretManagerDelegate, SessionManagerDelegate>

@property (nonatomic) NSInteger secretId;

- (BOOL)openUrl:(NSString*)urlString;
- (void)copyText:(NSString*)text;

- (void)updateView;

@end

