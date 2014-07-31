//
//  SecretManager.m
//  Mitro
//
//  Created by Adam Hilss on 10/11/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "SecretManager.h"

#include "base/bind.h"
#include "mitro_api/mitro_api.h"
#include "mitro_api/mitro_api_types.h"
#include "base/mac/mac_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/sys_string_conversions.h"

#include "MitroApi.h"

@implementation Secret

- (NSString*) title {
    if (self.userTitle.length > 0) {
        return self.userTitle;
    } else if (self.defaultTitle.length > 0) {
        return self.defaultTitle;
    } else {
        return self.domain;
    }
}

@end

@interface SecretManager()

@property (strong, nonatomic) NSMutableDictionary* secretsMap;

- (void)onListSecrets:(const mitro_api::ListMySecretsAndGroupKeysResponse&)secrets_vector
                error:(mitro_api::MitroApiError*)error;
- (void)onGetSecret:(const mitro_api::Secret&)cpp_secret
              error:(mitro_api::MitroApiError*)get_secret_error
        groupIdPath:(const std::vector<int>&)group_id_path
             groups:(const std::map<std::string, mitro_api::GroupInfo>&)groups;
@end

namespace mitro_ios {

// Wrapper class needed to proxy the callbacks to the Mitro API.
class SecretManagerWrapper {
public:
    SecretManagerWrapper(SecretManager* secret_manager) : secretManager_(secret_manager) {
    }

    void OnListSecrets(const mitro_api::ListMySecretsAndGroupKeysResponse& secrets, mitro_api::MitroApiError* error) {
        [secretManager_ onListSecrets:secrets error:error];
    }

    void OnGetSecret(const std::vector<int>& group_id_path,
                     const std::map<std::string, mitro_api::GroupInfo>& groups,
                     const mitro_api::Secret& secret,
                     mitro_api::MitroApiError* error) {
        [secretManager_ onGetSecret:secret error:error groupIdPath:group_id_path groups:groups];
    }

private:
    SecretManager* secretManager_;
};
}  // namespace mitro_ios

@implementation SecretManager {
    mitro_api::MitroApiClient* api_client;
    mitro_ios::SecretManagerWrapper* wrapper_;
    scoped_ptr<mitro_api::ListMySecretsAndGroupKeysResponse> secrets_response_;
}

- (id)init {
    self = [super init];
    if (self) {
        api_client = GetMitroApiClient();
        wrapper_ = new mitro_ios::SecretManagerWrapper(self);
        _secretsMap = [[NSMutableDictionary alloc] init];
    }
    return self;
}

- (void)clear {
    secrets_response_.reset();
    [_secretsMap removeAllObjects];
}

- (Secret*)getSecret:(NSInteger)secretId {
    return [self.secretsMap objectForKey:[NSNumber numberWithInteger:secretId]];
}

+ (NSString*)domainForURL:(NSString*)url_string {
    NSURL* url = [NSURL URLWithString:url_string];
    if (url == nil || url.host == nil) {
        return @"";
    }

    if ([url.host hasPrefix:@"www."]) {
        return [url.host substringFromIndex:4];
    } else {
        return url.host;
    }
}

+ (Secret*)secretFromCppSecret:(const mitro_api::Secret*)cpp_secret {
    Secret* secret = [[Secret alloc] init];

    secret.id = cpp_secret->get_secretId();
    secret.userTitle = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_title());
    secret.defaultTitle = base::SysUTF8ToNSString(cpp_secret->get_title());
    secret.type = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_type());
    secret.url = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_loginUrl());
    secret.domain = [SecretManager domainForURL:secret.url];
    secret.username = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_username());
    secret.usernameField = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_usernameField());
    secret.passwordField = base::SysUTF8ToNSString(cpp_secret->get_clientData().get_passwordField());

    return secret;
}

- (void)reportSecretsDecryptionProgress:(NSInteger)completed total:(NSInteger)total {
    dispatch_async(dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onSecretsDecryptionProgress:total:)]) {
            [self.delegate onSecretsDecryptionProgress:completed total:total];
        }
    });
}

