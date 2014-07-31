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

/* Compile with:
java -jar ../../../third_party/closure-compiler/compiler.jar --language_in ECMASCRIPT5_STRICT --warning_level VERBOSE --compilation_level ADVANCED_OPTIMIZATIONS --externs ../../../login/externs_node.js --externs ./externs_jsunit.js --js_output_file mitroclient_test_compiled.js kew.js mitroclient.js mitroclient_test.js
*/
var mitro = mitro || require('./mitroclient');
// hack to combine types from mitro_legacyapi.js in Node
if (!mitro.MutateOrganizationClientRequest) {
  var m = require('./mitro_legacyapi');
  for (var p in m) {
    mitro[p] = m[p];
  }
}

var mockKeyCounter = 0;

/**
@implements mitro.CryptoKey
@constructor
@struct
@param {string=} keyString
*/
var MockCryptoKey = function(keyString) {
  var type;
  var keyId;
  if (keyString) {
    var parts = keyString.split('|', 2);
    type = parts[0];
    keyId = parseInt(parts[1], 10);
  } else {
    type = 'privatekey';
    keyId = mockKeyCounter;
    mockKeyCounter += 1;
  }
  this.type = type;
  this.keyId = keyId;
};

MockCryptoKey.prototype.encrypt = function(data) {
  expect(this.type).toBe('privatekey');
  return 'encrypted|' + this.keyId.toString() + '|' + data;
};

/**
@param {string} ciphertext
@return {string}
*/
MockCryptoKey.prototype.decrypt = function(ciphertext) {
  expect(this.type).toBe('privatekey');
  var parts = ciphertext.split('|', 3);
  expect(parts[0]).toBe('encrypted');
  expect(parts[1]).toBe(this.keyId.toString());
  return parts[2];
};

MockCryptoKey.prototype.toJson = function() {
  return this.type + '|' + this.keyId.toString();
};

MockCryptoKey.prototype.exportPublicKey = function() {
  expect(this.type).toBe('privatekey');
  return new MockCryptoKey('publickey|' + this.keyId.toString());
};

/** Mock RPC object, containing the request object as this.request.
@implements mitro.LegacyAPI
@constructor
*/
var MockLegacyAPI = function() {
  this.identity = 'user@example.com';
  this.postRequest = null;

  /** @type {function(!Array.<!mitro.CryptoKey>)|null}
  @private*/
  this.rsaSuccess = null;

  this.transaction = null;
};

MockLegacyAPI.prototype._setTransaction = function(transaction) {
  if (transaction === null) {
    expect(this.transaction).toBe(null);
  } else {
    expect(this.transaction === null || this.transaction === transaction).toBe(true);
    this.transaction = transaction;
  }
};

MockLegacyAPI.prototype.getPublicKeys = function(identities, transaction, onSuccess, onError) {
  expect(identities).not.toBe(null);
  this._setTransaction(transaction);
  this.publicKeysRequest = identities;
  this.publicKeysSuccess = onSuccess;
  this.publicKeysFailure = onError;
};

MockLegacyAPI.prototype.cryptoLoadFromJson = function(jsonString) {
  // MockCryptoKey optionally takes a string; we want an error if passed undefined.
  if (jsonString.length > 0) {
    return new MockCryptoKey(jsonString);
  } else {
    throw new Error('jsonString cannot be empty');
  }
};

MockLegacyAPI.prototype.getNewRSAKeysAsync = function(count, onSuccess, onError) {
  this.rsaCount = count;
  this.rsaSuccess = onSuccess;
  this.rsaError = onError;
};

MockLegacyAPI.prototype.postSigned = function(path, request, transaction, onSuccess, onError) {
  this._setTransaction(transaction);
  this.postPath = path;
  this.postRequest = request;
  this.postSuccess = onSuccess;
  this.postFailure = onError;
};

MockLegacyAPI.prototype.getGroup = function(groupId, transaction, onSuccess, onError) {
  this._setTransaction(transaction);
  this.getGroupId = groupId;
  this.getGroupSuccess = onSuccess;
  this.getGroupError = onError;
};

