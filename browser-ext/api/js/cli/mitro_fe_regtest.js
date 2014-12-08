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

var assert = require('assert');

var rpc = require('./rpc');
var fe = require('./mitro_fe');
var lib = require('./mitro_lib');
var log = require('./logging');

var host = 'localhost';
var port = 8443;
console.log('capturing logs ...');
log.stopCapturingLogsToBuffer();
//log.captureLogsToBuffer();
fe.setDeviceId('idForTest');
function failIfCalled(args) {
  log.stopCapturingLogsToBuffer();
  console.log(args);
  if (args && args.local_exception) {
    console.log(args.local_exception.stack);
  }

  console.log('most recent log lines:');
  console.log('>>>>>' + log.logBuffer.toArray().join('\n>>>>>'));

  throw new Error('unexpected error:' + JSON.stringify(args, null, 4));

}

function testLogin(secretId) {
  console.log('############ testing login');
  fe.workerLogin('hello@example.com', 'foopassword', host, port, null, function (identity) {
    validateIdentity(identity, function() {
      // add a new site and list it
      fe.workerInvokeOnIdentity(identity, 'addSite', 'http://foo2.com/', 'user', 'pass', 'user_field', 'pass_field', function(secretId) {
        fe.workerInvokeOnIdentity(identity, 'listSites', function(sites) {
          assert.equal(2, sites.length);
          var exampleIndex = 0;
          var fooIndex = 1;
          if ('http://example.com/' != sites[0].clientData.loginUrl) {
            exampleIndex = 1;
            fooIndex = 0;
          }

          assert.equal('username', sites[exampleIndex].clientData.username);
          assert.equal('http://foo2.com/', sites[fooIndex].clientData.loginUrl);
          assert.equal('user', sites[fooIndex].clientData.username);

          console.log('SUCCESS');
        }, failIfCalled);
      }, failIfCalled);
    }, secretId);
  }, failIfCalled);
}

function validateIdentity(identity, onSuccess, secretId) {
  console.log('############ listing sites 1');

  fe.workerInvokeOnIdentity(identity, 'listSites', function(sites) {
    assert.equal(1, sites.length);
    assert.equal('http://example.com/', sites[0].clientData.loginUrl);
    assert.equal(sites[0].secretId, secretId);
    assert(!sites[0].password);

  console.log('############ getting secret data 1');
    fe.workerInvokeOnIdentity(identity, 'getSiteSecretData', sites[0].secretId, function (secretData) {
      assert.equal('username', secretData.clientData.username);
      assert.equal('password', secretData.criticalData.password);

      onSuccess(secretId);
    }, failIfCalled);
  }, failIfCalled);
}

console.log('############ creating identity 1');
// disable SSL certificate validation
rpc.setCertificateValidationForTest(false);
// lib.initForTest();

