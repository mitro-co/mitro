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
var fe = require('./mitro_fe');
mitro.log.stopCapturingLogsToBuffer();

function testBidirectionalSetDiff() {
  var added = [];
  var deleted = [];
  fe.bidirectionalSetDiff([], [1], added, deleted);
  assert.deepEqual([1], added);
  assert.deepEqual([], deleted);

  added = [];
  deleted = [];
  // previously with string sort, this thought 2 was added
  fe.bidirectionalSetDiff([2, 100], [2], added, deleted);
  assert.deepEqual([], added);
  assert.deepEqual([100], deleted);
}

function testConvertList() {
  // Create a ListMySecretsAndGroups response with a single secret
  response = {
    secretToPath: {
      90: {
        groupIdPath: [50],
        groups: [50, 51, 52],
        hiddenGroups: [70],
        users: ['hidden@example.com'],
        clientData: JSON.stringify({})
      }
    },
    groups: {
        50: {
            groupId: 50,
            users: ['u1@example.com']
        },
        51: {
            groupId: 51,
            users: ['u1@example.com', 'u2@example.com']
        }
    }
  };

  var output = fe.convertListSitesToExtension(response);
  assert.equal(1, output.length);
  var expectedUsers = ['hidden@example.com', 'u1@example.com', 'u2@example.com'];
  expectedUsers.sort();
  output[0].flattenedUsers.sort();
  assert.deepEqual(expectedUsers, output[0].flattenedUsers);
}

testBidirectionalSetDiff();
testConvertList();

console.log('SUCCESS');
