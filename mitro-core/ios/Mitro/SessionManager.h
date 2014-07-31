//
//  SessionManager.h
//  Mitro
//
//  Created by Adam Hilss on 10/9/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <Foundation/Foundation.h>

@protocol SessionManagerDelegate <NSObject>
@optional
- (void)onLogin;
- (void)onLoginFailed:(NSError*)error;
- (void)onLogout;
@end

@interface SessionManager : NSObject

@property (weak, nonatomic) id <SessionManagerDelegate> delegate;
@property (nonatomic) BOOL shouldKeepLoggedIn;
@property (nonatomic) BOOL isLoggedIn;

- (void)login:(NSString*)username withPassword:(NSString*)password withTwoFactorAuthCode:(NSString*)code;
- (void)logout;
- (NSString*)savedUsername;

@end
