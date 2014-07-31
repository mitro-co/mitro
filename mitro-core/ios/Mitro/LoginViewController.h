//
//  LoginViewController.h
//  Mitro
//
//  Created by Adam Hilss on 9/15/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <UIKit/UIKit.h>

#import "MitroButton.h"
#import "MitroTextField.h"
#import "SessionManager.h"

@interface LoginViewController : UIViewController<UITextFieldDelegate, SessionManagerDelegate>

@property (weak, nonatomic) IBOutlet UIView *headerView;
@property (weak, nonatomic) IBOutlet UIImageView *logoImage;
@property (weak, nonatomic) IBOutlet MitroTextField *usernameText;
@property (weak, nonatomic) IBOutlet MitroTextField *passwordText;
@property (weak, nonatomic) IBOutlet UILabel *verificationCodeLabel;
@property (weak, nonatomic) IBOutlet MitroTextField *verificationCodeText;
@property (weak, nonatomic) IBOutlet UISwitch *savePasswordSwitch;
@property (weak, nonatomic) IBOutlet MitroButton *signInButton;
@property (weak, nonatomic) IBOutlet UIActivityIndicatorView *loginActivityIndicator;

@end
