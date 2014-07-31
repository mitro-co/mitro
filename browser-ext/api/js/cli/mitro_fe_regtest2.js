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
var https = require('https');
var host = 'localhost';
var port = 8443;

fe.setDeviceId('idForTest');

// Enable logging to make debugging easier
log.stopCapturingLogsToBuffer();

function failIfCalled(args) {
  throw new Error('unexpected error:' + JSON.stringify(args, null, 4));
}

function verifyUser(username, onSuccess, onFailure) {
  // verify user.
  var options = {
    host: host,
    port: port,
    agent: false,
    rejectUnauthorized: false,
    path: '/mitro-core/user/VerifyAccount?user=' + username + '&code=' + username
  };

  https.get(options, function(res) {
    assert(res.statusCode === 302);
    onSuccess();
  }).on('error', onFailure);
}

// disable SSL certificate validation
rpc.setCertificateValidationForTest(false);
// lib.initForTest();
fe.initCacheFromFile('test_data/keys.cache');


// Test that any admin for a secret can make changes to the secret
// user1: add a secret, create a group with user2, share secret with the group
// user2: share secret with user3
// This previously failed because user2 can't access user1's private group
// TODO: Move this into mitro_fe_regtest.js
function testRemoveFromNonMemberGroup(finishedCallback) {
  // Create the identities
  lib.series(
    [
      [fe.createIdentity, ['user1@example.com', 'password1', null, host, port]],
      [fe.createIdentity, ['user2@example.com', 'password1', null, host, port]],
      [fe.createIdentity, ['user3@example.com', 'password1', null, host, port]]
    ], createdIdentities, failIfCalled);

  var identities = null;
  var siteId = null;
  var groupId = null;

  function createdIdentities(results) {
    identities = results;

    console.log('++++++++++++++ CREATED IDENTITIES');
    // add the secret, create the group
    lib.series(
      [
        [identities[0].addSite, ['http://example.com', 'u', 'p', 'user_field', 'pass_field']],
        [identities[0].addGroup, ['group1']],
        [fe.loginWithToken, ['user1@example.com', 'password1', identities[0].getLoginToken(), host, port, null]],
        // login with the wrong token is expected to fail
        [fe.loginWithToken, ['user1@example.com', 'password1', identities[1].getLoginToken(), host, port, null], false]
      ], createdSiteGroup, failIfCalled);
  }

  function createdSiteGroup(results) {
    siteId = results[0];
    groupId = results[1];
    process.stdout.write('++++++++++++++ CREATED SITE GROUP\n');
    // share site with the group and add user2 to the group
    assert(!identities[0].isVerified());
    assert(!identities[1].isVerified());
    assert(!identities[2].isVerified());

    lib.series(
      [
        // success: user is unverified but it will still work
        [identities[0].shareSite, [siteId, [groupId], ['user1@example.com']]],
        // fail: now the site is shared so we can't access it
        [identities[0].getSiteData, [siteId, false], false],

        // verify identity 0: it should work the second time should work
        [verifyUser, [identities[0].uid]],
        [identities[0].shareSite, [siteId, [groupId], ['user1@example.com']]],
        [identities[0].mutateGroup, [groupId, null, [], ['user2@example.com', 'user1@example.com']]]
      ], sharedSiteWithGroup, failIfCalled);
  }

  function sharedSiteWithGroup(results) {
    process.stdout.write('++++++++++++++ SHARED SITE WITH GROUP\n');
    // share site with other user
    // note that this will fail at first because the user is unverified
    identities[1].shareSite(siteId, [groupId], ['user1@example.com', 'user3@example.com'], failIfCalled, function () {
      process.stdout.write('------- attempting to verify site\n');
      verifyUser(identities[1].uid, function() {
        // TODO: re-login and verify that isVerified is set correctly.
        identities[1].shareSite(siteId, [groupId], ['user1@example.com', 'user3@example.com'], finishedCallback, failIfCalled);
      }, failIfCalled);
    });
  }
}

function done(results) {
  process.stdout.write('SUCCESS!\n');
}

testRemoveFromNonMemberGroup(done);
