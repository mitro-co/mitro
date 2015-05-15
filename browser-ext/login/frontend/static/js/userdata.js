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
/** @suppress {duplicate} */
var kew = kew || require('../../../../api/js/cli/kew');
// HACK to merge org-info in Node
(function(){
  if (!('loadOrganizationInfo' in mitro)) {
    var mitro2 = require('./org-info.js');
    for (var p in mitro2) {
      mitro[p] = mitro2[p];
    }
  }
})();
/** @suppress{duplicate} */
var assert = assert || require('../../../common/utils').assert;
/** @suppress{duplicate} */
var assertIsNumber = assertIsNumber || require('../../../common/utils').assertIsNumber;
/** @suppress{duplicate} */
var dictValues = dictValues || require('../../../common/utils').dictValues;


(function() {

/**
@constructor
@struct
*/
mitro.UserData = function() {
  this.secrets = null;
  /** @type {mitro.OrganizationInfo} */
  this.organizationInfo = null;
  this.orgId = null;
  this.organization = null;
  this.groups = null;
  this.users = null;
};

/**
@param {!Array.<!mitro.GroupInfo>} groups
@param {number} groupId
@return {mitro.GroupInfo}
*/
var groupSearch = function(groups, groupId) {
  assertIsNumber(groupId);
  for (var i = 0; i < groups.length; i++) {
    if (groups[i].groupId === groupId) {
      return groups[i];
    }
  }
  return null;
};

/** Returns the group object for groupId by searching both organization and personal groups
@param {number} groupId
@return {mitro.GroupInfo}
*/
mitro.UserData.prototype.getGroup = function(groupId) {
  if (!this.groups) {
    throw new Error('groups must not be null: UserData uninitialized');
  }
  assertIsNumber(groupId);

  // if the user has direct access, return that first (complete info)
  if (groupId in this.groups) {
    return this.groups[groupId];
  }

  // search the organization as a last resort (for members, this has limited info)
  if (this.organization && groupId in this.organization.groups) {
    return this.organization.groups[groupId];
  }
  return null;
};

/**
@param {number} secretId
@return {mitro.Secret}
*/
mitro.UserData.prototype.getSecret = function(secretId) {
  assertIsNumber(secretId);
  for (var i = 0; i < this.secrets.length; i++) {
    var secret = this.secrets[i];
    if (secret.secretId === secretId) {
      return secret;
    }
  }
  return null;
};

/**
@param {number} groupId
@return {!Array.<!mitro.Secret>}
*/
mitro.UserData.prototype.getSecretsForGroup = function(groupId) {
  // find secrets in both the personal and organization secret lists
  var secretLists = [this.secrets];
  if (this.organization) {
    secretLists.push(dictValues(this.organization.orgSecretsToPath));
  }

  // a group can be in both personal and org lists causing duplicates
  var uniqueSecrets = {};
  for (var listIndex = 0; listIndex < secretLists.length; listIndex++) {
    var secretList = secretLists[listIndex];
    for (var i = 0; i < secretList.length; ++i) {
      var secret = secretList[i];
      for (var j = 0; j < secret.groups.length; ++j) {
        if (secret.groups[j] === groupId) {
          uniqueSecrets[secret.secretId] = secret;
          break;
        }
      }
    }
  }
  return dictValues(uniqueSecrets);
};

/** Returns true if the user belongs to the organization specified by orgId.
@param {?number} orgId
@return {boolean}
*/
mitro.UserData.prototype.userBelongsToOrg = function(orgId) {
  return orgId !== undefined && orgId !== null &&
    this.organizationInfo.getOrganization(orgId) !== null;
};

/**
@return {Array.<!mitro.GroupInfo>}
*/
mitro.UserData.prototype.getGroupsVisibleToSecret = function() {
  if (this.orgId === null) {
    return this.groups;
  } else if (this.userBelongsToOrg(this.orgId)) {
    return this.organization.groups;
  } else {
    return [];
  }
};

/**
@return {Array.<string>}
*/
mitro.UserData.prototype.getUsersVisibleToSecret = function() {
  if (this.orgId === null) {
    return this.users;
  } else if (this.userBelongsToOrg(this.orgId)) {
    return this.organization.members;
  } else {
    return [];
  }
};

/**
@return {Array.<string>}
*/
mitro.UserData.prototype.getUsersVisibleToGroup = function() {
  if (this.orgId === null) {
    return this.users;
  } else if (this.userBelongsToOrg(this.orgId)) {
    return this.organization.members;
  } else {
    return [];
  }
};

/**
@return {Array.<!mitro.GroupInfo>}
*/
mitro.UserData.prototype.getSecretsVisibleToGroup = function() {
  if (this.orgId === null) {
    return this.secrets;
  } else if (this.userBelongsToOrg(this.orgId)) {
    return this.getOrganizationSecrets(this.orgId, this.organization);
  } else {
    return [];
  }
};

/** Returns true if the user belongs to the same organization as this group.
@param {number} groupId
@return {boolean}
*/
mitro.UserData.prototype.userBelongsToSameOrgAsGroup = function(groupId) {
  var group = this.getGroup(groupId);
  if (group === null) {
    throw new Error('group does not exist; id: ' + groupId);
  }
  return this.userBelongsToOrg(group.owningOrgId);
};

/** Returns true if the user belongs to the same organization as this secret.
@param {number} secretId
@return {boolean}
*/
mitro.UserData.prototype.userBelongsToSameOrgAsSecret = function(secretId) {
  var secret = this.getSecret(secretId);
  if (secret === null) {
    throw new Error('secret does not exist; id: ' + secretId);
  }
  return this.userBelongsToOrg(secret.owningOrgId);
};

/** Returns all secrets visible to this user in the selected organization. As an admin, this is all
secrets. As a regular user, this is their organization secrets.
@return {!Array.<!mitro.GroupInfo>}
*/
mitro.UserData.prototype.getOrganizationSecrets = function(orgId, org) {
  var organizationSecrets = [];
  if (org && this.organizationInfo) {
    if (this.organizationInfo.isOrgAdminFor(orgId)) {
      // org admin: return all organization secrets
      organizationSecrets = dictValues(org.orgSecretsToPath);
    } else {
      // organization member: filter personal secrets
      for (var i = 0; i < this.secrets.length; i++) {
        var secret = this.secrets[i];
        if (secret.owningOrgId === orgId) {
          organizationSecrets.push(secret);
        }
      }
    }
  }
  return organizationSecrets;
};

/** Returns all secrets visible to this user in the selected organization. As an admin, this is all
secrets. As a regular user, this is their organization secrets.
@return {!Array.<!mitro.GroupInfo>}
*/
mitro.UserData.prototype.getSelectedOrganizationSecrets = function() {
  var selOrgInfo = this.organizationInfo.getSelectedOrganization();
  if (selOrgInfo) {
    return this.getOrganizationSecrets(this.orgId, this.organization);
  } else {
    return [];
  }
};

/**
@return {!Array.<!mitro.OrganizationMetadata>}
*/
mitro.UserData.prototype.getOrganizations = function () {
  return this.organizationInfo.getOrganizations();
};

/**
@param {number} orgId
@return {mitro.OrganizationMetadata}
*/
mitro.UserData.prototype.getOrganization = function (orgId) {
  return this.organizationInfo.getOrganization(orgId);
};

/**
@return {mitro.OrganizationInfo}
*/
mitro.UserData.prototype.getAllOrganizationInfo = function() {
  return this.organizationInfo;
};

/**
@param {mitro.BackgroundApi} instance
@param {function(this:mitro.BackgroundApi, ?, ?, ...)} method
@param {...} varArgs
@return {!kew.Promise}
*/
var callBackgroundAsPromise = function(instance, method, varArgs) {
  var promise = new kew.Promise();
  var onSuccess = function(result) {
    promise.resolve(result);
  };
  var onError = function(error) {
    promise.reject(error);
  };

  // Take any extra arguments, append onSuccess, onError, and call
  var args = Array.prototype.slice.call(arguments, 2);
  args.push(onSuccess);
  args.push(onError);
  method.apply(instance, args);
  return promise;
};

/** @return {!kew.Promise.<!mitro.UsersGroupsSecrets>}} */
var listUsersGroupsAndSecretsWithPromise = function () {
  return callBackgroundAsPromise(background, background.listUsersGroupsAndSecrets);
};

/** @return {!kew.Promise.<!mitro.OrganizationInfo>} */
var getOrganizationInfoWithPromise = function () {
  return callBackgroundAsPromise(null, mitro.loadOrganizationInfo);
};

/**
@param {number} organizationId
*/
var getOrganizationWithPromise = function (organizationId) {
  return callBackgroundAsPromise(background, background.getOrganization, organizationId);
};

/** Loads UserData from the background using a Promise.
@return {!kew.Promise}
*/
mitro.loadUserDataPromise = function () {
  var data = new mitro.UserData();

  // get all secrets (not needed for manage-secrets page; but simplifies life?)
  var usersGroupsSecretsPromise = listUsersGroupsAndSecretsWithPromise();

  var organizationInfoPromise = getOrganizationInfoWithPromise();
  var organizationPromise = organizationInfoPromise.then(function (organizationInfo) {
    data.organizationInfo = organizationInfo;
  });

  // wait for all results from the background
  var allResultsPromise = kew.all([usersGroupsSecretsPromise, organizationInfoPromise]);
  var userDataPromise = allResultsPromise.then(function (results) {
    data.secrets = results[0].secrets;
    data.groups = results[0].groups;
    data.users = results[0].users;

    // done!
    return data;
  });
  return userDataPromise;
};

/** Loads UserData from the background, calling onSuccess with an initialized object.
@param {function(!mitro.UserData)} onSuccess
@param {function(!Error)} onError
*/
mitro.loadUserData = function (onSuccess, onError) {
  var userDataPromise = mitro.loadUserDataPromise();
  userDataPromise.then(onSuccess).fail(onError);
};

mitro.loadOrganizationPromise = function (userData, orgId) {
  assertIsNumber(orgId);
  var organizationPromise = getOrganizationWithPromise(orgId).then(function (result) {
    userData.orgId = orgId;
    userData.organization = result;

    return result;
  });

  return organizationPromise;
};

mitro.loadOrganization = function (userData, orgId, onSuccess, onError) {
  var organizationPromise = mitro.loadOrganizationPromise(userData, orgId);
  organizationPromise.then(onSuccess).fail(onError);
};

/** Returns all the secret data by calling getSiteData (RPC: GetSecret).
ListMySecretsAndGroups doesn't return groupMap, needed to display the secret ACL.
@param {number} secretId
@return {!kew.Promise} */
mitro.getCompleteSecretPromise = function (secretId) {
  assertIsNumber(secretId);
  return callBackgroundAsPromise(background, background.getSiteData, secretId);
};

/** Load user data for a secret.

@param {number} secretId
@param {function(!mitro.UserData, !mitro.Secret)} onSuccess
@param {function(!Error)} onError
*/
mitro.loadUserDataAndSecret = function (secretId, onSuccess, onError) {
  assertIsNumber(secretId);

  var userDataPromise = mitro.loadUserDataPromise();
  var secretPromise = mitro.getCompleteSecretPromise(secretId);
  var bothPromise = kew.all([userDataPromise, secretPromise]);
  bothPromise.then(function(results) {
    var userData = results[0];
    var secret = results[1];

    var loadOrgIfNeeded = null;
    if (secret !== null && userData.userBelongsToOrg(secret.owningOrgId)) {
      // loadOrganization modifies userData, so we ignore the result
      loadOrgIfNeeded = mitro.loadOrganizationPromise(userData, secret.owningOrgId);
    } else {
      loadOrgIfNeeded = new kew.Promise();
      loadOrgIfNeeded.resolve(null);
    }

    return loadOrgIfNeeded.then(function() {
      onSuccess(userData, secret);
    });
  }).fail(onError);
};

/** Load user data and a group.

@param {number} groupId
@param {function(!mitro.UserData, !mitro.GroupInfo)} onSuccess
@param {function(!Error)} onError
*/
mitro.loadUserDataAndGroup = function (groupId, onSuccess, onError) {
  assert(typeof groupId === 'number');

  var userData = null;
  var loadedUserAndOrgPromise = mitro.loadUserDataPromise().then(function (userDataResult) {
    userData = userDataResult;
    if (userData.organizationInfo.selectedOrgId !== null) {
      // user is a member of an organizations: load it
      return mitro.loadOrganizationPromise(userData, userData.organizationInfo.selectedOrgId);
    }
    return null;
  });

  loadedUserAndOrgPromise.then(function(organization) {
    var group = userData.getGroup(groupId);
    onSuccess(userData, group);
  }).fail(onError);
};

mitro.hackBackgroundForTest = function (bg) {
  background = bg;
  // pass background into the org-info module, and reset its internal caches
  mitro.hackOrgInfoBackgroundForTest(bg);
};

if (typeof(module) !== 'undefined' && module.exports) {
  module.exports = mitro;
}
})();
