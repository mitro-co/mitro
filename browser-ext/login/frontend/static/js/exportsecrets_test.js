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
var exportsecrets = exportsecrets || require('./exportsecrets');
/** @suppress {duplicate} */
var mocks = mocks || require('./mocks');
var mitro = mitro || require('./background_interface');

/** @suppress {duplicate} */
var background = background || null;

describe('convertToLastPassCSVArray', function() {
  it('converts empty', function() {
    var output = exportsecrets.convertToLastPassCSVArray([]);
    expect(output.length).toBe(1);
    expect(output[0]).toEqual(['url', 'username', 'password','extra', 'name', 'grouping','fav']);
  });

  it('converts data', function() {
    var secret = new exportsecrets.ExportedSecret();
    secret.title = 'title';
    secret.url = 'http://example.com/';
    secret.username = 'username';
    secret.password = 'password';

    var output = exportsecrets.convertToLastPassCSVArray([secret]);
    expect(output.length).toBe(2);
    expect(output[1]).toEqual([secret.url, secret.username, secret.password, '', secret.title, '', '0']);
  });

  it('converts null', function() {
    var secret = new exportsecrets.ExportedSecret();
    var output = exportsecrets.convertToLastPassCSVArray([secret]);
    expect(output.length).toBe(2);
    expect(output[1]).toEqual(['','','','','','','0']);
  });

  it('converts undefined', function() {
    var secret = new exportsecrets.ExportedSecret();
    secret.title = undefined;
    var output = exportsecrets.convertToLastPassCSVArray([secret]);
    expect(output.length).toBe(2);
    expect(output[1]).toEqual(['','','','','','','0']);
  });

  it('converts notes', function() {
    var secret = new exportsecrets.ExportedSecret();
    secret.note = 'i am a note';
    var output = exportsecrets.convertToLastPassCSVArray([secret]);
    expect(output.length).toBe(2);
    expect(output[1]).toEqual(['http://sn','','','i am a note','','Secure Notes','0']);
  });
});

