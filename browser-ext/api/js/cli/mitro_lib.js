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

(function() {
// define mitro
if(typeof(window) !== 'undefined') {
  if (typeof(mitro) === 'undefined') {mitro = window.mitro = window.mitro || {};}
  mitro.lib = {};
}
// define node.js module
else if(typeof(module) !== 'undefined' && module.exports) {
  getExtensionId = function() { return 'node_extension_id';};
  mitro = {
    crypto : require('./crypto.js'),
    crappycrypto : require('./crappycrypto.js'),
    keycache: require('./keycache'),
    rpc : require('./rpc.js'),
    cache: require('./lru_cache.js'),
    log: require('./logging.js')
  };
  module.exports = mitro.lib = {};
}

var globalDecryptionCache = new mitro.cache.LRUCache(1024);
var txnSpecificCaches = {};

var makeLocalException = function (e) {
  try {
    console.log('local exception:', e, e.stack);
  } catch (ee) {}
  var output = {status: -1,
          userVisibleError: 'Unknown local error',
          exceptionType: 'JavascriptException',
          local_exception: e};
  if (e.userVisibleError) {
    output.userVisibleError = e.userVisibleError;
  }
  return output;
};

// cache things for ~ 1 minute.
var CACHE_TIME_MS = 1000*60*1;

var _getCache = function(args) {
  if (args._transactionSpecificData && args._transactionSpecificData.isWriteOperation && args._transactionSpecificData.id) {
    if (!(args._transactionSpecificData.id in txnSpecificCaches)) {
      txnSpecificCaches[args._transactionSpecificData.id] = new mitro.cache.LRUCache();
    }
    return txnSpecificCaches[args._transactionSpecificData.id];
  } else {
    if (txnSpecificCaches[null] === undefined) {
      txnSpecificCaches[null] = new mitro.cache.LRUCache(1024);
    }
    return txnSpecificCaches[null];
  }
};

var postEndTransaction = function(transactionSpecificData) {
  delete txnSpecificCaches[transactionSpecificData.id];
};

var clearCacheAndCall = function(f) {
  return function() {
    console.log('mitro_lib: clearing global cache');
    delete txnSpecificCaches[null];
    f.apply(null, Array.prototype.slice.call(arguments));
  };
};

var crypto = mitro.crypto;
var initForTest = function() {
  if (!mitro.crappycrypto) {
    throw new Error('crappycrypto does not exist?');
  }
  crypto = mitro.crappycrypto;
  mitro.keycache.useCrappyCrypto();
};

var getCrypto = function() {
  return crypto;
};

var lib = mitro.lib;
var assert = function(expression) {
  if (!expression) {
    throw new Error('Assertion failed');
  }
};

// General code for all modules
var PostToMitro = function(outdict, args, path, onSuccess, onError) {
  
  // include the device id in the signed portion of the request
  outdict.deviceId = args.deviceId;

  var message = {
    'identity': args.uid,
    'request': JSON.stringify(outdict)
  };

  if (args._transactionSpecificData) {
    message.operationName =  args._transactionSpecificData.operationName;
    message.transactionId = args._transactionSpecificData.id;
    message.implicitEndTransaction = args._transactionSpecificData.implicitEndTransaction;
    if (args._transactionSpecificData.implicitBeginTransaction) {
      args._transactionSpecificData.implicitBeginTransaction = false;
      message.implicitBeginTransaction = true;
    }
  }

  // GetPrivateKey cannot sign the request
  if (args._privateKey !== undefined) {
    message.signature = args._privateKey.sign(message.request);
  }

  return mitro.rpc._PostToMitro(message, args, path, function(resp) {
    if (args._transactionSpecificData && !args._transactionSpecificData.id) {
      args._transactionSpecificData.id = resp.transactionId;
    }
    onSuccess(resp);
    }, onError);
};

var PostToMitroAgent = function(request, path, onSuccess, onError) {
  var args = {
    server_host: MITRO_AGENT_HOST,
    server_port: MITRO_AGENT_PORT
  };
  return mitro.rpc._PostToMitro(request, args, path, onSuccess, onError);
};

var setPostToMitroForTest = function(replacementFunction) {
  PostToMitro = replacementFunction;
};

// TODO: Apply other password strength rules?
// Calculations from the following say a 8 char mixed-case alpha password
// takes > 1 year to crack, so that seems like a reasonable rule?
// TODO: Enforce mixed-case, numeric, or special char rules?
// http://blog.agilebits.com/2013/04/16/1password-hashcat-strong-master-passwords/
var MIN_PASSWORD_LENGTH = 8;

var EditEncryptedPrivateKey = function(args, up, newEncryptedPrivateKey, onSuccess, onError) {
  try {
	var request = {
		userId: args.uid, encryptedPrivateKey: newEncryptedPrivateKey, tfaToken: up.token, tfaSignature: up.token_signature
	};
	assert (newEncryptedPrivateKey);
	PostToMitro(request,
			args, '/mitro-core/api/EditEncryptedPrivateKey', onSuccess, onError);
  } catch(e) {
    onError(makeLocalException(e));
  }
};


var checkTwoFactor = function (args, onSuccess, onError) {
	try {
		var request = {
    userId: args.uid,
    extensionId: getExtensionId()
		};
    PostToMitro(request, args, '/mitro-core/api/ChangePwdTwoFactorRequired', onSuccess, onError);
	}
	catch (e) {
		onError(makeLocalException(e));
	}
};


/**
 * AddIdentity -- Adds a new identity with a new generated key.
 * Args:
 *   args:
 *     { uid: user id to add (string), password: password to protect the key (string) }
 *   onSuccess: function(response)
 *     callback to call; response contains privateKey and transaction id
 *
 */
var AddIdentity = clearCacheAndCall(function(args, onSuccess, onError) {
  try {
    console.log('>>Add Identity');
    // uid must be email, password must be long enough
    if (args.uid.indexOf('@') == -1) {
      throw new Error('uid does not appear to be an email address: ' + args.uid);
    }
    if (args.password.length < MIN_PASSWORD_LENGTH) {
      throw new Error('password is not long enough (' + args.password.length +
        ' characters; must be at least ' + MIN_PASSWORD_LENGTH + ' characters)');
    }

    args._keyCache.getNewRSAKeysAsync(2, function(keys) {
      var privateKey = keys[0];
      var groupKey = keys[1];
      // generate a key; encrypt it
      var request = {
        userId: args.uid,
        publicKey: privateKey.exportPublicKey().toJson(),
        encryptedPrivateKey: privateKey.toJsonEncrypted(args.password),
        analyticsId: args.analyticsId
      };

      var onSuccessWithResult = null;
      if (onSuccess) {
        onSuccessWithResult = function(response) {
          response.privateKey = privateKey;
          onSuccess(response);
        };
      }

      args._privateKey = privateKey;
      if (args._createPrivateGroup) {
        request.groupKeyEncryptedForMe = privateKey.encrypt(groupKey.toJson());
        request.groupPublicKey = groupKey.exportPublicKey().toJson();
      }
      PostToMitro(request, args, '/mitro-core/api/AddIdentity', clearCacheAndCall(onSuccessWithResult), onError);
    }, onError);
  } catch (e) {
    onError(makeLocalException(e));
  }
});


/**
 * AddGroup -- add a new group to the DB, and add me to it.
 * Args:
 *   args: 
 *     { uid : user id of the actor (string)
 *       _privateKey: an initialized Private Key object from crypto.
 *      '_' : [ name (string)]},
 *       }
 *   onSuccess: function(response)
 *     callback to call; response = {groupId:int};
 *
 */ 
var AddGroup = clearCacheAndCall(function(args, onSuccess, onError) {
  try {
    args._keyCache.getNewRSAKeysAsync(1, function(keys) {
      var newGroupKey = keys[0];
      console.log('>>Add Group');
      assert(args.uid);
      if (args._.length < 2) {
        console.log('usage: mitro.js addgroup --uid=me@example.com NAME');
        throw new Error('Incorrect aruments');
      }
      args.name = args._[1];

      var request = {
        name : args.name,
        autoDelete : args.autoDelete,
        publicKey: newGroupKey.exportPublicKey().toJson(),
        signatureString: 'TODO',
        scope : args.scope,
        'acls' : [
                  {
                    level: 'ADMIN',
                    groupKeyEncryptedForMe: args._privateKey.encrypt(newGroupKey.toJson()),
                    memberGroup:null,
                    memberIdentity: args.uid
                    }
                  ]
      };

      PostToMitro(request, args, '/mitro-core/api/AddGroup', clearCacheAndCall(onSuccess), onError);
    }, onError);
  } catch (e) {
    console.log('>> exception in add group');
    onError(makeLocalException(e));
  }
});

/**
 * TODO: this is broken and should use a different field for the actor and the public key
 * 
 * GetPublicKey -- get a specific user's public key
 * Args:
 *   args: 
 *   
 *     { uid : user id of the calling user.
 *       target_uid: user id whose public key you want (string)
 *       _privateKey: an initialized Private Key object from crypto.
 *       }
 *   onSuccess: function(response)
 *     callback to call; response = {myUserId: int, publicKey: string}
 *
 */ 
var GetPublicKey = function(args, onSuccess, onError) {
  var uids = [args.target_uid];
  GetPublicKeys(args, uids,
    function(response) {
      // TODO: this is kind of ugly. We should fix this code eventually
      response.publicKey = response.userIdToPublicKey[uids[0]];
      response.myUserId = uids[0];
      onSuccess(response);
    }, onError);
};

var GetPublicKeys = function(args, uids, onSuccess, onError) {
  return GetUserAndGroupPublicKeys(args, args.addMissingUsers, uids, null, onSuccess, onError);
};

var GetUserAndGroupPublicKeys = function(args, addMissingUsers, uids, gids, onSuccess, onError) {
  try {
    console.log('>>Get public key');
    var request = {userIds : uids, addMissingUsers: addMissingUsers, groupIds: gids};
    PostToMitro(request, args, '/mitro-core/api/GetPublicKeyForIdentity', 
      function(r) {
        //TODO: pass this back so we can prompt users
        assert (!r.missingUsers || r.missingUsers.length === 0);
        onSuccess(r);
      }, onError);
   } catch (e) {
    console.log(e.stack);
    onError(makeLocalException(e));
  }
};

var RetrieveDeviceSpecificKey = function(args, onSuccess, onError) {
  try {
    console.log('>>Retrieve Device key');
    assert(args.uid);
    var request = {
      userId: args.uid,
      extensionId: getExtensionId()
    };
    PostToMitro(request, args, '/mitro-core/api/GetMyDeviceKey', onSuccess, onError);
  } catch (e) {
    onError(makeLocalException(e));
  }
};

/**
 * Get Private key for a user
 * args {uid: user id}
 * calls onSuccess with object from  RPC.java
 */
var GetPrivateKey = function(args, onSuccess, onError) {
  try {
    console.log('>>Get private key');
    assert(args.uid);
    var request = {
      userId: args.uid,
      loginToken: args.loginToken,
      loginTokenSignature: args.loginTokenSignature,
      twoFactorCode: args.twoFactorCode,
      extensionId: getExtensionId(),
      automatic: args.automatic
    };
    PostToMitro(request, args, '/mitro-core/api/GetMyPrivateKey', onSuccess, onError);
  } catch (e) {
    onError(makeLocalException(e));
  }
};

var decryptSecretWithKeyString = function(secret, keyString, privateKeyObject) {
  var keyStr = privateKeyObject.decrypt(keyString);
  var keyObj = crypto.loadFromJson(keyStr);
  secret.clientData = keyObj.decrypt(secret.encryptedClientData);
  if (secret.encryptedCriticalData) {
    secret.criticalData = keyObj.decryptNoMemo(secret.encryptedCriticalData);
  }
  return secret;
};

var decryptSecretWithGroups = function(secret, groups, previousUnencryptedKey) {
  assert(secret);
  var path = secret.groupIdPath;
  for (var pathId in path) {
    var groupId = path[pathId];
    if (!groups[groupId]) {
      console.log("ERROR: could not get group info for (group)", groupId);
      continue;
    }
    var pvtKeyString = groups[groupId].encryptedPrivateKey;
    var keyStr = previousUnencryptedKey.decrypt(
        pvtKeyString);
    previousUnencryptedKey = crypto.loadFromJson(keyStr);
  }

  secret.clientData = previousUnencryptedKey.decrypt(secret.encryptedClientData);
  if (secret.encryptedCriticalData) {
    secret.criticalData = previousUnencryptedKey.decryptNoMemo(secret.encryptedCriticalData);
  }
  return secret;


};

var _decryptSecret = function(secretId, listGroupAndSecretsResp, userPrivateKey) {
  var secret = listGroupAndSecretsResp.secretToPath[secretId];
  var rval = decryptSecretWithGroups(secret, listGroupAndSecretsResp.groups, userPrivateKey);
  return rval;
};

/**
 * GetSecret
 * args {uid: userId, '_':['secret_id'], _privateKey: key object}
 * calls onSuccess with a secret object, with .criticalData and .clientData set
 */
var GetSecret = function(args, onSuccess, onError) {
  try {
    // This is a bit complicated. First we have to list groups and secrets, then decrypt the 
    // chain of group keys, then decrypt the secret, by re-requesting it, requesting the critical data.
    // TODO: cache this.
    assert(onSuccess);
    var includeCriticalData = args.includeCriticalData;
    if (includeCriticalData === undefined) {
      includeCriticalData = 'true';
    }
    var cacheKey = null;
    if (!includeCriticalData) {
      cacheKey = mitro.cache.makeKey('GetSecret', args.uid, args._[1]);
      var resp = _getCache(args).getItem(cacheKey);
      if (resp) {
          console.log('mitro_lib GetSecret: Found response in cache');
          onSuccess(JSON.parse(resp));
          return; // IMPORTANT DO NOT REMOVE
      }
    }
    if (args._.length < 2) {
      console.log('usage: mitro.js cat --uid=me@example.com SECRET_ID');
      process.exit(-1);
    }

    var secretId = parseInt(args._[1], 10);
    var request = {userId: args.uid, secretId: secretId, includeCriticalData:includeCriticalData};
    console.log('>>GetSecret ('+JSON.stringify(request)+')');
    PostToMitro(request, args, '/mitro-core/api/GetSecret', function(response) {
      try {
        // This has critical data now, so replace it
        var output = decryptSecretWithKeyString(response.secret, response.encryptedGroupKey, args._privateKey);
        // output groupNames: required to edit ACLs for groups we do not belong to.
        if (response.secret.groupNames) {
          output.groupNames = response.secret.groupNames;
        }
        if (response.secret.groupMap) {
          output.groupMap = response.secret.groupMap;
        }
        output.groups = [];
        for (var i in output.groupNames) {
          var groupId = parseInt(i, 10);
          output.groups.push(groupId);
        }

        if (!includeCriticalData) {
          assert(cacheKey);
          _getCache(args).setItem(cacheKey, JSON.stringify(output),
                                  {expirationAbsolute: new Date(new Date().getTime() + CACHE_TIME_MS)});
        }
        onSuccess(output);
      } catch (e) {
        onError(makeLocalException(e));
      }
    }, onError);
  } catch (e) {
    onError(makeLocalException(e));
  }

};

/**
 * ListGroupsAndSecrets 
 * 
 * args {uid: userId, _privateKey: key object}
 * 
 * calls onSuccess with object in RPC.java, but with unencrypted .clientData
 * added to every secret.
 */
var ListGroupsAndSecrets = function(args, onSuccess, onError) {
  try {
    var resp = _getCache(args).getItem(mitro.cache.makeKey('ListGroupsAndSecrets', args.uid));
    if (resp) {
        console.log('mitro_lib ListGroupsAndSecrets: Found response in cache');
        (onSuccess || mitro.rpc.DefaultResponseHandler)(JSON.parse(resp));
        return; // IMPORTANT DO NOT REMOVE
    }
    console.log('>>Get List Groups and Secrets');
    assert(args.uid);
    var request = {myUserId : args.uid};
    PostToMitro(request, args, '/mitro-core/api/ListMySecretsAndGroupKeys', function(resp) {
      var secretIds = Object.keys(resp.secretToPath);
      for (var i in secretIds) {
        _decryptSecret(secretIds[i], resp, args._privateKey);
      }
      // Cache for one minute
      _getCache(args).setItem(mitro.cache.makeKey('ListGroupsAndSecrets', args.uid),
          // TODO: this should actually be a deep copy but I have no idea how to do that in JS.
          JSON.stringify(resp),
          {expirationAbsolute: new Date(new Date().getTime() + CACHE_TIME_MS)});
      (onSuccess || mitro.rpc.DefaultResponseHandler)(resp);
    },
    onError
    );
  } catch (e) {
    console.log(e);
    console.log(e.stack);
    onError(makeLocalException(e));
  }

};

var GetOrganizationState = function(args, postArgs, onSuccess, onError) {
  var path = '/mitro-core/api/GetOrganizationState';
  var key = mitro.cache.makeKey(path, postArgs.orgId);
  var resp = _getCache(args).getItem(key);
  if (resp) {
    onSuccess(JSON.parse(resp));
  } else {
    mitro.lib.PostToMitro(postArgs, args, path, function(resp) {
      _getCache(args).setItem(key, JSON.stringify(resp),
      {expirationAbsolute: new Date(new Date().getTime() + CACHE_TIME_MS)});
      onSuccess(resp);
    }, onError);
  }
};

// mutationFunction MUST RETURN TRUE if secrets are to be re-encrypted with
// a new key
var MutateMembership = clearCacheAndCall(function(args, mutationFunction, onSuccess, onError) {
  try {
    args.orgId = parseInt(args.orgId, 10);
    assert(onSuccess);
    assert(onError);
    console.log('mitro_lib MutateMembership(); onSuccess is truthy?', !!onSuccess);

    assert(args.gid);
    args.includeCriticalData = true;
    GetGroup(args, function(group) {
      // get the unencrypted group key.
      var unencryptedOldGroupKey = null;
      for (var i in group.acls) {
        var acl = group.acls[i];
        // make sure we're not already in the ACL (no duplicates!)
        if (acl.memberIdentity === args.uid) {
          // it's me!
          unencryptedOldGroupKey = crypto.loadFromJson(args._privateKey.decrypt(acl.groupKeyEncryptedForMe));
        }
      }
      //
      var doRest = function(unencryptedOldGroupKey) {
        try {
          var reEncrypt = mutationFunction(group, unencryptedOldGroupKey);
          if (!reEncrypt) {
            PostToMitro(group, args, '/mitro-core/api/EditGroup', clearCacheAndCall(onSuccess), onError);
          } else {
            assert (unencryptedOldGroupKey);
            assert (group.secrets !== null);
            // we need to re-encrypt everything with a new key
            args._keyCache.getNewRSAKeysAsync(1, function(keys) {
              try {
                var newGroupKey = keys[0];
                for (var i in group.acls) {
                  var userPublicKey = crypto.loadFromJson(group.acls[i].myPublicKey);
                  group.acls[i].groupKeyEncryptedForMe = userPublicKey.encrypt(newGroupKey.toJson());
                }
                // re-encrypt all the secret data with the new key
                for (i in group.secrets) {
                  group.secrets[i].encryptedClientData = newGroupKey.encrypt(
                      unencryptedOldGroupKey.decrypt(group.secrets[i].encryptedClientData));
                  group.secrets[i].encryptedCriticalData = newGroupKey.encrypt(
                      unencryptedOldGroupKey.decrypt(group.secrets[i].encryptedCriticalData));
                }
                group.publicKey = newGroupKey.exportPublicKey().toJson();
                PostToMitro(group, args, '/mitro-core/api/EditGroup', clearCacheAndCall(onSuccess), onError);
              } catch (e) {
                onError(makeLocalException(e));
              }
            }, onError);
          }
        } catch (e) {
          onError(makeLocalException(e));
        }
      };

      if (unencryptedOldGroupKey === null && args.orgId) {
        // see if we can access this data through the top level org group (for admins)
        
        var groupAcl = null;
        for (i in group.acls) {
          groupAcl = group.acls[i];
          args.gid = null;
          // make sure we're not already in the ACL (no duplicates!)
          if (parseInt(groupAcl.memberGroup, 10) === args.orgId) {
            args.gid = args.orgId;
            break;
          }
        }

        GetGroup(args, function(org) {
          var unencryptedOldGroupKey = null;
          for (var i in org.acls) {
            var orgAcl = org.acls[i];
            // make sure we're not already in the ACL (no duplicates!)
            if (orgAcl.memberIdentity === args.uid) {
              // it's me!
              var unencryptedOrgGroupKey = crypto.loadFromJson(args._privateKey.decrypt(orgAcl.groupKeyEncryptedForMe));
              unencryptedOldGroupKey = crypto.loadFromJson(unencryptedOrgGroupKey.decrypt(groupAcl.groupKeyEncryptedForMe));
            }
          }
          doRest(unencryptedOldGroupKey);
        }, onError);

      } else {
        doRest(unencryptedOldGroupKey);
      }
    }, onError);
  }  catch (e) {
    onError(makeLocalException(e));
  }
});

/**
 * AddMember
 * 
 * args {uid: userId, gid: group_id, target_uid: target user, 
 *       _privateKey: key object for me,
 *       _targetPublicKey: key object for the target, 
 * calls onSuccess with : see RPC.java
 */
var AddMember = clearCacheAndCall(function(args, onSuccess, onError) {
  MutateMembership(args, function(group, unencryptedGroupKey) {
    assert(unencryptedGroupKey);
    group.acls.push(
        {
          level: 'ADMIN',
          groupKeyEncryptedForMe: args._targetPublicKey.encrypt(unencryptedGroupKey.toJson()),
          memberIdentity : args.target_uid
        }
        );
    group.secrets = null;
    return false;
  }, onSuccess, onError);
});

/**
 * RemoveMember
 * 
 * args {uid: userId, gid: group_id, target_uid: target user, 
 *       _privateKey: key object for me,
 *       _targetPublicKey: key object for the target, 
 * calls onSuccess with: see RPC.java
 */
var RemoveMember = clearCacheAndCall(function(args, onSuccess, onError) {
  args.includeCriticalData = true;
  MutateMembership(args, function(group, unencryptedOldGroupKey) {
    var newacls = [];
    for (var i in group.acls) {
      var acl = group.acls[i];
      if (acl.memberIdentity === args.target_uid) {
        continue;
      }
      newacls.push(acl);
    }
    assert((newacls.length + 1) === group.acls.length);
    group.acls = newacls;
    return true;
  }, onSuccess, onError);
});

/**
 * GetGroup
 * 
 * args {uid: userId, gid: group_id
 *       _privateKey: key object for me,
 * calls onSuccess with: see RPC.java
 */
var GetGroup = function(args, onSuccess, onError) {
  try {
    assert(args.gid);
    var request = {groupId : args.gid, userId: args.uid, includeCriticalData: args.includeCriticalData};
    var resp = _getCache(args).getItem(mitro.cache.makeKey('GetGroup', args.uid, args.gid, args.includeCriticalData));
    if (resp) {
        console.log('mitro_lib GetGroup: Found response in cache');
        onSuccess(JSON.parse(resp));
        return; // IMPORTANT DO NOT REMOVE
    }
    PostToMitro(request, args, '/mitro-core/api/GetGroup', function(resp) {
      _getCache(args).setItem(mitro.cache.makeKey('GetGroup', args.uid, args.gid, args.includeCriticalData),
        JSON.stringify(resp),
        {expirationAbsolute: new Date(new Date().getTime() + CACHE_TIME_MS)});
      onSuccess(resp);
    }, onError);
  } catch (e) {
    onError(makeLocalException(e));
  }

};


var AddSecrets = clearCacheAndCall(function(args, data, onSuccess, onError) {
  try {
    if (data.groupIds.length === 0) {
      onSuccess();
      return;
    }
    var _AddSecretToGroups = function(clientSecret, criticalSecret) {
      GetUserAndGroupPublicKeys(args, false, [], data.groupIds, function(keys) {
        var toRun = [];
        try {

          //Used to prevent creating a function within a loop
          var messageFunction = function(response, onSuccess, onError){
            for (var j = 2; j < toRun.length; j++) {
              toRun[j][1][0].secretId = response.secretId;
            }
            onSuccess();
          };
          
          for (var i = 0; i < data.groupIds.length; ++i) {
            var gid = data.groupIds[i];
            var publicKeyString = keys.groupIdToPublicKey[gid];
            console.log("getting key for ", gid);
            assert(publicKeyString);
            var groupPublicKey = crypto.loadFromJson(publicKeyString);
            var secretId = data.secretId; // could be undefined, this is OK (!)
            assert (groupPublicKey);
            var request = {myUserId: args.uid, ownerGroupId : gid,
                     encryptedClientData: groupPublicKey.encrypt(clientSecret),
                     encryptedCriticalData: groupPublicKey.encrypt(criticalSecret),
                     secretId: secretId
                     };
            toRun.push([PostToMitro, [request, args, '/mitro-core/api/AddSecret']]);
            // if we are adding a new secret, set the secret id for the subsequent requests
            // TODO: Ideally this API should do this with one server request
            if (data.secretId === undefined && i === 0) {
              toRun.push([messageFunction, [undefined]]);
            }
          }
          series(toRun, clearCacheAndCall(onSuccess), onError);
        } catch (e) {
          onError(makeLocalException(e));
        }
      }, onError);
    };

    if (data.secretId) {
      // TODO: this is so gross I want to cry.
      args._ = [null, data.secretId];
      GetSecret(args, function (response){
        _AddSecretToGroups(response.clientData, response.criticalData);
      }, onError);
    } else {
      assert(data.clientData);
      assert(data.criticalData);
      _AddSecretToGroups(data.clientData, data.criticalData);
    }


  } catch (e) {
    onError(makeLocalException(e));
  }
});
/**
 * AddSecret
 * 
 * args {uid: userId, gid: group_id, 
 *       _privateKey: key object for me,
 *       secretId: If provided, adds an existing secret to a new group
 *       '_' : ['hostname', 'client secret', 'critical secret'],
 * 
 *       if chainedValue is set and group id is not, it is used in place of group id.

 * calls onSuccess with: see RPC.java
 */
var AddSecret = clearCacheAndCall(function(args, gid, onSuccess, onError) {

  try {

    // this is a horrible hack.
    if (onError === undefined) {
      onError = onSuccess;
      onSuccess = gid;
      gid = undefined;
    }
    if (args.gid === undefined) {
      args.gid = gid;
      console.log('set gid to ' + gid);
    }
    assert(args.gid);

    var wrappedOnSuccess = function(results) {
      try {
        onSuccess(results[0]);
      } catch (e) {
        onError(makeLocalException(e));
      }
    };

    if (args.secretId) {
      AddSecrets(args, {groupIds : [args.gid], secretId : args.secretId}, wrappedOnSuccess, onError);
    } else if (args._.length < 3) {
      console.log('usage: mitro.js add --uid=me@example.com --gid=21 HOSTNAME client critical');
      process.exit(-1);
    } else {
      AddSecrets(args, 
        {groupIds : [args.gid], 
        secretId : args.secretId,
        clientData: args._[2], 
        // TODO: WTF?
        criticalData: (args._.length === 4) ? args._[3] : null},
        wrappedOnSuccess, onError);
    }
  } catch (e) {
    onError(makeLocalException(e));
  }

});


/**
 * RemoveSecret
 * 
 * args {uid: userId, 
 *       gid: if provided, remove a secret from only this group
 *       _privateKey: key object for me,
 *       '_' : ['secret id'],
 * calls onSuccess with: see RPC.java
 */
var RemoveSecret = clearCacheAndCall(function(args, onSuccess, onError) {
  try {
    assert(args.uid);
    var secretId;
    if (args._) {
      if (args._.length < 2) {
        console.log('usage: mitro.js rm --uid=me@example.com SECRET_ID');
        process.exit(-1);
      }
      secretId = parseInt(args._[1], 10);
    } else {
      assert (args.secretId);
      secretId = args.secretId;
    }
    var request = {myUserId: args.uid, secretId:secretId, groupId: args.gid};
    PostToMitro(request, args, '/mitro-core/api/RemoveSecret', clearCacheAndCall(onSuccess), onError);
  } catch (e) {
    onError(makeLocalException(e));
  }

});

/**
 * AddIssue - report a new issue and add to the DB.
 * Args:
 *   args: 
 *     { url: url where the issue appeared, if any (string)
 *       type: type of issue (string)
 *       description: user description of issue (string)
 *       email : user id of the user reporting the issue (string)
 *     }
 *   onSuccess: function(response)
 *     callback to call
 *
 */ 
var AddIssue = function(args, onSuccess, onError) {
  try {
    console.log('>>Add Issue');
    PostToMitro(args, args, '/mitro-core/api/AddIssue', onSuccess, onError);
  } catch (e) {
    console.log('>> exception in add issue');
    onError(makeLocalException(e));
  }
};

var GetAuditLog = function (args, onSuccess, onError) {
  try {
    console.log('>>Get Audit Log');
    var request = args;
    PostToMitro(request, args, '/mitro-core/api/GetAuditLog', onSuccess, onError);
  } catch (e) {
    console.log('>> exception in get audit log');
    onError(makeLocalException(e));
  }
};

var runCommandWithPrivateKey = function(cmdFcn, argv, unencryptedPrivateKey, onSuccessIn, onErrorIn) {

    var onError = function(e) {
      console.log('ERROR IN TRANSACTION CODE:', e.message, e.stack);
      onErrorIn(mitro.lib.makeLocalException(e));
    };

  argv._privateKey = unencryptedPrivateKey;
  PostToMitro({}, argv, '/mitro-core/api/BeginTransaction', function(txResp) {
    var onSuccess = function(successResponse) {
      // close transaction
      console.log('trying to close transaction');
      PostToMitro({}, argv, '/mitro-core/api/EndTransaction', function(etResp) {
        postEndTransaction(argv._transactionSpecificData.id);
        onSuccessIn(successResponse);
      }, onError);
    };


    argv._transactionSpecificData = {id:txResp.transactionId, isWriteOperation: false};

    if (argv.target_uid) {
      // we need to get the private key
      var args = {
        uid: argv.uid,
        target_uid: argv.target_uid,
        deviceId: argv.deviceId,
        _transactionSpecificData: argv._transactionSpecificData,
        '_privateKey': unencryptedPrivateKey,
        server_host: argv.server_host,
        server_port: argv.server_port,
        _keyCache : argv._keyCache
      };

      GetPublicKey(args, function(pubResponse) {
        var targetKey = crypto.loadFromJson(pubResponse.publicKey);
        argv._targetPublicKey = targetKey;
        cmdFcn(argv, onSuccess, onError);
      }, onError);
    } else {
      cmdFcn(argv, onSuccess, onError);
    }
  }, onError);
  return true;
};


var runCommand = function(cmdFcn, argv, onSuccess, onError) {
    var success = false;
    onSuccess = onSuccess || mitro.rpc.DefaultResponseHandler;
    onError = onError || mitro.rpc.DefaultErrorHandler;

    argv._keyCache = argv._keyCache || mitro.keycache.MakeKeyCache();
    if (cmdFcn) {
      if (cmdFcn !== AddIdentity && cmdFcn !== GetPrivateKey) {
        // get the current user's private key and pass it along.
        // TODO: read this from a cache or something
        GetPrivateKey(argv, function(response) {
          var myKey = crypto.loadFromJson(response.encryptedPrivateKey, argv.password);
          runCommandWithPrivateKey(cmdFcn, argv, myKey, onSuccess, onError);
        },
        onError);
      } else {
        cmdFcn(argv, onSuccess, onError);

      }
    success = true;
  }
  return success;
};


var parallel = function(fcnArgListTuple, onSuccess, onError) {
  if (fcnArgListTuple.length === 0) {
    setTimeout(function() {onSuccess([]);}, 0);
    return;
  }
  var rvals = {};
  var success = 0;
  var error = false;
  var called = false;
  var _success = function(i, response) {
    if (error) {
      return;
    }
    assert(rvals[i] === undefined);
    rvals[i] = response;
    ++success;
    if (success === fcnArgListTuple.length) {
      assert (!called);
      called = true;
      var rvlist = [];
      for (var j = 0; j < success; ++j) {
        rvlist.push(rvals[j]);
      }
      onSuccess(rvlist);
    }
  };
  var _error = function(response) {
    if (error) // only first error gets called
      return;
    called = error = true;
    onError(response);
  };
  var makeCallback = function(counter) {
    return function(response) {_success(counter, response);};
  };

  for (var i in fcnArgListTuple) {
    var myargs = fcnArgListTuple[i][1];
    if (fcnArgListTuple[i][2] === undefined || fcnArgListTuple[i][2] === undefined) {
      myargs.push(makeCallback(i));
      myargs.push(_error);
    } else {
      // this command should fail
      myargs.push(_error);
      myargs.push(makeCallback(i));
    }
    fcnArgListTuple[i][0].apply(undefined, myargs);
  }
};

/**
@param {!Array} fcnArgListTuple
@param {function(*)} onSuccess
@param {function(Error)} onError
@param {*=} chainedArg
*/
var series = function(fcnArgListTuple, onSuccess, onError, chainedArg) {
  var labels = [];
  for (var i in fcnArgListTuple) {
    if (typeof(fcnArgListTuple[i][0]) === 'string' || typeof(fcnArgListTuple[i][0]) === 'number') {
      // assume this is a label
      labels[i] = String(fcnArgListTuple[i][0]);
      fcnArgListTuple[i].shift();
    }
  }
  return _series(labels, fcnArgListTuple, onSuccess, onError, chainedArg);
};


var _series = function(labels, fcnArgListTuple, onSuccess, onError, chainedArg) {
  if (fcnArgListTuple.length === 0) {
    setTimeout(function() {onSuccess([]);}, 0);
    return;
  }
  var rvals = [];
  var rvalMap = {};
  var functions = [];
  var _success = function(response) {
    if (labels[rvals.length]) {
      console.log('*** FINISHED ', labels[rvals.length]);
      rvalMap[labels[rvals.length]] = response;
    }

    rvals.push(response);

    var fcnargs = functions.pop();
    if (fcnargs !== undefined) {
      // replace any undefined value with the chained value or push to the end.
      for (var i in fcnargs[1]) {
        if (fcnargs[1][i] === undefined) {
          fcnargs[1][i] = response;
          response = undefined;
          break;
        }
      }
      ///
      fcnargs[0].apply(undefined, fcnargs[1]);
    } else {
      onSuccess(rvals, rvalMap);
    }
  };
  
  var _error = onError;

  for (var i in fcnArgListTuple) {
    var myargs = fcnArgListTuple[i][1];
    if (fcnArgListTuple[i][2] === undefined || fcnArgListTuple[i][2] === undefined) {
      myargs.push(_success);
      myargs.push(_error);
    } else {
      // this command should fail
      myargs.push(_error);
      myargs.push(_success);
    }
    functions.push([fcnArgListTuple[i][0], myargs]);
  }
  functions.reverse();
  var fcnargs = functions.pop();
  
  // replace any undefined value with the chained value or push to the end.
  for (i in fcnargs[1]) {
    if (fcnargs[1][i] === undefined) {
      fcnargs[1][i] = chainedArg;
      chainedArg = undefined;
      break;
    }
  }
  ///

  fcnargs[0].apply(undefined, fcnargs[1]);
};

// TODO: implement batch operations in Java
var batch = series;

lib.parallel = parallel;
lib.series = series;
lib.batch = batch;

lib.GetGroup = GetGroup;
lib.GetSecret =  GetSecret;
lib.GetPrivateKey =  GetPrivateKey;
lib.GetPublicKey =  GetPublicKey;
lib.GetPublicKeys =  GetPublicKeys;
lib.GetUserAndGroupPublicKeys = GetUserAndGroupPublicKeys;
lib.MutateMembership = MutateMembership;
lib.AddSecret = AddSecret;
lib.AddSecrets = AddSecrets;

lib.RetrieveDeviceSpecificKey = RetrieveDeviceSpecificKey;
lib.AddMember =  AddMember;
lib.AddGroup =  AddGroup;
lib.AddIdentity =  AddIdentity;
lib.RemoveSecret = RemoveSecret;
lib.RemoveMember =  RemoveMember;
lib.ListGroupsAndSecrets = ListGroupsAndSecrets;
lib.GetOrganizationState = GetOrganizationState;
lib.AddIssue = AddIssue;
lib.GetAuditLog = GetAuditLog;
lib.runCommand = runCommand;
lib.runCommandWithPrivateKey = runCommandWithPrivateKey;

lib.initForTest = initForTest;
lib.postEndTransaction = postEndTransaction;
lib.getCrypto = getCrypto;
lib.PostToMitro = PostToMitro;
lib.PostToMitroAgent = PostToMitroAgent;
lib.setPostToMitroForTest = setPostToMitroForTest;
lib.decryptSecretWithGroups = decryptSecretWithGroups;
lib.EditEncryptedPrivateKey = EditEncryptedPrivateKey;
lib.clearCaches = function() {delete txnSpecificCaches[null];};
lib.makeLocalException = makeLocalException;

lib.checkTwoFactor = checkTwoFactor;
})();
