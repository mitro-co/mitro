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

// Encoding/decoding usernames and passwords in browser hashes
var userpass = {};
(function(){
  'use strict';

  var decodeQueryString;

  if (typeof(window) === 'undefined') {
    decodeQueryString = require('./querystring');
  } else {
    decodeQueryString = window.decodeQueryString;
  }

  userpass.encodeUsernamePassword = function(username, password) {
    var output = 'u=';
    output += encodeURIComponent(username);
    output += '&p=';
    output += encodeURIComponent(password);
    return output;
  };

  function makeNullResult() {
    return {
      username: null,
      password: null
    };
  }

  userpass.decodeUsernamePassword = function(queryString) {
    var result = makeNullResult();

    var decoded = decodeQueryString(queryString);
    if (decoded.u) {
      result.username = decoded.u;
      if (decoded.p) {
        result.password = decoded.p;
      }
      if (decoded.token) {
          result.token = decoded.token;
      } 
			if (decoded.token_signature) {
	        result.token_signature = decoded.token_signature;
	}

	    
    } else if (Object.keys(decoded).length === 0) {
      // no parameters decoded: treat the entire string as the user name
      // this is compatible with the original version of the code
      result.username = decodeURIComponent(queryString);
    } else {
      // query parameters decoded, but no username: return {null, null}
    }

    return result;
  };

  /** @param {Window=} windowObject */
  userpass.hashToUsernamePassword = function(windowObject) {
    if (!windowObject) windowObject = window;

    var hashString = windowObject.location.hash;
    if (hashString.length === 0) {
      return makeNullResult();
    } else {
      // window.location.hash always starts with '#'
      hashString = hashString.substring(1);
      return userpass.decodeUsernamePassword(hashString);
    }
  };

  // define node.js module for testing
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = userpass;
  }
})();
