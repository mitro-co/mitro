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

var userpass = require('./userpass');

function assertRoundTrip(username, password) {
  var result = userpass.decodeUsernamePassword(
    userpass.encodeUsernamePassword(username, password));
  assert.equal(username, result.username);
  assert.equal(password, result.password);
}

assertRoundTrip('user', 'pass');
assertRoundTrip('héllo user + & = ', 'pwhéllo + & = | ');
assertRoundTrip('A + B', 'A + B');
assertRoundTrip('A %20+%2B B', 'A %20+%2B B');

var nullResult = {username: null, password: null};

// check that missing the username doesn't parse
assert.deepEqual(nullResult, userpass.decodeUsernamePassword('&p=pass&'));
assert.deepEqual(nullResult, userpass.decodeUsernamePassword('u='));
assert.deepEqual(nullResult, userpass.decodeUsernamePassword('u=&p=&extra=junk'));

// just the username does parse
assert.deepEqual({username: 'user', password: null},
  userpass.decodeUsernamePassword('u=user&&&&extra=junk'));

// test extracting the hash string from the window
var fakeWindow = {
  location: {
    hash: ""
  }
};

assert.deepEqual(nullResult, userpass.hashToUsernamePassword(fakeWindow));
fakeWindow.location.hash = '#' + userpass.encodeUsernamePassword('u', 'p') + '&extra=junk';
assert.deepEqual({username: 'u', password: 'p'}, userpass.hashToUsernamePassword(fakeWindow));

// test parsing a plain username (original version of hash)
assert.deepEqual({username: 'username@example.com', password: null},
  userpass.decodeUsernamePassword('username@example.com'));

console.log('SUCCESS');
