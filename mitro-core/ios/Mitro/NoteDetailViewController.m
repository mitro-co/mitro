//
//  SecureNoteDetailViewController.m
//  Mitro
//
//  Created by Adam Hilss on 10/14/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "NoteDetailViewController.h"

#import "Mitro.h"

@interface NoteDetailViewController ()

@end

@implementation NoteDetailViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [[Mitro secretManager] getSecretCriticalData:self.secretId];
    [self.noteActivityIndicator startAnimating];
}

- (void)updateView {
    Secret* secret = [[Mitro secretManager] getSecret:self.secretId];
    if (secret == nil) {
        return;
    }
    
    self.titleLabel.text = secret.displayTitle;
    self.linkLabel.text = secret.domain;
}

- (IBAction)onCopyButtonTouched:(id)sender {
    [self copyText:self.noteText.text];
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

- (void)onGetSecretCriticalData:(NSString*)password {
    [self.noteActivityIndicator stopAnimating];
    self.noteText.text = password;
}

- (void)onGetSecretCriticalDataFailed:(NSError*)error {
    [self.noteActivityIndicator stopAnimating];

    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error Fetching Note"
                                                    message:error.localizedDescription
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
}

@end
