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
mitro.cache = {};
if (typeof module !== 'undefined' && module.exports) {
    module.exports = mitro.cache;
}
var cache = mitro.cache;

var assert = function(expression) {
  if (!expression) {
    throw new Error('Assertion failed');
  }
};

/*
MIT LICENSE
Copyright (c) 2007 Monsur Hossain (http://www.monsur.com)

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
*/

// ****************************************************************************
// LRUCachePriority ENUM
// An easier way to refer to the priority of a cache item
var LRUCachePriority = {
    Low: 1,
    Normal: 2,
    High: 4
};

// ****************************************************************************
// LRUCache constructor
// Creates a new cache object
// INPUT: maxSize (optional) - indicates how many items the cache can hold.
//                             default is -1, which means no limit on the 
//                             number of items.
/** @constructor
@param {number} maxSize
*/
function LRUCache(maxSize) {
    this.items = {};
    this.count = 0;
    if (maxSize === null)
        maxSize = -1;
    this.maxSize = maxSize;
    this.fillFactor = 0.75;
    this.purgeSize = Math.round(this.maxSize * this.fillFactor);
    
    this.stats = {};
    this.stats.hits = 0;
    this.stats.misses = 0;
}

// ****************************************************************************
// LRUCache.getItem
// retrieves an item from the cache, returns null if the item doesn't exist
// or it is expired.
// INPUT: key - the key to load from the cache
LRUCache.prototype.getItem = function(key) {

    // retrieve the item from the cache
    var item = this.items[key];
    
    if (item !== null) {
        if (!this._isExpired(item)) {
            // if the item is not expired
            // update its last accessed date
            item.lastAccessed = new Date().getTime();
        } else {
            // if the item is expired, remove it from the cache
            this._removeItem(key);
            item = null;
        }
    }
    
    // return the item value (if it exists), or null
    var returnVal = null;
    if (item !== null) {
        returnVal = item.value;
        this.stats.hits++;
    } else {
        this.stats.misses++;
    }
    return returnVal;
};

// ****************************************************************************
// LRUCache.setItem
// sets an item in the cache
// parameters: key - the key to refer to the object
//             value - the object to cache
//             options - an optional parameter described below
// the last parameter accepts an object which controls various caching options:
//      expirationAbsolute: the datetime when the item should expire
//      expirationSliding: an integer representing the seconds since
//                         the last cache access after which the item
//                         should expire
//      priority: How important it is to leave this item in the cache.
//                You can use the values LRUCachePriority.Low, .Normal, or 
//                .High, or you can just use an integer.  Note that 
//                placing a priority on an item does not guarantee 
//                it will remain in cache.  It can still be purged if 
//                an expiration is hit, or if the cache is full.
//      callback: A function that gets called when the item is purged
//                from cache.  The key and value of the removed item
//                are passed as parameters to the callback function.
LRUCache.prototype.setItem = function(key, value, options) {

    /** @constructor
    @param {string} k
    @param {string} v
    @param {Object} o TODO: Implement options type
    */
    function LRUCacheItem(k, v, o) {
        if ((k === null) || (k === ''))
            throw new Error("key cannot be null or empty");
        this.key = k;
        this.value = v;
        if (o === null)
            o = {};
        if (o.expirationAbsolute !== null)
            o.expirationAbsolute = o.expirationAbsolute.getTime();
        if (o.priority === null)
            o.priority = LRUCachePriority.Normal;
        this.options = o;
        this.lastAccessed = new Date().getTime();
    }

    // add a new cache item to the cache
    if (this.items[key] !== null)
        this._removeItem(key);
    this._addItem(new LRUCacheItem(key, value, options));
    
    // if the cache is full, purge it
    if ((this.maxSize > 0) && (this.count > this.maxSize)) {
        this._purge();
    }
};

// ****************************************************************************
// LRUCache.clear
// Remove all items from the cache
LRUCache.prototype.clear = function() {

    // loop through each item in the cache and remove it
    for (var key in this.items) {
      this._removeItem(key);
    }  
};

// ****************************************************************************
// LRUCache._purge (PRIVATE FUNCTION)
// remove old elements from the cache
LRUCache.prototype._purge = function() {
    
    var tmparray = [];
    
    // loop through the cache, expire items that should be expired
    // otherwise, add the item to an array
    for (var key in this.items) {
        var item = this.items[key];
        if (this._isExpired(item)) {
            this._removeItem(key);
        } else {
            tmparray.push(item);
        }
    }
    
    if (tmparray.length > this.purgeSize) {

        // sort this array based on cache priority and the last accessed date
        tmparray = tmparray.sort(function(a, b) { 
            if (a.options.priority != b.options.priority) {
                return b.options.priority - a.options.priority;
            } else {
                return b.lastAccessed - a.lastAccessed;
            }
        });
        
        // remove items from the end of the array
        while (tmparray.length > this.purgeSize) {
            var ritem = tmparray.pop();
            this._removeItem(ritem.key);
        }
    }
};

// ****************************************************************************
// LRUCache._addItem (PRIVATE FUNCTION)
// add an item to the cache
LRUCache.prototype._addItem = function(item) {
    this.items[item.key] = item;
    this.count++;
};

// ****************************************************************************
// LRUCache._removeItem (PRIVATE FUNCTION)
// Remove an item from the cache, call the callback function (if necessary)
LRUCache.prototype._removeItem = function(key) {
    var item = this.items[key];
    delete this.items[key];
    this.count--;
    
    // if there is a callback function, call it at the end of execution
    if (item.options.callback !== null) {
        var callback = function() {
            item.options.callback(item.key, item.value);
        };
        setTimeout(callback, 0);
    }
};

// ****************************************************************************
// LRUCache._isExpired (PRIVATE FUNCTION)
// Returns true if the item should be expired based on its expiration options
LRUCache.prototype._isExpired = function(item) {
    var now = new Date().getTime();
    var expired = false;
    if ((item.options.expirationAbsolute) && (item.options.expirationAbsolute < now)) {
        // if the absolute expiration has passed, expire the item
        expired = true;
    } 
    if (!expired && (item.options.expirationSliding)) {
        // if the sliding expiration has passed, expire the item
        var lastAccess = item.lastAccessed + (item.options.expirationSliding * 1000);
        if (lastAccess < now) {
            expired = true;
        }
    }
    return expired;
};

LRUCache.prototype.toHtmlString = function() {
    var returnStr = this.count + " item(s) in cache<br /><ul>";
    for (var key in this.items) {
        var item = this.items[key];
        returnStr = returnStr + "<li>" + item.key.toString() + " = " + item.value.toString() + "</li>";
    }
    returnStr = returnStr + "</ul>";
    return returnStr;
};

var KEY_SEPARATOR = '\u009E'; // some random UTF-8 control character
var makeKey = function() {
    var args = Array.prototype.slice.call(arguments);
    var key = '';
    for (var i = 0; i < args.length; ++i) {
        // TODO: this should really stringify things in case that's needed
        assert(('' + args[i]).indexOf(KEY_SEPARATOR) === -1);
        key += args[i];
        key += KEY_SEPARATOR;
    }
    assert(key.length > 0);
    return key;
};

// Decent hash function
// TODO: think about security implications of using this in keys.
var hash = function(s){
  return s.split("").reduce(function(a,b){a=((a<<5)-a)+b.charCodeAt(0);return a&a;},0);              
};

cache.LRUCache = LRUCache;
cache.makeKey = makeKey;
cache.hash = hash;
})();
