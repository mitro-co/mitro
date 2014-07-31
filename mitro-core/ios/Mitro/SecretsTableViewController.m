//
//  MasterViewController.m
//  Mitro
//
//  Created by Peter Jasko on 8/1/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//

#import "SecretsTableViewController.h"

#import "Flurry.h"
#import "Mitro.h"
#import "MitroColor.h"
#import "ProgressOverlayView.h"
#import "SecretDetailViewController.h"
#import "SecretsTableViewCell.h"

@interface SecretsTableViewController ()

@property (nonatomic, strong) NSArray *secrets;
@property (nonatomic, strong) NSArray *filteredSecrets;
@property (nonatomic) NSInteger selectedSecretId;

@end

@implementation SecretsTableViewController

- (void)awakeFromNib {
    [super awakeFromNib];
}

- (void)viewDidLoad {
    [super viewDidLoad];

    [Flurry logAllPageViews:self.navigationController];

    self.navigationController.navigationBar.tintColor = [UIColor tintColor];
    self.navigationController.navigationBar.barTintColor = [UIColor whiteColor];
    UIImageView *imageView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"title_logo"]];
    self.navigationItem.titleView = imageView;

    UIBarButtonItem *logoutButton = [[UIBarButtonItem alloc] initWithTitle:@"Log out" style:UIBarButtonItemStylePlain target:self action:@selector(onLogoutButton)];
    self.navigationItem.leftBarButtonItem = logoutButton;

    self.secretsTableView.tableHeaderView = self.searchBar;
    [self.searchBar setSearchFieldBackgroundImage:[UIImage imageNamed:@"search_background" ] forState:UIControlStateNormal];
    self.searchDisplayController.searchResultsTableView.backgroundColor = [UIColor backgroundColor];

    self.progressView = [[ProgressOverlayView alloc] initWithFrame:self.view.bounds];
}

- (void)refreshSecrets {
    [[Mitro secretManager] listSecrets];

    self.progressView.titleLabel.text = @"Fetching Secrets";
    [self.progressView.activityIndicator startAnimating];
    self.progressView.detailLabel.hidden = YES;
    self.progressView.progressBar.hidden = YES;
    [self.view addSubview:self.progressView];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];

    if (![Mitro sessionManager].isLoggedIn) {
        [self clearSecrets];
        [self openLoginView];
    } else {
        [[Mitro secretManager] setDelegate:self];
        [[Mitro sessionManager] setDelegate:self];
        if (self.secrets == nil) {
            [self refreshSecrets];
        }
    }
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [[Mitro secretManager] setDelegate:nil];
    [[Mitro sessionManager] setDelegate:nil];
}

- (void)clearSecrets {
    if (self.secrets) {
        self.secrets = nil;
        self.filteredSecrets = nil;
        [self.secretsTableView reloadData];
        [self.searchDisplayController.searchResultsTableView reloadData];
    }
}

- (void)openLoginView {
    [self performSegueWithIdentifier:@"Login" sender:self];
}

- (void)onLogoutButton {
    [[Mitro sessionManager] logout];
}

- (void)onLogout {
    // TODO: Wait for secret manager background tasks to complete.
    // Background tasks could be running that could potentially leak information after logout.
    // We prevent data from leaking to the front end by detaching from the secret manager.
    // However, the data will still be in memory, so we should wait for the tasks to complete
    // to be more secure (or disable logout while tasks are in progress).
    [[Mitro secretManager] setDelegate:nil];
    [Mitro releaseSecretManager];
    [self clearSecrets];
    [self openLoginView];
}

- (void)onSecretsDecryptionProgress:(NSInteger)completed total:(NSInteger)total {
    if (self.progressView.progressBar.isHidden) {
        self.progressView.titleLabel.text = @"Decrypting Secrets";
        [self.progressView.activityIndicator stopAnimating];
        self.progressView.detailLabel.hidden = NO;
        self.progressView.progressBar.hidden = NO;
    }
    self.progressView.detailLabel.text = [NSString stringWithFormat:@"%d of %d", completed, total];
    self.progressView.progressBar.progress = (double) completed / (double) total;
}

