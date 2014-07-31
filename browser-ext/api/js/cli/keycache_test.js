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

var keycache = require('./keycache');

function testCacheSimple() {
  var cache = keycache.MakeKeyCache();
  var key = cache.pop();
  assert(key.encrypt('hello').length > 0);
  cache.push(key);

  var output = cache.toJson();
  assert.equal('string', typeof output);
  assert(output.length > 1);

  var cache2 = keycache.MakeKeyCache();
  cache2.loadFromJson(output);
  var key2 = cache2.pop();
  assert.equal(key.toJson(), key2.toJson());
}

function testListeners() {
  var cache = keycache.MakeKeyCache();

  var push = null;
  var pop = null;

  cache.setPushListener(function(c) {
    push = c.size();
  });

  cache.setPopListener(function(c) {
    pop = c.size();
  });

  // generates a key, does not call the listener
  var k = cache.pop();
  assert.equal(null, pop);

  // push calls listener
  cache.push(k);
  assert.equal(1, push);

  // pop calls listener
  cache.pop();
  assert.equal(0, pop);
}

function testWorker() {
  var cache = keycache.MakeKeyCache();

  var fullCount = 0;
  cache.setPushListener(function() {
    if (cache.size() == keycache.CACHE_TARGET) {
      fullCount += 1;
      if (fullCount == 1) {
        // we filled the cache: pop keys
        cache.pop();
        cache.pop();
      } else {
        // we filled the cache twice: quit
        worker.stop();
        clearTimeout(failTimeout);
      }
    }
  });

  var worker = keycache.startFiller(cache);
  // get a callback when full
}

keycache.useCrappyCrypto();
function failIfCalled() {
  console.log('TIMEOUT: Test failed');
  process.exit(1);
}

var failTimeout = setTimeout(failIfCalled, 2000);
var tests = [testCacheSimple, testListeners, testWorker];
for (var i = 0; i < tests.length; i++) {
  tests[i]();
  process.stdout.write('.');
}
console.log('SUCCESS');
