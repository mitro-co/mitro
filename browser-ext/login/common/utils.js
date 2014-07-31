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

// A transparent 1px image that can be used as a placeholder.
var EMPTY_IMAGE = 'data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==';

var KeyCodes = {
    RETURN: 13,
    LEFT: 37,
    UP: 38,
    RIGHT: 39,
    DOWN: 40,
    KEY_0: 48,
    KEY_Z: 90
};

var assert;
var assertIsNumber;
var lowercaseCompare;
var dictValues;
var createMapFromArrayOnAttribute;
var arrayToSet;
(function () {
    'use strict';

    /**
    @param {boolean} condition
    @param {string=} message
    */
    assert = function(condition, message) {
        if (!condition) {
            if (typeof message === 'undefined') {
                message = 'Assertion failed';
            }
            throw message;
        }
    };

    /**
    @param {number} number
    */
    assertIsNumber = function(number) {
      if (typeof number !== 'number') {
        throw new Error('argument must be a number');
      }
    };

    lowercaseCompare = function (a, b) {
        return a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase());
    };

    /**
    @param {!Object.<string, T>} dict
    @return {!Array.<T>}
    @template T
    */
    dictValues = function (dict) {
      var values = [];
      for (var key in dict) {
        values.push(dict[key]);
      }
      return values;
    };

    // Creates a map from an array of objects using the object attribute
    // specified by attr as the map key.
    //
    // The key attribute must be unique and non-null for all objects.
    createMapFromArrayOnAttribute = function (array, attr) {
        var map = {};
        for (var i = 0; i < array.length; ++i) {
            var object = array[i];
            if (!(attr in object)) {
                throw 'Missing key attribute: ' + attr;
            } else if (object[attr] === null) {
                throw 'Null key attribute: ' + attr;
            } else if (object[attr] in map) {
                throw 'Duplicate key attribute: ' + attr; 
            }
            map[object[attr]] = object;
        }
        return map;
    };

    // Converts an array into a set, implemented using a object where the keys
    // are the values of the array.
    arrayToSet = function (array) {
        var set = {};
        for (var i = 0; i < array.length; i++) {
          set[array[i]] = true;
        }
        return set;
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            assert: assert,
            assertIsNumber: assertIsNumber,
            lowercaseCompare: lowercaseCompare,
            dictValues: dictValues,
            createMapFromArrayOnAttribute: createMapFromArrayOnAttribute,
            arrayToSet: arrayToSet
        };
    }
})();
