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
var mocks = mocks || require('./mocks');
var mitro = mitro || require('./userdata');
// hack to combine background_interface in Node
if (!mitro.Secret) {
  var m = require('./background_interface');
  for (var p in m) {
    mitro[p] = m[p];
  }
}

/**
@param {!*} arg
*/
var failIfCalled = function(arg) {
  expect(false).toBe('failure callback called: ' + arg);
};


/**
@constructor
@struct
*/
var CommonTestData = function() {
  this.mockBackground = new mocks.MockBackground();
  this.result = null;
  this.error = null;

  this.secret = new mitro.Secret();
  this.secret.secretId = 99;
  this.secret.groups.push(5);
  this.secret.groups.push(6);

  // create an example group; secret belongs to this group
  this.group = new mitro.GroupInfo();
  this.group.groupId = 100;
  this.secret.groups.push(this.group.groupId);

  this.groupsMap = {};
  this.groupsMap[this.group.groupId] = this.group;

  this.usersGroupsAndSecretsResponse = {
    users: ['user@example.com'],
    groups: this.groupsMap,
    secrets: [this.secret]
  };
};

CommonTestData.prototype.startLoad = function () {
  mitro.hackBackgroundForTest(this.mockBackground);
  this.result = null;
  this.error = null;

  // BS required for JS's weird this scoping rules
  var testData = this;
  mitro.loadUserData(function (result) {
    testData.result = result;
  }, function (error) {
    testData.error = error;
  });
};

CommonTestData.prototype.startLoadAndGetBasicData = function () {
  this.startLoad();
  this.mockBackground.listUsersGroupsAndSecretsSuccess(this.usersGroupsAndSecretsResponse);
};

CommonTestData.prototype.startGetOrganization = function (orgId) {
  var testData = this;

  mitro.loadOrganization(testData.result, orgId, function (result) {
  }, function (error) {
    testData.error = error;
  });
};

CommonTestData.prototype.assertSecretsAndGroups = function() {
  expect(1).toBe(this.result.secrets.length);
  expect(this.secret.secretId).toBe(this.result.secrets[0].secretId);
  expect(this.group).toBe(this.result.groups[this.group.groupId]);
};

