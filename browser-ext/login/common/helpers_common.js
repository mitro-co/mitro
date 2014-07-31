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

var helpers_common = {};

/**
 * Generates the object to be used in browser helpers
 * and firefox main script for storage operations
 * 
 * @param {!Object} storage The object to be used as a storage.
 *        E.g. localStorage for safari and webpage
 *        and ss.storage for firefox main script
 * @param {function(string=)} parseValue (optional) function to parse a
 *        serialized value back to a javascript value.
 * @param {function(string)} serializeValue (optional) function to serialize
 *        a javascript value for storage.
 */
helpers_common.storage = function(storage, parseValue, serializeValue) {
    // In firefox main script the setTimeout is not available.
    // Also it is not required because the storage calls 
    // are being handled by the messaging transport 
    // which makes them asynchronous by default
    var localSetTimeout = null;
    if(typeof(setTimeout) === 'undefined') {
        localSetTimeout = function(func, timeout) {
            func();
        };
    } else {
        localSetTimeout = setTimeout;
    }
    
    // Provide a default implementation of parseValue if not provided.
    if (typeof(parseValue) === 'undefined') {
      /**
       * Parses the given json.
       *
       * @param {string=} json The json to parse
       * @returns {*|undefined} Returns the resulting value or 'undefined' if no json provided
       */
      parseValue = function(json) {
        return (typeof(json) === 'undefined') ? undefined : JSON.parse(json);
      };
    }

    
    // Provide a default implementation of serializeValue if not provided.
    if (typeof(serializeValue) === 'undefined') {
      /**
       * Serialize a value to json.
       *
       * @param {string} value
       */
      serializeValue = function(value){
        return JSON.stringify(value);
      };
    }

    return {
        /**
         * Fetches the one/multiple/all values from the local storage
         * (depending on the keys argument)
         * 
         * @param {string|!Array.<string>|!Object|undefined|null} keys
         * @param {function(*)} callback
         * 
         * Calls callback with one of the following, depending on the type of keys:
         * - The stored value|undefined if keys is a string
         * - An object containing the key/value pairs if keys is an array
         * - An object containing the requested values replaced by the default values if
         *   unavailable in local storage if keys is a dictionary.
         * - An object containing all key/values pairs if keys is undefined or null
         */
         // TODO test that the object returned if the value is not found does 
         // not contain the key, even if the value is undefined.
        get: function(keys, callback){
            localSetTimeout(function(){
                var rval = {};
                var key;
                if (keys && typeof(keys) === 'object'){
                    if (Array.isArray(keys)) {
                        // if it's a list return the values, if it's a dict use default values.
                        for (var i=0; i < keys.length; i++) {
                            if (storage[keys[i]] !== undefined) {
                                rval[keys[i]] = parseValue(storage[keys[i]]);
                            }
                        }
                    } else {
                        for (var k in keys) {
                            if (k in storage) {
                                rval[k] = parseValue(storage[k]);
                            } else {
                                rval[k] = keys[k];
                            }
                        }
                    }
                // Single string key case
                } else if (keys) {
                    if (storage[keys] !== undefined) {
                        rval[keys] = parseValue(storage[keys]);
                    }
                } else {
                    for (key in storage) {
                        rval[key] = parseValue(storage[key]);
                    }
                }
                callback(rval);
            }, 0);
        },
        /**
         * Saves the given key:value pairs in the local storage
         * 
         * @param {!Object} data The key:value pairs to save
         * @param {function()} callback
         */
        set: function(data, callback){
            localSetTimeout(function() {
                for(var key in data){
                    storage[key] = serializeValue(data[key]);
                }
                
                if (typeof(callback) != 'undefined') {
                    callback();
                }
            }, 0);
        },
        /**
         * Removes the storage entries by the given key or the arrey of keys
         * 
         * @param {string|!Array.<string>} keys
         * @param {function()} callback
         */
        remove: function(keys, callback) {
            localSetTimeout(function() {
                if (Array.isArray(keys)) {
                    keys = [keys];
                }
                
                for (var i=0; i<keys.length; i++) {
                    delete storage[keys[i]];
                }
                
                if (typeof(callback) != 'undefined') {
                    callback();
                }
            }, 0);
        }
    };
};

/**
 * The jquery-based ajax implementation
 * 
 * @param {!Object} params The set of parameters as following:
 *   {string} type The request type
 *   {string} url The request url
 *   {string, object} data The request data, eather serialized or not
 *   {string} dataType Request dataType
 *   {function} complete The onComplete callback function
 */
helpers_common.ajax = function(params){
    $.ajax({
        type: params.type,
        url: params.url,
        data: params.data,
        dataType: params.dataType,
        complete: function(jqXHR){
            params.complete({
                status: jqXHR.status ? jqXHR.status : -1,
                text: jqXHR.responseText 
            });
        }
    });
};

/**
 * Interface for tabs
 *
 * @constructor
 * @struct
 */
helpers_common.MitroTab = function(id) {
    this.id = id;
    this.windowId = null;
    /** @type {?number} */
    this.index = null;
    /** @type {?string} */
    this.url = null;
    /** @type {?string} */
    this.title = null;
};

// Mitro_storage function is also used in firefox main script.
// That's why we have to define the CommonJS module
if (typeof(exports) !== 'undefined') {
    exports.helpers_common = helpers_common;
} else if(typeof module !== 'undefined' && module.exports) {
    module.exports = helpers_common;
}
