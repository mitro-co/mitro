//
//  MasterViewController.h
//  Mitro
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import <UIKit/UIKit.h>

#import "ProgressOverlayView.h"
#import "SecretManager.h"
#import "SessionManager.h"

@interface SecretsTableViewController : UITableViewController<UISearchBarDelegate, UISearchDisplayDelegate, SecretManagerDelegate, SessionManagerDelegate>

@property (strong, nonatomic) IBOutlet UITableView *secretsTableView;
@property (strong, nonatomic) ProgressOverlayView *progressView;
@property (weak, nonatomic) IBOutlet UISearchBar *searchBar;

@end
