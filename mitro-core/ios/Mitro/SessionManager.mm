//
//  SessionManager.m
//  Mitro
//
//  Created by Adam Hilss on 10/9/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "SessionManager.h"

#include <string>

#include "base/bind.h"
#include "base/mac/mac_util.h"
#include "base/strings/sys_string_conversions.h"
#include "mitro_api/mitro_api.h"

#include "MitroApi.h"

@interface SessionManager()
- (void)onLogin:(NSString*)username
     loginToken:(NSString*)loginToken
        loginTokenSignature:(NSString*)loginTokenSignature
        encryptedPrivateKey:(NSString*)encryptedPrivateKey
          error:(mitro_api::MitroApiError*)login_error;
@end

namespace mitro_ios {

// Wrapper class needed to proxy the callbacks to the Mitro API.
class SessionManagerWrapper {
 public:
    SessionManagerWrapper(SessionManager* session_manager) : sessionManager_(session_manager) {
    }

    void OnLogin(const std::string& username,
                 const std::string& login_token,
                 const std::string& login_token_signature,
                 const std::string& encrypted_private_key,
                 mitro_api::MitroApiError* error) {
        [sessionManager_ onLogin:base::SysUTF8ToNSString(username)
                      loginToken:base::SysUTF8ToNSString(login_token)
             loginTokenSignature:base::SysUTF8ToNSString(login_token_signature)
             encryptedPrivateKey:base::SysUTF8ToNSString(encrypted_private_key)
                           error:error];
    }

 private:
    SessionManager* sessionManager_;
};
}  // namespace mitro_ios

@interface SessionManager() {
    mitro_api::MitroApiClient* api_client;
    mitro_ios::SessionManagerWrapper* wrapper_;
}
@end

@implementation SessionManager

static NSString* const kUsernameKey = @"username";
static NSString* const kLoginTokenKey = @"login_token";
static NSString* const kLoginTokenSignatureKey = @"login_token_signature";
static NSString* const kEncryptedPrivateKeyKey = @"encrypted_private_key";

- (id)init {
    self = [super init];
    if (self) {
        api_client = GetMitroApiClient();
        wrapper_ = new mitro_ios::SessionManagerWrapper(self);
        _shouldKeepLoggedIn = NO;
        _isLoggedIn = NO;
    }
    return self;
}

/* TODO: Implement this in a thread-safe way.
- (BOOL)isLoggedIn {
    return api_client->IsLoggedIn();
}
*/

- (NSString*)loginTokenKey:(NSString*)username {
    return [NSString stringWithFormat:@"%@:%@", kLoginTokenKey, username];
}

- (NSString*)loginTokenSignatureKey:(NSString*)username {
    return [NSString stringWithFormat:@"%@:%@", kLoginTokenSignatureKey, username];
}

- (NSString*)savedUsername {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    return [defaults stringForKey:kUsernameKey];
}

- (void)login:(NSString*)username withPassword:(NSString*)password withTwoFactorAuthCode:(NSString*)code {
    dispatch_async(GetDispatchQueue(), ^(void) {
        std::string username_string = base::SysNSStringToUTF8(username);
        std::string password_string = base::SysNSStringToUTF8(password);
        std::string two_factor_auth_code_string = base::SysNSStringToUTF8(code);

        std::string login_token;
        std::string login_token_signature;
        std::string encrypted_private_key;

        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        NSString* loginToken = [defaults stringForKey:[self loginTokenKey:username]];
        NSString* loginTokenSignature = [defaults stringForKey:[self loginTokenSignatureKey:username]];
        if (loginToken != nil && loginTokenSignature != nil) {
            login_token = base::SysNSStringToUTF8(loginToken);
            login_token_signature = base::SysNSStringToUTF8(loginTokenSignature);
        }

        if ([username isEqualToString:self.savedUsername]) {
            NSString* encryptedPrivateKey = [defaults stringForKey:kEncryptedPrivateKeyKey];
            if (encryptedPrivateKey != nil) {
                encrypted_private_key = base::SysNSStringToUTF8(encryptedPrivateKey);
            }
        }

        mitro_api::LoginCallback callback = base::Bind(&mitro_ios::SessionManagerWrapper::OnLogin,
                                                       base::Unretained(wrapper_));

        api_client->Login(username_string,
                          password_string,
                          two_factor_auth_code_string,
                          login_token,
                          login_token_signature,
                          encrypted_private_key,
                          self.shouldKeepLoggedIn,
                          callback);
        base::StartRunLoop();
    });
}

- (void)logout {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults removeObjectForKey:kEncryptedPrivateKeyKey];
    [defaults synchronize];

    dispatch_async(GetDispatchQueue(), ^(void) {
        api_client->Logout();
    });

    self.isLoggedIn = NO;

    if ([self.delegate respondsToSelector:@selector(onLogout)]) {
        [self.delegate onLogout];
    }
}

- (void)onLogin:(NSString*)username
                 loginToken:(NSString*)loginToken
        loginTokenSignature:(NSString*)loginTokenSignature
        encryptedPrivateKey:(NSString*)encryptedPrivateKey
          error:(mitro_api::MitroApiError*)login_error {
    NSLog(@"onLogin");
    base::StopRunLoop();
    mitro_api::MitroApiError* api_error = login_error == NULL ? NULL : new mitro_api::MitroApiError(*login_error);

    dispatch_async(dispatch_get_main_queue(), ^{
        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        [defaults setObject:username forKey:kUsernameKey];

        if (api_error == NULL) {
            [defaults setObject:loginToken forKey:[self loginTokenKey:username]];
            [defaults setObject:loginTokenSignature forKey:[self loginTokenSignatureKey:username]];

            if (self.shouldKeepLoggedIn) {
                [defaults setObject:encryptedPrivateKey forKey:kEncryptedPrivateKeyKey];
            } else {
                [defaults removeObjectForKey:kEncryptedPrivateKeyKey];
            }
        } else {
            // DoEmailVerificationException will be returned if, for some reason, the
            // login token was invalid.  Clear it or we will never be able to log in
            // again without reinstalling the app.
            if (api_error->GetExceptionType() == "DoEmailVerificationException") {
                [defaults removeObjectForKey:[self loginTokenKey:username]];
                [defaults removeObjectForKey:[self loginTokenSignatureKey:username]];
            }
            [defaults removeObjectForKey:kEncryptedPrivateKeyKey];
        }
        [defaults synchronize];

        if (api_error == NULL) {
            self.isLoggedIn = YES;
            if ([self.delegate respondsToSelector:@selector(onLogin)]) {
                [self.delegate onLogin];
            }
        } else {
            if ([self.delegate respondsToSelector:@selector(onLoginFailed:)]) {
                NSError* error = MitroApiErrorToNSError(*api_error);
                [self.delegate onLoginFailed:error];
            }
            delete api_error;
        }
    });
}

@end