describe('userdata', function() {
  it('LoadUserDataNoOrg', function() {
    var testData = new CommonTestData();
    testData.startLoadAndGetBasicData();

    // when no organization, the background returns {myOrgId: null}
    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = null;
    testData.mockBackground.getOrganizationInfoSuccess(organizationInfoResponse);
    testData.assertSecretsAndGroups();

    var organizationInfo = new mitro.OrganizationInfo(organizationInfoResponse);
    expect(organizationInfo).toEqual(testData.result.organizationInfo);
    expect(testData.result.organization).toBeNull();

    expect(testData.group).toBe(testData.result.getGroup(testData.group.groupId));
    expect(testData.result.getGroup(1)).toBeNull();
    expect(testData.result.getGroup(0)).toBeNull();

    expect(testData.result.getAllOrganizationInfo().getSelectedOrganization()).toBeNull();

    expect(testData.secret).toBe(testData.result.getSecret(testData.secret.secretId));
    expect(testData.result.getSecret(10001)).toBeNull();

    expect(testData.result.getSecretsForGroup(9000)).toEqual([]);
    expect(testData.result.getSecretsForGroup(testData.group.groupId)).toEqual([testData.secret]);

    // this user doesn't belong to any orgs
    expect(testData.result.userBelongsToSameOrgAsGroup(testData.group.groupId)).toBe(false);
    // even if the group does
    testData.group.owningOrgId = 42;
    expect(testData.result.userBelongsToSameOrgAsGroup(testData.group.groupId)).toBe(false);

    expect(testData.result.getSelectedOrganizationSecrets()).toEqual([]);

    // no orgs
    expect(testData.result.userBelongsToSameOrgAsSecret(testData.secret.secretId)).toBe(false);
    testData.secret.owningOrgId = 42;
    expect(testData.result.userBelongsToSameOrgAsSecret(testData.secret.secretId)).toBe(false);
  });

  it('LoadUserDataAsOrgAdmin', function() {
    var testData = new CommonTestData();
    testData.startLoadAndGetBasicData();

    // returns data WITH an organization (id 0)
    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = 0;
    var organizationMetadata = new mitro.OrganizationMetadata();
    organizationMetadata.id = 0;
    organizationMetadata.isAdmin = true;
    organizationInfoResponse.organizations[0] = organizationMetadata;
    testData.mockBackground.getOrganizationInfoSuccess(organizationInfoResponse);

    var organization = new mitro.Organization();
    var orgGroup = new mitro.GroupInfo();
    orgGroup.groupId = 0;
    organization.groups[orgGroup.groupId] = orgGroup;
    var orgGroup2 = new mitro.GroupInfo();
    orgGroup2.groupId = 99;
    organization.groups[orgGroup2.groupId] = orgGroup2;
    testData.startGetOrganization(organizationMetadata.id);
    testData.mockBackground.getOrganizationSuccess(organization);

    testData.assertSecretsAndGroups();
    expect(testData.result.organizationInfo.getSelectedOrganization().id).toBe(0);
    expect(Object.keys(testData.result.organization.groups).length).toBe(2);

    expect(testData.result.getGroup(orgGroup.groupId)).toBe(orgGroup);
    expect(testData.result.getGroup(orgGroup2.groupId)).toBe(orgGroup2);
    expect(testData.result.getGroup(testData.group.groupId)).toBe(testData.group);
    expect(testData.result.getGroup(1)).toBeNull();

    expect(testData.result.getAllOrganizationInfo()).toBe(testData.result.organizationInfo);
    expect(testData.result.getOrganizations().length).toBe(1);
    expect(testData.result.getOrganizations()[0].id).toBe(0);

    // this group doesn't belong to any orgs
    expect(testData.result.userBelongsToSameOrgAsGroup(testData.group.groupId)).toBe(false);
    // now the group belongs to the wrong org
    testData.group.owningOrgId = 1000;
    expect(testData.result.userBelongsToSameOrgAsGroup(testData.group.groupId)).toBe(false);
    // now it belongs to the right org
    testData.group.owningOrgId = organizationInfoResponse.myOrgId;
    expect(testData.result.userBelongsToSameOrgAsGroup(testData.group.groupId)).toBe(true);

    // no secrets in this org
    expect(testData.result.getSelectedOrganizationSecrets()).toEqual([]);
    expect(testData.result.userBelongsToSameOrgAsSecret(testData.secret.secretId)).toBe(false);

    // put the secret in the org
    organization.orgSecretsToPath[testData.secret.secretId] = testData.secret;
    testData.secret.owningOrgId = organizationInfoResponse.myOrgId;
    expect(testData.result.getSelectedOrganizationSecrets()).toEqual([testData.secret]);
    expect(testData.result.userBelongsToSameOrgAsSecret(testData.secret.secretId)).toBe(true);
  });

  it('LoadUserDataAsOrgMember', function() {
    var testData = new CommonTestData();
    testData.startLoadAndGetBasicData();

    // returns data WITH an organization (id 0), but as a regular user
    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = 0;
    var organizationMetadata = new mitro.OrganizationMetadata();
    organizationMetadata.id = 0;
    organizationMetadata.isAdmin = false;
    organizationInfoResponse.organizations[0] = organizationMetadata;
    testData.mockBackground.getOrganizationInfoSuccess(organizationInfoResponse);
    testData.startGetOrganization(organizationMetadata.id);

    // as a regular user, this returns limited info (no secrets, no users in groups)
    var organization = new mitro.Organization();
    var orgGroup = new mitro.GroupInfo();
    orgGroup.groupId = testData.group.groupId;
    orgGroup.users = null;
    organization.groups[orgGroup.groupId] = orgGroup;
    organization.orgSecretsToPath = null;
    testData.mockBackground.getOrganizationSuccess(organization);

    // getGroup should return the "complete" group from ListMySecretsAndGroups
    expect(testData.result.getGroup(testData.group.groupId)).toBe(testData.group);

    // getSelectedOrganizationSecrets should return the user's secrets
    expect(testData.result.getSelectedOrganizationSecrets()).toEqual([]);
    testData.secret.owningOrgId = organizationInfoResponse.myOrgId;
    expect(testData.result.getSelectedOrganizationSecrets()).toEqual([testData.secret]);
  });

  it('LoadErrorHandling', /** @suppress{checkTypes} to test passing bad types */ function() {
    var testData = new CommonTestData();

    // calling onError at any point causes the error callback to get called
    testData.startLoad();
    testData.mockBackground.listUsersGroupsAndSecretsError(new Error('load error'));
    expect(testData.result).toBeNull();
    expect(testData.error.message).toBe('load error');

    testData.startLoad();
    testData.mockBackground.getOrganizationInfoError(new Error('org error'));
    expect(testData.result).toBeNull();
    expect(testData.error.message).toBe('org error');

    testData.startLoad();
    // call back with bad organization information: causes exception in the onSuccess handler
    testData.mockBackground.getOrganizationInfoSuccess(null);
    expect(testData.result).toBeNull();
    expect(testData.error.message).toBe("Cannot read property 'myOrgId' of null");

    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = 99;
    var organizationMetadata = new mitro.OrganizationMetadata();
    organizationMetadata.id = 99;
    organizationMetadata.isAdmin = true;
    organizationInfoResponse.organizations[99] = organizationMetadata;

    testData.startGetOrganization(organizationMetadata.id);
    testData.mockBackground.getOrganizationError(new Error('org error'));
    expect(testData.error.message).toBe('org error');

    // test the promise version
    var mockBackground = new mocks.MockBackground();
    background = mockBackground;
    var promise = mitro.loadUserDataPromise();
    testData.error = null;
    promise.fail(function (e) { testData.error = e; });
    expect(testData.error).toBeNull();
    mockBackground.listUsersGroupsAndSecretsError(new Error('promise'));
    expect(testData.error.message).toBe('promise');
  });

  var assertThrows = function(fn) {
    var exception = null;
    try {
      fn();
    } catch (e) {
      exception = e;
    }
    expect(exception).not.toBeNull();
    return exception;
  };

  // Explicitly test passing non-number parameters; must disable type-checks
  it('TypeAsserts', /** @suppress {checkTypes} */ function() {
    var userData = new mitro.UserData();

    userData.groups = [];
    var e = assertThrows(function() { userData.getGroup('0'); });
    expect(e.message).toBe('argument must be a number');

    e = assertThrows(function() { userData.getSecret('0'); });
    expect(e.message).toBe('argument must be a number');

    e = assertThrows(function() { userData.userBelongsToSameOrgAsGroup('0'); });
    expect(e.message).toBe('argument must be a number');
  });

  it('GetCompleteSecretPromise', function() {
    var mockBackground = new mocks.MockBackground();
    background = mockBackground;

    var promise = mitro.getCompleteSecretPromise(99);
    expect(mockBackground.getSiteId).toBe(99);
    var result = null;
    promise.then(function (r) { result = r; }).done();
    var fakeSiteData = {fakeData: 42};
    mockBackground.getSiteDataSuccess(fakeSiteData);
    expect(result).toBe(fakeSiteData);
  });

  it('LoadUserDataAndSecret', function() {
    // clear any errors stored in org-info
    // TODO: Remove this global state in org-info!
    mitro.onLoadOrganizationInfoError(new Error());

    var userdata = null;
    var secret = null;
    var onSuccess = function(r1, r2) {
      userdata = r1;
      secret = r2;
    };
    var testData = new CommonTestData();

    mitro.hackBackgroundForTest(testData.mockBackground);
    mitro.loadUserDataAndSecret(42, onSuccess, failIfCalled);
    expect(testData.mockBackground.getSiteId).toBe(42);
    testData.mockBackground.listUsersGroupsAndSecretsSuccess(testData.usersGroupsAndSecretsResponse);

    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = null;
    testData.mockBackground.getOrganizationInfoSuccess(organizationInfoResponse);

    var secretResponse = new mitro.Secret();
    testData.mockBackground.getSiteDataSuccess(secretResponse);

    // this must return the secret from getSiteData, not the one from ListMySecretsAndGroups
    expect(userdata.orgId).toBeNull();
    expect(secretResponse).toBe(secret);
  });

  it('loadUserDataAndGroup loads group from organization', function() {
    // clear any errors stored in org-info
    // TODO: Remove this global state in org-info!
    mitro.onLoadOrganizationInfoError(new Error());

    var userData = null;
    var group = null;
    var onSuccess = function(r1, r2) {
      userData = r1;
      group = r2;
    };
    var testData = new CommonTestData();

    mitro.hackBackgroundForTest(testData.mockBackground);
    // get group 0, contained in the organization
    mitro.loadUserDataAndGroup(0, onSuccess, failIfCalled);
    testData.mockBackground.listUsersGroupsAndSecretsSuccess(testData.usersGroupsAndSecretsResponse);

    // returns data WITH an organization (id 0)
    var organizationInfoResponse = new mitro.OrganizationInfoResponse();
    organizationInfoResponse.myOrgId = 0;
    var organizationMetadata = new mitro.OrganizationMetadata();
    organizationMetadata.id = 0;
    organizationMetadata.isAdmin = true;
    organizationInfoResponse.organizations[0] = organizationMetadata;
    testData.mockBackground.getOrganizationInfoSuccess(organizationInfoResponse);

    var organization = new mitro.Organization();
    var orgGroup = new mitro.GroupInfo();
    orgGroup.groupId = 0;
    organization.groups[orgGroup.groupId] = orgGroup;
    var orgGroup2 = new mitro.GroupInfo();
    orgGroup2.groupId = 99;
    organization.groups[orgGroup2.groupId] = orgGroup2;

    var orgSecret = new mitro.Secret();
    orgSecret.secretId = 1057;
    orgSecret.groups.push(0);
    // hack testData.secret to be part of group 0 (both personal and organization)
    organization.orgSecretsToPath[testData.secret.secretId] = testData.secret;
    organization.orgSecretsToPath[orgSecret.secretId] = orgSecret;
    testData.secret.groups.push(0);
    testData.mockBackground.getOrganizationSuccess(organization);

    expect(userData).not.toBeNull();
    expect(group).toBe(orgGroup);

    // getSecretsForGroup must de-dupe
    var secrets = userData.getSecretsForGroup(0);
    expect(secrets).toEqual([testData.secret, orgSecret]);
  });
});
