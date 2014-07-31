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

// Closure compiler definition of the background type
/** @suppress{duplicate} */
var mitro = mitro || {};

/**
@constructor
@struct
*/
mitro.ServiceClientData = function() {
  this.loginUrl = "";
  /** @type {?string|undefined} */
  this.title = "";
  this.type = "";
  this.username = "";
  /** @type {?string} */
  this.comment = null;
};

/**
@constructor
@struct
*/
mitro.ServiceCriticalData = function() {
  /** @type {?string} */
  this.note = null;
  /** @type {?string} */
  this.password = null;
};

/**
@constructor
@struct
*/
mitro.ServiceHintsData = function() {
  /** @type {!Array.<string>} */
  this.icons = [];
  this.title = "";
};

/**
@constructor
@struct
*/
mitro.Secret = function() {
  this.clientData = new mitro.ServiceClientData();
  this.criticalData = new mitro.ServiceCriticalData();
  /** @type {!Array.<number>} */
  this.groups = [];
  /** @type {!Array.<!mitro.ServiceHintsData>} */
  this.hints = [];
  this.secretId = 0;
  /** @type {!Array.<string>} */
  this.users = [];
  /** @type {?number} */
  this.owningOrgId = null;

  // This actually belongs to SecretToPath in Java; we use Secret in JS instead
  // TODO: Unify the types in Java as well?
  /** @type {Array.<number>} */
  this.groupIdPath = null;
};

/**
@constructor
@struct
*/
mitro.GroupInfo = function() {
  this.autoDelete = false;
  this.encryptedPrivateKey = "";
  this.groupId = 0;
  this.isNonOrgPrivateGroup = false;
  this.isOrgPrivateGroup = false;
  this.isRequestorAnOrgAdmin = false;
  this.isTopLevelOrg = false;
  this.name = "";
  /** @type {Array.<string>} */
  this.users = [];
  /** @type {?number} */
  this.owningOrgId = null;
};

/**
@constructor
@struct
*/
mitro.OrganizationMetadata = function() {
  this.id = 0;
  this.name = '';
  this.isAdmin = false;
  this.privateOrgGroupId = 0;
};

/** The background will set myOrgId as null in getOrgInfo() if the user has no orgs.
@constructor
@struct
*/
mitro.OrganizationInfoResponse = function() {
  /** @type {?number} */
  this.myOrgId = 0;
  /** @type {!Object.<number, !mitro.OrganizationMetadata>} */
  this.organizations = {};
};

/**
@constructor
@struct
*/
mitro.Organization = function() {
  /** @type {!Object.<number, !mitro.GroupInfo>} */
  this.groups = {};
  // Value is SecretToPath in Java; the types are unified in JS (see mitro.Secret)
  /** @type {Object.<number, !mitro.Secret>} */
  this.orgSecretsToPath = {};
};

/**
@constructor
@struct
*/
mitro.UsersGroupsSecrets = function() {
  /** @type {!Array.<string>} */
  this.users = [];
  /** @type {!Array.<!mitro.Secret>} */
  this.secrets = [];
  /** Map from groupId to group.
  @type {!Object.<number, !mitro.GroupInfo>} */
  this.groups = {};
};

/**
@constructor
@struct
*/
mitro.AddSecretToGroupsData = function() {
  this.clientData = '';
  this.criticalData = '';
  /** @type {!Array.<number>} */
  this.groupIds = [];
};

/** @interface */
mitro.BackgroundApi = function() {};
/**
@param {function(!mitro.UsersGroupsSecrets)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.listUsersGroupsAndSecrets = function(onSuccess, onError) {};
/** myOrgId will be null if the user has no orgs. See getOrgInfo().
@param {function(!mitro.OrganizationInfoResponse)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.getOrganizationInfo = function(onSuccess, onError) {};
/**
@param {number} orgId
@param {function(!mitro.Organization)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.getOrganization = function(orgId, onSuccess, onError) {};
/**
@param {number} secretId
@param {function(!mitro.Secret)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.getSiteData = function(secretId, onSuccess, onError) {};
/**
@param {number} secretId
@param {function(!mitro.Secret)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.getSiteSecretDataForDisplay = function(secretId, onSuccess, onError) {};
/**
@param {function(!Array.<!mitro.Secret>)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.fetchServices = function(onSuccess, onError) {};
/**
@param {!mitro.AddSecretToGroupsData} data
@param {function(number)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.addSecretToGroups = function(data, onSuccess, onError) {};
/** Defined in client.js
@param {!Object} serverData
@param {!Object} clientData
@param {!Object} secretData
@param {function(number)} onSuccess
@param {function(!Error)} onError
*/
mitro.BackgroundApi.prototype.addSecret = function (serverData, clientData, secretData, onSuccess, onError) {};

/** Actually declared in background-init.js
@type {mitro.BackgroundApi}
@suppress {duplicate}
*/
var background;

if (typeof module !== 'undefined' && module.exports) {
  module.exports = mitro;
}
