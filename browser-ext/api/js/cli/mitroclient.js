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

/* Mitro Client: a client API that is compiled and tested using the Closure Compiler. */
/** @suppress{duplicate} */
var mitro = mitro || {};
/** @suppress{duplicate} */
var kew = kew || require('./kew');

/**
@param {!mitro.LegacyAPI} legacyApi makes requests with the old API.
@constructor
*/
mitro.Client = function(legacyApi) {
  /**
  @type {!mitro.LegacyAPI}
  @private
  */
  this.legacyApi = legacyApi;
};

/** @param {boolean} expressionResult */
mitro.assert = function(expressionResult) {
  if (!expressionResult) {
    throw new Error('Assertion failed');
  }
};

/**
@param {number} numKeys
@returns {!kew.Promise}
*/
mitro.Client.prototype.getRSAKeys = function(numKeys) {
  var d = kew.defer();
  this.legacyApi.getNewRSAKeysAsync(numKeys, function(keys) {
    d.resolve(keys);
  }, function(error) {
    d.reject(error);
  });
  return d;
};

/** Finds the group key encrypted for identity from acls.
@param {!Array.<!mitro.AclRpc>} acls
@param {string} identity
*/
var findEncryptedKeyForIdentity = function(acls, identity) {
  for (var i = 0; i < acls.length; i++) {
    var acl = acls[i];
    if (acl.memberIdentity === identity) {
      return acl.groupKeyEncryptedForMe;
    }
  }
  throw new Error('Could not find acl for identity: ' + identity);
};

/**
@param {!mitro.LegacyAPI} legacyApi
@param {!mitro.GroupRpc} groupData
@returns {!mitro.CryptoKey}
*/
var getAndDecryptGroupKey = function(legacyApi, groupData) {
  var identity = legacyApi.getIdentity();
  var encryptedKey = findEncryptedKeyForIdentity(groupData.acls, identity);
  var keyString = legacyApi.decrypt(encryptedKey);
  return legacyApi.cryptoLoadFromJson(keyString);
};

/** Returns a promise for the decrypted key from group with id=groupId.
@param {number} groupId
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@returns {!kew.Promise}
*/
mitro.Client.prototype.getGroupKey = function(groupId, transaction) {
  var groupPromise = new kew.Promise();
  var legacyApi = this.legacyApi;
  legacyApi.getGroup(groupId, transaction, function(groupData) {
    groupPromise.resolve(groupData);
  }, function(error) {
    groupPromise.reject(error);
  });

  // after the group promise resolves, decrypt and extract the key
  var keyPromise = groupPromise.then(function(groupData) {
    return getAndDecryptGroupKey(legacyApi, /**@type{!mitro.GroupRpc}*/ (groupData));
  });
  return keyPromise;
};

/** Returns a promise for a map from user email to public key object.
@param {!Array.<string>} identities
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@returns {!kew.Promise}
*/
mitro.Client.prototype.getPublicKeys = function(identities, transaction) {
  var d = kew.defer();
  var legacyApi = this.legacyApi;
  legacyApi.getPublicKeys(identities, transaction, function(publicKeys) {
    // load all the public keys to objects
    var result = {};
    var userNames = Object.getOwnPropertyNames(publicKeys);
    for (var i = 0; i < userNames.length; i++ in publicKeys) {
      var keyString = publicKeys[userNames[i]];
      result[userNames[i]] = legacyApi.cryptoLoadFromJson(keyString);
    }
    d.resolve(result);
  }, function(error) {
    d.reject(error);
  });
  return d;
};

/**
@param {string} path
@param {!Object} request
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@returns {!kew.Promise}
*/
mitro.Client.prototype.postSigned = function(path, request, transaction) {
  var d = kew.defer();
  this.legacyApi.postSigned(path, request, transaction, function(result) {
    d.resolve(result);
  }, function(error) {
    d.reject(error);
  });
  return d;
};

/**
@param {string} name Name of the organization.
@param {!Array.<string>} admins addresses of admins. Must contain the requesting identity.
@param {!Array.<string>} members addresses of regular member of the organization. Must not
    contain any admins.
@param {function(!Object)} onSuccess
@param {function(!Error)} onError
*/
mitro.Client.prototype.createOrganization = function(name, admins, members, onSuccess, onError) {
  // TODO: local parameter validation?
  var client = this;  // avoids this scoping "fun" in nested functions

  // get public keys and generate keys for the org, admins and members (in parallel)
  var combinedUsers = mitro.combineUniqueUsers(admins, members);
  mitro.assert(combinedUsers.length == admins.length + members.length);
  var publicKeysPromise = client.getPublicKeys(combinedUsers, null);

  var numKeys = 1 + combinedUsers.length;
  var generatedKeysPromise = client.getRSAKeys(numKeys);
  var waitForAllKeys = kew.all([publicKeysPromise, generatedKeysPromise]);

  // after both succeed, send the create organization request
  var createdOrganization = waitForAllKeys.then(function(resultArray) {
    // build and send the create organization request
    var publicKeys = resultArray[0];
    var keys = resultArray[1];
    var request = mitro.makeOrganizationRequest(
      client.legacyApi, name, admins, members, keys, publicKeys);
    return client.postSigned('/mitro-core/api/CreateOrganization', request, null);
  });

  // handle all errors or wait for all success
  createdOrganization.then(function(result) {
    onSuccess(/** @type {!Object} */ (result));
  }).fail(function(error) {
    onError(/** @type {!Error} */ (error));
  }).done();
};

