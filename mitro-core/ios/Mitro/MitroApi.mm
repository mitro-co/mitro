//
//  MitroApi.cpp
//  Mitro
//
//  Created by Adam Hilss on 10/10/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#import "MitroApi.h"
#import "Mitro.h"

#include <string>

#include "base/strings/stringprintf.h"
#include "base/strings/sys_string_conversions.h"
#include "net/http_client.h"
#include "mitro_api/mitro_api.h"


static NSString* const kDeviceIdKey = @"device_id";

static net::HttpClient* http_client = NULL;
static mitro_api::MitroApiClient* mitro_api_client = NULL;

static dispatch_queue_t dispatch_queue = NULL;

dispatch_queue_t GetDispatchQueue() {
    if (dispatch_queue == NULL) {
        dispatch_queue = dispatch_queue_create("mitro_api", DISPATCH_QUEUE_SERIAL);
    }
    return dispatch_queue;
}

mitro_api::MitroApiClient* GetMitroApiClient() {
    if (mitro_api_client == NULL) {
        http_client = new net::HttpClient;
        mitro_api_client = new mitro_api::MitroApiClient(http_client);
        mitro_api_client->SetHost(base::SysNSStringToUTF8(kMitroHost));
        mitro_api_client->SetClientID(base::StringPrintf("Mitro iOS %s", [[Mitro version] UTF8String]));

        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        NSString *deviceId = [defaults objectForKey:kDeviceIdKey];

        if (deviceId == nil) {
            // GetDeviceId will generate a new device id if device id has not been set
            std::string device_id = mitro_api_client->GetDeviceID();
            [defaults setObject:base::SysUTF8ToNSString(device_id) forKey:kDeviceIdKey];
            [defaults synchronize];
        } else {
            mitro_api_client->SetDeviceID(base::SysNSStringToUTF8(deviceId));
        }
        deviceId = [defaults objectForKey:@"device_id"];
    }

    return mitro_api_client;
}

NSError* MitroApiErrorToNSError(const mitro_api::MitroApiError& api_error) {
    NSDictionary* userInfo = [NSDictionary dictionaryWithObjectsAndKeys:
            base::SysUTF8ToNSString(api_error.GetMessage()), NSLocalizedDescriptionKey,
            base::SysUTF8ToNSString(api_error.GetExceptionType()), @"ExceptionType", nil];
    return [NSError errorWithDomain:kMitroErrorDomain code:0 userInfo:userInfo];
}