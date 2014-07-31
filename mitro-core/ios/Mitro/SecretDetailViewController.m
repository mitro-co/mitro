//
//  DetailViewController.m
//  Mitro
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import "SecretDetailViewController.h"

#import "Mitro.h"
#import "OpenInChromeController.h"
#import "SecretManager.h"

@implementation SecretDetailViewController

#pragma mark - Managing the detail item

- (void)viewDidLoad {
    [super viewDidLoad];
    self.navigationController.navigationBar.barTintColor = [UIColor whiteColor];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [[Mitro secretManager] setDelegate:self];
    [[Mitro sessionManager] setDelegate:self];
    [self updateView];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [[Mitro secretManager] setDelegate:nil];
    [[Mitro sessionManager] setDelegate:nil];
}

- (BOOL)openUrl:(NSString*)urlString {
    NSURL* url = [NSURL URLWithString:urlString];

    if ([[OpenInChromeController sharedInstance] isChromeInstalled]) {
        return [[OpenInChromeController sharedInstance] openInChrome:url withCallbackURL:nil createNewTab:YES];
    } else {
        return [[UIApplication sharedApplication] openURL:url];
    }
}

- (void)copyText:(NSString*)text {
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = text;
}

- (void)updateView {
}

- (void)onLogout {
    [[Mitro secretManager] setDelegate:nil];
    [Mitro releaseSecretManager];

    [self.navigationController popViewControllerAnimated:NO];
}

@end