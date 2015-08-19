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

var getSecretDataFromPage = function() {
  'use strict';
    var isNewSecret = $('#is-new-secret').is(':checked');
    var orgId = parseInt($('#secret-org-id').val(), 10);
    orgId = isNaN(orgId) ? null: orgId;
    var clientData = {};
    var secretData = {};
    var secretType = null;

    if ($('#is-web-password').is(':checked')) {
      secretType = 'manual';
      secretData.password = $('#secret-password').val();
      clientData.loginUrl = $('#secret-url').val();
      clientData.username = $('#secret-username').val();
      clientData.comment = $('#secret-note').val();
    } else if ($('#is-secure-note').is(':checked')) {
      secretType = 'note';
      secretData.note = $('#secret-note').val();
    } else {
      throw new Error('bad secret data');
    }
    clientData.type = secretType;
    clientData.title = $('#secret-name').val();

    // an empty title string results in empty strings being shown to the user
    // TODO: we need to have it treat an empty string like NULL.
    if (!clientData.title) {
      clientData.title = null;
    }
    var serverData = null;
    if ($('#is-not-viewable').length) {
      serverData = {isViewable: !($('#is-not-viewable').is(':checked'))};
    }
    return {clientData: clientData, serverData: serverData, secretData: secretData, orgId: orgId};
};

var makeUserFromUserId = function (userId) {
  return {userId: userId};
};

// Convert an array of user ids into a map of user objects.
var userIdsToUserMap = function (userIds) {
  var userMap = {};
  for (var i = 0; i < userIds.length; i++) {
    var userId = userIds[i];
    userMap[userId] = makeUserFromUserId(userId);
  }
  return userMap;
};

var nextCheckBoxId = 0;

/** Processes a map of users into an array for template substitution.
@param {!Object.<string, !Object>} userMap
@param {Object=} checkedUsers
*/
var processUsersForRendering = function (userMap, checkedUsers) {
  if (typeof checkedUsers === 'undefined') {
    checkedUsers = {};
  }
  var users = [];

  for (var userId in userMap) {
    var user = userMap[userId];
    users.push({
      userId: user.userId,
      name: user.userId,
      email: '',
      photo: EMPTY_IMAGE,
      checkboxId: 'checkbox' + nextCheckBoxId++,
      checked: userId in checkedUsers
    });
  }
  users.sort(function (a, b) {return lowercaseCompare(a.name, b.name);});

  return users;
};

/** Processes a map of groups into an array for template substitution.
@param {!Object.<string, !Object>} groupMap
@param {Object=} checkedGroups
*/
var processGroupsForRendering = function (groupMap, checkedGroups) {
  if (typeof checkedGroups === 'undefined') {
    checkedGroups = {};
  }
  var groups = [];

  for (var groupId in groupMap) {
    var group = groupMap[groupId];
    if (isVisibleGroup(group)) {
      groups.push({
        groupId: group.groupId,
        image: EMPTY_IMAGE,
        name: group.name,
        checkboxId: 'checkbox' + nextCheckBoxId++,
        checked: groupId in checkedGroups
      });
    }
  }
  groups.sort(function (a, b) {return lowercaseCompare(a.name, b.name);});

  return groups;
};

var makeSecretAclTemplateData = function (userData, secret) {
  var allUsers = userIdsToUserMap(userData.getUsersVisibleToSecret(secret.secretId));
  var existingUsers = userIdsToUserMap(secret.users);

  // Merge existing users
  for (var userId in existingUsers) {
    allUsers[userId] = existingUsers[userId];
  }

  var allGroups = userData.getGroupsVisibleToSecret(secret.secretId);
  var existingGroups = secret.groupMap;

  // Merge existing groups
  for (var groupId in existingGroups) {
    allGroups[groupId] = existingGroups[groupId];
  }

  var users = processUsersForRendering(allUsers, existingUsers);
  var groups = processGroupsForRendering(allGroups, existingGroups);

  return {users: users, groups: groups};
};

