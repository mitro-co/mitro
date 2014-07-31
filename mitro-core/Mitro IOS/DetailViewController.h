//
//  DetailViewController.h
//  Mitro IOS
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface DetailViewController : UIViewController




-(IBAction)openUrl:(id)sender;
-(IBAction)copyUsernameButton:(id)sender;
-(IBAction)copyPasswordButton:(id)sender;
- (IBAction)changePasswordVisibleState:(id)sender;
@property (strong, nonatomic) id detailItem;
@property (weak, nonatomic) IBOutlet UILabel *detailDescriptionLabel;
@property (weak, nonatomic) IBOutlet UITextField *urlToGoTo;
@property (weak, nonatomic) IBOutlet UITextField *usernameField;
@property (weak, nonatomic) IBOutlet UITextField *passwordField;
@property (weak, nonatomic) IBOutlet UIButton *viewButton;


@end

