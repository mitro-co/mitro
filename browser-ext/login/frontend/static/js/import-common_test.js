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
var mitro = mitro || require('./background_interface.js');
/** @suppress{duplicate} */
var mocks = mocks || require('./mocks.js');
mitro.importer = mitro.importer || require('./import-common.js');
// HACK to merge userdata in Node
(function(){
  if (!('UserData' in mitro)) {
    var mitro2 = require('./userdata.js');
    for (var p in mitro2) {
      mitro[p] = mitro2[p];
    }
  }
})();

var _fakeGroupId = 5000;
/** @constructor */
var FakeGroup = function(groupName, owningOrgId, owningOrgName) {
  this.groupId = _fakeGroupId++;
  this.name = groupName;
  if (owningOrgId) {
    expect(owningOrgId).toBeGreaterThan(0);
    expect(owningOrgName.length).not.toBe(0);
    this.owningOrgId = owningOrgId;
    this.owningOrgName = owningOrgName;
  }
};

describe('importer', function() {
  var result = null;
  var fakeBg = null;

  var onSuccess = function(r) {
    expect(result).toBe(null);
    expect(r).not.toBe(null);
    result = r;
  };

  beforeEach(function() {
    result = null;
    fakeBg = new mocks.MockBackground();
    mitro.hackBackgroundForTest(fakeBg);
  });

  it('groups by organization', function() {
    mitro.importer.getGroupsByOrganization(fakeBg, onSuccess, null);

    var usersGroupsSecrets = new mitro.UsersGroupsSecrets();
    usersGroupsSecrets.groups = [
      new FakeGroup("group 1", 42, "org"),
      new FakeGroup("group 2", null, null),
      new FakeGroup("group 3", 42, "org")
    ];
    fakeBg.listUsersGroupsAndSecretsSuccess(usersGroupsSecrets);
    var orgData = new mitro.OrganizationMetadata();
    orgData.id = 42;
    orgData.name = 'org';
    var orgInfo = new mitro.OrganizationInfoResponse();
    orgInfo.organizations[42] = orgData;
    fakeBg.getOrganizationInfoSuccess(orgInfo);

    expect(Object.keys(result).sort()).toEqual(['42', '0'].sort());
    expect(result[42].groups).toEqual([usersGroupsSecrets.groups[0], usersGroupsSecrets.groups[2]]);
    expect(result[0].groups).toEqual([usersGroupsSecrets.groups[1]]);

    // no groups at all!
    usersGroupsSecrets.groups = [];
    result = null;
    mitro.importer.getGroupsByOrganization(fakeBg, onSuccess, null);
    fakeBg.listUsersGroupsAndSecretsSuccess(usersGroupsSecrets);
    fakeBg.getOrganizationInfoSuccess(orgInfo);
    // TODO: Remove next line for Closure bug https://github.com/google/closure-compiler/issues/444
    result = result || {};
    expect(Object.keys(result).sort()).toEqual(['42', '0'].sort());
    expect(result[0].groups.length).toBe(0);
    expect(result[42].groups.length).toBe(0);
  });

  it('fixes line endings', function() {
    var macEndings = "url,username\rhttp://example.com,useruser\rhttps://example2.com,user";
    var out = mitro.importer.convertLineEndingsToUnix(macEndings);
    expect(out.indexOf('\r')).toBe(-1);

    var windowsEndings = "url,username\r\nhttp://example.com,useruser\r\nhttps://example2.com,user";
    out = mitro.importer.convertLineEndingsToUnix(windowsEndings);
    expect(out.indexOf('\r\n')).toBe(-1);

    var unixEndings = "url,username\nhttp://example.com,useruser\nhttps://example2.com,user";
    out = mitro.importer.convertLineEndingsToUnix(windowsEndings);
    expect(out).toBe(unixEndings);
  });

  it('adds secret to group then organization', function() {
    var orgAndGroup = {organizationId: 123, groupId: 456};
    mitro.importer.addSecret('title', 'login url', 'username', 'password', 'comment', orgAndGroup, onSuccess, null);
    // order is critical: private group first, then organization
    expect(fakeBg.addSecretData.groupIds).toEqual([456, 123]);
    var expectedResult = {foo: 22};
    expect(result).toBe(null);
    fakeBg.addSecretSuccess(null);
    expect(result).toBe(orgAndGroup);
  });

  it('adds secret to personal group', function() {
    var orgAndGroup = {organizationId: null, groupId: 456};
    mitro.importer.addSecret('title', 'login url', 'username', 'password', 'comment', orgAndGroup, onSuccess, null);
    expect(fakeBg.addSecretData.groupIds).toEqual([456]);
    var expectedResult = {foo: 22};
    expect(result).toBe(null);
    fakeBg.addSecretSuccess(null);
    expect(result).toBe(orgAndGroup);
  });
});
