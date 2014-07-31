//
//  NoteDetailViewController.h
//  Mitro
//
//  Created by Adam Hilss on 10/14/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "SecretDetailViewController.h"

#import "MitroTextView.h"

@interface NoteDetailViewController : SecretDetailViewController

@property (weak, nonatomic) IBOutlet UILabel *titleLabel;
@property (weak, nonatomic) IBOutlet UILabel *linkLabel;
@property (weak, nonatomic) IBOutlet MitroTextView *noteText;
@property (weak, nonatomic) IBOutlet UIActivityIndicatorView *noteActivityIndicator;

- (IBAction)onCopyButtonTouched:(id)sender;

@end
