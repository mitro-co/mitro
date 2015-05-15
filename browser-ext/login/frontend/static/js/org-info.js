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
var mitro = mitro || {};
/** @suppress {duplicate} */
var kew = kew || require('../../../../api/js/cli/kew');
/** @suppress{duplicate} */
var dictValues = dictValues || require('../../../common/utils').dictValues;

/**
@constructor
@struct
@param {mitro.OrganizationInfoResponse} orgInfoResponse
*/
mitro.OrganizationInfo = function (orgInfoResponse) {
  this.selectedOrgId = orgInfoResponse.myOrgId;
  this.organizations = orgInfoResponse.organizations;
};

/** Returns organization specified by orgId or null if user does not belong to
organization.
@param {number} orgId
@return {?mitro.OrganizationMetadata}
*/
mitro.OrganizationInfo.prototype.getOrganization = function (orgId) {
  if (!this.organizations || !(orgId in this.organizations)) {
    return null;
  }
  return this.organizations[orgId];
};

/**
@return {!Array.<!mitro.OrganizationMetadata>}
*/
mitro.OrganizationInfo.prototype.getOrganizations = function () {
  if (this.organizations) {
    return dictValues(this.organizations);
  } else {
    return [];
  }
};

/** Returns the selected org.
@return {?mitro.OrganizationMetadata}
*/
mitro.OrganizationInfo.prototype.getSelectedOrganization = function () {
  if (this.selectedOrgId === null) {
    return null;
  }
  return this.getOrganization(this.selectedOrgId);
};

/** Check whether a user is an admin for a given org id.
@param {number} orgId
@return {boolean}
*/
mitro.OrganizationInfo.prototype.isOrgAdminFor = function (orgId) {
  var org = this.getOrganization(orgId);
  return org ? org.isAdmin : false;
};

mitro._orgInfo = null;
mitro._orgInfoPromises = [];

mitro.onLoadOrganizationInfoSuccess = function () {
  for (var i = 0; i < mitro._orgInfoPromises.length; ++i) {
    mitro._orgInfoPromises[i].resolve(mitro._orgInfo);
  }
  mitro._orgInfoPromises = [];
};

/**
@param {!Error} error
*/
mitro.onLoadOrganizationInfoError = function (error) {
   for (var i = 0; i < mitro._orgInfoPromises.length; ++i) {
     mitro._orgInfoPromises[i].reject(error);
   }
   mitro._orgInfoPromises = [];
};

/** Loads the organization info exactly once for the current js context.

If loadOrganizationInfo is called while another async load is in progress,
it will be queued and both callbacks will be fulfilled with the same return
value when the async operation completes.

Likewise, if multiple callbacks are queued and the operation fails, each error
callbacks will be called.
WARNING: This can lead to duplicate reporting of errors.

@param {function(!mitro.OrganizationInfo)} onSuccess
@param {function(!Error)} onError
*/
mitro.loadOrganizationInfo = function (onSuccess, onError) {
  if (mitro._orgInfo === null) {
    var promise = new kew.Promise();
    promise.then(onSuccess).fail(onError);
    mitro._orgInfoPromises.push(promise);

    if (mitro._orgInfoPromises.length === 1) {
      background.getOrganizationInfo(function (orgInfoResponse) {
        var error = null;
        try {
          mitro._orgInfo = new mitro.OrganizationInfo(orgInfoResponse);
        } catch (e) {
          error = e;
        }
        if (error) {
          mitro.onLoadOrganizationInfoError(error);
        } else {
          mitro.onLoadOrganizationInfoSuccess();
        }
      }, mitro.onLoadOrganizationInfoError);
    }
  } else {
    onSuccess(mitro._orgInfo);
  }
};

// TODO: Refactor code to not rely on global variables. Org info should probably create
// an object so its state can be correctly re-initialized in unit tests.
mitro.hackOrgInfoBackgroundForTest = function(bg) {
  background = bg;
  mitro._orgInfo = null;
  mitro._orgInfoPromises = [];
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = mitro;
}
