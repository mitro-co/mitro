//
//  MitroApi.m
//  Mitro IOS
//
//  Created by Peter Jasko on 8/2/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//
#import "MitroApi.h"
#import "FakeAPIServer.h"
#import "NSObject+SBJSON.h"
#import "SBJsonParser.h"
#import "GlobalData.h"
@interface MitroApi ()

@end

@implementation MitroApi:NSObject



- (NSString*)getMyCryptoKey: (NSString*)username
{

  FakeAPIServer *server = [[FakeAPIServer alloc] init];
  NSMutableDictionary *Json = [[NSMutableDictionary alloc] init];
  NSMutableDictionary *req = [[NSMutableDictionary alloc] init];
  GlobalData *data = [[GlobalData alloc] init];
  NSString* requestURL =  [[data getBaseAPIURL] stringByAppendingString:@"GetMyPrivateKey"];  
  
  [Json setObject:username forKey:@"identity"];
  
  [req setObject:username forKey: @"userId"];
  NSString *escapeCharFormattedRequest  =[self addEscapeChars: [req JSONRepresentation]];
  
  [Json setObject: escapeCharFormattedRequest  forKey: @"request"];
  [Json setObject:@"iOSv.1Beta" forKey: @"clientIdentifier"];
  
  NSString *jsonString = [Json JSONRepresentation];
    
    NSString* response = [server respondToCall: jsonString requestUrl:requestURL];
  
  return response;
  
 
}

- (NSMutableArray*)getListMySecretsAndGroups: (NSString*)username
{
    FakeAPIServer *server = [[FakeAPIServer alloc] init];
    NSMutableDictionary *Json = [[NSMutableDictionary alloc] init];
    NSMutableDictionary *req = [[NSMutableDictionary alloc] init];
    GlobalData *data = [[GlobalData alloc] init];
    
    NSString* requestURL =  [[data getBaseAPIURL] stringByAppendingString: @"ListMySecretsAndGroupKeys"];
    //Add real signature later
    NSString* signature = @"Fakesignature";
    [Json setObject:username forKey:@"identity"];
    
    [req setObject:username forKey: @"myUserId"];
    NSString *escapeCharFormattedRequest  =[self addEscapeChars: [req JSONRepresentation]];
    
    [Json setObject: escapeCharFormattedRequest  forKey: @"request"];
    [Json setObject:signature forKey: @"signature"];
    [Json setObject:@"iOSv.1Beta" forKey: @"clientIdentifier"];
    
    NSLog([Json JSONRepresentation]);
    
    NSString *jsonString = [Json JSONRepresentation];
    
    NSString* response = [server respondToCall: jsonString requestUrl:requestURL];
    
    SBJsonParser *jsonParser = [[SBJsonParser alloc] init];
    NSError *error = nil;
 //   NSArray *jsonObjects = [jsonParser objectWithString:response];
    id jsonObject = [jsonParser objectWithString:response];
    
    if ([jsonObject isKindOfClass:[NSDictionary class]]){
        NSLog([jsonObject allKeys][0]);
    }
    else if ([jsonObject isKindOfClass:[NSArray class]]){
            // treat as an array or reassign to an array ivar.
    }
    NSMutableArray *secretIds = [[NSMutableArray alloc]init];
    int i = 0;
    for(i;i<[[[jsonObject valueForKey:@"secretToPath"] allKeys]count];i++){
    NSLog([[jsonObject valueForKey:@"encryptedClientData"] allKeys][i]);
        [secretIds addObject:[[jsonObject valueForKey:@"secretToPath"] allKeys][i]];
    }
    return secretIds;
  
  
}


- (NSString*)getCriticalData: (NSString*)username secretId: secretId groupId: groupId
{
    FakeAPIServer *server = [[FakeAPIServer alloc] init];
    NSMutableDictionary *Json = [[NSMutableDictionary alloc] init];
    NSMutableDictionary *req = [[NSMutableDictionary alloc] init];
    GlobalData *data = [[GlobalData alloc] init];
    
    NSString* requestURL =  [[data getBaseAPIURL] stringByAppendingString: @"GetSecret"];
    //Add real signature later
    NSString* signature = @"Fakesignature";
    [Json setObject:username forKey:@"identity"];
    
    [req setObject:username forKey: @"myUserId"];
    [req setObject:secretId forKey:@"secretId"];
    [req setObject:groupId forKey:@"groupId"];
    [req setObject:@"true" forKey:@"includeCriticalData"];
    NSString *escapeCharFormattedRequest  =[self addEscapeChars: [req JSONRepresentation]];
    
    [Json setObject: escapeCharFormattedRequest  forKey: @"request"];
    [Json setObject:signature forKey: @"signature"];
    [Json setObject:@"iOSv.1Beta" forKey: @"clientIdentifier"];
    
    NSLog([Json JSONRepresentation]);
    
    NSString *jsonString = [Json JSONRepresentation];
    
    NSString* response = [server respondToCall: jsonString requestUrl:requestURL];
    
    return response;
    
    
}

-(NSString*) addEscapeChars: (NSString*)originalString
{
  
  return [originalString stringByReplacingOccurrencesOfString:@"\"" withString:@"\""];
  
}


@end
