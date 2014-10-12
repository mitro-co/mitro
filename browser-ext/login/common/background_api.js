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

var keycache = mitro.keycache.MakeKeyCache();
mitro.keycache.startFiller(keycache);

var getNewRSAKeysAsync = function(numKeys, onSuccess, onError) {
    keycache.getNewRSAKeysAsync(numKeys, function(keys) {
        var rval = [];
        for (var i = 0; i < keys.length; ++i) {
            rval.push(keys[i].toJson());
        }
        onSuccess(rval);
    }, onError);
};

var getRandomnessFirstTime = true;
var getRandomness = function(onSuccess, onError) {
    try {
        // the first time around, we need to seed this with 32**2 bytes of randomness to
        // "fill up the pools".
        var seed = forge.random.seedFileSync(getRandomnessFirstTime ? 32*32 : 32);
        getRandomnessFirstTime = false;
        onSuccess({'seed' : seed});
    } catch (e) {
        onError(e);
    }
};

var client = new Client('background');

// we have to use unsafeWindow in firefox
var _Worker = typeof(unsafeWindow) !== 'undefined' ? unsafeWindow.Worker : Worker;

var worker = new _Worker('worker.js');
worker.addEventListener('message', function(event) {
    // make a deep copy of message
    var message = jQuery.extend(true, {}, event.data);

    message.sendResponse = function(data) {
        var new_message = client.composeResponse(this, data);
        worker.postMessage(new_message);
    };
    client.processIncoming(message);
});
client.addSender('worker', function(message){
    worker.postMessage(message);
});

// this function will be used to console.log from inside the worker
// because the webworker doesn't have such feature
var console_log = function(message) {
    // message is an array of console.log arguments
    var args = ['Web Worker console.log:'];
    args = args.concat(message);
    console.log.apply(console, args);
};

var ajax = mitro.rpc._PostToMitro;


client.initRemoteCalls('worker', [
    'signMessageAsync',
    'setExtensionId', 'setFailover', 'setDeviceId', 'getDeviceId', 'getDeviceIdAsync',
    'workerInvokeOnIdentity', 'createIdentity', 'workerCreateIdentity', 'workerLogin', 'workerLoginWithToken',
    'workerLoginWithTokenAndLocalKey', 'login', 'loginWithToken', 'loginWithTokenAndLocalKey', 'addIssue', 'initCacheFromFile',
    'initCacheFromJson', 'clearCaches', 'bidirectionalSetDiff', 'workerLogout'
    ]);

client.initRemoteExecution('worker', ['getNewRSAKeysAsync', 'console_log', 'ajax', 'getRandomness'], this);
client.setExtensionId(getExtensionId());

// try to catch loading things in the incorrect order
assert(mitro.fe);

mitro.fe = client;
var fe = mitro.fe;

var userIdentity = null;
var attemptingLogin = false;

// used in background.js by the popup (and maybe by the infobar?)
var serviceInstances = null;

var storePasswordForTwoFactor = null;
var clearPasswordCallbackId = null;

var selectedOrgId = null;

var reportError = function (onError, message) {
    console.log(message);
    if (typeof onError !== 'undefined') {
        onError(message);
    }
};

var checkTwoFactor = function (onSuccess, onError) {
	fe.workerInvokeOnIdentity(userIdentity, 'checkTwoFactor', onSuccess, onError);
};

var reportSuccess = function (onSuccess, arg) {
    if (typeof onSuccess !== 'undefined') {
        onSuccess(arg);
    }
};

var refreshTabsOnMitroLogin = function () {
    console.log('refreshTabsOnMitroLogin');
    helper.tabs.getAll(function(tabs) {
        for (var i = 0; i < tabs.length; i++) {
            var tab = tabs[i];
            var host = getCanonicalHost(tab.url);
            helper.tabs.sendMessage(tab.id,
                client.composeMessage('content',
                                      'refreshOnMitroLogin',
                                      getLoginHintsForHost(host)));
        }
    });
};