/** Returns an Array of unique users from admins and members.
@param {!Array.<string>} admins
@param {!Array.<string>} members
@return {!Array.<string>}
*/
mitro.combineUniqueUsers = function(admins, members) {
  var combined = admins.concat(members);
  var uniqueUsers = {};
  for (var i = 0; i < combined.length; i++) {
    var user = combined[i];
    if (!uniqueUsers.hasOwnProperty(user)) {
      uniqueUsers[user] = true;
    }
  }
  return Object.getOwnPropertyNames(uniqueUsers);
};

/** Returns a correctly initialized CreateOrganizationRequest object.
@param {!mitro.LegacyAPI} legacyApi
@param {string} name
@param {!Array.<string>} admins
@param {!Array.<string>} members
@param {!Array.<!mitro.CryptoKey>} generatedKeys
@param {!Object.<string, !mitro.CryptoKey>} publicKeys
@returns {!mitro.CreateOrganizationRequest}
*/
mitro.makeOrganizationRequest = function(
    legacyApi, name, admins, members, generatedKeys, publicKeys) {
  var organizationKey = generatedKeys.pop();
  var i;
  var request = new mitro.CreateOrganizationRequest();
  request.name = name;
  request.publicKey = organizationKey.exportPublicKey().toJson();

  // Give each admin access to the organization key
  request.adminEncryptedKeys = mitro.encryptOrganizationKeyForAdmins(
    legacyApi, organizationKey, admins, publicKeys);

  // Give each member a private group
  var combinedUsers = mitro.combineUniqueUsers(admins, members);
  request.memberGroupKeys = mitro.createOrganizationMemberGroups(
    legacyApi, combinedUsers, organizationKey, publicKeys, generatedKeys);

  return request;
};

/** Encrypts organizationKey for each admin, using public keys in publicKeys.
@param {!mitro.LegacyAPI} legacyApi mitro_fe API implementation.
@param {!mitro.CryptoKey} organizationKey organization's private key that will be encrypted.
@param {!Array.<string>} admins administrators who will receive organizationKey.
@param {!Object.<string, !mitro.CryptoKey>} publicKeys map of emails to public keys.
@returns {!Object.<string, string>}
*/
mitro.encryptOrganizationKeyForAdmins = function(legacyApi, organizationKey, admins, publicKeys) {
  var organizationKeyJson = organizationKey.toJson();
  var result = {};

  // Give each admin access to the organization key
  for (var i = 0; i < admins.length; i++) {
    result[admins[i]] = encryptForUser(legacyApi, organizationKeyJson, admins[i], publicKeys);
  }
  return result;
};

/**
@param {!mitro.LegacyAPI} legacyApi
@param {string} plaintext
@param {string} user
@param {Object.<string, !mitro.CryptoKey>} publicKeys
@returns {string}
*/
var encryptForUser = function(legacyApi, plaintext, user, publicKeys) {
  var publicKey = publicKeys[user];
  if (!publicKey) {
    throw new Error('Missing public key for ' + user);
  }
  return publicKey.encrypt(plaintext);
};

/** Create private member groups for members using a new key. The key is encrypted for both
the organization and the member.
@param {!mitro.LegacyAPI} legacyApi mitro_fe API implementation.
@param {!Array.<string>} members users who will become members of the organization.
@param {!mitro.CryptoKey} organizationKey organization's private key.
@param {!Object.<string, !mitro.CryptoKey>} publicKeys map of emails to public keys.
@param {!Array.<!mitro.CryptoKey>} generatedKeys list of available generated private keys.
@return {!Object.<string, !mitro.PrivateGroupKeys>}
*/
mitro.createOrganizationMemberGroups = function(
    legacyApi, members, organizationKey, publicKeys, generatedKeys) {
  mitro.assert(generatedKeys.length >= members.length);
  var result = {};

  for (var i = 0; i < members.length; i++) {
    var privateGroupKey = generatedKeys.pop();
    var privateGroupKeyJson = privateGroupKey.toJson();

    var memberPrivateGroup = new mitro.PrivateGroupKeys();
    memberPrivateGroup.publicKey = privateGroupKey.exportPublicKey().toJson();
    memberPrivateGroup.keyEncryptedForUser =
        encryptForUser(legacyApi, privateGroupKeyJson, members[i], publicKeys);
    memberPrivateGroup.keyEncryptedForOrganization = organizationKey.encrypt(privateGroupKeyJson);
    result[members[i]] = memberPrivateGroup;
  }
  return result;
};

