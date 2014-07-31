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
var lib = require('./mitro_lib');
var crappycrypto = require('./crappycrypto');

// TODO: This is a single test for MutateMembership; extract this into a separate function

// make mitro_lib use crappycrypto
lib.initForTest();

var mutated = false;
var mutationFunction = function(group, unencryptedGroupKey, response) {
  // just return true: force the group key to be regenerated
  assert.ok(!mutated);
  mutated = true;
  return true;
};

var onSuccess = function() {
  console.log('onSuccess');
};

var onError = function(e) {
  throw new Error('onError called; Trace: ' + e.local_exception.stack);
};


var fakeArgs = null;
var fakePostToMitro = function(request, args, path, onSuccess, onError) {
  console.log('request', path);
  fakeArgs = {
    request: request,
    args: args,
    path: path,
    onSuccess: onSuccess,
    onError: onError
  };
};
lib.setPostToMitroForTest(fakePostToMitro);

var getNewRSAKeysAsyncArgs = null;
var mockGetNewRSAKeysAsync = function(numKeys, onSuccess, onError) {
  getNewRSAKeysAsyncArgs = {
    numKeys: numKeys,
    onSuccess: onSuccess,
    onError: onError
  };
};

var fakeUserKey = crappycrypto.generate();
var args = {
  uid: 'someone@example.com',
  _privateKey: fakeUserKey,
  gid: 42,
  _keyCache: {
    getNewRSAKeysAsync: mockGetNewRSAKeysAsync
  }
};
lib.MutateMembership(args, mutationFunction, onSuccess, onError);

// mutate first requests groups
assert.equal(fakeArgs.path, '/mitro-core/api/GetGroup');
var fakeGroupKey = crappycrypto.generate();
var fakeGroup = {
  name: 'some group',
  acls: [{
    myPublicKey: fakeUserKey.exportPublicKey().toJson(),
    level: 'ADMIN',
    groupKeyEncryptedForMe: fakeUserKey.encrypt(fakeGroupKey.toJson()),
    memberIdentity: args.uid
  }]
};
var tempArgs = fakeArgs;
fakeArgs = null;
tempArgs.onSuccess(fakeGroup);

// it then attempts to get a new RSA key
assert.equal(null, fakeArgs);
assert.equal(1, getNewRSAKeysAsyncArgs.numKeys);

// generate a fake key and trigger it: triggers EditGroup
var newGroupKey = crappycrypto.generate();
getNewRSAKeysAsyncArgs.onSuccess([newGroupKey]);
assert.equal(fakeArgs.path, '/mitro-core/api/EditGroup');
var decryptedGroupKey = fakeUserKey.decrypt(fakeArgs.request.acls[0].groupKeyEncryptedForMe);
assert.equal(newGroupKey.toJson(), decryptedGroupKey);

// check that making a local exception passes out the uservisibleerror
var err = new Error('low level error message');
err.userVisibleError = 'Human visible error';
var e = mitro.lib.makeLocalException(err);
assert(e.userVisibleError == 'Human visible error');