var saveLoginToken = function(identity) {
    fe.workerInvokeOnIdentity(identity, 'getLoginTokenAsync', function(loginTokenRaw) {
        var token = JSON.stringify(loginTokenRaw);
        var key = 'loginToken:' + identity.uid;
        var data = {};
        data[key] = token;
        helper.storage.local.set(data, function() {
            if (CHROME && chrome.runtime.lastError) {
                // TODO(ivan): safari and ff implementation
                console.log('error storing login token', chrome.runtime.lastError.message);
            }
        });
    }, function(e) {
        console.log('could not get login token from worker: ', e);
    });
};

var getLoginToken = function(email, callback) {
    var key = 'loginToken:' + email;
    helper.storage.local.get(key, function(r) {
        var token = r;

        try {
            if (CHROME && chrome.runtime.lastError) {
                // TODO(ivan): safari and ff implementation
                console.log('local storage error', chrome.runtime.lastError.message);
            } else {
                token = JSON.parse(r[key]);
                console.log("got login token for user " + email);
            }
        } catch (e) {
            console.log('problem getting key', (e.stack ? e.stack : ''));
        } finally {
            callback(token);
        }
    });
};

// This method saves an encrypted key for a specific user id.
// If the uid is null, it clears the last saved key (used for logout).
var saveEncryptedKey = function(uid, keystring) {
    var value = uid ? {key:keystring, uid:uid} : null;
    helper.storage.local.set({'encryptedKey':value}, function() {
        if (CHROME && chrome.runtime.lastError) {
            // TODO(ivan): safari and ff implementation
            console.log('error storing encrypted key: ' + chrome.runtime.lastError.message);
        }
    });
};

var pregenerateKeys = function(num, callback) {
    keycache.setTemporaryLimit(num, callback);
};
client.initRemoteExecution('extension', 'pregenerateKeys');

var getSiteData = function(secretId, onSuccess, onError) {
    fe.workerInvokeOnIdentity(userIdentity, 'getSiteData', secretId, false, onSuccess, onError);
};
client.initRemoteExecution('extension', 'getSiteData');

var getPendingGroupDiffs = function(identity, onSuccess, onError) {
    fe.workerInvokeOnIdentity(identity, 'getPendingGroups', null, onSuccess, onError);
};
client.initRemoteExecution('extension', 'getPendingGroupDiffs');

var commitPendingGroupDiffs = function(identity, nonce, checksum, onSuccess, onError) {
    var options = {};
    options.scope = null;
    options.nonce = nonce;
    fe.workerInvokeOnIdentity(identity, 'applyPendingGroups', options, onSuccess, onError);
};
client.initRemoteExecution('extension', 'commitPendingGroupDiffs');

var loadEncryptedKey = function(callback) {
    var key = 'encryptedKey';
    helper.storage.local.get(key, function(items) {
        if (CHROME && chrome.runtime.lastError) {
            // TODO(ivan): safari and ff implementation
            console.log('error loading encrypted key: ' + chrome.runtime.lastError.message);
        }

        if (key in items) {
            callback(items[key]);
        } else {
            callback(null);
        }
    });
};
var mitroSignup = function (username, password, rememberMe, onSuccess, onError) {
    console.log('mitroSignup');
    var onCreateIdentity = function (identity) {
        console.log('onCreateIdentity');
        setIdentity(identity);
        saveLoginToken(identity);
        if (rememberMe) {
            fe.workerInvokeOnIdentity(identity, 'getPrivateKeyStringForLocalDiskAsync', function(diskString) {
                saveEncryptedKey(identity.uid, diskString);
            }, function(e) {
                console.log('error', e);
            });
        }

        var settings = {
            username: username,
            rememberMe: rememberMe
        };
        var ignoreResult = function() {};
        saveSettingsAsync(settings, ignoreResult, ignoreResult);

        reportSuccess(onSuccess, userIdentity);
        refreshTabsOnMitroLogin();
    };

    var onCreateIdentityError = function (error) {
        reportError(onError, 'Error during signup: ' + error.userVisibleError);
    };
    
    helper.cookies.get({url: 'http://' + MITRO_HOST, name: 'glcid'}, function (cookie) {
        var analyticsId = null;

        if (cookie) {
            analyticsId = cookie.value;
        }
        fe.workerCreateIdentity(username, password, analyticsId, MITRO_HOST, MITRO_PORT, onCreateIdentity, onCreateIdentityError);
    });
};

