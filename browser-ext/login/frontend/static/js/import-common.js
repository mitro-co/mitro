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

/** @suppress {duplicate} */
var mitro = mitro || require('./background_interface.js');
// HACK to import part of the mitro namespace from userdata.js in Node
(function(){
  if (!('UserData' in mitro)) {
    var mitro2 = require('./userdata.js');
    for (var p in mitro2) {
      mitro[p] = mitro2[p];
    }
  }
})();
if (typeof isVisibleGroup === 'undefined') {
  var admin_common = require('./admin-common.js');
  isVisibleGroup = admin_common.isVisibleGroup;
}

var passwords = [];

/** @constructor */
function Password(username, password, loginurl, title, comment) {
  this.username = username;
  this.password = password;
  this.loginurl = loginurl;
  this.title = title;
  this.comment = comment;
  this.shouldAdd = true;
}

function whichPasswordsToAdd(){
  var passwordForMitro = [];
  var checkboxes = $(":checkbox");
  var i;
  for (i = 0; i < checkboxes.length; i++){
    if($(checkboxes[i]).prop('checked') === true) {
        passwordForMitro.push(i);
      }
  }
  return passwordForMitro;
}

function appendPasswordsToPage(pwds) {
  passwords = pwds;
  for (var i = 0; i < passwords.length; i++) {
    passwords[i].id = i;
  }
  var html = templates['import-template'].render({passwords: passwords});
  $('#password-list').html(html);

  $("#add-to-mitro-button").show();
}

var numberCompleted = 0;
/**
@param {Object=} destinationOrgAndGroup
*/
function addDataToMitro(destinationOrgAndGroup) {
  window.onbeforeunload = function() {
    return 'WARNING: Leaving this tab will cancel the import!';
  };

  // TODO: WTF is this? It should be using a list and popping stuff off of it.
  var passwordForMitro = whichPasswordsToAdd();

  if (numberCompleted < passwordForMitro.length){
    var index = passwordForMitro[numberCompleted];

    var title = passwords[index].title;

    var loginurl = passwords[index].loginurl;
    if (loginurl && loginurl.indexOf('http') !== 0) {
      loginurl = 'http://' + loginurl;
    }
    var username = passwords[index].username;
    var password = passwords[index].password;
    var comment = passwords[index].comment;
    mitro.importer.addSecret(title, loginurl, username, password, comment, destinationOrgAndGroup,
        uploadProgress, showErrorDialog);
  } else {
    $("#upload-progress").empty();
    $("#upload-progress").append("<h3>Upload Completed.</h3>");
    numberCompleted = 0;
    window.onbeforeunload = null;
  }
}

function uploadProgress(destinationOrgAndGroup) {
  $("#add-to-mitro-button").hide();
  $('#password-list').hide();
  numberCompleted++;
  $("#upload-progress").empty();
  $("#upload-progress").append("<h3>" + numberCompleted + "/" + passwords.length + " secrets added to Mitro. </h3>");
  addDataToMitro(destinationOrgAndGroup);
}

