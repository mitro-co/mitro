//
//  FakeAPIServer.h
//  Mitro IOS
//
//  Created by Peter Jasko on 8/2/13.
//  Copyright (c) 2013 Peter Jasko. All rights reserved.
//


@interface FakeAPIServer : NSObject

-(NSString*)respondToCall:(NSString*)jsonRequest requestUrl:(NSString*) url;
@end