/** @constructor */
var SecretMutater = function (secret, background, userData) {
  this.secret = secret;
  this.background = background;
  this.aclDirty = false;

  this.allUsers = userIdsToUserMap(
      userData.getUsersVisibleToSecret(secret.secretId));
  this.existingUsers = userIdsToUserMap(secret.users);

  // Merge existing users
  for (var userId in this.existingUsers) {
    this.allUsers[userId] = this.existingUsers[userId];
  }

  this.allGroups = userData.getGroupsVisibleToSecret(secret.secretId);
  this.existingGroups = secret.groupMap;

  // Merge existing groups
  for (var groupId in this.existingGroups) {
    this.allGroups[groupId] = this.existingGroups[groupId];
  }
};

SecretMutater.prototype.getType = function () {
  return this.secret.clientData.type;
};

SecretMutater.prototype.addUser = function (userId) {
  if (!(userId in this.existingUsers)) {
    if (!(userId in this.allUsers)) {
      this.allUsers[userId] = makeUserFromUserId(userId);
    }
    this.existingUsers[userId] = this.allUsers[userId];
    this.aclDirty = true;
  }
};

SecretMutater.prototype.addGroup = function (groupId) {
  if (!(groupId in this.existingGroups)) {
    if (!(groupId in this.allGroups)) {
      throw new Error('Unknown group id: ' + groupId);
    }
    this.existingGroups[groupId] = this.allGroups[groupId];
    this.aclDirty = true;
  }
};

SecretMutater.prototype.removeUser = function (userId) {
  if (userId in this.existingUsers) {
    delete this.existingUsers[userId];
    this.aclDirty = true;
  }
};

SecretMutater.prototype.removeGroup = function (groupId) {
  if (groupId in this.existingGroups) {
    delete this.existingGroups[groupId];
    this.aclDirty = true;
  }
};

SecretMutater.prototype.addAclItem = function ($item) {
  var userId = $item.attr('data-user-id');
  var groupId = $item.attr('data-group-id');

  if (typeof userId !== 'undefined') {
    this.addUser(userId);
  } else if (typeof groupId !== 'undefined') {
    this.addGroup(groupId);
  } else {
    throw new Error('acl item without a user id or group id');
  }
};

SecretMutater.prototype.removeAclItem = function ($item) {
  var userId = $item.attr('data-user-id');
  var groupId = $item.attr('data-group-id');

  if (userId) {
    this.removeUser(userId);
  } else if (groupId) {
    this.removeGroup(groupId);
  } else {
    throw new Error('acl item without a user id or group id');
  }
};

/**
@param {?number} orgId
*/
SecretMutater.prototype.setOrg = function (orgId) {
  assert(orgId === null || typeof orgId === 'number');

  if (orgId !== this.secret.owningOrgId) {
    this.secret.owningOrgId = orgId;
    this.aclDirty = true;
  }
};

// TODO: cache critical data for subsequent requests.
SecretMutater.prototype.getCriticalDataForDisplay = function (onSuccess, onError) {
  background.getSiteSecretDataForDisplay(this.secret.secretId, function(siteData) {
    var data = null;
    if (siteData.clientData.type === 'note') {
      data = siteData.criticalData.note;
    } else {
      data = siteData.criticalData.password;
    }
    onSuccess(data);
  }, onError);
};

/**
@param {function()=} onSuccess
@param {function()=} onError
*/
SecretMutater.prototype.saveChanges = function (onSuccess, onError) {
  if (!this.aclDirty) {
    if (onSuccess) {
      onSuccess();
    }
    return;
  }

  var groupIds = [];
  for (var groupId in this.existingGroups) {
    groupIds.push(parseInt(groupId, 10));
  }
  var userIds = Object.keys(this.existingUsers);
  var orgId = this.secret.owningOrgId;

  // Even though changes are not committed until onSuccess is received, it
  // doesn't make sense to try to save the same changes again.  If the
  // commit fails, the secret is marked as aclDirty again.
  this.aclDirty = false;

  this.background.editSiteShares(
      this.secret.secretId,
      groupIds,
      userIds,
      orgId,
      onSuccess,
      function () {
        this.aclDirty = true;
        onError();
      }
  );
};

SecretMutater.prototype.makeSecretAclTemplateData = function () {
  var users = processUsersForRendering(this.allUsers, this.existingUsers);
  var groups = processGroupsForRendering(this.allGroups, this.existingGroups);

  return {users: users, groups: groups};
};
