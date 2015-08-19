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
var keyczar = keyczar || require('keyczarjs');
/** @suppress{duplicate} */
var mitro = mitro || {};
/** @suppress{duplicate} */
var forge = forge || require('node-forge');
(function() {
mitro.fe = {};
var keyCache = null;
if(typeof(module) !== 'undefined' && module.exports) {
  /// NODE module
  mitro = {
    lib: require('./mitro_lib.js'),
    keycache: require('./keycache'),
    fs : require('fs'),
    log : require('./logging')
  };

  var mitroClientModule = require('./mitroclient');
  mitro.Client = mitroClientModule.Client;

  module.exports = mitro.fe = {};

  mitro.fe.initCacheFromFile = function(cacheFileName) {
    try {
      keyCache = mitro.keycache.MakeKeyCache();
      keyCache.loadFromJson(mitro.fs.readFileSync(cacheFileName));
      console.log('loaded ' + keyCache.size() + ' keys');
    } catch (e) {
      console.log('could not read from key cache file '+ cacheFileName+'... using default');
      console.log(e);
      console.log(e.stack);
    }
  };

  mitro.fe.startCacheFiller = function() {
    keyCache = mitro.keycache.MakeKeyCache();
    mitro.keycache.startFiller(keyCache);
  };

  mitro.fe.initCacheFromJson = function(json) {
    keyCache = mitro.keycache.MakeKeyCache();
    keyCache.loadFromJson(json);
  };
}

mitro.fe.getRandomness = function(onSuccess, onError) {
  // This runs in a webworker and can't touch window.crypto (because of slow JS in Firefox)
  // worker.js replaces this as a "proxy" to post a message to the background extension page.
  // background_api.js implements the "real" version that gets entropy.
  // This null implementation makes the regression test work
  // TODO: Add keyczarjs.collectEntropy() to push entropy to KeyczarJS's RNG source correctly
  // on all platforms
  console.log('WARNING: no randomness source supported');
  onSuccess({seed: ''});
};

mitro.fe.setKeyCache = function(kc) {
  keyCache = kc;
};


var fe = mitro.fe;
var assert = function(expression) {
  if (!expression) {
    throw new Error('Assertion failed');
  }
};

var crypto = function() {return mitro.lib.getCrypto();};


/**
Computes the difference between oldList and newList (treating them as sets), 
putting the result in added and deleted. The lists will be sorted afterwards. 
Only works for numeric types.

@param {!Array.<number>} oldList
@param {!Array.<number>} newList
@param {!Array.<number>} addedList
@param {!Array.<number>} deletedList
@return {boolean} true iff the lists are different
*/
function bidirectionalSetDiff(oldList, newList, addedList, deletedList) {
  return bidrectionalSetDiffWithComparator(oldList, newList, addedList, deletedList,
  function(a, b) {
    return a - b;
  });
}

/**
Computes the difference between oldList and newList (treating them as sets), 
putting the result in added and deleted. The lists will be sorted afterwards. 

Supply a comparator if you want non-string compare.

@param {!Array} oldList
@param {!Array} newList
@param {!Array} addedList
@param {!Array} deletedList
@param {function(?, ?):number=} comparator
@return {boolean} true iff the lists are different
*/
function bidrectionalSetDiffWithComparator(oldList, newList, addedList, deletedList, comparator) {
  // Array.sort converts arguments to strings by default. WTF
  oldList.sort(comparator);
  newList.sort(comparator);
  var i = 0;
  var j = 0;
  while ((i < oldList.length) && (j < newList.length)) {
    if (oldList[i] < newList[j]) {
      // oldList[i] has been removed
      deletedList.push(oldList[i]);
      ++i;
    } else if (oldList[i] > newList[j]) {
      // newList[j] has been added
      addedList.push(newList[j]);
      ++j;
    } else {
      // item exists in both lists
      ++i;
      ++j;
    }
  }
  for (; i < oldList.length; ++i) {
    deletedList.push(oldList[i]);
  }
  for (; j < newList.length; ++j) {
    addedList.push(newList[j]);
  }
  return (deletedList.length + addedList.length) !== 0;
}

/** Converts the response from ListMySecretsAndGroups to the format expected by the extension. */
mitro.fe.convertListSitesToExtension = function(response) {
  var output = [];
  for (var secretId in response.secretToPath) {
    var obj = {};
    obj.clientData = JSON.parse(response.secretToPath[secretId].clientData);
    obj.secretId = response.secretToPath[secretId].secretId;
    obj.owningOrgId = response.secretToPath[secretId].owningOrgId;
    obj.owningOrgName = response.secretToPath[secretId].owningOrgName;
    obj.groups = response.secretToPath[secretId].groups;
    obj.users = response.secretToPath[secretId].users;
    obj.hints = {};
    obj.hints.icons = response.secretToPath[secretId].icons;
    obj.hints.title = response.secretToPath[secretId].title;

    var flattenedUsersSet = {};
    for (var i = 0; i < obj.users.length; ++i) {
      flattenedUsersSet[obj.users[i]] = 1;
    }
    for (i = 0; i < obj.groups.length; ++i) {
      var groupId = obj.groups[i];
      var groupInfo = response.groups[groupId];
      if (groupInfo) {
        for (var j = 0; j < groupInfo.users.length; ++j) {
          flattenedUsersSet[groupInfo.users[j]] = 1;
        }
      }
    }
    obj.flattenedUsers = Object.keys(flattenedUsersSet);

    output.push(obj);
  }
  return output;
};

var FAILOVER_MITRO_HOST = null;
var FAILOVER_MITRO_PORT = null;
var DEVICE_ID = null;

/**
 * Wraps an operation and retries it entirely if it fails
 * due to a cancelled transaction that should be retried.
 *
 **/
 var wrapWithTransactionCancelledRetry = function(wrappedFcn) {
  var MIN_RETRY_DELAY = 1000;
  var MAX_RETRY_DELAY = 3000;
  // some of the write functions modify arguments, so we need to keep a deep
  // copy of the objects to use every time.  Sadly, JS doesn't have this kind of
  // function. Luckily, except for the last two args, which are functions,
  // all other possible params are serializable via JSON, so use this to deepcopy.
  var _copy = function(o) {
    return JSON.parse(JSON.stringify(o));
  };

  return function() {
    var maxRetries = 3;
    var wrappedFcnArgs = Array.prototype.slice.call(arguments);
    var oldOnError = wrappedFcnArgs.pop();
    var oldOnSuccess = wrappedFcnArgs.pop();
    var originalwrappedFcnArgs = _copy(wrappedFcnArgs);

    // do this double copy to be sure that the default implementation actually works
    wrappedFcnArgs = _copy(originalwrappedFcnArgs);
    
    var newOnError = function(e) {
      try {
        if (e.exceptionType === 'RetryTransactionException') {
          // wait somewhere between 1 and 3 seconds
          var wait = Math.random() * (MAX_RETRY_DELAY - MIN_RETRY_DELAY) + MIN_RETRY_DELAY;
          console.log('retrying operation after ', wait, ' ms');
          setTimeout(
            function() {
              var wrappedFcnArgs2 = _copy(originalwrappedFcnArgs);
              wrappedFcnArgs2.push(oldOnSuccess);
              wrappedFcnArgs2.push((--maxRetries < 0) ? oldOnError : newOnError);
              wrappedFcn.apply(undefined, wrappedFcnArgs2);
            },
            wait);
        } else {
          oldOnError(e);
        }
      } catch (e2) {
        oldOnError(mitro.lib.makeLocalException(e2));
      }
    };
    wrappedFcnArgs.push(oldOnSuccess);
    wrappedFcnArgs.push(newOnError);
    wrappedFcn.apply(undefined, wrappedFcnArgs);
  };
};

/**
 * Wraps a mitro.lib function with failover.
 * Args must be as follows.
 *
 * arg[0]: {args dictionary}
 * ...
 * ...
 * arg[n-1]: onSuccess
 * args[n]: onError
 */
var wrapWithFailover = function(wrappedFcn) {
  return function() {
    var wrappedFcnArgs = Array.prototype.slice.call(arguments);
    var oldOnError = wrappedFcnArgs.pop();
    var newOnError = function(e) {
      var cannotUseBackup = !FAILOVER_MITRO_HOST || !FAILOVER_MITRO_PORT ||
            (wrappedFcnArgs[0]._transactionSpecificData &&
               wrappedFcnArgs[0]._transactionSpecificData.id) ||
            (wrappedFcnArgs[0]._transactionSpecificData &&
              wrappedFcnArgs[0]._transactionSpecificData.isWriteOperation);

      // don't retry 400 errors on the backup. status is set by rpc.js
      cannotUseBackup = cannotUseBackup || (400 <= e.status && e.status < 500);
      cannotUseBackup = cannotUseBackup || (e && (
        (e.exceptionType === 'DoTwoFactorAuthException') ||
        (e.exceptionType === 'UnverifiedUserException')));

      if (cannotUseBackup) {
        console.log('could not retry on backup.');
        oldOnError(e);
        return;
      } else {
        // retry on backup
        console.log('retrying on backup due to ', e);
        wrappedFcnArgs[0].server_host = FAILOVER_MITRO_HOST;
        wrappedFcnArgs[0].server_port = FAILOVER_MITRO_PORT;
        wrappedFcnArgs.pop();
        wrappedFcnArgs.push(oldOnError);
        wrappedFcn.apply(undefined, wrappedFcnArgs);
      }
    };

    wrappedFcnArgs.push(newOnError);
    wrappedFcn.apply(undefined, wrappedFcnArgs);
  };
};





/* Creates a new identity. onSuccess gets called with the MitroIdentity. */
function createIdentity(email, password, analyticsId, host, port, onSuccess, onError) {

  var onSuccessWrapper = function(result) {
    try {
      var identity = _make(email, result.verified, result.unsignedLoginToken, result.privateKey, false, host, port, result.privateGroupId);
      identity.retrieveDeviceSpecificKey(function() {
        onSuccess(identity);
      }, onError);

    } catch (e) {
      console.log(e);
      onError(mitro.lib.makeLocalException(e));
    }
  };
  var args = {
    uid: email,
    password: password,
    analyticsId: analyticsId,
    server_host: host,
    server_port: port,
    _keyCache : keyCache,
    _createPrivateGroup : true,
    deviceId : DEVICE_ID
  };
  mitro.lib.AddIdentity(args, onSuccessWrapper, onError);
}

/* Accesses an existing identity. onSuccess gets called with the MitroIdentity. */
function login(email, password, host, port, tfaCode, onSuccess, onError) {
  return loginWithToken(email, password, undefined, host, port, tfaCode, onSuccess, onError);
}

function loginWithToken(email, password, token, host, port, tfaCode, onSuccess, onError) {
  return loginWithTokenAndLocalKey(email, password, token, host, port, null, tfaCode, onSuccess, onError);
}

function loginWithTokenAndLocalKey(email, password, token, host, port, locallyEncryptedKey, tfaCode, onSuccess, onError) {
  mitro.lib.clearCaches();
  if (!token) {
    token = {};
  }

  var args = {
    uid: email,
    server_host: host,
    server_port: port,
    loginToken: token.loginToken,
    twoFactorCode : tfaCode,
    loginTokenSignature : token.loginTokenSignature,
    deviceId : DEVICE_ID,
    automatic: !!locallyEncryptedKey
  };
  
  (wrapWithFailover(mitro.lib.GetPrivateKey))(args, function(response) {
    try {
      var privateKey = null;
      // in case we have an AES key, and a locally stored key, and no password.
      if (!password && response.deviceKeyString && locallyEncryptedKey) {
        var aesKey = keyczar.fromJson(response.deviceKeyString);
        privateKey = crypto().decryptWith(locallyEncryptedKey, aesKey);
      }
      // no private key yet; try decrypting with password
      if (!privateKey) {
        privateKey = crypto().loadFromJson(response.encryptedPrivateKey, password);
      }

      var identity = _make(email, response.verified, response.unsignedLoginToken, privateKey, response.changePasswordOnNextLogin,
        host, port);

      // list sites to find the id of our private "secrets" group
      identity.listSites(function(sites) {
        // ignore the response and call onSuccess
        console.log('*** successfully logged in to ', identity.getUid());
        identity.retrieveDeviceSpecificKey(function() {
          // hack to ensure org data is loaded.
          // Bonus: running list my secrets here prevents the triple request to the server thanks to caching.
          identity.listGroups(function(ignored) {
            onSuccess(identity);
          }, onError);
        }, onError);

      }, onError);
    } catch (e)  {
      console.log(e);
      onError(mitro.lib.makeLocalException(e));
    }
  }, onError);
}

function addIssue(args, host, port, onSuccess, onError) {
  args.server_host = host;
  args.server_port = port;

  mitro.lib.AddIssue(args, onSuccess, onError);
}

/** Provides Closure-compiled code access to existing APIs.
@constructor
@implements mitro.LegacyAPI
@param {function(Object=)} makeArgs the _makeArgs function used by the fe identity object.
*/
var LegacyAPIImplementation = function(makeArgs) {
  this.makeArgs = makeArgs;
};

LegacyAPIImplementation.prototype.getPublicKeys = function(
    identities, transaction, onSuccess, onError) {
  var args = this.makeArgs(transaction);
  args.addMissingUsers = true;
  identities.sort();
  mitro.lib.GetPublicKeys(args, identities, function(result) {
    if (result.missingUsers.length !== 0) {
      onError(new Error('LegacyAPIImplementation.getPublicKeys: missing users: ' +
        result.missingUsers.length));
    } else {
      onSuccess(result.userIdToPublicKey);
    }
  }, onError);
};

LegacyAPIImplementation.prototype.cryptoLoadFromJson = function(jsonString) {
  return crypto().loadFromJson(jsonString);
};

LegacyAPIImplementation.prototype.postSigned = function(
    path, request, transaction, onSuccess, onError) {
  var args = this.makeArgs(transaction);
  mitro.lib.PostToMitro(request, args, path, onSuccess, onError);
};

LegacyAPIImplementation.prototype.getNewRSAKeysAsync = function(count, onSuccess, onError) {
  keyCache.getNewRSAKeysAsync(count, onSuccess, onError);
};

LegacyAPIImplementation.prototype.getGroup = function(groupId, transaction, onSuccess, onError) {
  var args = this.makeArgs(transaction);
  args.gid = groupId;
  mitro.lib.GetGroup(args, onSuccess, onError);
};

LegacyAPIImplementation.prototype.getIdentity = function() {
  var args = this.makeArgs();
  return args.uid;
};

LegacyAPIImplementation.prototype.decrypt = function(ciphertext) {
  var args = this.makeArgs();
  return args._privateKey.decrypt(ciphertext);
};

/**
@param {string} email
@param {boolean} verified
@param {string} unsignedToken
@param {mitro.crypto.Key} privateKey
@param {boolean} changePwd
@param {string} host
@param {number} port
@param {?number=} privateNonOrgGroupId
@return {!Object} TODO: Make this a type?
*/
function _make(email, verified, unsignedToken, privateKey, changePwd, host, port, privateNonOrgGroupId) {
  assert(keyczar);
  if (privateNonOrgGroupId === undefined) {
    privateNonOrgGroupId = null;
  }

  var token = null;
  if (unsignedToken) {
    console.log('mitro_fe createIdentity got token:', unsignedToken);
    token = {loginToken : unsignedToken,
                 loginTokenSignature : privateKey.sign(unsignedToken)};
  }

  var obj = {
    uid: email,
    // privateKey: privateKey,
    // host: host,
    // port: port,
    holdingTransaction:{}
  };
  var legacyApi = new LegacyAPIImplementation(_makeArgs);
  obj.mitroclient = new mitro.Client(legacyApi);
  var deviceSpecificKey = null;
  
  obj.getPrivateKeyStringForLocalDisk = function() {
    return deviceSpecificKey ? privateKey.encryptWith(deviceSpecificKey) : null;
  };

  obj.setDeviceSpecificKeyFromString = function(str) {
    deviceSpecificKey = str ? keyczar.fromJson(str) : null;
  };

  obj.getPrivateKeyStringForLocalDiskAsync = function(onSuccess) {
    onSuccess(deviceSpecificKey ? privateKey.encryptWith(deviceSpecificKey) : null);
  };


  obj.holdingTransaction.retrieveDeviceSpecificKey = function(transactionSpecificData, onSuccess, onError) {
    try {
      var args = _makeArgs(transactionSpecificData);
      wrapWithFailover(mitro.lib.RetrieveDeviceSpecificKey)(args,
          function (response) {
            try {
              obj.setDeviceSpecificKeyFromString(response.deviceKeyString);
              onSuccess();
            } catch(e) {
              onError(mitro.lib.makeLocalException(e));
            }
          }, onError);
    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.signMessage = function(msg) {
    return privateKey.sign(msg);
  };
  obj.getLoginToken = function() { return token;};

  obj.signMessageAsync = function(msg, onSuccess, onError) {
    try {
      onSuccess(privateKey.sign(msg));
    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.getLoginTokenAsync = function(onSuccess, onError) {
    onSuccess(token);
  };



  /** Returns an object containing the common arguments
  @param {Object=} transactionSpecificData optional (when? why?)
  @return {!Object}
  */
  function _makeArgs(transactionSpecificData) {
    return {
      uid: email,
      _privateKey: privateKey,
      server_host: host,
      server_port: port,
      _transactionSpecificData: transactionSpecificData,
      _keyCache: keyCache,
      deviceId : DEVICE_ID
    };
  }
  obj.isVerified = function() { return verified; };
  obj.getUid = function() { return email;};
  obj.shouldChangePassword = function() {
    return changePwd;
  };
  obj.isVerifiedAsync = function(onSuccess) { onSuccess(verified); };
  obj.getUidAsync = function(onSuccess) { onSuccess(email);};
  obj.shouldChangePasswordAsync = function(onSuccess) {
    onSuccess(changePwd);
  };

  // TODO: should we verify that the hostnames match?
  obj.holdingTransaction.editSitePassword = function(transactionSpecificData,
    secretId, newPassword, onSuccess, onError) {
    // get the secret
    obj.holdingTransaction.getSiteData(transactionSpecificData, secretId, true,
      function(secret) {
        try {
          var secretData = {
            password: newPassword,
            oldPassword: secret.criticalData.password
          };
          obj.holdingTransaction.mutateSecret(transactionSpecificData, secretId,
            secret.serverData, secret.clientData, secretData, onSuccess, onError);
        } catch(e) {
          onError(mitro.lib.makeLocalException(e));
        }
      }, onError);
  };

  obj.holdingTransaction.mutateSite = function(transactionSpecificData, secretId,
    loginUrl, username, password, usernameField, passwordField, onSuccess, onError) {
    var clientAndSecretData = _makeClientAndSecretData(loginUrl, username, password,
        usernameField, passwordField);
    return obj.holdingTransaction.mutateSecret(transactionSpecificData, secretId, null,
        clientAndSecretData[0], clientAndSecretData[1], onSuccess, onError);
  };

  obj.holdingTransaction.mutateSecret = function(transactionSpecificData, secretId,
    updatedServerData, updatedClientData, updatedCriticalData, onSuccess, onError) {
    // ensure that we have write access to all groups that this secret requires.
    var toRun = [
      [obj.holdingTransaction.getSiteData, [transactionSpecificData, secretId, true]]
    ];

    if (updatedServerData) {
      updatedServerData.secretId = secretId;
      toRun.push([obj.holdingTransaction.editServerSecret, [transactionSpecificData, updatedServerData]]);
    }

    mitro.lib.batch(toRun,
      function(rvals)  {
        try {
          var secret = rvals[0];
          // update the secret
          var toRun = [];
          var allGroups = secret.groups.concat(secret.hiddenGroups);
          console.log('all groups:', allGroups);
          var groupIdToEncryptedData = {};
          for (var gIdx in allGroups) {
            var gid = allGroups[gIdx];
            var groupPublicKey = crypto().loadFromJson(secret.groupIdToPublicKeyMap[gid]);
            groupIdToEncryptedData[gid] = {
              encryptedClientData : groupPublicKey.encrypt(JSON.stringify(updatedClientData))
            };

            if (updatedCriticalData) {
              // if critical data is not provided, we leave the old one as is.
              groupIdToEncryptedData[gid].encryptedCriticalData = groupPublicKey.encrypt(JSON.stringify(updatedCriticalData));
            }
          }
          var data = {
            secretId : secretId,
            groupIdToEncryptedData : groupIdToEncryptedData
          };
          transactionSpecificData.implicitEndTransaction = true;
          mitro.lib.PostToMitro(data, _makeArgs(transactionSpecificData),
          '/mitro-core/api/EditSecretContent', onSuccess, onError);
        } catch (e) {
          console.log(e.message);
          console.log(e.stack);
          onError(mitro.lib.makeLocalException(e));
        }
      }, onError);
  };

  obj.holdingTransaction.removePendingGroups = function(transactionSpecificData, scope, onSuccess, onError) {
    try {
      var rpc = {scope: scope};
      mitro.lib.PostToMitro(rpc, _makeArgs(transactionSpecificData), '/mitro-core/api/RemovePendingGroupApprovals', onSuccess, onError);
    } catch (e) {
      console.log(e.stack);
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.holdingTransaction.editServerSecret = function(transactionSpecificData, args, onSuccess, onError) {
    try {
      var data = {};
      assert (args.isViewable !== undefined);
      data.isViewable = args.isViewable;
      data.secretId = args.secretId;
      mitro.lib.PostToMitro(data, _makeArgs(transactionSpecificData),
        '/mitro-core/api/EditSecret', onSuccess, onError);

    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };
  obj.holdingTransaction.getPendingGroups = function(transactionSpecificData, scope, onSuccess, onError) {
    try {
      mitro.lib.PostToMitro({scope:scope}, _makeArgs(transactionSpecificData),
        '/mitro-core/api/GetPendingGroups', onSuccess, onError);
    } catch (e) {
      console.log('error getting groups:', e.stack);
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.holdingTransaction.getOrganizationState = function(transactionSpecificData, orgId, onSuccess, onError) {
    try {
      // PostToMitro has a different parameter order than wrapWithFailover


      (wrapWithFailover(mitro.lib.GetOrganizationState))(_makeArgs(transactionSpecificData),
        {orgId:orgId}, function(resp) {
      var secrets = resp.orgSecretsToPath;
      for (var i in resp.orgSecretsToPath) {
        // if the secret has encrypted data, decrypt it. TODO: Fix server to ALWAYS send this
        if (secrets[i].encryptedClientData) {
          mitro.lib.decryptSecretWithGroups(secrets[i], resp.groups, privateKey);
          secrets[i].clientData = JSON.parse(secrets[i].clientData);
        }
        secrets[i].hints = {};
        secrets[i].hints.icons = secrets[i].icons;
        secrets[i].hints.title = secrets[i].title;
      }
      onSuccess(resp);
    }, onError);

    } catch (e) {
      console.log('error getting org state:', e.stack);
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.holdingTransaction.applyPendingGroups = function(transactionSpecificData, options, onSuccess, onError) {
    try {
      var scope = options.scope;
      var nonce = options.nonce;
      var removeUsersFromOrg = options.removeUsersFromOrg;
      obj.holdingTransaction.getPendingGroups(transactionSpecificData, scope,
        function(approvals) {
          if (approvals.syncNonce !== nonce) {
            onError("Your sync information is stale, please refresh the page.");
            return;
          }
          try {
            console.log('got approvals!');
            var toRun = [];
            var scopes = {};
            for (var i = 0; i < approvals.pendingAdditionsAndModifications.length; ++i) {
              var syncGroup = approvals.pendingAdditionsAndModifications[i];
              var memberListMap = JSON.parse(syncGroup.memberListJson);
              assert (memberListMap.groupName === syncGroup.groupName);
              assert(!scope || syncGroup.scope === scope);
              scopes[syncGroup.scope] = true;
              // TODO: verify signature
              var identityPermissionMap = {};
              var identities = memberListMap.memberList;
              var j;
              for (j in identities) {
                identityPermissionMap[identities[j]] = 'MODIFY_SECRETS_BUT_NOT_MEMBERSHIP';
              }
              var groupId;
              if (!syncGroup.matchedGroup) {
                // need to add a new group
                toRun.push([obj.holdingTransaction.addGroupWithScope,
                  [transactionSpecificData, syncGroup.groupName, syncGroup.scope, false]]);
                groupId = undefined;
              } else {
                assert (syncGroup.matchedGroup.name === syncGroup.groupName);
                assert (syncGroup.matchedGroup.scope === syncGroup.scope);
                groupId = syncGroup.matchedGroup.groupId;
              }

              // existing (or newly added) group needs to be modified.
              toRun.push([obj.holdingTransaction.mutateGroupWithScopesAndPermissions,
                [transactionSpecificData, groupId, null, [approvals.orgId], identities, syncGroup.scope, {}, identityPermissionMap]]);
            }

            // remove old groups (this will not hurt anything because orgs capture orphaned secrets)
            for (i = 0; i < approvals.pendingDeletions.length; ++i) {
              toRun.push([obj.holdingTransaction.removeGroup, [transactionSpecificData,
                approvals.pendingDeletions[i].groupId]]);
            }

            // delete all pending groups from the sync table
            for (var thisScope in scopes) {
              toRun.push([obj.holdingTransaction.removePendingGroups, [transactionSpecificData, thisScope]]);
            }

            // mutate the organization as requested.
            var mutateRequest = new mitro.MutateOrganizationClientRequest();
            mutateRequest.orgId = approvals.orgId;
            mutateRequest.newMembers = approvals.newOrgMembers;
            if (options.removeUsersFromOrg) {
              // TODO: this will fail if we attempt to remove an admin. How should we handle this?
              mutateRequest.membersToRemove = approvals.deletedOrgMemers;
            }
            if (mutateRequest.membersToRemove.length || mutateRequest.newMembers.length) {
              toRun.push([obj.holdingTransaction.mutateOrganization, [transactionSpecificData,
                mutateRequest]]);
            }
            mitro.lib.series(toRun, onSuccess, onError);
          } catch (e) {
            onError(mitro.lib.makeLocalException(e));
          }
        }, onError);
    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };

	obj.holdingTransaction.checkTwoFactor = function (transactionSpecificData, onSuccess, onError) {
		var args = _makeArgs(transactionSpecificData);
    args.loginToken = token.loginToken,
    args.loginTokenSignature = token.loginTokenSignature;
		try {
				mitro.lib.checkTwoFactor(args, function(d) { onSuccess(d.twoFactorUrl); }, onError);
		}	catch(e) {
			onError(mitro.lib.makeLocalException(e));
		}
	};

	obj.holdingTransaction.mutatePrivateKeyPassword = function(transactionSpecificData, oldPassword, newPassword, up, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    args.loginToken = token.loginToken,
    args.loginTokenSignature = token.loginTokenSignature;

    mitro.lib.GetPrivateKey(args, function(privateKeyResponse) {
      try {
        // refetch the private key.
        var privateKey = crypto().loadFromJson(privateKeyResponse.encryptedPrivateKey, oldPassword);
        var newEncryptedPrivateKey = privateKey.toJsonEncrypted(newPassword);
				mitro.lib.EditEncryptedPrivateKey(_makeArgs(transactionSpecificData), up, newEncryptedPrivateKey,
          function(resp) {
            changePwd = false;
            onSuccess(resp);
          }, onError);


      } catch (e) {
        onError(mitro.lib.makeLocalException(e));
      }
    });
  };

  obj.holdingTransaction.mutatePrivateKeyPasswordWithoutOldPassword = function(transactionSpecificData, args, onSuccess, onError) {
    try {
      var newEncryptedPrivateKey = privateKey.toJsonEncrypted(args.newPassword);
      mitro.lib.EditEncryptedPrivateKey(_makeArgs(transactionSpecificData), args.up, newEncryptedPrivateKey,
          function(resp) {
            changePwd = false;
            onSuccess(resp);
      }, onError);
    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };



  /* Adds a new secret. onSuccess called with secretId. */
  obj.holdingTransaction.addSecret = function(transactionSpecificData, loginUrl, clientData, secretData, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    args.gid = privateNonOrgGroupId;
    args._ = [null, loginUrl, JSON.stringify(clientData), JSON.stringify(secretData)];
    mitro.lib.AddSecret(args, function (response) {
      onSuccess(response.secretId);
    }, onError);
  };

  obj.holdingTransaction.addSecrets = function(transactionSpecificData, data, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    data.clientData = JSON.stringify(data.clientData);
    data.criticalData = JSON.stringify(data.criticalData);
    console.log('mitro_fe addSecrets', data.groupIds);
    mitro.lib.AddSecrets(args, data, onSuccess, onError);
  };

  var _makeClientAndSecretData = function(loginUrl, username, password,
    usernameField, passwordField) {
    var clientData = {
      loginUrl: loginUrl,
      username: username,
      usernameField: usernameField,
      passwordField: passwordField
    };

    var secretData = {
      password: password
    };
    return [clientData, secretData];
  };

  /* Adds a new site. onSuccess called with secretId. */
  obj.holdingTransaction.addSite = function(transactionSpecificData, loginUrl, username, password,
    usernameField, passwordField, onSuccess, onError) {
    var clientAndSecretData = _makeClientAndSecretData(loginUrl, username, password, usernameField, passwordField);
    return obj.holdingTransaction.addSecret(transactionSpecificData, loginUrl, clientAndSecretData[0], clientAndSecretData[1], onSuccess, onError);
  };

  obj.holdingTransaction.shareSite = function(transactionSpecificData, secretId, newGroupIdList, 
    newIdentityList, onSuccess, onError) {
    return obj.holdingTransaction.shareSiteAndOptionallySetOrg(transactionSpecificData, secretId, newGroupIdList, newIdentityList, 
      null, onSuccess, onError);
  };

  obj.holdingTransaction.shareSiteAndOptionallySetOrg = function(transactionSpecificData, secretId, newGroupIdList, 
    newIdentityList, orgGroupId, onSuccess, onError) {
    // 1. list groups and secrets and get existing secret
    assert(secretId);
    mitro.lib.parallel([
      [obj.holdingTransaction.getSiteSecretData, [transactionSpecificData, secretId]]
      ],

      function(rval) {

        var oldOrgId = rval[0].owningOrgId;

        if (oldOrgId && !orgGroupId) {
          orgGroupId = oldOrgId;
        } else if (oldOrgId) {
          // you cannot move a secret between orgs.
          assert (orgGroupId === oldOrgId);
        }

        var newNumberOfGroups = 0;
        var deleteSecret = false;
        var toRun = [];
        var toadd = [];
        var addNewHiddenGroup = function(newGroupId, onSuccess, onError) {
          toadd.push(newGroupId);
          onSuccess(newGroupId);
        };

        var i;


        // we cannot share secrets with just identities.
        // for now, map all identities into a hidden group which we create.
        // 2. search existing groups for a hidden group which matches the acl we need
        // 3. if this does not exist, create the group, and add memberships
        //    otherwise add the group to the list of groups.

        // this means we need to delete the group.
        if (newGroupIdList === null && newIdentityList === null) {
          assert(false);
        } else if (newGroupIdList.length === 0 &&
                (newIdentityList.length === 0 || (newIdentityList.length === 1 && newIdentityList[0] === email))) {
          console.log('mitro_lib shareSiteAndOptionallySetOrg: desired final ACL is empty');
          // if there are no groups and there are no users or only me, use my private group.
          if (orgGroupId && obj.organizations[orgGroupId] &&
              obj.organizations[orgGroupId].privateOrgGroupId) {
            console.log('mitro_lib shareSiteAndOptionallySetOrg: using org private group');
            newGroupIdList = [obj.organizations[orgGroupId].privateOrgGroupId];
          } else {
            console.log('mitro_lib shareSiteAndOptionallySetOrg: using non-org private group');
            newGroupIdList = [privateNonOrgGroupId];
          }
        } else {

          // TODO: implement (2) above. Currently we just make a new group every time. (Yuck)
          var newGroupArgs = _makeArgs(transactionSpecificData);
          newGroupArgs.secretId = secretId;
          var add_myself = true;
          for (var j = 0;  j < newIdentityList.length; ++j) {
            if (email === newIdentityList[j]) {
              add_myself = false;
              break;
            }
          }
          if (add_myself) {
            newIdentityList.push(email);
          }
          newNumberOfGroups = 1;
          var newOrgList = [];

          if (orgGroupId) {
            newOrgList = [orgGroupId];
          }

          // TODO: this should actually edit the group with the modified user list instead of 
          // recreating from scratch.
          var hasDifferentUsers = bidrectionalSetDiffWithComparator(rval[0].users, newIdentityList, [], []);
          if (rval[0].owningOrgId !== orgGroupId || hasDifferentUsers) {
            console.log(' *** Mutation results in a different set of users or has org changes. We must create new groups, ugh.');
            // for now, just make a new group, add the appropriate users
            toRun.push([obj.holdingTransaction._addGroup, [transactionSpecificData, 'hidden group ' + secretId, true]]);
            toRun.push([obj.holdingTransaction.mutateGroup, [transactionSpecificData, undefined, null, newOrgList, newIdentityList]]);

            // this will add the new hidden group that we've created to the list of groups we need to edit.
            toRun.push([addNewHiddenGroup, [undefined]]); // group Id comes from previous command
          } else {
            // we need to keep old hidden group around since it's not being modified.
            newGroupIdList = newGroupIdList.concat(rval[0].hiddenGroups);
            console.log(" *** Mutation didn't change user list; don't need to create new groups!");
          }
        }
        // get the list of old and new groups, then in series, add, then remove groups
        var oldgroups = rval[0].groups;
        oldgroups = oldgroups.concat(rval[0].hiddenGroups);

        var todel = [];

        if (orgGroupId) {
          var mustAdd = true;
          for (i = 0; i < newGroupIdList.length; ++i) {
            if (parseInt(newGroupIdList[i], 10) === parseInt(orgGroupId, 10)) {
              mustAdd = false;
              break;
            }
          }
          if (mustAdd) {
            newGroupIdList.push(orgGroupId);
            console.log('mitro_lib shareSiteAndOptionallySetOrg: FORCE-adding org group id to ACL');
          }
        }

        bidirectionalSetDiff(oldgroups, newGroupIdList, toadd, todel);

        toRun.push([mitro.lib.AddSecrets, [_makeArgs(transactionSpecificData),
          {groupIds: toadd,
           secretId : secretId}]]);

        for (i = 0; i < todel.length; ++i) {
          var delArgs = _makeArgs(transactionSpecificData);
          delArgs.secretId = secretId;
          delArgs._ = [null, secretId];
          delArgs.gid = todel[i];
          toRun.push([mitro.lib.RemoveSecret, [delArgs]]);
        }

        newNumberOfGroups += oldgroups.length - todel.length + toadd.length;
        
        // Boo JS has no XOR. should continue to have groups IFF we are not deleting the secret.
        assert ((newNumberOfGroups > 0) === (!deleteSecret));

        mitro.lib.series(toRun, onSuccess, onError);

      }, onError
      );
  };

  obj.holdingTransaction.getGroup = function(transactionSpecificData, groupId, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    args.gid = groupId;
    mitro.lib.GetGroup(args, onSuccess, onError);
  };


  obj.holdingTransaction.mutateGroup = function(transactionSpecificData, groupId, groupName, newGroupIdList, newIdentityList,
    onSuccess, onError) {
    return obj.holdingTransaction.mutateGroupWithScopesAndPermissions(transactionSpecificData, groupId, groupName, newGroupIdList, newIdentityList,
      null, {}, {}, onSuccess, onError);
  };

  // calls onSucces(groupId)
  obj.holdingTransaction.mutateGroupWithScopesAndPermissions = function(transactionSpecificData, groupId, groupName, newGroupIdList, newIdentityList,
    scope, groupIdPermissionMap, identityPermissionMap, onSuccess, onError) {
    try {
      console.log('>>> fe.mutategroup');
      assert (groupId);
      if (!newGroupIdList) {
        newGroupIdList = [];
      }
      // nested groups must be organizations, so the length must be 0 or 1.
      assert (newGroupIdList.length <= 1);

      var args = _makeArgs(transactionSpecificData);


      // fetch all the users' public keys.
      mitro.lib.GetUserAndGroupPublicKeys(args, /* add missing users*/ true, newIdentityList, newGroupIdList, function(keysResponse) {
        args.gid = groupId;
        args.orgId = newGroupIdList !== null ? newGroupIdList[0] : null;
        mitro.lib.MutateMembership(args, function(group, unencryptedGroupKey, response) {

          var i;
          var regenerateGroupKey = null;

          assert (null === groupName || typeof groupName === 'string');
          if (groupName !== null) {
            group.name = groupName;
          }

          // Figure out which users need to be added and deleted.
          var newacls = [];
          var oldGroupAcl = null;
          for (i in group.acls) {
            if (!group.acls[i].memberIdentity) {
              // this is an org acl.
              // you cannot remove or modify an ORG acl on a group, so forbid that.

              // if the user has not specified a group (org) id, but one was present before,
              // we copy it over.
              if (0 === newGroupIdList.length) {
                newGroupIdList.push(group.acls[i].memberGroup);
              }
              assert(group.acls[i].memberGroup == newGroupIdList[0]);
              oldGroupAcl = group.acls[i];
              // this is copied over later.
              continue;
            }

            var publicKey = keysResponse.userIdToPublicKey[group.acls[i].memberIdentity];
            if (publicKey === undefined) {
              // we must delete this user (since we didn't fetch a key for him)
              regenerateGroupKey = true;
            } else {
              // this user is still there
              // see if we need to mutate permissions
              var newLevel = identityPermissionMap[group.acls[i].memberIdentity];
              if (newLevel && newLevel != group.acls[i].level) {
                console.log('mutating permission from ', group.acls[i].level, ' to ', newLevel, ' for ', group.acls[i].memberIdentity);
              }
              group.acls[i].level = newLevel ? newLevel : group.acls[i].level;
              newacls.push(group.acls[i]);
              // no need to keep this person's public key around any more
              delete keysResponse.userIdToPublicKey[group.acls[i].memberIdentity];
            }
          }

          var targetPublicKey;

          // add new people
          for (var k in keysResponse.userIdToPublicKey) {
            targetPublicKey = crypto().loadFromJson(keysResponse.userIdToPublicKey[k]);

            newacls.push(
            {
              myPublicKey : keysResponse.userIdToPublicKey[k],
              level: (identityPermissionMap[k] ? identityPermissionMap[k] : 'ADMIN'),  // TODO: support alternative roles.
              groupKeyEncryptedForMe: targetPublicKey.encrypt(unencryptedGroupKey.toJson()),
              memberIdentity : k
            });
          }


          // add org group if it needs to be added.
          if (!oldGroupAcl && newGroupIdList && newGroupIdList.length) {
            targetPublicKey = crypto().loadFromJson(keysResponse.groupIdToPublicKey[newGroupIdList[0]]);
            newacls.push( {
              myPublicKey : keysResponse.groupIdToPublicKey[newGroupIdList[0]],
              level: 'ADMIN', // TODO: what should this be?
              groupKeyEncryptedForMe: targetPublicKey.encrypt(unencryptedGroupKey.toJson()),
              memberGroup: newGroupIdList[0]
            });
          } else if (oldGroupAcl) {
            newacls.push(oldGroupAcl);
          }

          if (scope) {
            assert (group.scope == scope);
          }

          group.acls = newacls;
          if (!regenerateGroupKey) {
            group.secrets = null;
          }
          return regenerateGroupKey;
        }, function(response) { onSuccess(response.groupId);},
        onError);

      // close GetPublicKeys
      }, onError);
      
    } catch(e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.holdingTransaction.getPublicKeys = function(transactionSpecificData, userIds, onSuccess, onError) {
    mitro.lib.GetPublicKeys(_makeArgs(transactionSpecificData), userIds, onSuccess, onError);
  };
  
  obj.holdingTransaction._addGroup = function(transactionSpecificData, groupName, autoDelete, onSuccess, onError) {
    return obj.holdingTransaction.addGroupWithScope(transactionSpecificData, groupName, null, autoDelete, onSuccess, onError);
  };
  
  obj.holdingTransaction.addGroupWithScope = function(transactionSpecificData, groupName, groupScope, autoDelete, onSuccess, onError) {
    try {
      console.log('in addgroup with args: ', transactionSpecificData, groupName, groupScope, autoDelete);
      if (privateNonOrgGroupId === null) {
        if (groupName !== '') {
          throw new Error('No private group id; must create a group without name first');
        }
      } else if (groupName === '') {
        // only permit an empty group when creating a new identity
        throw new Error('Empty group names are not permitted');
      }

      var args = _makeArgs(transactionSpecificData);
      args.scope = groupScope;
      args._ = [null, groupName];
      args.autoDelete = autoDelete;
      mitro.lib.AddGroup(args, function(response) {

        // if we are creating the initial empty group, save the id
        if (privateNonOrgGroupId === null) {
          privateNonOrgGroupId = response.groupId;
        }
        onSuccess(response.groupId);
      }, onError);
    } catch (e) {
      onError(e);
    }
  };

  /* Creates a new group with groupName. Calls onSuccess(groupId). */
  obj.holdingTransaction.addGroup = function(transactionSpecificData, groupName, onSuccess, onError) {
    return obj.holdingTransaction._addGroup(transactionSpecificData, groupName, false, onSuccess, onError);
  };

  obj.holdingTransaction.deleteSecret = function(transactionSpecificData, secretId, onSuccess, onError) {
    try {
      var rpc = {secretId: secretId, groupId:null};
      mitro.lib.PostToMitro(rpc, _makeArgs(transactionSpecificData), '/mitro-core/api/RemoveSecret', onSuccess, onError);
    } catch (e) {
      console.log(e.stack);
      onError({local_exception : e});
    }
  };

  obj.holdingTransaction.removeGroup = function(transactionSpecificData, groupId, onSuccess, onError) {
    try {
      var rpc = {groupId: groupId};
      mitro.lib.PostToMitro(rpc, _makeArgs(transactionSpecificData), '/mitro-core/api/DeleteGroup', onSuccess, onError);
    } catch (e) {
      console.log(e.stack);
      onError(mitro.lib.makeLocalException(e));
    }
  };


  var processListSites = function(response) {
    var output = mitro.fe.convertListSitesToExtension(response);

    // find our private group if we haven't already done it
    if (privateNonOrgGroupId === null) {
      for (var groupId in response.groups) {
        var group = response.groups[groupId];
        if (group.isNonOrgPrivateGroup) {
          privateNonOrgGroupId = group.groupId;
          break;
        }
      }
      if (privateNonOrgGroupId === null) {
        throw new Error('Could not find the private group for identity ' + email);
      }
    }
    return output;
  };

  obj.holdingTransaction.listSites = function(transactionSpecificData, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);

    (wrapWithFailover(mitro.lib.ListGroupsAndSecrets))(args, function(response) {
      try {
        onSuccess(processListSites(response));
      } catch (e) {
        console.log('Error:', e);
        onError(mitro.lib.makeLocalException(e));
      }
    }, onError);
  };

  obj.getDefaultOrgId = function() {
    if (!obj.organizations) {
      // User has no orgs.
      return null;
    } else {
      var defaultOrgId = null;

      // Select first org where user has admin access or first org otherwise.
      for (var orgId in obj.organizations) {
        var org = obj.organizations[orgId];

        if (org.isAdmin) {
          // Use org.id because orgId is a string.
          defaultOrgId = org.id;
          break;
        } else if (defaultOrgId === null) {
          defaultOrgId = org.id;
        }
      }

      return defaultOrgId;
    }
  };

  obj.getOrgInfo = function(onSuccess, onError) {
    var rval = {};
    console.log('mitro_fe getOrgInfo(): returning stored state');
    rval.myOrgId = obj.getDefaultOrgId();
    rval.organizations = obj.organizations;
    onSuccess(rval);
  };

  var processListGroups = function(response) {
    var groups = response.groups;
    var organizations = response.organizations;

    obj.organizations = {};

    var output = {};
    for (var groupId in groups) {
      var group = groups[groupId];
      if (!group.isNonOrgPrivateGroup) {
        if (group.isOrgPrivateGroup) {
          group.name = email;

          obj.organizations[group.owningOrgId] = {
            id: group.owningOrgId,
            name: group.owningOrgName,
            privateOrgGroupId: group.groupId,
            isAdmin: false
          };
        }
        output[group.groupId] = group;
      }
    }
    for (var orgId in organizations) {
      if (response.organizations[orgId].isRequestorAnOrgAdmin) {
        obj.organizations[orgId].isAdmin = true;
      }
    }
    return output;
  };

  obj.holdingTransaction.listGroups = function(transactionSpecificData, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);

    (wrapWithFailover(mitro.lib.ListGroupsAndSecrets))(args, function(response) {
      onSuccess(processListGroups(response));
    }, onError);
  };

  var processListUsers = function(response) {
    return response.autocompleteUsers;
  };

  obj.holdingTransaction.listUsers = function(transactionSpecificData, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);

    (wrapWithFailover(mitro.lib.ListGroupsAndSecrets))(args, function(response) {
      onSuccess(processListUsers(response));
    }, onError);
  };

  obj.holdingTransaction.listUsersGroupsAndSecrets = function(transactionSpecificData, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    (wrapWithFailover(mitro.lib.ListGroupsAndSecrets))(args, function(response) {
      try {
        var out = {
          users: processListUsers(response),
          secrets: processListSites(response),
          groups: processListGroups(response)

        };
        onSuccess(out);
      } catch (e) {
        onError(mitro.lib.makeLocalException(e));
      }
    }, onError);
  };

  obj.holdingTransaction.addSiteToOrg = function(transactionSpecificData, secretId, orgGroupId, onSuccess, onError) {
    try {
      // first get the secret.
      obj.holdingTransaction.getSiteData(transactionSpecificData, secretId, false,
        function(secret) {
          // TODO this should be merged into the share secret function for increased efficiency.
          try {
            assert (!secret.owningOrgId);
            var newGroupIds = [];
            for (var i = 0; i < secret.groups.length; ++i) {
              newGroupIds.push(secret.groups[i].groupId);
            }
            obj.holdingTransaction.shareSiteAndOptionallySetOrg(transactionSpecificData, secretId, newGroupIds,
              secret.users, orgGroupId, onSuccess, onError);
          } catch (e) {
            onError(mitro.lib.makeLocalException(e));
          }
          
      }, onError);


    } catch (e) {
      onError(mitro.lib.makeLocalException(e));
    }
  };

  obj.holdingTransaction.getSiteSecretData = function(transactionSpecificData, secretId, onSuccess, onError) {
    return obj.holdingTransaction.getSiteData(transactionSpecificData, secretId, 'true', onSuccess, onError);
  };
  
  obj.holdingTransaction.getSiteSecretDataForDisplay = function(transactionSpecificData, secretId, onSuccess, onError) {
    return obj.holdingTransaction.getSiteData(transactionSpecificData, secretId, 'display', onSuccess, onError);
  };


  obj.holdingTransaction.getSiteData = function(transactionSpecificData, secretId, includeCriticalData, onSuccess, onError) {
    // includeCriticalData is now a string "enum", not a boolean
    includeCriticalData = includeCriticalData.toString();
    assert (includeCriticalData in {'true':1, 'false':1, 'display':1});
    
    var args = _makeArgs(transactionSpecificData);
    args._ = [null, secretId];
    args.includeCriticalData = includeCriticalData;
    (wrapWithFailover(mitro.lib.GetSecret))(args, function(response) {
      var secret = {};
      secret.secretId = response.secretId;
      secret.clientData = JSON.parse(response.clientData);
      if (includeCriticalData === 'true' || includeCriticalData === 'display') {
        secret.criticalData = JSON.parse(response.criticalData);
      }
      secret.hints = {};
      secret.hints.icons = response.icons;
      secret.hints.title = response.title;
      secret.groups = response.groups;
      secret.users = response.users;
      secret.hiddenGroups = response.hiddenGroups;
      secret.groupNames = response.groupNames;
      secret.groupMap = response.groupMap;
      secret.owningOrgId = response.owningOrgId;
      // TODO: Figure out why the server is not setting owningOrgName.
      //secret.owningOrgName = response.owningOrgName;
      secret.owningOrgName = typeof secret.owningOrgId === 'number' ? secret.groupNames[secret.owningOrgId] : undefined;
      secret.canEditServerSecret = response.canEditServerSecret;
      secret.isViewable = response.isViewable;
      secret.creator = response.creator;
      secret.groupIdToPublicKeyMap = response.groupIdToPublicKeyMap;
      onSuccess(secret);
    }, onError);
  };

  obj.holdingTransaction.getAuditLog = function (transactionSpecificData, data, onSuccess, onError) {
    var args = _makeArgs(transactionSpecificData);
    args.orgId = data.orgId;
    args.offset = data.offset;
    args.limit = data.limit;
    args.startTimeMs = data.startTimeMs;
    args.endTimeMs = data.endTimeMs;

    var processAuditEvents = function (auditResponse, secrets, groups, onSuccess) {
      var events = auditResponse.events;
      for (var i = 0; i < events.length; i++) {
        var event = events[i];
        var secret = secrets[event.secretId];

        if (secret) {
          // TODO: use renderedTitle.
          if (secret.clientData && secret.clientData.title !== null && secret.clientData.title !== undefined) {
            event.secretTitle = secret.clientData.title;
          } else if (secret.hints && secret.hints.title) {
            event.secretTitle = secret.hints.title;
          } else if (secret.clientData) {
            event.secretTitle = getCanonicalHost(secret.clientData.loginUrl) || '';
          } else {
            event.secretTitle = '';
          }
        }

        var group = groups[event.groupId];
        if (group) {
          event.groupName = group.name;
        }
      }
      onSuccess(auditResponse);
    };

    mitro.lib.GetAuditLog(args, function (auditResponse) {
      if (data.orgId === null) {
        obj.holdingTransaction.listSites(transactionSpecificData, function(secrets) {
          obj.holdingTransaction.listGroups(transactionSpecificData, function (groups) {
            var secretsMap = {};
            for (var i = 0; i < secrets.length; i++) {
              secretsMap[secrets[i].secretId] = secrets[i];
            }

            processAuditEvents(auditResponse, secretsMap, groups, onSuccess);
          }, onError);
        }, onError);
      } else {
        obj.holdingTransaction.getOrganizationState(transactionSpecificData, data.orgId, function (org) {
          var secrets = org.orgSecretsToPath;
          for (var secretId in org.orphanedSecretsToPath) {
            secrets[secretId] = org.orphanedSecretsToPath[secretId];
          }
          processAuditEvents(auditResponse, secrets, org.groups, onSuccess);
        }, onError);
      }
    }, onError);
  };

  obj.holdingTransaction.createOrganization = function(
      transactionSpecificData, request, onSuccess, onError) {
    // this ignores the existing infrastructure and just calls in to mitroclient.js
    obj.mitroclient.createOrganization(
        request.name, request.owners, request.members,
        function() {
          mitro.lib.clearCaches();
          var oldArgs = Array.prototype.slice.call(arguments);
          // This list groups command refreshes the user's org id. Do not delete this line!
          obj.holdingTransaction.listGroups(transactionSpecificData, function() {
            onSuccess.apply(undefined, oldArgs);
          }, onError);
        }, onError);
  };

  obj.holdingTransaction.mutateOrganization = function(
      transactionSpecificData, request, onSuccess, onError) {
    obj.mitroclient.mutateOrganization(request, transactionSpecificData, onSuccess, onError);
  };

  function isFunction(functionToCheck) {
   var getType = {};
   return functionToCheck && getType.toString.call(functionToCheck) === '[object Function]';
  }

  /**
   * Change the password on a remote service.
   *
   * request is a dictionary with the following params:
   * {
   *   secretId: integer,
   *   newPassword: string
   * }
  */
  obj.holdingTransaction.changeRemotePassword = function(
      transactionSpecificData, request, onSuccess, onError) {
    obj.holdingTransaction.getSiteData(transactionSpecificData, request.secretId, true,
      function(secret) {
        try {
          var oldCriticalData = secret.criticalData;
          var newCriticalData = oldCriticalData;
          newCriticalData.oldPassword = newCriticalData.password;
          newCriticalData.password = request.newPassword;

          var userData = {
            secretId: request.secretId,
            userId: email,
            criticalData: newCriticalData
          };

          var userDataString = JSON.stringify(userData);
          var signature = obj.signMessage(userDataString);

          var agentRequest = {
            dataFromUser: userDataString,
            dataFromUserSignature: signature,
            url: secret.clientData.loginUrl,
            username: secret.clientData.username,
            oldCriticalData: oldCriticalData
          };

          mitro.lib.PostToMitroAgent(agentRequest, '/ChangePassword', onSuccess, onError);
        } catch(e) {
          onError(mitro.lib.makeLocalException(e));
        }
      }, onError);
  };

  var makeWrapperFunction = function(fcnName, wrappedFcn) {
    // these functions don't need transactions since they're only one api call.
    if (fcnName === 'createIdentity' ||
        fcnName === 'listSecrets' || fcnName === 'listUsers' || fcnName === "listUsersGroupsAndSecrets" ||
        fcnName === 'listGroups'  || fcnName === 'listSites' ||
        fcnName === 'retrieveDeviceSpecificKey' ||
        fcnName === 'getSiteData' || fcnName === 'getSiteSecretData' ||
        fcnName === 'createOrganization' || fcnName === 'getOrganizationState') {
      return function() {
        // TODO: change onError to clear the cache and retry before failing
        var wrappedFcnArgs = Array.prototype.slice.call(arguments);
        wrappedFcnArgs.unshift({id:null, isWriteOperation:false});
        wrappedFcn.apply(undefined, wrappedFcnArgs);
      };
    }

    return wrapWithTransactionCancelledRetry(function() {
      var wrappedFcnArgs = Array.prototype.slice.call(arguments);
      var oldOnError = wrappedFcnArgs.pop();
      var oldOnSuccess = wrappedFcnArgs.pop();
      if (!isFunction(oldOnError)) {
        console.log(oldOnError);
        console.log(fcnName);
        assert (isFunction(oldOnError));
      }

      var isWriteOperation = (fcnName.indexOf('add') === 0) ||(fcnName.indexOf('mutate') === 0) ||
                             (fcnName.indexOf('remove') === 0) || (fcnName.indexOf('edit') === 0) ||
                             (fcnName.indexOf('rm') === 0) || (fcnName.indexOf('create') === 0) ||
                             (fcnName.indexOf('delete') === 0) || (fcnName.indexOf('apply') === 0) ||
                             (fcnName.indexOf('share') === 0);

      assert (isFunction(oldOnSuccess));

      var mitroArgs = _makeArgs();
      
      assert (mitroArgs.transactionSpecificData === undefined);

      var transactionSpecificData = {id:null, isWriteOperation: isWriteOperation,
        implicitBeginTransaction: true, operationName: fcnName};

      var onSuccess = function(successResponse) {
        var afterEndTxn = function() {
          if (isWriteOperation) {
            mitro.lib.clearCaches();
            mitro.lib.postEndTransaction(transactionSpecificData);
          }
          oldOnSuccess(successResponse);
        };
        // close transaction. It's possible that there is no transaction id
        // if all the data we wanted was in the cache.
        if (transactionSpecificData.id) {
          if (!transactionSpecificData.implicitEndTransaction) {
            console.log('trying to close transaction with data:', transactionSpecificData);
            mitro.lib.PostToMitro({}, _makeArgs(transactionSpecificData), '/mitro-core/api/EndTransaction', afterEndTxn, onError);
          } else {
            console.log('transaction already closed: ', transactionSpecificData);
            afterEndTxn();
          }
        }
      };
      var onError = function(e) {
        console.log('ERROR IN TRANSACTION CODE:', e);
        // TODO: rollback transaction
        mitro.lib.clearCaches();
        mitro.lib.postEndTransaction(transactionSpecificData);
        // attempt to send log to server.
        var a = _makeArgs();
        a.type = 'exception';
        a.description = JSON.stringify(e);
        a.email = email;
        a.url = "";
        //mitro.lib.AddIssue(a, function() {}, function() {});
        oldOnError(e);
      };
      wrappedFcnArgs.unshift(transactionSpecificData);
      wrappedFcnArgs.push(onSuccess);
      wrappedFcnArgs.push(onError);
      wrappedFcn.apply(undefined, wrappedFcnArgs);
    });
  };

  // set up external interface by locking things in transactions
  for (var funcName in obj.holdingTransaction) {
    obj[funcName] = makeWrapperFunction(funcName, obj.holdingTransaction[funcName]);
  }
  return obj;
}

fe.setFailover = function(host, port) {
  FAILOVER_MITRO_HOST = host;
  FAILOVER_MITRO_PORT = port;
};
fe.setDeviceId = function(deviceId) {
  DEVICE_ID = deviceId;
};

fe.getDeviceId = function() {
  return DEVICE_ID;
};

fe.getDeviceIdAsync = function(onSuccess) {
  onSuccess(DEVICE_ID);
};


/**
 * This function wraps login and create calls and returns only clone-able objects.
 * The key is retained in the identities object and is never sent back to the client.
 */
var identities = {};
var lastIdentityId = 0;
var wrapLogin = function(wrappedFcn) {
  return function() {
    var wrappedFcnArgs = Array.prototype.slice.call(arguments);
    var oldOnError = wrappedFcnArgs.pop();
    var oldOnSuccess = wrappedFcnArgs.pop();
    var newOnSuccess = function(identity) {
      identities[++lastIdentityId] = identity;

      // TODO: this is a hack to allow TFA preferences to be accessed
      // TODO: this nonce needs to be random _every time_ not just once. But it is ignored for now.
      var token = JSON.stringify({'email' : identity.uid,
      'nonce' : Math.random().toString(36).substring(7)});
      var signature = identity.signMessage(token);
      var url_path = "/mitro-core/TwoFactorAuth/TFAPreferences?user=" + 
                encodeURIComponent(identity.getUid()) + "&token=" + 
                encodeURIComponent(token) + "&signature=" + 
                encodeURIComponent(signature);


      oldOnSuccess({identityId : lastIdentityId,
                    uid: identity.getUid(),
                    tfaAccessUrlPath: url_path,
                    verified : identity.isVerified(),
                    changePwd : identity.shouldChangePassword()});
    };
    try {
      wrappedFcnArgs.push(newOnSuccess);
      wrappedFcnArgs.push(oldOnError);
      wrappedFcn.apply(undefined, wrappedFcnArgs);
    } catch (e) {
      oldOnError(mitro.lib.makeLocalException(e));
    }
  };

};

fe.workerInvokeOnIdentity = function() {
  var args = Array.prototype.slice.call(arguments);
  var onError = args[args.length-1];
  try {
    var identityId = args.shift();
    var operation = args.shift();

    // Unnecessary to initialize to undefined
    var appliedThisPointer;
    identities[identityId.identityId][operation].apply(appliedThisPointer, args);
    fe.getRandomness(function(data) {
      forge.random.collect(data.seed);
      console.log('added randomness...');
    }, function() {
      console.log('error adding randomness');
    });

  } catch (e) {
    onError(mitro.lib.makeLocalException(e));
  }
};

fe.workerLogout = function(identityObject) {
  delete identities[identityObject.identityId];
};

fe.createIdentity = createIdentity;
fe.workerCreateIdentity = wrapLogin(createIdentity);
fe.workerLogin = wrapLogin(login);
fe.workerLoginWithToken = wrapLogin(loginWithToken);
fe.workerLoginWithTokenAndLocalKey = wrapLogin(loginWithTokenAndLocalKey);

fe.login = login;

fe.loginWithToken = loginWithToken;
fe.loginWithTokenAndLocalKey = loginWithTokenAndLocalKey;
fe.addIssue = addIssue;
fe.keyCache = keyCache;
fe.clearCaches = mitro.lib.clearCaches;
fe.bidirectionalSetDiff = bidirectionalSetDiff;
})();