- (void)onListSecrets:(const mitro_api::ListMySecretsAndGroupKeysResponse&)response error:(mitro_api::MitroApiError*)list_secrets_error {
    NSLog(@"onListSecrets");

    base::StopRunLoop();

    mitro_api::ListMySecretsAndGroupKeysResponse* response_copy =
            new mitro_api::ListMySecretsAndGroupKeysResponse(response);
    mitro_api::MitroApiError* api_error = NULL;
    NSMutableDictionary* secretsMap = [[NSMutableDictionary alloc] init];
    NSMutableArray* secrets = [[NSMutableArray alloc] init];

    if (list_secrets_error == NULL) {
        const std::map<std::string, mitro_api::GroupInfo>& groups = response.get_groups();
        const std::map<std::string, mitro_api::Secret>& cpp_secrets = response.get_secretToPath();
        std::map<std::string, mitro_api::Secret>::const_iterator secret_iter;

        NSInteger total = cpp_secrets.size();
        NSInteger completed = 0;
        [self reportSecretsDecryptionProgress:completed total:total];

        for (secret_iter = cpp_secrets.begin(); secret_iter != cpp_secrets.end(); ++secret_iter, ++completed) {
            mitro_api::MitroApiError decryption_error;
            mitro_api::Secret cpp_secret(secret_iter->second);

            if (!api_client->DecryptSecret(&cpp_secret, groups, &decryption_error)) {
                api_error = new mitro_api::MitroApiError(decryption_error);
                goto send_list_secrets_response;
            }
            Secret* secret = [SecretManager secretFromCppSecret:&cpp_secret];
            [secrets addObject:secret];
            [secretsMap setObject:secret forKey:[NSNumber numberWithInteger:secret.id]];

            [self reportSecretsDecryptionProgress:completed total:total];

        }
    } else {
        api_error = new mitro_api::MitroApiError(*list_secrets_error);
    }

send_list_secrets_response:
    dispatch_async(dispatch_get_main_queue(), ^{
        self->secrets_response_.reset(response_copy);
        self.secretsMap = secretsMap;

        if (api_error == NULL) {
            if ([self.delegate respondsToSelector:@selector(onListSecrets:)]) {
                [self.delegate onListSecrets:secrets];
            }
        } else {
            if ([self.delegate respondsToSelector:@selector(onListSecretsFailed:)]) {
                NSError* error = MitroApiErrorToNSError(*api_error);
                [self.delegate onListSecretsFailed:error];
            }
            delete api_error;
        }
    });
}

- (void)listSecrets {
    dispatch_async(GetDispatchQueue(), ^(void) {
        mitro_api::GetSecretsListCallback callback =
            base::Bind(&mitro_ios::SecretManagerWrapper::OnListSecrets, base::Unretained(wrapper_));

        api_client->GetSecretsList(callback);
        base::StartRunLoop();
    });
}

- (void)onGetSecret:(const mitro_api::Secret&)cpp_secret
              error:(mitro_api::MitroApiError*)get_secret_error
        groupIdPath:(const std::vector<int>&)group_id_path
             groups:(const std::map<std::string, mitro_api::GroupInfo>&)groups {
    NSString* password = nil;
    mitro_api::MitroApiError* api_error = NULL;

    if (get_secret_error == NULL) {
        mitro_api::Secret decrypted_secret(cpp_secret);
        decrypted_secret.set_groupIdPath(group_id_path);
        mitro_api::MitroApiError decryption_error;
        if (!api_client->DecryptSecret(&decrypted_secret, groups, &decryption_error)) {
            api_error = new mitro_api::MitroApiError(decryption_error);
        } else {
            const mitro_api::SecretCriticalData& criticalData = decrypted_secret.get_criticalData();
            if (decrypted_secret.get_clientData().get_type() == "note") {
                password = base::SysUTF8ToNSString(criticalData.get_note());
            } else {
                password = base::SysUTF8ToNSString(criticalData.get_password());
            }
        }
    } else {
        api_error = new mitro_api::MitroApiError(*get_secret_error);
    }

    base::StopRunLoop();

    dispatch_async(dispatch_get_main_queue(), ^{
        if (api_error == NULL) {
            if ([self.delegate respondsToSelector:@selector(onGetSecretCriticalData:)]) {
                [self.delegate onGetSecretCriticalData:password];
            }
        } else {
            if ([self.delegate respondsToSelector:@selector(onGetSecretCriticalDataFailed:)]) {
                NSError* error = MitroApiErrorToNSError(*api_error);
                [self.delegate onGetSecretCriticalDataFailed:error];
            }
            delete api_error;
        }
    });
}

- (void)getSecretCriticalData:(NSInteger)secretId {
    std::string secret_id_string = base::IntToString(secretId);
    std::map<std::string, mitro_api::Secret>::const_iterator secret_iter =
        self->secrets_response_->get_secretToPath().find(secret_id_string);

    if (secret_iter == self->secrets_response_->get_secretToPath().end()) {
        // TODO: report error
        return;
    }

    const mitro_api::Secret& cpp_secret = secret_iter->second;
    if (!cpp_secret.has_groupIdPath() || cpp_secret.get_groupIdPath().empty()) {
        // TODO: report error
        return;
    }
    const std::vector<int> group_id_path = cpp_secret.get_groupIdPath();
    int group_id = group_id_path.back();
    const std::map<std::string, mitro_api::GroupInfo> groups = self->secrets_response_->get_groups();

    dispatch_async(GetDispatchQueue(), ^(void) {
        mitro_api::GetSecretCallback callback =
            base::Bind(&mitro_ios::SecretManagerWrapper::OnGetSecret, base::Unretained(wrapper_), group_id_path, groups);

        api_client->GetSecret(secretId, group_id, true, callback);
        base::StartRunLoop();
    });
}

@end
