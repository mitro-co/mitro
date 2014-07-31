//
//  DetailViewController.m
//  Mitro IOS
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import "DetailViewController.h"

@interface DetailViewController ()
- (void)configureView;
@end

@implementation DetailViewController
@synthesize urlToGoTo;
@synthesize usernameField;
@synthesize passwordField;
@synthesize viewButton;
#pragma mark - Managing the detail item

- (void)setDetailItem:(id)newDetailItem
{
    if (_detailItem != newDetailItem) {
        _detailItem = newDetailItem;
        
        // Update the view.
        [self configureView];
    }
}

- (void)configureView
{
    // Update the user interface for the detail item.
    
    if (self.detailItem) {
        self.detailDescriptionLabel.text = [self.detailItem description];
        self.title = self.detailItem;
    }
    
    
    
    
}

- (void)viewDidLoad
{
    [super viewDidLoad];
	// Do any additional setup after loading the view, typically from a nib.
    [self configureView];
    
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

-(IBAction)openUrl:(id)sender;
{
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:self.urlToGoTo.text]];
}

- (IBAction)copyUsernameButton:(id)sender {
    
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = self.usernameField.text;
}

- (IBAction)copyPasswordButton:(id)sender
{
    if([self.passwordField.text isEqualToString:@"······"]){
        [self.passwordField setText:@"YAY"];
        [self.viewButton setTitle:@"Hide" forState:UIControlStateNormal];
        UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
        pasteboard.string = self.passwordField.text;
    }
    else{
        UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
        pasteboard.string = self.passwordField.text;
    }
    
}
- (IBAction)changePasswordVisibleState:(id)sender
{
    if([self.passwordField.text isEqualToString:@"······"]){
        [self.passwordField setText:@"YAY"];
        [self.viewButton setTitle:@"Hide" forState:UIControlStateNormal];
    }
    else{
        [self.passwordField setText:@"······"];
        [self.viewButton setTitle:@"View" forState:UIControlStateNormal];
    }
    
}
@end
