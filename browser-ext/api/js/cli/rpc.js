/*
 * *****************************************************************************
 * Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     You can contact the authors at inbound@mitro.co.
 * *****************************************************************************
 */

/** @suppress{duplicate} */
var mitro = mitro || {};
(function() {
  mitro.rpc = {};
  var PLATFORM = 'unknown';
  if(typeof(window) !== 'undefined') {
    try {
      if (CHROME) {
        PLATFORM = 'CHROME';
      } else if (SAFARI) {
        PLATFORM = 'SAFARI';
      } else if (FIREFOX) {
        PLATFORM = 'FIREFOX';
      } else if (WEBPAGE) {
        PLATFORM = 'WEBPAGE';
      }
    } catch (e) {
    }

    //////////////////////////////////////////////////////////////////////////
    //   Code below here is only for the browser
    //////////////////////////////////////////////////////////////////////////
    mitro.rpc._PostToMitro = function(outdict, args, path, onSuccess, onError) {
      var url = 'https://' + args.server_host + ':' + args.server_port + path;
      outdict.clientIdentifier = helper.getClientIdentifier();
      outdict.platform = PLATFORM;
      
      var requestString = JSON.stringify(outdict);
      helper.ajax({
        type: 'POST',
        url: url,
        data: requestString,
        dataType: 'json',
        complete: function (response) {
          try {
            var rval = JSON.parse(response.text);
            if(response.status === 200){
              onSuccess(rval);
            } else {
              onError(rval);
            }
          } catch(e) {
            onError({
              status : response.status,
              userVisibleError: 'Unknown error',
              exceptionType: 'UnknownException'
            });
          }
        }
      });
    };
  }
  ////////////////////////////////////////////////////////////////////////////
  //   Code below here is only for the node.js implementation
  ////////////////////////////////////////////////////////////////////////////
  else if(typeof(module) !== 'undefined' && module.exports) {
    var https = require('https');
    module.exports = mitro.rpc;

    var _certificateValidation = true;

    // Setting this to false allows connecting to a self-signed SSL certificate
    // Should only be used for testing!
    mitro.rpc.setCertificateValidationForTest = function(value) {
      _certificateValidation = Boolean(value);
    };

    mitro.rpc._PostToMitro = function(outdict, args, path, onSuccess, onError) {
      onSuccess = onSuccess || mitro.rpc.DefaultResponseHandler;
      onError = onError || mitro.rpc.DefaultErrorHandler;

      var requestString = JSON.stringify(outdict);
      var headers = {
        'Content-Type': 'application/json',
        'Content-Length': requestString.length
      };

      var options = {
        host: args.server_host,
        port: args.server_port,
        path: path,
        method: 'POST',
        headers: headers,

        // validate TLS certificates: (default; specify it just in case)
        rejectUnauthorized: true
      };

      if (!_certificateValidation) {
        // disable certificate validation for testing with self-signed certificate
        options.agent = false;
        options.rejectUnauthorized = false;
      }

      var req = https.request(options, function(res) {
        res.setEncoding('utf-8');

        var responseString = '';

        res.on('data', function(data) {
          responseString += data;
        });

        res.on('end', function() {
          console.log("status: " + res.statusCode);
          if (200 === res.statusCode) {
            onSuccess(JSON.parse(responseString));
          } else {
            var dict = {'status' : res.statusCode};
            try {
              dict = JSON.parse(responseString);
            } finally {
              console.log('rpc error: ', responseString);
              onError(dict);
            }
          }
        });
      });

      req.on('error', function(e) {
        console.log('rpc.js: RPC error');
        onError({'status' : -1});
      });
      req.write(requestString);
      req.end();
    };

    mitro.rpc.DefaultResponseHandler = function(data) {
      console.log(JSON.stringify(data, null, 4));
    };

    mitro.rpc.DefaultErrorHandler = function(data) {
      console.log(JSON.stringify(data, null, 4));
      throw new Error('RPC error: ' + data);
    };
  }

})();