var commonOnLoginCode = function(identity, rememberMe, onSuccess, onError) {
    console.log('onLogin: ', identity.uid);
    attemptingLogin = false;
    if (isLoggedIn()) {
        // this looks like a race condition. Abandon login efforts
        console.log('already logged in. Potential race condition? Aborted.');
        onError(new Error('already logged in'));
        return;
    }
    saveLoginToken(identity);
    setIdentity(identity);
    if (rememberMe) {
        fe.workerInvokeOnIdentity(identity, 'getPrivateKeyStringForLocalDiskAsync', function(diskString) {
            saveEncryptedKey(identity.uid, diskString);
        }, function(e) {
            console.log('error saving encrypted key', e);
        });
    }
    listUsersGroupsAndSecrets(function () {
       refreshTabsOnMitroLogin();
       onSuccess();
    }, onError);
    
    helper.addContextMenu();
};

var mitroLogin = function (username, password, onSuccess, onError, onTwoFactorAuth, tokenString, tokenSignature, rememberMe,
    tfaCode) {
    console.log('mitroLogin');
    attemptingLogin = true;
    if (tokenString && tokenSignature && storePasswordForTwoFactor) {
	password = storePasswordForTwoFactor;
	storePasswordForTwoFactor = null;
	if (clearPasswordCallbackId) {
        clearTimeout(clearPasswordCallbackId);
    }
	clearPasswordCallbackId = null;
    }

    var onLogin = function(identity) {
        try {
            commonOnLoginCode(identity, rememberMe, function() {
                reportSuccess(onSuccess, userIdentity);
            }, onError);
        } catch (e) {
            onError(e);
        }
    };

    var onLoginError = function (error) {
      attemptingLogin = false;
      if (onTwoFactorAuth && error.exceptionType === 'DoTwoFactorAuthException') {
        // store password in a global field
		// automatically remove it after 10 minutes.
		storePasswordForTwoFactor = password;
		clearPasswordCallbackId = setTimeout(
            function() {
                storePasswordForTwoFactor = null;
            },
            1000*60*10);
        onTwoFactorAuth(error.rawMessage);
      } else {
        reportError(onError, error);
      }
    };
    var doRequest = function(token) {
        if (!token) {
            fe.workerLogin(username, password, MITRO_HOST, MITRO_PORT, tfaCode, onLogin, onLoginError);
        } else {
            fe.workerLoginWithToken(username, password, token, MITRO_HOST, MITRO_PORT, tfaCode, onLogin, onLoginError);
        }
    };

    if (tokenString && tokenSignature) {
        var tk = {'loginToken' : tokenString, 'loginTokenSignature' : tokenSignature};
        doRequest(tk);
    } else {
        getLoginToken(username, doRequest);
    }
};

var mitroLogout = function (onSuccess, onError) {
    console.log('mitroLogout');
    
    // This will clear the state in the worker
    fe.workerLogout(userIdentity);

    // This will clear state in the background context.
    setIdentity(null);
    saveEncryptedKey(null, null);
    serviceInstances = null;
    selectedOrgId = null;
    // these variables no longer exist
    // pendingRequests = {};
    // partialFormRecorder = {};
    formRecorder = {};

    helper.removeContextMenu();
    
    // TODO: Record the logout on the server.
    reportSuccess(onSuccess, null);
};

var isLoggedIn = function () {
    return !!userIdentity;
};

var isAttemptingLogin = function () {
    return attemptingLogin;
};

