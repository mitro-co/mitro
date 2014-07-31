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

var bf = require('./bloomfilter');
var fs = require('fs');
var assert = require('assert');
var lines = fs.readFileSync('/dev/stdin').toString().split('\n');

var bloom = new bf.BloomFilter(
  (1<<21), // number of bits to allocate.
  16        // number of hash functions.
);

for (var i = 0; i < lines.length; ++i) {
  if (lines[i].length >= 4) {
    lines[i] = lines[i].replace(/\W/g,'');
    bloom.add(lines[i]);
    console.log('adding ', JSON.stringify(lines[i]));
  }
}

var k = bloom.test('HelloThereWhatIsYourName');
console.log(bloom.test('HelloThereWhatIsYourNamjjjjjje'));
console.log(k);
assert(!k);

var array = [].slice.call(bloom.buckets);
console.log(array);
var bloom2 = new bf.BloomFilter(array, 5);
for (var i = 0; i < lines.length; ++i) {
  if (lines[i] >= 4) {
    console.log(lines[i]);
    assert(bloom.test(lines[i]));
    assert(bloom2.test(lines[i]));
  }
}
assert (bloom2.test('password'));
fs.writeFileSync("./bad_password_bloom_data.js", 'badPasswordBloomArray = ' + JSON.stringify(array) + ';');