describe('exportAllSecretsPromise', function() {
  var mockBackground;
  var promiseResult;
  var promiseError;

  beforeEach(function() {
    mockBackground = new mocks.MockBackground();
    exportsecrets.setBackgroundForTest(mockBackground);
  });

  var syncResolve = function(promise) {
    promiseResult = null;
    promiseError = null;
    promise.then(function(r) { promiseResult = r; });
    promise.fail(function(e) { promiseError = e; });
  };

  it('exports empty list', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    mockBackground.fetchServicesSuccess([]);
    expect(promiseResult).toBe(null);
    mockBackground.getOrganizationInfoSuccess({});
    expect(promiseResult).toEqual([]);
  });

  it('fetchServices error', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    mockBackground.fetchServicesError(new Error('error'));
    expect(promiseError.toString()).toBe('Error: error');
  });

  it('exports service with empty title', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service = new mitro.Secret();
    service.secretId = 99;
    service.clientData.username = 'username';
    service.clientData.title = '';
    mockBackground.fetchServicesSuccess([service]);
    mockBackground.getOrganizationInfoSuccess({});

    // fetch the critical data
    expect(mockBackground.getSiteId).toBe(service.secretId);
    service.criticalData.password = 'password';
    mockBackground.getSiteDataSuccess(service);

    expect(promiseResult.length).toBe(1);
    var data = promiseResult[0];
    expect(data.title).toBe('');
    expect(data.password).toBe(service.criticalData.password);
  });

  it('exports service with undefined title', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service = new mitro.Secret();
    service.secretId = 99;
    service.clientData.username = 'username';
    service.clientData.title = undefined;
    mockBackground.fetchServicesSuccess([service]);
    mockBackground.getOrganizationInfoSuccess({});

    // fetch the critical data
    expect(mockBackground.getSiteId).toBe(service.secretId);
    service.criticalData.password = 'password';
    mockBackground.getSiteDataSuccess(service);

    expect(promiseResult.length).toBe(1);
    var data = promiseResult[0];
    expect(data.title).toBe(undefined);
  });

  it('exports note', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service = new mitro.Secret();
    service.secretId = 99;
    service.clientData.type = 'note';
    mockBackground.fetchServicesSuccess([service]);
    mockBackground.getOrganizationInfoSuccess({});

    // fetch the critical data
    expect(mockBackground.getSiteId).toBe(service.secretId);
    service.criticalData.note = 'hello I am a bonus note';
    mockBackground.getSiteDataSuccess(service);

    expect(promiseResult.length).toBe(1);
    var data = promiseResult[0];
    expect(data.note).toBe(service.criticalData.note);
  });

  it('exports service with note', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service = new mitro.Secret();
    service.secretId = 99;
    service.clientData.comment = 'i am a note';
    mockBackground.fetchServicesSuccess([service]);
    mockBackground.getOrganizationInfoSuccess({});

    // fetch the critical data
    expect(mockBackground.getSiteId).toBe(service.secretId);
    service.criticalData.password = 'password';
    mockBackground.getSiteDataSuccess(service);

    expect(promiseResult.length).toBe(1);
    var data = promiseResult[0];
    expect(data.password).toBe(service.criticalData.password);
    expect(data.note).toBe(service.clientData.comment);
  });

  it('exports org service as member', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service = new mitro.Secret();
    service.secretId = 99;
    service.clientData.username = 'username';
    service.clientData.title = undefined;
    service.owningOrgId = 42;
    mockBackground.fetchServicesSuccess([service]);
    mockBackground.getOrganizationInfoSuccess({});

    expect(promiseResult.length).toBe(0);
  });

  var makeOrgServicesRequest = function() {
    var p = exportsecrets.fetchServicesForExport();
    syncResolve(p);

    var orgService = new mitro.Secret();
    orgService.secretId = 99;
    orgService.clientData.username = 'username';
    orgService.clientData.title = 'org secret';
    orgService.owningOrgId = 72;

    var personalService = new mitro.Secret();
    personalService.secretId = 100;
    personalService.clientData.username = 'username';
    personalService.clientData.title = '';
    mockBackground.fetchServicesSuccess([orgService, personalService]);
  };

  it('fetch services as org member', function() {
    makeOrgServicesRequest();

    // Trigger getorginfo but no org
    mockBackground.getOrganizationInfoSuccess({});
    expect(promiseResult.length).toBe(1);
    expect(promiseResult[0].secretId).toBe(100);
  });

  it('fetch services as org admin', function() {
    makeOrgServicesRequest();

    // Trigger getorginfo but no org
    var organizationInfo = new mitro.OrganizationInfoResponse();
    var organizationMetadata = new mitro.OrganizationMetadata();
    organizationMetadata.isAdmin = true;

    organizationInfo.myOrgId = 72;
    organizationInfo.organizations[72] = organizationMetadata;
    mockBackground.getOrganizationInfoSuccess(organizationInfo);

    expect(promiseResult.length).toBe(2);
    expect(promiseResult[0].secretId).toBe(99);
    expect(promiseResult[1].secretId).toBe(100);
  });

  it('only requests one secret at a time', function() {
    var p = exportsecrets.exportAllSecretsPromise();
    syncResolve(p);

    var service1 = new mitro.Secret();
    service1.secretId = 99;
    service1.clientData.username = 'username';
    service1.clientData.title = 'title1';
    var service2 = new mitro.Secret();
    service2.secretId = 72;
    service2.clientData.username = 'username2';
    service2.clientData.title = 'title2';
    mockBackground.fetchServicesSuccess([service1, service2]);
    mockBackground.getOrganizationInfoSuccess({});

    expect(mockBackground.getSiteId).toBe(service1.secretId);
    service1.criticalData.password = 'password';
    mockBackground.getSiteDataSuccess(service1);

    expect(mockBackground.getSiteId).toBe(service2.secretId);
    service2.criticalData.password = 'password';
    mockBackground.getSiteDataSuccess(service2);

    expect(promiseResult.length).toBe(2);
  });
});