- (void)setSecretDisplayTitles {
    NSMutableDictionary* titles = [[NSMutableDictionary alloc] init];

    for (Secret* secret in self.secrets) {
        if ([titles objectForKey:secret.title] == nil) {
            [titles setObject:[NSNumber numberWithInt:1] forKey:secret.title];
        } else {
            [titles setObject:[NSNumber numberWithInt:2] forKey:secret.title];
        }
    }

    // Disambiguate duplicate titles with username
    for (Secret* secret in self.secrets) {
        if ([secret.username length] > 0 &&
            ((NSNumber*)[titles objectForKey:secret.title]).intValue > 1) {
            secret.displayTitle = [NSString stringWithFormat:@"%@ (%@)", secret.title, secret.username];
        } else {
            secret.displayTitle = secret.title;
        }
    }
}

- (void)sortSecrets {
    self.secrets = [self.secrets sortedArrayUsingComparator:^(id a, id b) {
        Secret* secret1 = (Secret*)a;
        Secret* secret2 = (Secret*)b;
        return [secret1.displayTitle localizedCaseInsensitiveCompare:secret2.displayTitle];
    }];
}

- (void)onListSecrets:(NSArray*)newSecretsList {
    [self.progressView removeFromSuperview];

    self.secrets = newSecretsList;
    [self setSecretDisplayTitles];
    [self sortSecrets];

    [self.secretsTableView reloadData];
    [self.searchDisplayController.searchResultsTableView reloadData];
}

- (void)onListSecretsFailed:(NSError*)error {
    [self.progressView removeFromSuperview];

    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error Refreshing Secrets"
                                                    message:error.localizedDescription
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
}

#pragma mark - Table View

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    return 1;
}

- (NSArray *)getDataForTableView:(UITableView *)tableView {
    if (tableView == self.searchDisplayController.searchResultsTableView) {
        return self.filteredSecrets;
    } else {
        return self.secrets;
    }
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return [[self getDataForTableView:tableView] count];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    static NSString *kCellIdentifier = @"SecretsTableViewCell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:kCellIdentifier];
    if(cell == nil) {
        cell = [[SecretsTableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:kCellIdentifier];
    }

    Secret* secret = [[self getDataForTableView:tableView] objectAtIndex:indexPath.row];
    cell.textLabel.text = secret.displayTitle;
    cell.detailTextLabel.text = secret.domain;
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    Secret* secret = [[self getDataForTableView:tableView] objectAtIndex:indexPath.row];
    self.selectedSecretId = secret.id;

    if ([secret.type isEqualToString:@"note"]) {
        [self performSegueWithIdentifier:@"ViewNoteDetails" sender:self];
    } else {
        [self performSegueWithIdentifier:@"ViewPasswordDetails" sender:self];
    }
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
}

-(void)filterContentForSearchText:(NSString*)searchText scope:(NSString*)scope {
    NSPredicate *predicate = [NSPredicate predicateWithFormat:@"(SELF.displayTitle contains[c] %@) OR (SELF.domain contains[c] %@) OR (SELF.username contains[c] %@)",
                              searchText, searchText, searchText];
    self.filteredSecrets = [NSMutableArray arrayWithArray:[self.secrets filteredArrayUsingPredicate:predicate]];
}

-(BOOL)searchDisplayController:(UISearchDisplayController *)controller shouldReloadTableForSearchString:(NSString *)searchString {
    [self filterContentForSearchText:searchString scope:
     [[self.searchDisplayController.searchBar scopeButtonTitles] objectAtIndex:[self.searchDisplayController.searchBar selectedScopeButtonIndex]]];
    return YES;
}

-(BOOL)searchDisplayController:(UISearchDisplayController *)controller shouldReloadTableForSearchScope:(NSInteger)searchOption {
    [self filterContentForSearchText:self.searchDisplayController.searchBar.text scope:
     [[self.searchDisplayController.searchBar scopeButtonTitles] objectAtIndex:searchOption]];
    return YES;
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    if ([[segue identifier] isEqualToString:@"ViewPasswordDetails"] ||
        [[segue identifier] isEqualToString:@"ViewNoteDetails"]) {
        [[segue destinationViewController] setSecretId:self.selectedSecretId];
    }
}

@end
