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

//#!/usr/bin/env node

var optimist = require('optimist');
var assert = require('assert');
var lib = require('./mitro_lib.js');
var rpc = require('./rpc');
var fe = require('./mitro_fe');
var fs = require('fs');

var allowableCommands = {
    'getpub' : lib.GetPublicKey,
    'getpvt' : lib.GetPrivateKey,
    'addiden' : lib.AddIdentity,
    'addgroup' : lib.AddGroup,
    'ls'  : lib.ListGroupsAndSecrets,
    'getgroup' : lib.GetGroup,
    'addmember' : lib.AddMember,
    'rmmember': lib.RemoveMember,
    'add': lib.AddSecret,
    'rm' : lib.RemoveSecret,
    'cat' : lib.GetSecret,
    'upload_groups' : null,
    'sync_groups' : null,
    'api_addsite' : null
};

var argv = optimist.usage('The Mitro interface\nCommands are:\n\n' + Object.keys(allowableCommands).join('\n'), {
    'uid': {
        description: 'my user id',
        required: true,
        'short': 'u'
      },
    'target_uid': {
        description: 'target user id',
        required: true,
        'short': 't'
      },
    'gid': {
        description: 'group id',
        required: false,
        'short': 'g'
      },
    'server_host': {
        description: 'server host',
        required: true,
        'default': 'localhost',
        'short': 's'
      },
    'server_port': {
        description: 'server port',
        required: true,
        'default': 8443,
        'short': 'p'
      },
    'password': {
        description: 'password',
        required: true,
        'default': ''
      },
    'test_server': {
        description: 'allow connecting to a test server (disables SSL validation)',
        required: false,
        'default': false
      },
    'test_use_crappy_crypto': {
        description: 'allow connecting to a test server (disables SSL validation)',
        required: false,
        'default': false
      },
    'key_cache': {
        description: 'use keys from a cache file (warning: insecure!)',
        required: false,
        'default': false
      },
    'deviceId': {
        description: 'device id',
        required: false,
        'default': 'device_for_commandline'
      }
  }).argv;

console.log = function() {
  process.stdout.write(JSON.stringify(Array.prototype.slice.call(arguments), null, 2));
};
// Exits with a fatal error code so other tools can detect failure
function fatalExit() {
  process.stderr.write('ERROR! exiting\n');
  process.exit(1);
}
function okExit() {
  console.log('OK!');
  process.exit(0);
}

if (argv.test_server) {
  rpc.setCertificateValidationForTest(false);
}
if (argv.test_use_crappy_crypto) {
  lib.initForTest();
}

if (argv.key_cache) {
  fe.initCacheFromFile('test_data/keys.cache');
  argv._keyCache = fe.keyCache;
}

fe.setDeviceId(argv.deviceId);

var command = argv._[0];

var success = false;
if (command !== undefined && command.length !== 0) {
  var cmdFcn = allowableCommands[command];
  if (argv.password) {
    argv.password = '' + argv.password;
  }
  if (cmdFcn) {
    success = lib.runCommand(cmdFcn, argv);
  } else if (command === 'api_addsite') {
    fe.login(argv.uid, argv.password, argv.server_host, argv.server_port, null, function(iden) {
      try {
        process.stdout.write('login ok');
        var toRun = [];
        var url = argv._[1];
        var user = argv._[2];
        var pass = argv._[3];
        var toShare = argv._[4];
        assert (url);
        assert (user);
        assert (pass);
        assert (toShare);
        console.log('creating account for url ', url, ' with u/p', user,pass,'and sharing with',toShare);
        iden.addSite(url, user, pass, null, null, function(secretId){
          iden.shareSite(secretId, [],[toShare], okExit, fatalExit);
        }, fatalExit);
      } catch(e) {
        console.log(e);
      }
    }, function() {console.log('ok');}, fatalExit);
    success = true;

  } else if (command === 'upload_groups') {
    var scope = argv._[1];
    var adminGroup = argv.gid;
    assert (adminGroup);
    console.log("Enter json for groups followed by EOF:");
    // read json from stdin
    var jsonString = fs.readFileSync('/dev/stdin').toString();
    var nameMap = JSON.parse(jsonString);
    fe.login(argv.uid, argv.password, argv.server_host, argv.server_port, null, function(iden) {
      iden.clearAndAddPendingGroups(adminGroup, scope, nameMap, okExit, fatalExit);
    }, fatalExit);
    success = true;
  } else if (command === 'sync_groups') {
    var scope2 = argv._[1];
    fe.login(argv.uid, argv.password, argv.server_host, argv.server_port, null,
      function(iden) {
        iden.applyPendingGroups(scope2, okExit, fatalExit
        );
      }, fatalExit);
    success = true;
  }
}
if (!success) {
  console.log('unknown command: ' + command);
  optimist.showHelp();
  process.exit(1);
}