var changePassword = function (oldPassword, newPassword, up, onSuccess, onError) {
    if (!isLoggedIn() || up.token === null || up.token_signature === null) {
        reportError(onError, 'Cannot change password; not logged in');
        return;
    }

    var onMutatePrivateKeyPassword = function (response) {
        // clear the "must change password" flag: we just did!
        // TODO: reload the identity to ensure the server cleared this flag?
        userIdentity.changePwd = false;
        reportSuccess(onSuccess, response);
    };

    var onMutatePrivateKeyPasswordError = function (error) {
        reportError(onError, 'Error changing password: ' + error.userVisibleError);
    };
    if (oldPassword === null) {
        fe.workerInvokeOnIdentity(userIdentity, 'mutatePrivateKeyPasswordWithoutOldPassword', {newPassword:newPassword, up:up}, onMutatePrivateKeyPassword, onMutatePrivateKeyPasswordError);
    } else {
        fe.workerInvokeOnIdentity(userIdentity, 'mutatePrivateKeyPassword', oldPassword, newPassword, up, onMutatePrivateKeyPassword, onMutatePrivateKeyPasswordError);
    }
};

var getIdentity = function (onSuccess, onError) {
    reportSuccess(onSuccess, userIdentity);
};

var getLoginState = function (onSuccess, onError) {
  var result = {
    identity: userIdentity,
    attemptingLogin: attemptingLogin
  };
  reportSuccess(onSuccess, result);
};

var updateIconState = function () {
    var LOGGED_OUT_ICONS = {'19': 'img/mitro_logo_gray-19.png',
                            '32': 'img/mitro_logo_gray-32.png',
                            '38': 'img/mitro_logo_gray-38.png'};
    var LOGGED_IN_ICONS = {'19': 'img/mitro_logo-19.png',
                           '32': 'img/mitro_logo-32.png',
                           '38': 'img/mitro_logo-38.png'};

    var icons = isLoggedIn() ? LOGGED_IN_ICONS : LOGGED_OUT_ICONS;
    helper.setIcon({path: icons});
};

var setIdentity = function (identity) {
    userIdentity = identity;
    updateIconState();
};

