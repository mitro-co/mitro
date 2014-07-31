//
//  SecretManager.h
//  Mitro
//
//  Created by Adam Hilss on 10/11/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface Secret : NSObject

@property (nonatomic) NSInteger id;
@property (strong, nonatomic) NSString* userTitle;
@property (strong, nonatomic) NSString* defaultTitle;
@property (nonatomic) NSString* displayTitle;
@property (readonly, nonatomic) NSString* title;
@property (strong, nonatomic) NSString* type;
@property (strong, nonatomic) NSString* url;
@property (strong, nonatomic) NSString* domain;
@property (strong, nonatomic) NSString* username;
@property (strong, nonatomic) NSString* usernameField;
@property (strong, nonatomic) NSString* passwordField;

@end

@protocol SecretManagerDelegate <NSObject>
@optional
- (void)onListSecrets:(NSArray*)secrets;
- (void)onListSecretsFailed:(NSError*)error;
- (void)onSecretsDecryptionProgress:(NSInteger)completed total:(NSInteger)total;
- (void)onGetSecretCriticalData:(NSString*)criticalData;
- (void)onGetSecretCriticalDataFailed:(NSError*)error;
@end

@interface SecretManager : NSObject

@property (weak, nonatomic) id <SecretManagerDelegate> delegate;

- (Secret*)getSecret:(NSInteger)secretId;
- (void)listSecrets;
- (void)getSecretCriticalData:(NSInteger)secretId;

@end
