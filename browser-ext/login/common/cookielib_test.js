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

var assert = require('assert');
var fs = require('fs');
var vm = require('vm');
var cookielib = require('./cookielib');

var MitroCookie = cookielib.MitroCookie;
var MitroCookieJar = cookielib.MitroCookieJar;

// I'm sure there's a better way to do this
var myEval = function(dir) {
    var code = fs.readFileSync(dir);
    vm.runInThisContext(code, dir);
}.bind(this);

myEval(__dirname+"/URI.js");

var cookie = new MitroCookie();
assert.throws(function() {
    cookie.fromHeader('cname=cvalue');
});

// make sure you can't set a cookie for another random domain.
assert.throws(function() {
    cookie.fromHeader('cname=cvalue; domain=www.example.com', 'http://www.example2.com');
});

var testCookieString = function(cookie, str) {
    assert.equal(cookie.toUniqueString(), str);
};

cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue', 'https://www.example.com');
testCookieString(cookie, '{"name":"cname","domain":"example.com"}');
var c2 = new MitroCookie();
c2.fromHeader('cname=cvalue2; domain=example.com', 'https://www.example.com');
assert.ok(c2.equals(cookie));
assert.ok(cookie.equals(c2));

var cj = new MitroCookieJar();
cj.push(cookie);
cj.push(c2);
var cookies = cj.cookiesMatching('http://www.example.com');

// c2 and cookie are the same cookie
assert.equal(1, cookies.length);
assert.equal(1, cj.getAllCookies().length);


var c3 = new MitroCookie();
c3.fromHeader('cname=cvalue; domain=sub.www.example.com', 'http://sub.www.example.com');
cj.push(c3);
cookies = cj.cookiesMatching('http://www.example.com');
assert.equal(1, cookies.length);

cookies = cj.cookiesMatching('http://sub.www.example.com');
assert.equal(1, cookies.length);

// Domain tests
cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=.example.com', 'http://www.example.com');
assert.ok(cookie.matchesUrl("http://example.com"));
assert.ok(cookie.matchesUrl("http://www.example.com"));
assert.ok(!cookie.matchesUrl("http://test.com"));

cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=example.com', 'http://example.com');
assert.ok(cookie.matchesUrl("http://example.com"));
assert.ok(cookie.matchesUrl("http://www.example.com"));
assert.ok(!cookie.matchesUrl("http://www.test.com"));

cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=example.com', 'http://www.example.com');
assert.ok(cookie.matchesUrl("http://example.com"));
assert.ok(cookie.matchesUrl("http://www.example.com"));
assert.ok(!cookie.matchesUrl("http://www2.example.com"));
assert.ok(!cookie.matchesUrl("http://www.test.com"));

// Should check the secure property
cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=example.com; secure', 'https://example.com');
assert.ok(cookie.matchesUrl("https://example.com"));
assert.ok(!cookie.matchesUrl("http://example.com"));

// Should verify the path makes sense
cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=example.com; secure; path=/', 'https://example.com');
assert.ok(cookie.matchesUrl("https://example.com"));
assert.ok(cookie.matchesUrl("https://example.com/"));
assert.ok(cookie.matchesUrl("https://example.com/some"));
assert.ok(cookie.matchesUrl("https://example.com/some?withvar=1"));

cookie = new MitroCookie();
cookie.fromHeader('cname=cvalue; domain=example.com; secure; path=/some', 'https://example.com/some');
assert.ok(!cookie.matchesUrl("https://example.com"));
assert.ok(!cookie.matchesUrl("https://example.com/"));
assert.ok(cookie.matchesUrl("https://example.com/some"));
assert.ok(cookie.matchesUrl("https://example.com/some/"));
assert.ok(cookie.matchesUrl("https://example.com/some/path"));
assert.ok(cookie.matchesUrl("https://example.com/some?withvar=1"));

// Tests for multiple cookies in one header
var c4 = new MitroCookieJar();
c4.fromHeaders('cname=cvalue; domain=sub.www.example.com, cname2=cvalue2; domain=sub.www.example.com', 'http://sub.www.example.com');
assert.equal(2, c4.getAllCookies().length);

var c5 = new MitroCookie();
c5.fromHeader('cname=cvalue; domain=sub.www.example.com', 'http://sub.www.example.com');
assert.equal(1, c4.getAllCookies().filter(function (i) {return c5.equals(i);}).length);

var c6 = new MitroCookie();
c6.fromHeader('cname2=cvalue2; domain=sub.www.example.com', 'http://sub.www.example.com');
assert.equal(1, c4.getAllCookies().filter(function (i) {return c6.equals(i);}).length);

// Test updates
var old = new MitroCookieJar();
old.fromHeaders('cname=cvalue; domain=example.com', 'http://www.example.com');
assert.equal(1, old.getAllCookies().length);
assert.equal(old.getAllCookies()[0].cookieData.value, "cvalue");

old.fromHeaders('cname=cvalue2; domain=example.com', 'http://www.example.com');
assert.equal(1, old.getAllCookies().length);
assert.equal(old.getAllCookies()[0].cookieData.value, "cvalue2");

// Test adding new ones
old.fromHeaders('cname2=newvalue; domain=example.com', 'http://www.example.com');
assert.equal(2, old.getAllCookies().length);
assert.equal(old.getAllCookies()[0].cookieData.value, "cvalue2");
assert.equal(old.getAllCookies()[1].cookieData.value, "newvalue");

// Test a cookie name that is a cookie attribute name
cookie = new MitroCookie();
cookie.fromHeader('expires=cvalue', 'https://www.example.com');
testCookieString(cookie, '{"name":"expires","domain":"example.com"}');

// Test expires header parsing
// TODO: Test weird edge cases?
cookie.fromHeader('name2=value2; Expires=Wed, 09 Jun 2021 10:18:14 GMT', 'https://www.example.com');
testCookieString(cookie, '{"name":"name2","domain":"example.com"}');

// Test max-age parsing
cookie.fromHeader('name2=value2; max-age=1000', 'https://www.example.com');
testCookieString(cookie, '{"name":"name2","domain":"example.com"}');

// Test isExpired()
assert.equal(false, cookie.isExpired());
cookie.fromHeader('name2=value2; max-age=-1', 'https://www.example.com');
assert.equal(true, cookie.isExpired());

console.log('SUCCESS');
