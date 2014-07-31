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
exports=undefined;
eval(fs.readFileSync('./bloomfilter.js','utf8'));
eval(fs.readFileSync('./bad_password_bloom_data.js','utf8'));
eval(fs.readFileSync('./passwords.js','utf8'));



sp = passwords.scorePassword;


assert.equal(sp('aaaaaaaa'), sp('aaaaaaaaaaaaaaa'));
assert.equal(sp('abcdefghij'), sp('abcdefghijklmnop'));


// horrible passwords
assert(sp('') < 0);
assert(sp(null) < 0);
assert(sp(undefined) < 0);
assert(sp('aaaaa') < 0);
assert(sp('1234567890') < 33);
assert(sp('aaaaaaaaa') < 33);

// weak passwords
assert(sp('Pwd123') < 0);
assert(sp('pwd123456') < 34);

// good passwords
assert(sp('ThisIs#1') < 99);

// strong passwords
assert(sp('this is a totally awesome password') > 100);

console.log('SUCCESS');
