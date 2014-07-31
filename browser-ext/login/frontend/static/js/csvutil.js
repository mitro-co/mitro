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

var csvutil = {};
(function() {
  'use strict';

  /** Converts value to a string, and escapes it as a single CSV field. If the field contains
  a double quote, comma, or new line characters, the field should be surrounded by double
  quotes (RFC4180 2.6). Double quotes in a quoted field must escaped by embedded them twice.

  See:  http://tools.ietf.org/html/rfc4180#section-2
  @param {*} value
  @return {string}
  */
  var csvEscape = function(value) {
    var s = value.toString();
    if (s.match(/[\r\n,"]/)) {
      // value contains a "special" char: needs to be double quoted
      // escape all double quotes and surround the whole thing with double quotes
      s = s.replace(/"/g, '""');
      s = '"' + s + '"';
    }

    return s;
  };

  /** Converts rows to CSV, with Excel-compatible quoting.
  @param {!Array.<!Array>} rows 2D-array of data that will be stringified
  @return {string}
  */
  csvutil.toCSV = function(rows) {
    var output = '';
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      for (var j = 0; j < row.length; j++) {
        if (j !== 0) {
          output += ',';
        }
        output += csvEscape(row[j]);
      }
      output += '\n';
    }
    return output;
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = csvutil;
  }
})();
