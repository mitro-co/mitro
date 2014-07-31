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

var decodeQueryString;
(function(){
  'use strict';

  // It seems like nearly all Javascript query string parsers are wrong. This probably is too, but
  // it seems to work for this test case. See: http://unixpapa.com/js/querystring.html
  decodeQueryString = function (queryString) {
    var output = {};

    // stuff separated by =, separated by &
    var re = /([^=&]+)(=([^&]*))?/g;
    var match;
    while (match = re.exec(queryString)) {
      var key = decodeURIComponent(match[1].replace(/\+/g, ' '));
      // skip parsing keys without values: 'key&k1=v1' -> {"k1": "v1"}
      if (!match[2]) {
        continue;
      }
      // if missing value = empty string: 'k1=&k2=' -> {"k1": "", "k2": ""}
      var value = "";
      if (match[3]) {
        value = decodeURIComponent(match[3].replace(/\+/g, ' '));
      }

      output[key] = value;
    }

    return output;
  };

  // define node.js module for testing
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = decodeQueryString;
  }
})();
