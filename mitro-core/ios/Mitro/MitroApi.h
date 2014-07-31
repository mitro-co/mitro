//
//  MitroApi.h
//  Mitro
//
//  Created by Adam Hilss on 10/10/13.
//  Copyright (c) 2013 Lectorius, Inc. All rights reserved.
//

#ifndef __Mitro__MitroApi__
#define __Mitro__MitroApi__

#include "mitro_api/mitro_api.h"

dispatch_queue_t GetDispatchQueue();
mitro_api::MitroApiClient* GetMitroApiClient();

NSError* MitroApiErrorToNSError(const mitro_api::MitroApiError& api_error);

#endif /* defined(__Mitro__MitroApi__) */
