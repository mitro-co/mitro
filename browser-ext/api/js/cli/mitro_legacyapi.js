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

/** @suppress{duplicate} */
var mitro = mitro || {};

/**
@interface
*/
mitro.CryptoKey = function() {};
/**
@return {string}
*/
mitro.CryptoKey.prototype.toJson = function() {};
/**
@param {string} data
@return {string}
*/
mitro.CryptoKey.prototype.encrypt = function(data) {};
/**
@return {mitro.CryptoKey}
*/
mitro.CryptoKey.prototype.exportPublicKey = function() {};

/**
Opaque object representing the current transaction. Do not look inside.
@interface
*/
mitro.Transaction = function() {};

/**
@constructor
@struct
*/
mitro.AclRpc = function() {
  /** @type{string} */
  this.level = '';
  /** @type{string} */
  this.groupKeyEncryptedForMe = '';
  /** @type{string} */
  this.myPublicKey = '';
  /** @type {?number} */
  this.memberGroup = null;
  /** @type {?string} */
  this.memberIdentity = null;
};

/** Actually called AddGroupRequest in Java; seems like a bad name?
@constructor
@struct
*/
mitro.GroupRpc = function() {
  /** @type{string} */
  this.name = '';
  /** @type{string} */
  this.publicKey = '';
  /** @type{string} */
  this.signatureString = '';
  /** @type{string} */
  this.scope = '';
  /** @type{boolean} */
  this.autoDelete = false;
  /** @type {!Array.<!mitro.AclRpc>} */
  this.acls = [];
};

/** JavaScript client arguments to mutate organization.
@constructor
@struct
*/
mitro.MutateOrganizationClientRequest = function() {
  /** id of the organization to modify. @type {number} */
  this.orgId = 0;
  /** New or existing members who will get admin priviledges.
  @type {!Array.<string>} */
  this.membersToPromote = [];
  /** New members to be added. Must not be members already. @type {!Array.<string>} */
  this.newMembers = [];
  /** Admins who will get admin privledges removed. @type {!Array.<string>} */
  this.adminsToDemote = [];
  /** Members to be removed. @type {!Array.<string>} */
  this.membersToRemove = [];
};

/**
Performs operations with the old API.
@interface
*/
mitro.LegacyAPI = function() {};

/**
Gets public keys for users.
@param {!Array.<string>} identities email addresses of users to fetch.
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@param {function(!Object.<string, string>)} onSuccess called with address -> key mappings.
@param {function(!Error)} onError called with an error if anything fails.
*/
mitro.LegacyAPI.prototype.getPublicKeys = function(identities, transaction, onSuccess, onError) {};
/**
@param {string} jsonString
@return {!mitro.CryptoKey}
*/
mitro.LegacyAPI.prototype.cryptoLoadFromJson = function(jsonString) {};
/**
@param {string} path
@param {!Object} request
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@param {function(!Object)} onSuccess
@param {function(!Error)} onError
*/
mitro.LegacyAPI.prototype.postSigned = function(path, request, transaction, onSuccess, onError) {};
/**
@param {number} count
@param {function(!Array.<mitro.CryptoKey>)} onSuccess
@param {function(!Error)} onError
*/
mitro.LegacyAPI.prototype.getNewRSAKeysAsync = function(count, onSuccess, onError) {};
/**
@param {number} groupId
@param {mitro.Transaction} transaction transaction this request belongs to (or null).
@param {function(mitro.GroupRpc)} onSuccess
@param {function(!Error)} onError
*/
mitro.LegacyAPI.prototype.getGroup = function(groupId, transaction, onSuccess, onError) {};
/**
@return {string}
*/
mitro.LegacyAPI.prototype.getIdentity = function() {};
/**
@param {string} ciphertext
@return {string}
*/
mitro.LegacyAPI.prototype.decrypt = function(ciphertext) {};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = mitro;
}
