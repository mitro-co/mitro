//
//  PasswordDetailViewController.m
//  Mitro
//
//  Created by Adam Hilss on 10/14/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "PasswordDetailViewController.h"

#import "Mitro.h"

@interface PasswordDetailViewController ()

@end

@implementation PasswordDetailViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.password = nil;
}

- (void)updateView {
    Secret* secret = [[Mitro secretManager] getSecret:self.secretId];
    if (secret == nil) {
        return;
    }
    
    self.titleLabel.text = secret.displayTitle;
    self.linkLabel.text = secret.domain;
    self.usernameText.text = secret.username;
}

- (IBAction)onCopyUsernameButtonTouched:(id)sender {
    [self copyText:self.usernameText.text];
}

- (void)copyPassword {
    [self copyText:self.password];
}

- (void)tryCopyPassword {
    if (self.password == nil) {
        [[Mitro secretManager] getSecretCriticalData:self.secretId];
        self.passwordAction = kCopy;
        [self.passwordButton setTitle:@"" forState:UIControlStateNormal];
        [self.passwordActivityIndicator startAnimating];
    } else {
        [self copyPassword];
    }
}

- (IBAction)onCopyPasswordButtonTouched:(id)sender {
    [self tryCopyPassword];
}

- (void)togglePasswordVisibility {
    if (self.passwordText.isSecureTextEntry) {
        self.passwordText.text = self.password;
        [self.passwordText setSecureTextEntry:NO];
        [self.viewButton setTitle:@"Hide" forState:UIControlStateNormal];
    } else {
        self.passwordText.text = @"......";
        [self.passwordText setSecureTextEntry:YES];
        [self.viewButton setTitle:@"View" forState:UIControlStateNormal];
    }
}

- (void)tryTogglePasswordVisibility {
    if (self.password == nil) {
        [[Mitro secretManager] getSecretCriticalData:self.secretId];
        self.passwordAction = kView;
        [self.viewButton setTitle:@"" forState:UIControlStateNormal];
        [self.viewActivityIndicator startAnimating];
    } else {
        [self togglePasswordVisibility];
    }
}

- (IBAction)onViewTouched:(id)sender {
    [self tryTogglePasswordVisibility];
}

-(void)touchesBegan:(NSSet*)touches withEvent:(UIEvent*)event {
    UITouch *touch = [touches anyObject];
    
    if(touch.view.tag == kTitleTag || touch.view.tag == kUrlTag) {
        Secret* secret = [[Mitro secretManager] getSecret:self.secretId];
        if (secret == nil) {
            return;
        }

        [self openUrl:secret.url];
    }
}

- (void)onGetSecretCriticalDataCommon {
    if (self.passwordAction == kCopy) {
        [self.passwordActivityIndicator stopAnimating];
        [self.passwordButton setTitle:@"Copy" forState:UIControlStateNormal];
    } else {
        [self.viewActivityIndicator stopAnimating];
        [self.viewButton setTitle:@"View" forState:UIControlStateNormal];
    }
}

- (void)onGetSecretCriticalData:(NSString*)password {
    [self onGetSecretCriticalDataCommon];

    self.password = password;

    if (self.passwordAction == kCopy) {
        [self copyPassword];
    } else {
        [self togglePasswordVisibility];
    }
}

- (void)onGetSecretCriticalDataFailed:(NSError*)error {
    [self onGetSecretCriticalDataCommon];

    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error Fetching Password"
                                                    message:error.localizedDescription
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
}

@end