fe.initCacheFromFile('test_data/keys.cache');

  //  TODO: the consistency stuff is a disaster. Change this to parallel when it's fixed.
  //lib.parallel([
  lib.series([
    [fe.workerCreateIdentity, ['aaa1@example.com', 'password1', null, host, port]],
    [fe.workerCreateIdentity, ['aaa2@example.com', 'password2', null, host, port]],
    [fe.workerCreateIdentity, ['aaa3@example.com', 'password3', null, host, port]],
    [fe.workerCreateIdentity, ['aaa4@example.com', 'password4', null, host, port]]],
    function(identities) {
      try {
        for (var q=0; q < 4; ++q) {
          fe.workerInvokeOnIdentity(identities[q], 'isVerifiedAsync', assert, failIfCalled);
          assert(!identities[q].changePwd);
        }
      } catch (e) {
        log.stopCapturingLogsToBuffer();
        console.log('most recent log lines:');
        console.log('>>>>>' + log.logBuffer.toArray().join('\n>>>>>'));
        throw e;
      }

      fe.workerInvokeOnIdentity(identities[3], 'addGroup', 'group1', function(groupId) {
        console.log(groupId);
        var toRun = [];
        var count = 0;
        toRun.push(['create_org', fe.workerInvokeOnIdentity, [identities[3], 'createOrganization',
          {name:'org1', owners:['aaa1@example.com', 'aaa4@example.com'], members:['aaa2@example.com']}]]);

        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['aaa4@example.com']]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]);

        // these should fail:

        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'getGroup', groupId], false]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'mutateGroup', groupId, null, [], ['aaa4@example.com']], false]);
        //toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['invaliduser@example.com']], false]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', 42, null, [], ['invaliduser@example.com']], false]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', 42, null, [], ['invaliduser@example.com']], false]);

        // these will succeed
        // make this an org group
        var orgGroupId = 6;
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [orgGroupId], ['aaa4@example.com', 'aaa2@example.com']]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['aaa4@example.com', 'aaa3@example.com' ]]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]);

        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['aaa4@example.com', 'aaa2@example.com', 'aaa1@example.com']]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]); // 12
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'addSite', 'loginurl', 'user', 'password', 'unf', 'pwf']]); // 13
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'shareSite', undefined, [groupId], ['aaa3@example.com']]]); // 14

        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listSites']]); // 15
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listSites']]); // 16
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listSites']]); // 17
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listSites']]); // 18
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'shareSite', 1, [], ['aaa3@example.com']]]); // 19
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'shareSite', 1, [], []]]); // 24
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listSites']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listGroups']]);//29
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateSite', 1,'loginurl2', 'user2', null, 'unf2', 'pwf2']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getSiteSecretData', 1]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateSite', 1,'loginurl3', 'user3', 'password3','unf3', 'pwf3']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getSiteSecretData', 1]]);
        // this ensures the cache gets used:
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getSiteData', 1, false]]);//34
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getSiteData', 1, false]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutatePrivateKeyPassword', 'password4', '4PASS4', {}]]);
        toRun.push([count++, fe.login, ['aaa4@example.com', '4PASS4', host, port, null]]);
        // this ought to fail, since we have a new password now.
        toRun.push([count++, fe.workerLogin, ['aaa4@example.com', 'password4', host, port, null], false]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['newuser@example.com', 'aaa4@example.com', 'aaa2@example.com', 'aaa1@example.com']]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getGroup', groupId]]); // 40

        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'getPublicKeys', ['newuser@example.com']]]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listUsers']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listUsers']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listUsers']]);
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listUsers']]);//45
        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'mutateGroup', groupId, null, [], ['newuser@example.com', 'aaa1@example.com']]]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'addPendingGroup', groupId, 'scope1', 'name1', ['newuser@example.com', 'aaa4@example.com']]]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'applyPendingGroups', 'scope1']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listGroups']]);//50
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'addPendingGroup', groupId, 'scope1', 'name1', ['newuser@example.com', 'aaa4@example.com']]]);
//          // this should kill all previously pending updates
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'clearAndAddPendingGroups', groupId, 'scope1',  {
//            'name1' : ['newuser@example.com', 'aaa3@example.com'],
//            'name88' : ['newuser8@example.com', 'newuser9@example.com']
//          }]]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'applyPendingGroups', 'scope1']]); //55
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[0], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[1], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[2], 'listGroups']]);
//        toRun.push([count++, fe.workerInvokeOnIdentity, [identities[3], 'listGroups']]);
//        toRun.push(['delete_group_with_secrets', fe.workerInvokeOnIdentity, [identities[3], 'removeGroup', groupId], false]);
//        // toRun.push(['add group to delete', fe.workerInvokeOnIdentity, [identities[0], 'addGroup', 'todelete']]);
//        toRun.push(['delete_group_without_secrets', fe.workerInvokeOnIdentity, [identities[0], 'removeGroup', 15]]);
//        toRun.push(['list_deleted_group', fe.workerInvokeOnIdentity, [identities[0], 'getGroup', 15 /*this is fragile*/], false]);
//        toRun.push(['re-get secret', fe.workerInvokeOnIdentity, [identities[3], 'getSiteData', 1, false]]);
//        toRun.push(['delete_secret', fe.workerInvokeOnIdentity, [identities[3], 'deleteSecret', 1]]);


        toRun.push(['orginfo-aaa1', fe.workerInvokeOnIdentity, [identities[0], 'getOrgInfo']]);
        toRun.push(['orginfo-aaa2', fe.workerInvokeOnIdentity, [identities[1], 'getOrgInfo']]);
        toRun.push(['orginfo-aaa3', fe.workerInvokeOnIdentity, [identities[2], 'getOrgInfo']]);
        toRun.push(['orginfo-aaa4', fe.workerInvokeOnIdentity, [identities[3], 'getOrgInfo']]);

        // add a site from a member
        toRun.push(['add-site-for-org', fe.workerInvokeOnIdentity, [identities[1], 'addSite', 'loginurl for org', 'user2', 'password2', 'unf2', 'pw2f']]); // 13
        // share the site with an admin
        toRun.push(['share-site-with-non-member', fe.workerInvokeOnIdentity, [identities[1], 'shareSite', undefined, [], ['aaa4@example.com']]]); // 14
        toRun.push(['share-site-with-org', fe.workerInvokeOnIdentity, [identities[1], 'addSiteToOrg', 2, orgGroupId]]); // 14
        toRun.push(['re-share-site-with-member', fe.workerInvokeOnIdentity, [identities[1], 'shareSite', 2, [], ['aaa1@example.com']]]); // 14
        // TODO: run org api call here.
        toRun.push(['org-state-non-member', fe.workerInvokeOnIdentity, [identities[2], 'getOrganizationState', orgGroupId], false]);
        toRun.push(['org-state-non-admin', fe.workerInvokeOnIdentity, [identities[1], 'getOrganizationState', orgGroupId]]);
        toRun.push(['org-state-admin-1', fe.workerInvokeOnIdentity, [identities[0], 'getOrganizationState', orgGroupId]]);
        toRun.push(['org-state-admin-2', fe.workerInvokeOnIdentity, [identities[3], 'getOrganizationState', orgGroupId]]);
        toRun.push(['org-display-pwd-member-ok', fe.workerInvokeOnIdentity, [identities[1], 'getSiteSecretDataForDisplay', 2]]);
        toRun.push(['org-display-pwd-admin-ok', fe.workerInvokeOnIdentity, [identities[0], 'getSiteSecretDataForDisplay', 2]]);


        toRun.push(['share-site-with-non-member', fe.workerInvokeOnIdentity, [identities[1], 'shareSite', 2, [], ['aaa1@example.com', 'aaa2@example.com']]]); // 14

        // random user can see the secret
        toRun.push(['org-display-pwd-user-ok', fe.workerInvokeOnIdentity, [identities[1], 'getSiteSecretDataForDisplay', 2]]);
        toRun.push(['edit-secret-ok', fe.workerInvokeOnIdentity, [identities[0], 'editServerSecret', {secretId:2, isViewable:false}]]);

        // random user can no longer see the secret
        toRun.push(['org-display-pwd-user-fail', fe.workerInvokeOnIdentity, [identities[1], 'getSiteSecretDataForDisplay', 2], false]);

        // random user can still see the critical data
        toRun.push(['org-display-pwd-user-forlogin', fe.workerInvokeOnIdentity, [identities[1], 'getSiteSecretData', 2]]);

        toRun.push(['edit-secret-ok-2-admin', fe.workerInvokeOnIdentity, [identities[3], 'editServerSecret', {secretId:2, isViewable:false}]]);
        toRun.push(['edit-secret-fail-2-nonadmin', fe.workerInvokeOnIdentity, [identities[2], 'editServerSecret', {secretId:2, isViewable:false}], false]);
        toRun.push(['edit-secret-fail-3-nonadmin', fe.workerInvokeOnIdentity, [identities[1], 'editServerSecret', {secretId:2, isViewable:false}], false]);
        toRun.push(['org-display-pwd-member-fail', fe.workerInvokeOnIdentity, [identities[2], 'getSiteSecretDataForDisplay', 2], false]);
        toRun.push(['org-display-pwd-admin-ok-2', fe.workerInvokeOnIdentity, [identities[0], 'getSiteSecretDataForDisplay', 2]]);
        toRun.push(['edit-secret-ok-3-admin', fe.workerInvokeOnIdentity, [identities[3], 'editServerSecret', {secretId:2, isViewable:true}]]);

        // random user can see the secret again
        toRun.push(['org-display-pwd-user-ok-2', fe.workerInvokeOnIdentity, [identities[1], 'getSiteSecretDataForDisplay', 2]]);




        lib.series(toRun,
          function(rval, rvalMap) {
            try {




              // make sure the keys were changed.
              assert(rvalMap['0'].publicKey !== rvalMap['11'].publicKey);
              assert.equal(rvalMap['0'].publicKey ,rvalMap['2'].publicKey);
              assert.equal(rvalMap['0'].publicKey ,rvalMap['8'].publicKey);
              assert.equal(rvalMap['0'].acls.length, 1);
              assert.equal(rvalMap['2'].acls.length, 1);
              assert.equal(rvalMap['8'].acls.length, 3);
              assert.equal(rvalMap['10'].acls.length, 3);
              assert.equal(rvalMap['12'].acls.length, 4);
              for (var i = 0; i < 4; ++i) {
                assert.equal(rvalMap[String(i+15)][0].secretId, 1);
              }
              assert.equal(rvalMap['20'].length, 0);
              assert.equal(rvalMap['21'].length, 0);
              assert.equal(rvalMap['22'][0].secretId, 1);
              assert.equal(rvalMap['23'][0].secretId, 1);

              assert.equal(rvalMap['25'].length, 0);
              assert.equal(rvalMap['26'].length, 0);
              assert.equal(rvalMap['27'].length, 0);
              assert.equal(rvalMap['28'][0].secretId, 1);
              assert.equal(Object.keys(rvalMap['29']).length, 3);

              assert.equal(rvalMap['29'][groupId].groupId, groupId);

              console.log('HEREEEEE', JSON.stringify(rvalMap['31']));
              //assert.equal(rvalMap['31'])
              assert.equal(rvalMap['31'].clientData.loginUrl, 'loginurl2');
              assert.equal(rvalMap['31'].clientData.username, 'user2');
              assert.equal(rvalMap['31'].clientData.usernameField, 'unf2');
              assert.equal(rvalMap['31'].clientData.passwordField, 'pwf2');
              assert.equal(rvalMap['33'].clientData.loginUrl, 'loginurl3');
              assert.equal(rvalMap['33'].clientData.username, 'user3');
              assert.equal(rvalMap['33'].clientData.usernameField, 'unf3');
              assert.equal(rvalMap['33'].clientData.passwordField, 'pwf3');
              assert.equal(rvalMap['31'].criticalData.password, null);
              assert.equal(rvalMap['33'].criticalData.password, 'password3');
              assert.equal(JSON.stringify(rvalMap['34']), JSON.stringify(rvalMap['35']));

              assert.equal('aaa4@example.com', rvalMap['37'].getUid());
              assert.equal(rvalMap['40'].acls.length, 5);

              assert(false === rvalMap['37'].shouldChangePassword());

              assert.deepEqual(rvalMap['42'].sort(), [ 'newuser@example.com','aaa2@example.com','aaa4@example.com','aaa1@example.com'].sort());
              assert.deepEqual(rvalMap['43'].sort(), [ 'newuser@example.com','aaa2@example.com','aaa4@example.com','aaa1@example.com'].sort());
              assert.deepEqual(rvalMap['44'].sort(), [ 'aaa3@example.com'].sort());
              assert.deepEqual(rvalMap['45'].sort(), [ 'newuser@example.com','aaa4@example.com', 'aaa2@example.com','aaa1@example.com'].sort());
              
              // this is prone to breaking due to reliance on group id of 15
              // assert.equal(rvalMap['49']['15'].name,'name1');
              // assert.equal(rvalMap['50']['15'], undefined);
              // assert.equal(rvalMap['51']['15'], undefined);
              // assert.equal(rvalMap['52']['15'].name,'name1');
              // TODO: also verify scope and permissions.
              // assert.equal(rvalMap['56']['15'].name,'name1');
              // assert.equal(rvalMap['57']['15'], undefined);
              // assert.equal(rvalMap['58']['15'].name,'name1');
              // assert.equal(rvalMap['59']['15'], undefined);

              assert.equal(rvalMap['orginfo-aaa1'].myOrgId, 6); // owner
//              assert.equal(rvalMap['orginfo-aaa2'].myOrgId, 6) // member
              assert.equal(rvalMap['orginfo-aaa3'].myOrgId, undefined); // nothing 
              assert.equal(rvalMap['orginfo-aaa4'].myOrgId, 6); // owner
              // assert.equal(rvalMap['orginfo-aaa1'].myOrgName, 'org1') // owner
              // assert.equal(rvalMap['orginfo-aaa2'].myOrgName, 'org1') // member
              // assert.equal(rvalMap['orginfo-aaa3'].myOrgName, undefined) // nothing 
              // assert.equal(rvalMap['orginfo-aaa4'].myOrgName, 'org1') // owner

              // TODO: fix orgs and assert that org-state-admin-1 and org-state-admin-2 have the following info:
              /*

{"members":["aaa1@example.com",
"aaa4@example.com",
"newuser@example.com",
"aaa2@example.com"],
"organizations":
{},
"groups":
{"5":
{"groupId":5,
"autoDelete":false,
"name":"group1",
"isOrgPrivateGroup":false,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"7":
{"groupId":7,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"8":
{"groupId":8,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"9":
{"groupId":9,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"21":
{"groupId":21,
"autoDelete":true,
"name":"hidden group 2",
"isOrgPrivateGroup":false,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false}},
"admins":[],
"orgSecretsToPath":
{"2":
{"groupIdPath":[21],
"secretId":2,
"hostname":"loginurl for org",
"groups":[],
"hiddenGroups":[21],
"users":["aaa1@example.com",
"aaa2@example.com"],
"icons":[],
"title":"",
"owningOrgGroupId":6}},
"orphanedSecretsToPath":
{}}
------

{"members":["aaa1@example.com",
"aaa4@example.com",
"newuser@example.com",
"aaa2@example.com"],
"organizations":
{},
"groups":
{"5":
{"groupId":5,
"autoDelete":false,
"name":"group1",
"isOrgPrivateGroup":false,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"7":
{"groupId":7,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"8":
{"groupId":8,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"9":
{"groupId":9,
"autoDelete":false,
"name":"",
"isOrgPrivateGroup":true,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false},
"21":
{"groupId":21,
"autoDelete":true,
"name":"hidden group 2",
"isOrgPrivateGroup":false,
"isNonOrgPrivateGroup":false,
"owningOrgId":6,
"owningOrgName":"org1",
"isTopLevelOrg":false}},
"admins":[],
"orgSecretsToPath":
{"2":
{"secretId":0,
"hostname":"loginurl for org",
"groups":[],
"hiddenGroups":[21],
"users":["aaa1@example.com",
"aaa2@example.com"],
"icons":[],
"title":"",
"owningOrgGroupId":6}},
"orphanedSecretsToPath":
{}}



              */

              
            } catch (e) {
              log.stopCapturingLogsToBuffer();
              console.log('most recent log lines:');
//              console.log('>>>>>' + log.logBuffer.toArray().join('\n>>>>>'));
              console.log(e.message);
              console.log(e.stack);
              throw e;
            }

            fe.workerCreateIdentity('hello@example.com', 'foopassword', null, host, port, function (identity) {
              console.log('############ adding site 1');
              fe.workerInvokeOnIdentity(identity, 'addSite', 'http://example.com/', 'username', 'password', 'user_field', 'pass_field', function(secretId) {
                // check the identity has the right data; continue at testLogin
                validateIdentity(identity, testLogin, secretId);
              }, failIfCalled);
            }, failIfCalled);
          }, failIfCalled);
      }, failIfCalled);
    }, failIfCalled);






/*
*/
