//
//  PasswordDetailViewController.h
//  Mitro
//
//  Created by Adam Hilss on 10/14/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "SecretDetailViewController.h"

#import "MitroButton.h"

typedef enum {
    kCopy,
    kView
} PasswordAction ;

@interface PasswordDetailViewController : SecretDetailViewController

@property (weak, nonatomic) IBOutlet UILabel *titleLabel;
@property (weak, nonatomic) IBOutlet UILabel *linkLabel;
@property (weak, nonatomic) IBOutlet UITextField *usernameText;
@property (weak, nonatomic) IBOutlet UITextField *passwordText;
@property (weak, nonatomic) IBOutlet MitroButton *viewButton;
@property (weak, nonatomic) IBOutlet UIActivityIndicatorView *viewActivityIndicator;
@property (weak, nonatomic) IBOutlet MitroButton *passwordButton;
@property (weak, nonatomic) IBOutlet UIActivityIndicatorView *passwordActivityIndicator;

@property (strong, nonatomic) NSString* password;
@property (nonatomic) PasswordAction passwordAction;

- (IBAction)onCopyUsernameButtonTouched:(id)sender;
- (IBAction)onCopyPasswordButtonTouched:(id)sender;
- (IBAction)onViewTouched:(id)sender;

@end
