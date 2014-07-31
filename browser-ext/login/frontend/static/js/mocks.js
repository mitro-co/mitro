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

var mocks = {};
(function(){
  /**
  @implements mitro.BackgroundApi
  @constructor
  */
  mocks.MockBackground = function() {
  };
  mocks.MockBackground.prototype.listUsersGroupsAndSecrets = function(onSuccess, onError) {
    this.listUsersGroupsAndSecretsSuccess = onSuccess;
    this.listUsersGroupsAndSecretsError = onError;
  };
  mocks.MockBackground.prototype.getOrganizationInfo = function(onSuccess, onError) {
    /** @type {function(!mitro.OrganizationInfoResponse)} onSuccess */
    this.getOrganizationInfoSuccess = onSuccess;
    this.getOrganizationInfoError = onError;
  };
  mocks.MockBackground.prototype.getOrganization = function(orgId, onSuccess, onError) {
    this.getOrgId = orgId;
    this.getOrganizationSuccess = onSuccess;
    this.getOrganizationError = onError;
  };
  mocks.MockBackground.prototype.getSiteData = function(secretId, onSuccess, onError) {
    this.getSiteId = secretId;
    this.getSiteDataSuccess = onSuccess;
    this.getSiteDataError = onError;
  };
  mocks.MockBackground.prototype.getSiteSecretDataForDisplay = function(secretId, onSuccess, onError) {
    this.getSiteData(secretId, onSuccess, onError);
  };
  mocks.MockBackground.prototype.fetchServices = function(onSuccess, onError) {
    this.fetchServicesSuccess = onSuccess;
    this.fetchServicesError = onError;
  };
  mocks.MockBackground.prototype.addSecretToGroups = function(data, onSuccess, onError) {
    this.addSecretData = data;
    this.addSecretSuccess = onSuccess;
    this.addSecretError = onError;
  };
  mocks.MockBackground.prototype.addSecret = function (serverData, clientData, secretData, onSuccess, onError) {
    throw new Error('unimplemented');
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = mocks;
  }
})();
