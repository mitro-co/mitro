//
//  Mitro.h
//  Mitro
//
//  Created by Adam Hilss on 10/10/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "SecretManager.h"
#import "SessionManager.h"

extern NSString* const kMitroHost;

// NSError domain for Mitro errors.
extern NSString* const kMitroErrorDomain;

@interface Mitro : NSObject

+ (SessionManager*)sessionManager;
+ (SecretManager*)secretManager;
+ (void)releaseSecretManager;

+ (NSString*)version;

@end
