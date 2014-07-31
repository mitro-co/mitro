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
var background = background || null;
/** @suppress {duplicate} */
var kew = kew || require('../../../../api/js/cli/kew');

var exportsecrets = {};
(function() {
  'use strict';

  /**
  @struct
  @constructor
  */
  exportsecrets.ExportedSecret = function() {
    /** @type {?string|undefined} */
    this.username = null;
    /** @type {?string|undefined} */
    this.password = null;
    /** @type {?string|undefined} */
    this.note = null;
    /** @type {?string|undefined} */
    this.url = null;
    /** @type {?string|undefined} */
    this.title = null;
  };

  /**
  @param {?string|undefined} s
  @return {string}
  */
  var valueOrEmpty = function(s) {
    if (s === null || s === undefined) {
      return '';
    }
    return s;
  };

  /**
  @param {!Array.<!exportsecrets.ExportedSecret>} exportedSecrets
  @return {!Array.<!Array.<string>>}
  */
  exportsecrets.convertToLastPassCSVArray = function(exportedSecrets) {
    var rows = [];
    rows.push(['url', 'username', 'password', 'extra', 'name', 'grouping', 'fav']);
    for (var i = 0; i < exportedSecrets.length; i++) {
      var secret = exportedSecrets[i];
      var row = [];

      var grouping = '';
      if (secret.note && !secret.url) {
        // last pass uses this url to indicate a note
        secret.url = 'http://sn';
        grouping = 'Secure Notes';
      }

      row.push(valueOrEmpty(secret.url));
      row.push(valueOrEmpty(secret.username));
      row.push(valueOrEmpty(secret.password));
      row.push(valueOrEmpty(secret.note));
      row.push(valueOrEmpty(secret.title));
      row.push(grouping);
      row.push('0');
      rows.push(row);
    }
    return rows;
  };

  /**
  @param {number} secretId
  @return {!kew.Promise.<!exportsecrets.ExportedSecret>}
  */
  var exportCriticalAsPromise = function(secretId) {
    var promise = new kew.Promise();
    background.getSiteSecretDataForDisplay(secretId, function(siteData) {
      var note = null;
      if (siteData.criticalData.note) {
        note = siteData.criticalData.note;
      } else {
        note = siteData.clientData.comment;
      }

      var exported = new exportsecrets.ExportedSecret();
      exported.username = siteData.clientData.username;
      exported.password = siteData.criticalData.password;
      exported.note = note;
      exported.url = siteData.clientData.loginUrl;
      exported.title = siteData.clientData.title;

      promise.resolve(exported);
    }, function(e) { promise.reject(e); });
    return promise;
  };

  /** @return {!kew.Promise.<!Array>} */
  var fetchServicesPromise = function() {
    var promise = new kew.Promise();
    background.fetchServices(function(serviceInstances) {
      promise.resolve(serviceInstances);
    }, function(e) {
      promise.reject(e);
    });
    return promise;
  };

  /** Returns an Object mapping orgId -> true if user is an admin for orgId.
  @return {!kew.Promise.<Object.<number, boolean>>}
  */
  var getOrgAdminSet = function() {
    var promise = new kew.Promise();
    background.getOrganizationInfo(function (orgInfo) {
      var orgAdminSet = {};
      for (var orgId in orgInfo.organizations) {
        if (orgInfo.organizations[parseInt(orgId, 10)]) {
          orgAdminSet[orgId] = true;
        }
      }
      promise.resolve(orgAdminSet);
    }, function(e) {
      promise.reject(e);
    });
    return promise;
  };

  /** For the exporter, we only show:
  - Personal secrets
  - Org secrets if the user is an org administrator.

  This filters the secrets as appropriate.

  @return {!kew.Promise.<!Array>} */
  exportsecrets.fetchServicesForExport = function() {
    var servicesPromise = fetchServicesPromise();
    var orgAdminSetPromise = getOrgAdminSet();

    var both = kew.all([servicesPromise, orgAdminSetPromise]);
    return both.then(function(r) {
      var services = r[0];
      var orgAdminSet = r[1];

      var output = [];
      for (var i = 0; i < services.length; i++) {
        var service = services[i];
        if (service.owningOrgId) {
          // org secret: only add if we are an admin for this org
          if (orgAdminSet[service.owningOrgId]) {
            output.push(service);
          }
        } else {
          // personal secret: add it
          output.push(service);
        }
      }

      return output;
    });
  };

  /** Returns a function that exports critical data for secretId, and appends it to the array
  passed in.
  @param {number} secretId
  @return {function(!Array.<Object>):!kew.Promise}
  */
  var exportAndAppendFunction = function(secretId) {
    return function (allPasswords) {
      var exportPromise = exportCriticalAsPromise(secretId);
      return exportPromise.then(function (secretWithCritical) {
        allPasswords.push(secretWithCritical);
        return allPasswords;
      });
    };
  };

  /** @return {!kew.Promise.<!Array.<!exportsecrets.ExportedSecret>>} */
  exportsecrets.exportAllSecretsPromise = function() {
    var fetchServices = exportsecrets.fetchServicesForExport();
    var exportAllPromise = fetchServices.then(function(serviceInstances) {
      var exportFunctions = [];

      // Create a chain of promises that append to allPasswords
      var chainStart = new kew.Promise();
      var promiseChain = chainStart;
      for (var i = 0; i < serviceInstances.length; i++) {
        // Bind the loop variable by calling as separate function
        var fn = exportAndAppendFunction(serviceInstances[i].secretId);
        promiseChain = promiseChain.then(fn);
      }
      // start the chain
      var allPasswords = [];
      chainStart.resolve(allPasswords);
      return promiseChain;
    });
    return exportAllPromise;
  };

  exportsecrets.setBackgroundForTest = function(bg) {
    background = bg;
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = exportsecrets;
  }
})();