MockLegacyAPI.prototype.getIdentity = function() {
  return this.identity;
};

MockLegacyAPI.prototype.decrypt = function(plaintext) {
  return plaintext;
};

/**
@param {!*} arg
*/
var failIfCalled = function(arg) {
  throw new Error('failure callback called: ' + arg);
};

describe('mitroclient', function() {
  it('createOrganization success', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    var onSuccessResult = null;
    var onSuccess = function(result) { onSuccessResult = result; };

    var admins = ['admin@example.com'];
    var members = ['member@example.com'];
    client.createOrganization('org name', admins, members, onSuccess, failIfCalled);
    expect(onSuccessResult).toBe(null);

    // should have made an identities request and an rsa request
    expect(admins.concat(members).sort()).toEqual(legacyApi.publicKeysRequest.sort());
    expect(1 + admins.length + members.length).toBe(legacyApi.rsaCount);
    // transaction is always null for createOrganization
    expect(null).toBe(legacyApi.transaction);

    // trigger the RSA result: no callback
    var keys = [];
    for (var i = 0; i < legacyApi.rsaCount; i++) {
      keys.push(new MockCryptoKey());
    }
    // pass in a copy of the array (it gets modified)
    legacyApi.rsaSuccess(keys.slice());
    expect(legacyApi.postRequest).toBe(null);

    // trigger the public keys result: posts the "real" request
    var adminKey = new MockCryptoKey();
    var memberKey = new MockCryptoKey();
    var publicKeys = {};
    publicKeys[admins[0]] = adminKey.toJson();
    publicKeys[members[0]] = memberKey.toJson();
    legacyApi.publicKeysSuccess(publicKeys);

    var request = legacyApi.postRequest;
    expect('org name').toBe(request.name);
    expect(keys[2].exportPublicKey().toJson()).toBe(request.publicKey);
    expect(admins).toEqual(Object.getOwnPropertyNames(request.adminEncryptedKeys));
    expect(request.adminEncryptedKeys[admins[0]]).not.toBe(null);

    expect(admins.concat(members).sort()).toEqual(
        Object.getOwnPropertyNames(request.memberGroupKeys).sort());
    expect(request.memberGroupKeys[admins[0]]).not.toBe(null);
    expect(request.memberGroupKeys[members[0]]).not.toBe(null);

    legacyApi.postSuccess({});
    expect({}).toEqual(onSuccessResult);
  });

  it('fails if members includes admins', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    // bad call: members must not include admins
    var admins = ['admin@example.com'];
    var members = [admins[0], 'member@example.com'];
    try {
      client.createOrganization('org name', admins, members, failIfCalled, failIfCalled);
      expect('expected exception').toBe('');
    } catch (e) {
      expect('Assertion failed').toBe(e.message);
    }
  });

  it('catches errors in callbacks', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    var errorResult = null;
    var onError = function(e) { errorResult = e; };

    var admins = ['admin@example.com'];
    var members = [];
    client.createOrganization('org name', admins, members, failIfCalled, onError);
    // generation succeeds, public keys fails
    legacyApi.rsaSuccess([]);
    legacyApi.publicKeysFailure(new Error('public key failed'));
    expect('public key failed').toBe(errorResult.message);

    // both fail
    client.createOrganization('org name', admins, members, failIfCalled, onError);
    legacyApi.rsaError(new Error('rsa'));
    legacyApi.publicKeysFailure(new Error('public key failed'));
    expect('rsa').toBe(errorResult.message);

    // error occurs after post
    client.createOrganization('org name', admins, members, failIfCalled, onError);
    var keys = [new MockCryptoKey(), new MockCryptoKey()];
    legacyApi.rsaSuccess(keys.slice());
    var publicKeys = {'admin@example.com': new MockCryptoKey().toJson()};
    legacyApi.publicKeysSuccess(publicKeys);
    legacyApi.postFailure(new Error('post'));
    expect('post').toBe(errorResult.message);

    // not enough generated keys: exception occurs while handling results
    client.createOrganization('org name', admins, members, failIfCalled, onError);
    legacyApi.rsaSuccess([]);
    legacyApi.publicKeysSuccess(publicKeys);
    // TODO: This error message is variable between browsers! Do something smarter?
    expect(errorResult.message).toContain('undefined');

    // exception occurs in the onSuccess callback: still caught
    legacyApi = new MockLegacyAPI();
    client = new mitro.Client(legacyApi);
    client.createOrganization('org name', admins, members, function(result) {
      throw new Error('onSuccess exception');
    }, onError);
    legacyApi.rsaSuccess(keys);
    legacyApi.publicKeysSuccess(publicKeys);
    legacyApi.postSuccess({});
    expect('onSuccess exception').toBe(errorResult.message);
  });

  it('CreateOrganization member groups', function() {
    var legacyApi = new MockLegacyAPI();
    var members = [];
    var organizationKey = new MockCryptoKey();
    var adminKey = new MockCryptoKey();
    var publicKeys = {};
    var generatedKeys = [];

    // empty request is okay
    var groups = mitro.createOrganizationMemberGroups(legacyApi, members,
        organizationKey, publicKeys, generatedKeys);
    expect(members).toEqual(Object.getOwnPropertyNames(groups));

    // member with the wrong number of keys
    members = ['member@example.com'];
    try {
      mitro.createOrganizationMemberGroups(
          legacyApi, members, organizationKey, publicKeys, generatedKeys);
      expect(false).toBe(true);
    } catch (e) {
      expect('Assertion failed').toEqual(e.message);
    }

    // correct generated keys, no public keys
    generatedKeys.push(new MockCryptoKey());
    try {
      mitro.createOrganizationMemberGroups(
          legacyApi, members, organizationKey, publicKeys, generatedKeys.concat());
      expect(false).toBe(true);
    } catch (e) {
      expect(e.message).toContain('Missing public key');
    }

    publicKeys[members[0]] = new MockCryptoKey();
    groups = mitro.createOrganizationMemberGroups(
        legacyApi, members, organizationKey, publicKeys, generatedKeys);
    expect(members).toEqual(Object.getOwnPropertyNames(groups));
  });

  it('combineUniqueUsers test', function() {
    expect([]).toEqual(mitro.combineUniqueUsers([], []));
    expect(['hello']).toEqual(mitro.combineUniqueUsers(['hello'], []));
    expect(['hello']).toEqual(mitro.combineUniqueUsers(['hello'], ['hello']));
    // TODO: This Depends on object key iteration order?
    var out = mitro.combineUniqueUsers(['hello'], ['hello', '2']);
    out.sort();
    expect(['2', 'hello']).toEqual(out);
  });

  it('makeMutateOrganizationRequestRpc', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    var request = new mitro.MutateOrganizationClientRequest();
    request.orgId = 52;
    request.membersToPromote = ['admin@example.com'];
    request.newMembers = ['member@example.com'];
    request.adminsToDemote = ['oldadmin@example.com'];
    request.membersToRemove = ['oldmember@example.com'];

    var generatedKeys = [];
    var organizationKey = new MockCryptoKey();
    var publicKeys = {};
    var adminKey = new MockCryptoKey();
    var memberKey = new MockCryptoKey();
    publicKeys[request.membersToPromote[0]] = adminKey;
    publicKeys[request.newMembers[0]] = memberKey;
    generatedKeys.push(new MockCryptoKey());

    var rpc = mitro.makeMutateOrganizationRequestRpc(
      legacyApi, request, organizationKey, publicKeys, generatedKeys.slice());

    expect(request.orgId).toBe(rpc.orgId);
    expect(request.membersToPromote).toEqual(
        Object.getOwnPropertyNames(rpc.promotedMemberEncryptedKeys));
    expect(request.newMembers).toEqual(Object.getOwnPropertyNames(rpc.newMemberGroupKeys));
    expect(request.adminsToDemote).toEqual(rpc.adminsToDemote);
    expect(request.membersToRemove).toEqual(rpc.membersToRemove);
  });

  it('MutateOrganization', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    var onSuccessResult = null;
    var onSuccess = function(result) { onSuccessResult = result; };

    var request = new mitro.MutateOrganizationClientRequest();
    request.orgId = 52;
    request.membersToPromote = ['admin@example.com'];
    request.newMembers = request.membersToPromote;
    request.adminsToDemote = ['oldadmin@example.com'];
    request.membersToRemove = ['oldmember@example.com'];
    client.mutateOrganization(request, null, onSuccess, failIfCalled);

    // makes an identities request and an rsa request; group keys only after public keys
    expect(request.membersToPromote).toEqual(legacyApi.publicKeysRequest);
    expect(1).toBe(legacyApi.rsaCount);
    expect(legacyApi.getGroupId).toBeUndefined();

    // trigger the public keys result: no callback but it makes a group keys request
    var adminKey = new MockCryptoKey();
    var publicKeys = {};
    publicKeys[request.membersToPromote[0]] = adminKey.toJson();
    legacyApi.publicKeysSuccess(publicKeys);
    expect(request.orgId).toBe(legacyApi.getGroupId);
    expect(legacyApi.postRequest).toBeNull();

    // trigger the RSA result: no callback
    var keys = [new MockCryptoKey()];
    legacyApi.rsaSuccess(keys.slice());
    expect(legacyApi.postRequest).toBeNull();

    // return the group result: callback
    var group = new mitro.GroupRpc();
    var acl = new mitro.AclRpc();
    var groupKey = new MockCryptoKey();
    acl.groupKeyEncryptedForMe = groupKey.toJson();
    acl.memberIdentity = legacyApi.identity;
    group.acls.push(acl);
    legacyApi.getGroupSuccess(group);

    expect('/mitro-core/api/MutateOrganization').toBe(legacyApi.postPath);
    expect(request.orgId).toBe(legacyApi.postRequest.orgId);
    expect(request.membersToPromote).toEqual(
        Object.getOwnPropertyNames(legacyApi.postRequest.promotedMemberEncryptedKeys));
    expect(request.newMembers).toEqual(Object.getOwnPropertyNames(legacyApi.postRequest.newMemberGroupKeys));
    expect(request.adminsToDemote).toEqual(legacyApi.postRequest.adminsToDemote);
    expect(request.membersToRemove).toEqual(legacyApi.postRequest.membersToRemove);

    // trigger the post response
    expect(onSuccessResult).toBeNull();
    legacyApi.postSuccess({});
    expect({}).toEqual(onSuccessResult);
  });

  /**
  @implements mitro.Transaction
  @constructor
  */
  var MockTransaction = function() {};

  it('MutateTransaction', function() {
    var legacyApi = new MockLegacyAPI();
    var client = new mitro.Client(legacyApi);

    var onSuccessResult = null;
    var onSuccess = function(result) { onSuccessResult = result; };

    var request = new mitro.MutateOrganizationClientRequest();
    request.orgId = 52;
    request.newMembers = ['user@example.com'];
    var transaction = new MockTransaction();
    client.mutateOrganization(request, transaction, onSuccess, failIfCalled);
    // should have made a request with a transaction object
    expect(transaction).toBe(legacyApi.transaction);

    // trigger the RSA result: no callback, no group keys request
    var keys = [new MockCryptoKey()];
    legacyApi.rsaSuccess(keys.slice());
    expect(onSuccessResult).toBeNull();
    expect(legacyApi.getGroupSuccess).toBeUndefined();

    // trigger the public keys result: no callback but it sends the getGroup request
    var publicKeys = {};
    publicKeys[request.newMembers[0]] = new MockCryptoKey().toJson();
    legacyApi.publicKeysSuccess(publicKeys);
    expect(legacyApi.getGroupSuccess).not.toBeNull();
    expect(onSuccessResult).toBeNull();

    // return the group result: callback
    var group = new mitro.GroupRpc();
    var acl = new mitro.AclRpc();
    var groupKey = new MockCryptoKey();
    acl.groupKeyEncryptedForMe = groupKey.toJson();
    acl.memberIdentity = legacyApi.identity;
    group.acls.push(acl);
    legacyApi.transaction = null;
    legacyApi.getGroupSuccess(group);
    expect(transaction).toBe(legacyApi.transaction);
  });
});
