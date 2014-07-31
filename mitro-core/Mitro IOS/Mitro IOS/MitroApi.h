//
//  MitroApi.h
//  Mitro IOS
//
//  Created by Peter Jasko on 8/2/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//



@interface MitroApi : NSObject
-(NSString*)getMyCryptoKey:(NSString*)username;
-(NSMutableArray*)getListMySecretsAndGroups: (NSString*)username;
-(NSString*)getCriticalData: (NSString*)username secretId:secretId groupId:groupId;
-(NSString*) addEscapeChars;
@end
