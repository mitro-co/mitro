//
//  LoginViewController.m
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <QuartzCore/QuartzCore.h>

#import "LoginViewController.h"
#import "Mitro.h"
#import "MitroColor.h"

@interface LoginViewController ()

@end

@implementation LoginViewController

static const NSUInteger kHeaderBorderColor = 0xcccccc;

- (id)initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil {
    self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
    if (self) {
    }
    return self;
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    CALayer *bottomBorder = [CALayer layer];
    
    bottomBorder.frame = CGRectMake(0.0f, 83.0f, 320.0, 1.0f);
    bottomBorder.backgroundColor = [UIColor colorWithHex:kHeaderBorderColor].CGColor;
    
    [self.headerView.layer addSublayer:bottomBorder];

    [self.navigationController setNavigationBarHidden:YES animated:animated];

    [[Mitro sessionManager] setDelegate:self];
    NSString* username = [[Mitro sessionManager] savedUsername];

    if (username != nil) {
        self.usernameText.text = username;
    }

    self.usernameText.delegate = self;
    self.passwordText.delegate = self;
    self.verificationCodeText.delegate = self;
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [self.navigationController setNavigationBarHidden:NO animated:animated];

    [[Mitro sessionManager] setDelegate:nil];
}

- (void)tryLogin {
    if (self.loginActivityIndicator.isAnimating) {
        return;
    }
    [self.loginActivityIndicator startAnimating];

    NSString* username = [self.usernameText.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    NSString* password = self.passwordText.text;
    NSString* code = self.verificationCodeText.text;
    [[Mitro sessionManager] login:username withPassword:password withTwoFactorAuthCode:code];
}

- (IBAction)onSignInTouched:(id)sender {
    [self.view endEditing:YES];
    [self tryLogin];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    NSInteger nextTag = textField.tag + 1;
    UIResponder* nextResponder = [textField.superview viewWithTag:nextTag];
    if (nextResponder) {
        [nextResponder becomeFirstResponder];
    } else {
        [textField resignFirstResponder];
        [self tryLogin];
    }

    return NO;
}

- (void)onLogin {
    [self.loginActivityIndicator stopAnimating];
    [self.navigationController popViewControllerAnimated:YES];
}

- (void)onLoginFailed:(NSError*)error {
    [self.loginActivityIndicator stopAnimating];

    NSString* exceptionType = [error.userInfo objectForKey:@"ExceptionType"];

    if ([exceptionType isEqualToString:@"DoTwoFactorAuthException"]) {
        if (self.verificationCodeText.hidden) {
            self.usernameText.hidden = YES;
            self.passwordText.hidden = YES;
            self.verificationCodeLabel.hidden = NO;
            self.verificationCodeText.hidden = NO;
            [self.signInButton setTitle:@"VERIFY" forState:UIControlStateNormal];
            [self.verificationCodeText becomeFirstResponder];
        } else {
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Invalid Code"
                                                            message:@""
                                                           delegate:nil
                                                  cancelButtonTitle:@"OK"
                                                  otherButtonTitles:nil];
            [alert show];
            [self.verificationCodeText becomeFirstResponder];
            [self.verificationCodeText selectAll:nil];
        }
    } else {
        NSString* alertTitle = nil;
        NSString* alertMessage = nil;

        if ([exceptionType isEqualToString:@"DoEmailVerificationException"]) {
            alertTitle = @"Verification Required";
            alertMessage = [NSString stringWithFormat:@"For security reasons, an email has been sent to %@ to verify your new device. "
                            "Click on the link in your email and try again.", self.usernameText.text];
        } else {
            alertTitle = @"Login Error";
            alertMessage = error.localizedDescription;

            if ([error.localizedDescription isEqualToString:@"Invalid password"] && self.passwordText.hidden) {
                self.usernameText.hidden = NO;
                self.passwordText.hidden = NO;
                self.verificationCodeLabel.hidden = YES;
                self.verificationCodeText.hidden = YES;
                [self.signInButton setTitle:@"SIGN IN" forState:UIControlStateNormal];
            }
        }
        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:alertTitle
                                                        message:alertMessage
                                                       delegate:nil
                                              cancelButtonTitle:@"OK"
                                              otherButtonTitles:nil];
        [alert show];
        [self.passwordText becomeFirstResponder];
        [self.passwordText selectAll:nil];
    }
}

@end
