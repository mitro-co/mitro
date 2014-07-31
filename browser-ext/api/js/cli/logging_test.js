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

var log = require('./logging.js');

var failIfRun = function() {
  throw new Error('Failure: did not expect to get here');
};

var testLimitedBuffer = function() {
  var buffer = log.makeCircularBuffer(2);
  assert.strictEqual(buffer.get(-1), undefined);
  assert.strictEqual(buffer.get(0), undefined);
  assert.strictEqual(buffer.get(1), undefined);
  assert.strictEqual(buffer.get(2), undefined);

  buffer.push('arg');
  assert.strictEqual(buffer.get(-1), undefined);
  assert.deepEqual(buffer.get(0), ['arg']);
  assert.strictEqual(buffer.get(1), undefined);
  assert.strictEqual(buffer.get(2), undefined);

  buffer.push('arg2', 'one', {});
  assert.strictEqual(buffer.get(-1), undefined);
  assert.deepEqual(buffer.get(0), ['arg']);
  assert.deepEqual(buffer.get(1), ['arg2', 'one', {}]);
  assert.strictEqual(buffer.get(2), undefined);

  buffer.push('arg3');
  assert.strictEqual(buffer.get(-1), undefined);
  // .get(0) should be the "first" entry in time order
  assert.deepEqual(buffer.get(0), ['arg2', 'one', {}]);
  assert.deepEqual(buffer.get(1), ['arg3']);
  assert.strictEqual(buffer.get(2), undefined);
};

var testObjectConversion = function() {
  // objects get converted (instead of [Object object])
  var buffer = log.makeCircularBuffer(2);
  buffer.push('hello', {'prop': 42});
  assert.equal(buffer.toString(), 'hello {"prop":42}\n');

  // errors get converted
  try {
    notDefinedReference();
  } catch (e) {
    buffer = log.makeCircularBuffer(2);
    buffer.push('exception', e);
    assert(buffer.toString().indexOf(
        'exception {"name":"ReferenceError","message":"notDefinedReference is not defined"') === 0);
  }
};

var cb = log.makeCircularBuffer(1);
assert (cb.size() === 0);
cb.push('hi', 'there', 2);
assert (cb.size() === 1);
cb.push('hi', 'there', 3);
assert (cb.size() === 1);
assert(JSON.stringify(cb.toArray()) == JSON.stringify([ [ 'hi', 'there', 3 ] ]));

log.captureLogsToBuffer();
console.log('This is cool', 4);
log.stopCapturingLogsToBuffer();
console.log('lame');
assert.equal(log.logBuffer.size(), 1);
assert.deepEqual(log.logBuffer.get(0), ['This is cool', 4]);

testLimitedBuffer();
testObjectConversion();
