//
//  Mitro.m
//  Mitro
//
//  Created by Adam Hilss on 10/10/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "Mitro.h"
#import "SecretManager.h"
#import "SessionManager.h"

//NSString* const kMitroHost = @"localhost:8443";
NSString* const kMitroHost = @"www.mitro.co";

NSString* const kMitroErrorDomain = @"MitroErrorDomain";

static SessionManager* gSessionManager = nil;
static SecretManager* gSecretManager = nil;

@implementation Mitro

+ (SessionManager*)sessionManager {
    @synchronized(self) {
        if (gSessionManager == nil) {
            gSessionManager = [[SessionManager alloc] init];
        }
    }
    return gSessionManager;
}

+ (SecretManager*)secretManager {
    @synchronized(self) {
        if (gSecretManager == nil) {
            gSecretManager = [[SecretManager alloc] init];
        }
    }
    return gSecretManager;
}

+ (void)releaseSecretManager {
    @synchronized(self) {
        gSecretManager = nil;
    }
}

+ (NSString *)version {
    return [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleShortVersionString"];
}

@end