(function(){
'use strict';
mitro.importer = mitro.importer || {};

mitro.importer.addSecret = function(newTitle,newLoginUrl,newUsername,newPassword, newComment,
    destinationOrgAndGroup, onSuccess, onError) {
  var clientData = {
    type: "manual",
    title:newTitle
  };

  clientData.loginUrl = newLoginUrl;
  clientData.username = newUsername;
  clientData.comment = newComment;

  var secretData = {
    password: newPassword
  };

  var finalOnSuccess = function() {
    onSuccess(destinationOrgAndGroup);
  };

  if (destinationOrgAndGroup) {
    var data = new mitro.AddSecretToGroupsData();
    data.clientData = clientData;
    data.criticalData = secretData;
    // order must be private group, org group. If not org admin, the other order is not permitted
    data.groupIds = [destinationOrgAndGroup.groupId];
    if (destinationOrgAndGroup.organizationId !== null) {
      data.groupIds.push(destinationOrgAndGroup.organizationId);
    }
    background.addSecretToGroups(data, finalOnSuccess, onError);
  } else {
    background.addSecret({}, clientData, secretData, finalOnSuccess, onError);
  }
};

/**
@constructor
@struct
@param {number} orgId
@param {string} orgName
*/
var OrgGroupData = function(orgId, orgName) {
  this.orgId = orgId;
  this.orgName = orgName;
  /** @type {!Array.<!mitro.GroupInfo>} */
  this.groups = [];
};

/** @const */
var _PERSONAL_ORG_ID = 0;
/** @const */
var _PERSONAL_ORG_NAME = 'Personal';

/** @param {!mitro.UserData} userdata
@return {!Object.<number, !OrgGroupData>} */
var groupByOrganization = function(userdata) {
  /** @type {!Object.<number, !OrgGroupData>} */
  var organizationGroupMap = {};
  for (var orgIdString in userdata.organizationInfo.organizations) {
    var orgInfo = userdata.organizationInfo.organizations[parseInt(orgIdString, 10)];
    var groupData = new OrgGroupData(orgInfo.id, orgInfo.name);
    organizationGroupMap[groupData.orgId] = groupData;
  }
  var personalOrg = new OrgGroupData(_PERSONAL_ORG_ID, _PERSONAL_ORG_NAME);
  organizationGroupMap[_PERSONAL_ORG_ID] = personalOrg;

  // add the groups to the correct orgs
  for (var groupIdString in userdata.groups) {
    var group = userdata.groups[groupIdString];
    if (group.owningOrgId && group.isOrgPrivateGroup) {
      group.name = '(no team)';
    } else if (!isVisibleGroup(group)) {
      continue;
    }

    var organizationId = _PERSONAL_ORG_ID;
    if (group.owningOrgId) {
      organizationId = group.owningOrgId;
    }
    organizationGroupMap[organizationId].groups.push(group);
  }

  return organizationGroupMap;
};

mitro.importer.getGroupsByOrganization = function(background, onSuccess, onError) {
  // mitro.loadUserData()
  // background.listUsersGroupsAndSecrets(function (userdata) {
  mitro.loadUserData(function (userdata) {
    var groupMap = groupByOrganization(userdata);
    onSuccess(groupMap);
  }, onError);
};

/** @const */
var _SEPARATOR = ':';
var makeOptionValue = function(organizationId, groupId) {
  return organizationId + _SEPARATOR + groupId;
};

/** @const */
var _NO_GROUP_ID = 0;
/**
@param {HTMLSelectElement} selectElement
@return {?{organizationId:?number,groupId:number}}
*/
mitro.importer.getOrgAndGroupFromSelect = function (selectElement) {
  var selectedValue = selectElement.value;
  if (selectedValue === '') {
    return null;
  }
  var values = selectedValue.split(_SEPARATOR);
  var result = {
    organizationId: null,
    groupId: parseInt(values[1], 10)
  };
  if (values[0] !== _PERSONAL_ORG_ID.toString()) {
    result.organizationId = parseInt(values[0], 10);
  }
  return result;
};

mitro.importer.applyGroupsToSelect = function(selectElement, organizationGroupMap) {
  var currentOrgId = null;
  var orgElement = null;

  /**
  @param {!OrgGroupData} orgGroupData
  */
  var appendOrganizationOptGroup = function(orgGroupData) {
    var optgroup = document.createElement('optgroup');
    optgroup.label = orgGroupData.orgName;

    for (var i = 0; i < orgGroupData.groups.length; i++) {
      var group = orgGroupData.groups[i];

      var option = document.createElement('option');
      option.value = makeOptionValue(orgGroupData.orgId, group.groupId);
      option.label = group.name;

      optgroup.appendChild(option);
    }

    selectElement.appendChild(optgroup);
    return optgroup;
  };

  // Append all the organizations
  for (var organizationId in organizationGroupMap) {
    if (organizationId === _PERSONAL_ORG_ID.toString()) {
      continue;
    }
    appendOrganizationOptGroup(organizationGroupMap[organizationId]);
  }
  var lastOptGroup = appendOrganizationOptGroup(organizationGroupMap[_PERSONAL_ORG_ID]);
  // append a default "no group"
  var noOrg = document.createElement('option');
  noOrg.value = '';
  noOrg.label = '(no group)';
  noOrg.selected = true;
  lastOptGroup.appendChild(noOrg);
};

// Converts line endings in data to Unix line endings (\n). Allows Mac and Windows formatted
// CSV files to be imported correctly.
mitro.importer.convertLineEndingsToUnix = function(data) {
  // replace \r\n with \n
  // replace remaining standalone \r with \n
  // use regexp with /g flag to replace all (otherwise it stops at end of "line")
  data = data.replace(/\r\n/g, '\n');
  data = data.replace(/\r/g, '\n');
  return data;
};

// Attaches JQuery event handlers to the page
mitro.importer.attachEventHandlers = function(onFileSelected) {
  $('#toggle-checked').click(function() {
    var checkboxes = $('input[type=checkbox]');
    checkboxes.prop('checked', !checkboxes.prop('checked'));
  });

  var selectElement = /** @type{HTMLSelectElement} */ (document.getElementById('group-select'));
  mitro.importer.getGroupsByOrganization(background, function(groups) {
    mitro.importer.applyGroupsToSelect(selectElement, groups);
  }, function(error) {
    alert('Error loading groups ' + error);
  });

  if ($('#files').length > 0) {
    $('#files')[0].addEventListener('change', onFileSelected, false);
  }
  $("#add-to-mitro-button").click( function(){
    $("#add-to-mitro-button").hide();
    var destinationOrgAndGroup = mitro.importer.getOrgAndGroupFromSelect(selectElement);
    addDataToMitro(destinationOrgAndGroup);
  });
};

// Export everything for Node
if (typeof exports !== 'undefined') {
  for (var member in mitro.importer) {
    exports[member] = mitro.importer[member];
  }
}
})();
