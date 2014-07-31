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

(function () {
    // Library for parsing and dealing with cookies.

    var getCanonicalHost;

    if (typeof(module) !== 'undefined') {
        var domain = require('./domain');
        getCanonicalHost = domain.getCanonicalHost;
    } else {
        getCanonicalHost = window.getCanonicalHost;
    }

    /** @constructor */
    var MitroCookie = function() {
        this.cookieData = this;
        // for closure compiler
        this.cookieData.path = undefined;
    };

    MitroCookie.prototype.getDomain = function() {
        return this.cookieData.domain;
    };

    MitroCookie.prototype.fromHeader = function(header, sourceUrlString) {
        var cookie = this.cookieData;
        if (!sourceUrlString) {
            throw 'need source url';
        }
        cookie.domain = getCanonicalHost(sourceUrlString);
        if (!cookie.domain) {
            throw 'bad url';
        }

        var attributes = ['expires', 'path', 'domain', 'secure', 'httponly', 'max-age', 'comment', 'version'];
        var params = header.split('; ');

        for (var i = 0; i < params.length; i++) {
            var param = params[i];
            param = param.split('=');
            var key = param[0];
            var value = param[1];
            
            if (i === 0) {
                cookie.name = key;
                cookie.value = value;
            } else if (attributes.indexOf(key.toLowerCase()) >= 0) {
                if (key.toLowerCase() === 'expires') {
                    // "Sane" cookies should be an HTTP date:
                    // http://tools.ietf.org/html/rfc2616#section-3.3.1
                    // But Cookie can be weird, and to be correct we need to implement
                    // http://tools.ietf.org/html/rfc6265#section-5.1.1
                    // For now we hope that JS's Date does the right thing
                    cookie.expires = new Date(value);
                } if (key.toLowerCase() === 'max-age') {
                    var parsed = parseInt(value, 10);
                    if (!isNaN(parsed)) {
                        // calculate expires
                        cookie.expires = new Date(Date.now() + parseInt(value, 10) * 1000);
                    }
                } else {
                    cookie[key] = value || true;
                }
            } else {
                console.log('unknown key/value pair: ' + key + '=' + value);
            }
        }

        if (!this.matchesUrl(sourceUrlString)) {
            throw 'incorrect domain for cookie; cannot set';
        }
    };

    // Convert a cookie structure to a cookie string
    MitroCookie.prototype.toCookie = function() {
        return this.cookieData.name + "=" + this.cookieData.value;
    };

    MitroCookie.prototype.toUniqueString = function() {
        return JSON.stringify({'name' :   this.cookieData.name,
                               'domain' : this.cookieData.domain,
                               'path':    this.cookieData.path});
    };

    MitroCookie.prototype.equals = function(otherCookie) {
        return this.toUniqueString() == otherCookie.toUniqueString();
    };

    MitroCookie.prototype.matchesUrl = function(full_url) {
        var cookie = this.cookieData;
        var hostnameCheck = true;
        var hostname = getCanonicalHost(full_url);
        if (cookie.domain[0] === '.') {
            hostnameCheck = hostname.slice(1 - cookie.domain.length) === cookie.domain.slice(1);
        } else {
            hostnameCheck = (cookie.domain === hostname);
        }
        var uri = new URI(full_url);
        var pathCheck = true;
        if (cookie.path) {
            if (uri.getPath() !== null) {
                // check if the path of the url is inside the cookie path
                pathCheck = uri.getPath().indexOf(cookie.path) >= 0;
            } else {
                pathCheck = cookie.path === "/";
            }
        }
        var httpsCheck = true;
        if (cookie.secure) {
            // check if the protocol is https (I guess we should support other secure protocols?)
            httpsCheck = uri.getScheme() === "https";
        }
        return hostnameCheck && pathCheck && httpsCheck;
    };

    MitroCookie.prototype.isExpired = function() {
        if (this.cookieData.expires) {
            return Date.now() > this.cookieData.expires.getTime();
        } else {
            return false; // Session cookies don't expire
        }
    };

    /** @constructor */
    var MitroCookieJar = function() {
        this.cookies = {};
    };
    MitroCookieJar.prototype.push = function(cookie) {
        // this uniquifies cookies.
        this.cookies[cookie.toUniqueString()] = cookie;
    };

    MitroCookieJar.prototype.fromHeaders = function(cookietext, sourceUrlString) {
        var self = this;
        var splitter = /,\s(?=\w+=\w)/g;
        var cookie_array = cookietext.split(splitter);
        cookie_array.forEach(function(e) {
            var cookie = new MitroCookie();
            cookie.fromHeader(e, sourceUrlString);
            self.push(cookie);
        });
    };

    MitroCookieJar.prototype.getAllCookies = function() {
        var rval = [];
        for (var k in this.cookies) {
            rval.push(this.cookies[k]);
        }
        return rval;
    };

    MitroCookieJar.prototype.cookiesMatching = function(url) {
        return this.getAllCookies().filter(function(cookie) {
            return cookie.matchesUrl(url);
        });
    };

    // define node.js module for testing
    if (typeof module !== 'undefined' && module.exports) {
        module.exports.MitroCookie = MitroCookie;
        module.exports.MitroCookieJar = MitroCookieJar;
    } else {
        window.MitroCookie = MitroCookie;
        window.MitroCookieJar = MitroCookieJar;
    }  

})();