/** Adds new admins and members and removes existing admins and members to an organization.
membersToPromote and adminsToDemote only apply to a user's admin rights. To add a brand
new administrator, they must appear in both membersToPromote and newMembers. To remove an administrator
from the organization completely, they must appear in both adminsToDemote and membersToRemove.
The server will verify these properties.

@param {!mitro.MutateOrganizationClientRequest} request
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@param {function(!Object)} onSuccess
@param {function(!Error)} onError
*/
mitro.Client.prototype.mutateOrganization = function(request, transaction, onSuccess, onError) {
  var client = this;  // avoids this scoping issues in nested functions

  // get public keys for all users
  var uniqueAddedUsers = mitro.combineUniqueUsers(request.membersToPromote, request.newMembers);
  var publicKeysPromise = client.getPublicKeys(uniqueAddedUsers, transaction);

  var numKeys = uniqueAddedUsers.length;
  var generatedKeysPromise = client.getRSAKeys(numKeys);

  // after we get public keys, get the group keys
  var orgGroupKeyAfterPublicKeyPromise = publicKeysPromise.then(function(publicKeys) {
    var groupKeysPromise = client.getGroupKey(request.orgId, transaction);
    // return both the public keys and group keys
    return groupKeysPromise.then(function(groupKeys) {
      return [publicKeys, groupKeys];
    });
  });

  var waitForAllKeys = kew.all([orgGroupKeyAfterPublicKeyPromise, generatedKeysPromise]);

  // after all succeed, send the mutate organization request
  var mutatedOrganization = waitForAllKeys.then(function(resultArray) {
    // build and send the create organization request
    var publicKeys = resultArray[0][0];
    var orgGroupKey = resultArray[0][1];
    var generatedKeys = resultArray[1];
    var rpc = mitro.makeMutateOrganizationRequestRpc(
      client.legacyApi, request, orgGroupKey, publicKeys, generatedKeys);
    return client.postSigned('/mitro-core/api/MutateOrganization', rpc, transaction);
  });

  // handle all errors or wait for all success
  mutatedOrganization.then(function(result) {
    onSuccess(/** @type {!Object} */ (result));
  }).fail(function(error) {
    onError(/** @type {!Error} */ (error));
  }).done();
};

/** Returns a correctly initialized MutateOrganizationRequestRpc object.
@param {!mitro.LegacyAPI} legacyApi
@param {!mitro.MutateOrganizationClientRequest} request
@param {!mitro.CryptoKey} organizationKey
@param {!Object.<string, !mitro.CryptoKey>} publicKeys
@param {!Array.<!mitro.CryptoKey>} generatedKeys
@returns {!mitro.MutateOrganizationRequestRpc}
*/
mitro.makeMutateOrganizationRequestRpc = function(
    legacyApi, request, organizationKey, publicKeys, generatedKeys) {
  var rpc = new mitro.MutateOrganizationRequestRpc();
  rpc.orgId = request.orgId;

  rpc.promotedMemberEncryptedKeys = mitro.encryptOrganizationKeyForAdmins(
    legacyApi, organizationKey, request.membersToPromote, publicKeys);

  // Give each member a private group
  rpc.newMemberGroupKeys = mitro.createOrganizationMemberGroups(
    legacyApi, request.newMembers, organizationKey, publicKeys, generatedKeys);

  rpc.adminsToDemote = request.adminsToDemote;
  rpc.membersToRemove = request.membersToRemove;

  return rpc;
};

/**
@constructor
@struct
*/
mitro.PrivateGroupKeys = function() {
  /** @type {string|null} */
  this.publicKey = null;
  /** @type {string|null} */
  this.keyEncryptedForUser = null;
  /** @type {string|null} */
  this.keyEncryptedForOrganization = null;
};

/**
@constructor
@struct
*/
mitro.CreateOrganizationRequest = function() {
  /**
  @type {string|null}
  */
  this.name = null;
  /**
  @type {string|null}
  */
  this.publicKey = null;
  /**
  @type {!Object.<string, string>}
  */
  this.adminEncryptedKeys = {};
  /**
  @type {!Object.<string, !mitro.PrivateGroupKeys>}
  */
  this.memberGroupKeys = {};
};

/**
@constructor
@struct
*/
mitro.MutateOrganizationRequestRpc = function() {
  /** @type {number} */
  this.orgId = 0;
  /** @type {!Object.<string, string>} */
  this.promotedMemberEncryptedKeys = {};
  /** @type {!Object.<string, !mitro.PrivateGroupKeys>} */
  this.newMemberGroupKeys = {};
  /** @type {!Array.<string>} */
  this.adminsToDemote = [];
  /** @type {!Array.<string>} */
  this.membersToRemove = [];
};

// exports for node
if (typeof(module) !== 'undefined' && module.exports) {
  module.exports = mitro;
}