var listUsersGroupsAndSecrets = function (onSuccess, onError) {
    console.log('trying to fetch services from ' + MITRO_HOST + '...');

    if (!isLoggedIn()) {
        reportError(onError, 'Cannot fetch services; not logged in');
        return;
    }

    var onDoneOk = function (response) {
        console.log('fetched ' + response.secrets.length + ' services');
        console.log('fetched ' + response.users.length + ' users');
        console.log('fetched ' + response.groups.length + ' groups');
        serviceInstances = response.secrets;
        reportSuccess(onSuccess, response);
    };

    var onDoneError = function (error) {
        reportError(onError, 'Failed to fetch services: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'listUsersGroupsAndSecrets', onDoneOk, onDoneError);
};


var internalGetSiteSecretData = function (fcn, secretId, onSuccess, onError) {
    console.log('getSiteSecretData: ' + secretId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onGetSiteSecretData = function (data) {
        reportSuccess(onSuccess, data);
    };

    var onGetSiteSecretDataError = function (error) {
        reportError(onError, 'Error getting site secret data: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, fcn, secretId, onGetSiteSecretData, onGetSiteSecretDataError);
};
var getSiteSecretData = function (secretId, onSuccess, onError) {
    return internalGetSiteSecretData('getSiteSecretData', secretId, onSuccess, onError);
};
var getSiteSecretDataForDisplay = function (secretId, onSuccess, onError) {
    return internalGetSiteSecretData('getSiteSecretDataForDisplay', secretId, onSuccess, onError);
};


var addSecretToGroups = function (data, onSuccess, onError) {
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }
    if (!data.clientData || !data.criticalData || !data.groupIds || data.groupIds.length === 0) {
        reportError(onError, 'invalid data');
        return;
    }

    var onAddSecret = function (secretId) {
        console.log('secret added to groups successfully');
        onSuccess(secretId);
    };

    var onAddSecretError = function (error) {
        reportError(onError, 'Error saving secret: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'addSecrets', data, onAddSecret, onAddSecretError);
};


var addSecret = function (data, onSuccess, onError) {
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var serverData = data.serverData;
    var clientData = data.clientData;
    var secretData = data.secretData;

    var loginUrl = 'none';
    if ('loginUrl' in serverData) {
        loginUrl = serverData.loginUrl;
    }

    var onAddSecret = function (secretId) {
        console.log('secret added successfully');
        onSuccess(secretId);
    };

    var onAddSecretError = function (error) {
        reportError(onError, 'Error saving secret: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'addSecret', loginUrl, clientData, secretData, onAddSecret, onAddSecretError);
};
var editSecret = function (data, onSuccess, onError) {
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onEditSecret = function (secretId) {
        console.log('secret edited successfully');
        onSuccess(secretId);
    };

    var onEditSecretError = function (error) {
        reportError(onError, 'Error saving secret: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'mutateSecret', data.secretId, data.serverData, data.clientData,
                              data.secretData, onEditSecret, onEditSecretError);
};

var addSite = function (loginData, onSuccess, onError) {
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var loginPage = loginData.before_page;
    var usernameField = loginData.usernameField;
    var passwordField = loginData.passwordField;
    console.log('adding site for ', loginPage, usernameField, passwordField);

    var onAddSite = function (secretId) {
        console.log('service added successfully');
        listUsersGroupsAndSecrets(function() { onSuccess(secretId);}, onError);
    };

    var onAddSiteError = function (error) {
        reportError(onError, 'Error saving password from ' + loginPage + ': ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'addSite', loginPage,
                         usernameField.value,
                         passwordField.value,
                         usernameField.name,
                         passwordField.name,
                         onAddSite,
                         onAddSiteError);
};

var removeSecret = function (secretId, onSuccess, onError) {
    console.log('removeSecret: ' + secretId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onRemoveSecret = function (data) {
        reportSuccess(onSuccess, data);
    };

    var onRemoveSecretError = function (error) {
        reportError(onError, 'Error removing secret: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'deleteSecret', secretId, onRemoveSecret, onRemoveSecretError);
};

var editSiteShares = function (siteData, onSuccess, onError) {
    var secretId = siteData.secretId;
    var groupIdList = siteData.groupIdList;
    var identityList = siteData.identityList;
    var orgGroupId = siteData.orgGroupId;

    console.log('editSiteShares: ' + secretId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onShareSite = function (data) {
        reportSuccess(onSuccess, data);
    };

    var onShareSiteError = function (error) {
        reportError(onError, 'Error editing site share list: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'shareSiteAndOptionallySetOrg', secretId, groupIdList, identityList, orgGroupId, onShareSite, onShareSiteError);
};

var getGroup = function (groupId, onSuccess, onError) {
    console.log('getGroup: ' + groupId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onGetGroup = function (group) {
        console.log('got group successfully');
        reportSuccess(onSuccess, group);
    };

    var onGetGroupError = function (error) {
        reportError(onError, 'Error getting group: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'getGroup', groupId, onGetGroup, onGetGroupError);
};

var addGroup = function (groupName, onSuccess, onError) {
    console.log('addGroup: ' + groupName);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onAddGroup = function (groupId) {
        console.log('group created successfully');
        reportSuccess(onSuccess, groupId);
    };

    var onAddGroupError = function (error) {
        reportError(onError, 'Error creating group: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'addGroup', groupName, onAddGroup, onAddGroupError);
};

var removeGroup = function (groupId, onSuccess, onError) {
    console.log('removeGroup: ' + groupId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onRemoveGroup = function (groupId) {
        console.log('group removed successfully');
        reportSuccess(onSuccess, groupId);
    };

    var onRemoveGroupError = function (error) {
        reportError(onError, 'Error removing group: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'removeGroup', groupId, onRemoveGroup, onRemoveGroupError);
};

var editGroup = function (groupData, onSuccess, onError) {
    var groupId = groupData.groupId;
    var groupName = groupData.name;
    var groupIdList = groupData.groupIdList;
    var identityList = groupData.identityList;

    console.log('editGroup: ' + groupId);

    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onMutateGroup = function (groupId) {
        console.log('group edited successfully');
        reportSuccess(onSuccess, groupId);
    };

    var onMutateGroupError = function (error) {
        reportError(onError, 'Error editing group: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'mutateGroup', groupId, groupName, groupIdList, identityList, onMutateGroup, onMutateGroupError);
};

var addIssue = function (data, onSuccess, onError) {
    console.log('addIssue');

    var onAddIssue = function (data) {
        console.log('issue reported successfully');
        reportSuccess(onSuccess, data);
    };

    var onAddIssueError = function (error) {
        reportError(onError, 'Error reporting issue: ' + error.userVisibleError);
    };

    data.logs = mitro.log.logBuffer.toString();
    fe.addIssue(data, MITRO_HOST, MITRO_PORT, onAddIssue, onAddIssueError);
};

var getAuditLog = function (data, onSuccess, onError) {
    console.log('getAuditLog');

    var onGetAuditLog = function (data) {
       console.log('audit log retrieved successfully');
       reportSuccess(onSuccess, data);
    };

    var onGetAuditLogError = function (error) {
        reportError(onError, 'Error getting audit log: ' + error.userVisibleError);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'getAuditLog', data, onGetAuditLog, onGetAuditLogError);
};

var createOrganization = function(request, onSuccess, onError) {
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    fe.workerInvokeOnIdentity(userIdentity, 'createOrganization', request, onSuccess, onError);
};

var getOrganizationInfo = function (request, onSuccess, onError) {
    console.log('getOrganizationInfo');
    if (!isLoggedIn()) {
        onError('Not logged in');
        return;
    }

    var onGetOrgInfoSuccess = function (orgInfo) {
        if (orgInfo.organizations && selectedOrgId in orgInfo.organizations) {
            // Replace default org id with selected org id.
            orgInfo.myOrgId = selectedOrgId;
        }
        onSuccess(orgInfo);
    };

    fe.workerInvokeOnIdentity(userIdentity, 'getOrgInfo', onGetOrgInfoSuccess, onError);
};

var getOrganization = function (orgId, onSuccess, onError) {
    console.log('getOrganization: ', orgId);
    if (!isLoggedIn()) {
        onError('Not logged in');
        return;
    }

    fe.workerInvokeOnIdentity(userIdentity, 'getOrganizationState', orgId, onSuccess, onError);
};

var selectOrganization = function (orgId, onSuccess, onError) {
    console.log('selectOrganization: ', orgId);
    if (!isLoggedIn()) {
        reportError(onError, 'Not logged in');
        return;
    }

    var onGetOrgInfoSuccess = function (orgInfo) {
        if ((typeof orgId === 'number') && (orgId in orgInfo.organizations)) {
            selectedOrgId = orgId;
            onSuccess();
        } else {
            onError({userVisibleError: 'Invalid org id: ' + orgId});
        }
    };

    fe.workerInvokeOnIdentity(userIdentity, 'getOrgInfo', onGetOrgInfoSuccess, onError);
};

var mutateOrganization = function(request, onSuccess, onError) {
    if (!isLoggedIn()) {
        onError('Not logged in');
        return;
    }

    fe.workerInvokeOnIdentity(userIdentity, 'mutateOrganization', request, onSuccess, onError);
};

var changeRemotePassword = function(request, onSuccess, onError) {
    if (!isLoggedIn()) {
        onError('Not logged in');
        return;
    }

    fe.workerInvokeOnIdentity(userIdentity, 'changeRemotePassword', request, onSuccess, onError);
};

/**
 * Add secret from selection using page url as a title.
 * To be called from the content script.
 * 
 * @param {Object} url - page url
 * @param {Object} text - selection text
 */
var addSecretFromSelection = function(url, text) {
    console.log('addSecretFromSelection: url=' + url + ', text=' + text);
    var clientData = {};
    var secretData = {};
    clientData.type = 'note';
    clientData.title = 'note for ' + getCanonicalHost(url);

    secretData.note = text;
    addSecret({
        serverData: {},
        clientData: clientData,
        secretData: secretData
    }, function(secretId) {
        console.log('SUCCESS. Successfully saved secret from selection. secretId:', secretId);
    }, function(err) {
        console.log('ERROR. Something went wrong while saving the secret from selection', err);
    });
    return true;
};
