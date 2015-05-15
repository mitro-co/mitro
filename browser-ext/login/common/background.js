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


/**
 * Setting up the worker, binding the client
 */

// once every 20 minutes.
var SERVICE_LIST_REFRESH_PERIOD = 1000 * 60 * 20;

var helper = new helpers_background.BackgroundHelper();
helper.bindClient(client);

// Stores login details. Returned when the content script requests it.
var pendingLoginRequests = {};

// Map of recorded form urls.
var formRecorder = {};

var settings = {};
var LOGIN_STORE_KEY_PREFIX = 'last_used_service_for_host:';
var serviceForHostCache = {};

var populateLastUsedServiceCache = function() {
    // TODO: this should be null but that would requiring changing
    // the stuff in helpers.js (safari)
    helper.storage.local.get(undefined, function(items) {
        var loaded = 0;
        for (var key in items) {
            if (key.indexOf(LOGIN_STORE_KEY_PREFIX) === 0) {
                serviceForHostCache[key.substr(LOGIN_STORE_KEY_PREFIX.length)] = items[key];
                ++loaded;
            }
        }
        console.log('loaded', loaded, 'recently used services from store');
    });
};

var updateLastUsedServiceForHost = function(service) {
    var key = LOGIN_STORE_KEY_PREFIX + getCanonicalHost(service.clientData.loginUrl);
    var data = {};
    data[key] = service.secretId;
    helper.storage.local.set(data);
    serviceForHostCache[getCanonicalHost(service.clientData.loginUrl)] = service.secretId;
};

var getLastUsedServiceForHost = function(canonicalHost) {
    return serviceForHostCache[canonicalHost];
};

var getLoginHintsForHost = function(host) {
    var data = {};
    var matches = getServiceInstances(host);

    if (matches.length) {
        data.services = matches;
    }
    return data;
};

/** Return service instances matching host, or all services if host is undefined.
@param {string} host
@param {function(!Array.<!Object>)=} callback optional
@return {!Array.<!Object>}
*/
var getServiceInstances = function (host, callback) {
    var matches = [];
    var recent = parseInt(getLastUsedServiceForHost(host), 10);
    // undefined arguments get 'null' when passed through messaging
    if (serviceInstances) {
        if (typeof host !== 'undefined' && host !== null) {
            for (var i = 0; i < serviceInstances.length; i++) {
                var instance = serviceInstances[i];
                var instanceHost = getCanonicalHost(instance.clientData.loginUrl);
                if (instanceHost === host || instanceHost === 'www.' + host) {
                    serviceInstances[i].mostRecent = (serviceInstances[i].secretId === recent);
                    matches.push(serviceInstances[i]);
                }
            }
        } else {
            matches = serviceInstances;
        }
    }
    
    if(typeof(callback) === 'function') callback(matches);
    return matches;
};

var loadSettings = function () {
    helper.storage.sync.get('settings', function (items) {
        if (CHROME && chrome.runtime.lastError) {
            // TODO(ivan): safari and ff implementation
            console.log('error loading settings', chrome.runtime.lastError.message);
        } else if ('settings' in items) {
            console.log('settings loaded; username:', items.settings.username,
                    'rememberMe:', items.settings.rememberMe);
            settings = items.settings;
        }
    });
    populateLastUsedServiceCache();
};

var loadSettingsAsync = function(onSuccess) {
    loadSettings();
    if (onSuccess) {
        onSuccess(settings);
    }
};

var saveSettingsAsync = function(newSettings, onSuccess, onError) {
    helper.storage.sync.set({'settings': newSettings}, function () {
        if(CHROME && chrome.runtime.lastError){
            // TODO(ivan): safari and ff implementation
            console.log('error saving settings to storage', chrome.runtime.lastError.message);
            if (onError) {
                onError(chrome.runtime.lastError.message);
            }
        } else {
            loadSettingsAsync(onSuccess);
        }
    });
};


// this code tries to preserve old, un-b64-encoded device ids where possible.
var getDeviceId = function(callback) {
    var NEW_DEVICE_ID_KEY = 'deviceIdAsBase64';
    helper.storage.local.get(NEW_DEVICE_ID_KEY, function(items)  {
        if (CHROME && chrome.runtime.lastError) {
            // TODO(ivan): safari and ff implementation
            console.log('error loading device key', chrome.runtime.lastError.message);
            // items seems to be undefined if chrome.storage fails: generate deviceid anyway!
            items = {};
        }
        
        if (items && items[NEW_DEVICE_ID_KEY]) {
            // we have the new-style key
            callback(items[NEW_DEVICE_ID_KEY]);
            return;
        }
        // no id exists, get a new random one.
        var rawDeviceId = forge.random.getBytes(20);
        // b64 encode the device id so we don't have any weird unicode errors upstream
        // also avoids this bug: https://code.google.com/p/chromium/issues/detail?id=268991
        var deviceId = window.btoa(rawDeviceId);
        var newvals = {};
        newvals[NEW_DEVICE_ID_KEY] = deviceId;

        // store the new device id in the store and execute the callback using the encoded value
        helper.storage.local.set(newvals, function() {
            if (CHROME && chrome.runtime.lastError) {
                // TODO(ivan): safari and ff implementation
                console.log('error setting key', chrome.runtime.lastError.message);
            }
        });
        callback(deviceId);
    });
};


var saveBlacklist = {};
// TODO: Define these preferences in some common place
/** A hidden preference to never show the "warning not logged in" infobar. */
var hideNotLoggedInOnLoginPages = false;
/** A hidden preference to never ask to save any passwords. */
var neverAskToSavePasswords = false;


var highlightSelectedForms = false;
// TODO: The blacklist should be stored on Mitro instead of browser storage.
// The blacklist should per user, not per browser.
helper.storage.sync.get(null, function(items){
    if (CHROME && chrome.runtime.lastError) {
        // TODO(ivan): safari and ff implementation
        console.log('error loading blacklist', chrome.runtime.lastError.message);
    }
    if ('save_blacklist' in items) {
        console.log('blacklist loaded');
        saveBlacklist = items.save_blacklist;
    }
    if ('highlightSelectedForms' in items) {
        highlightSelectedForms = items.highlightSelectedForms;
    }
    if (items.hideNotLoggedInOnLoginPages) {
        hideNotLoggedInOnLoginPages = true;
    }

    if (items.neverAskToSavePasswords) {
        neverAskToSavePasswords = true;
    }

    if (items.serverHostnameOverride) {
        MITRO_HOST = items.serverHostnameOverride;
    }
});

var addSiteToSaveBlacklist = function (url) {
    var domain = getCanonicalHost(url);
    saveBlacklist[domain] = 1;

    helper.storage.sync.set({'save_blacklist': saveBlacklist}, function () {
        if (CHROME && chrome.runtime.lastError) {
            // TODO(ivan): safari and ff implementation
            console.log('error saving blacklist to storage', chrome.runtime.lastError.message);
        } else {
            console.log('blacklist saved successfully');
        }
    });
};

var isSiteOnSaveBlacklist = function (url) {
    var domain = getCanonicalHost(url);
    return domain in saveBlacklist;
};

var serviceMatchesForm = function (service, formData) {
    // We don't need to check the password value to determine a match,
    // although it could be used for detecting a changed password.
    return formData.usernameField.value === service.clientData.username;
};


var findServicesMatchingForm = function (formData) {
    var host = getCanonicalHost(formData.before_page);
    var services = getServiceInstances(host);
    var matches = [];

    for (var i = 0; i < services.length; i++) {
        if (serviceMatchesForm(services[i], formData)) {
            matches.push(services[i]);
        }
    }

    return matches;
};

var shouldShowSaveServiceDialog = function (formData, cb) {
    var host = getCanonicalHost(formData.before_page);
    if (host === MITRO_HOST) {
        cb(false, null);
        return;
    }

    if (isSiteOnSaveBlacklist(formData.before_page)) {
        console.log('ignoring password on ' + formData.before_page);
        cb(false, null);
        return;
    }

    var onError = function(e) {
        console.log('shouldShowSaveServiceDialog onError called:', e);
        cb(false, null);
    };

    var matches = findServicesMatchingForm(formData);
    if (matches && matches.length === 1) {
        // we have a match (same site, same username);
        // we should check to see if the password is different from what we have stored.
        var secretId = matches[0].secretId;
        getSiteSecretData(secretId, function (loginData) {
            if (formData.passwordField.value === loginData.criticalData.password) {
                // passwords are the same, don't offer to overwrite
                cb(false, null);
            } else {
                delete loginData.criticalData;
                cb(true, loginData);
            }
        }, onError);
    } else {
        cb(true, null);
    }
};

var injectSaveDialog = function(tabId) {
    // Let's check if we recorded a form.
    // This is weak, the user may have clicked command+click or the page started another tab
    if (!neverAskToSavePasswords && formRecorder[tabId]) {
        shouldShowSaveServiceDialog(formRecorder[tabId], function(shouldShow, secretInfo) {
            if (shouldShow) {
                var storedData = formRecorder[tabId];
                var sendSaveServiceMessage = function (orgInfo) {
                    console.log('sending showSaveServiceDialog');
                    helper.tabs.sendMessage(tabId, client.composeMessage(
                        'content', 'showSaveServiceDialog',
                        {recordedData: JSON.stringify(storedData),
                         orgInfo: orgInfo,
                         replacedSecretData: secretInfo}));
                };

                getOrganizationInfo(null, sendSaveServiceMessage,
                    function () {
                        // If unable to retrieve org info, show infobar
                        // without org selection.
                        sendSaveServiceMessage(null);
                    }
                );
            }
        });
    }

};
helper.tabs.onUpdated(function(tabId) {
    // This is necessary because the firefox content scripts sometimes aren't 
    // yet listening for messages even after tab updated is sent, even though
    // their docs claim that this cannot happen.

    // Instead, none of these things should be sent until an init message is sent 
    // to the background script, but the current helpers API is missing the 
    // ability to extract the tab ID from inbound messages.

    // So, we will do the disgusting thing and wait for "a while".
    setTimeout(function() {
        if (!isLoggedIn()) {
            if (!hideNotLoggedInOnLoginPages && !isAttemptingLogin()) {
                helper.tabs.sendMessage(tabId, client.composeMessage('content', 'showMitroLoginWarningInfobar'));
            }
        } else {
            injectSaveDialog(tabId);
        }
    },
    // TODO: yuck.
    FIREFOX ? 8000 : 0);
});

helper.tabs.onRemoved(function(tabId) {
    if (tabId in formRecorder) {
        delete formRecorder[tabId];
    }
    if (tabId in pendingLoginRequests) {
        delete pendingLoginRequests[tabId];
    }
});

var isLoginInProgress = function (tabId) {
    var logindata = pendingLoginRequests[tabId];
    return !!logindata;
};

var sendLoginMessageToTab = function (tab, message) {
    var secretId = pendingLoginRequests[tab.id].secretId;
    delete pendingLoginRequests[tab.id];

    getSiteSecretData(secretId, function (loginData) {
        message.sendResponse({data: {'login' : loginData,
                                     'frameId' : message.data.frameId,
                                     'serverHints' : getServerHintsForUrl(tab.url)}
        });
    }, function(e) {
        console.log('sendLoginMessageToTab call to getSiteSecretData failed?', e);
        // TODO: Should we respond to message?
    });
};

var doLogin = function (secretData, tabIndex) {
    console.log('login request for ' + secretData.clientData.loginUrl);
     // requesting login for page; open tab and store the data
    var options = {
        url: secretData.clientData.loginUrl,
        active: true,
        index: tabIndex
    };
    createTab(options, function (tab) {
        pendingLoginRequests[tab.id] = secretData;
    });
};

var resolveUri = function (baseUri, uri) {
    var uriObj = new URI(uri);
    if (!(uriObj.getScheme() === "http" || uriObj.getScheme() === "https")) {
        return uriObj.resolve(new URI(baseUri)).toString();
    } else {
        return uri;
    }
};

var hidePopup = function() {
    helper.hidePopup();
};

var createTab = function(options, callback){
    helper.tabs.create(options, callback);
};

var getSelectedTab = function(callback){
    helper.tabs.getSelected(callback);
};

/** @constructor
@struct
@implements {mitro.Forge}
*/
var RealForge = function() {};
RealForge.prototype.getRandomByte = function() {
    return forge.random.getBytesSync(1).charCodeAt(0);
};

var generatePassword = function(data, onSuccess, onError) {
    try {
        var reqs = new mitro.PasswordRequirements();
        if (data && data.passwordReqs) {
            reqs.numCharacters = data.passwordReqs.numCharacters !== undefined ? data.passwordReqs.numCharacters : reqs.numCharacters;
            reqs.minUppercase = data.passwordReqs.minUppercase !== undefined ? data.passwordReqs.minUppercase : reqs.minUppercase;
            reqs.maxUppercase = data.passwordReqs.maxUppercase !== undefined ? data.passwordReqs.maxUppercase : reqs.maxUppercase;
            reqs.minDigits = data.passwordReqs.minDigits !== undefined ? data.passwordReqs.minDigits : reqs.minDigits;
            reqs.maxDigits = data.passwordReqs.maxDigits !== undefined ? data.passwordReqs.maxDigits : reqs.maxDigits;
            reqs.minSymbols = data.passwordReqs.minSymbols !== undefined ? data.passwordReqs.minSymbols : reqs.minSymbols;
            reqs.maxSymbols = data.passwordReqs.maxSymbols !== undefined ? data.passwordReqs.maxSymbols : reqs.maxSymbols;
            reqs.symbolSet  = data.passwordReqs.symbolSet  !== undefined ? data.passwordReqs.symbolSet  : reqs.symbolSet ;
        }
        // TODO: potentially load reqs based on data.url
        
        onSuccess(mitro.generatePassword(new RealForge(), reqs));
    } catch (e) {
        console.log(e.message);
        console.log(e.stack);
        if (onError) {
            onError(e.message);
        }
    }
};

client.initRemoteExecution('extension', ['checkTwoFactor', 'changePassword', 'hidePopup',
                                         'mitroSignup', 'saveSettingsAsync', 'loadSettingsAsync', 'mitroLogin',
                                         'getSelectedTab', 'getServiceInstances']);
if (FIREFOX) {
    var setPopupHeight = function(new_height){
        helper.setPopupHeight(new_height);
    };
    client.initRemoteExecution('extension', 'setPopupHeight');
    client.initRemoteExecution('main', 'addSecretFromSelection');
} else {
    client.initRemoteExecution('content', 'addSecretFromSelection');
}

client.initRemoteExecution(['extension', 'content'], ['createTab', 'generatePassword']);
// Listen for messages from content scripts
//TODO: we should separate the content and the extension calls
client.addListener(['content', 'extension'], function (message) {
    var type = message.type;
    var sender = message.sender;
    var data = message.data;
    
    console.log('background received message from content script, type:', message.type);
    var onError = function (error) {
        message.sendResponse({error: error});
    };

    if (type === 'init') {
        console.log('got init message from Frame ', message.data.frameId);
        // Should content script fill a form on this page?
        if (isLoginInProgress(sender.id)) {
            sendLoginMessageToTab(sender, message);
        } else {
            var host = getCanonicalHost(message.data.url);
            console.log('trying to find logins for ' , host);
            data = getLoginHintsForHost(host);
            data.serverHints = getServerHintsForUrl(message.data.url);
            data.frameId = message.data.frameId;
            message.sendResponse({data: data});
        }
    } else if (type === 'showInfoBarOnTopFrame') {
        message.sendResponse({data : message.data});
    } else if (type === 'login') {
        // Login request from mitro service list page.
        doLogin(data, sender.index + 1);
    } else if (type === 'loginAccepted') {
        // Login request from login infobar
        var secretId = parseInt(data.value, 10);
        console.log('fetching secret for id ', secretId);

        var onSuccess = function (response_data) {
            message.sendResponse({data: response_data, frameId: data.frameId});
            // record that we logged in
            updateLastUsedServiceForHost(response_data);
        };

        getSiteSecretData(secretId, onSuccess, onError);
        return true;
    } else if (type === 'loginRejected') {
        // User closed the login infobar.
        console.log('login rejected');
    } else if (type === 'saveServiceAccepted') {
        delete formRecorder[sender.id];
        addSite(data, function(secretId) {
            var maybeShareSecret = function () {
                if (data.showShareDialog) {
                    var options = {
                        url: helper.getURL('/html/secrets.html#' +
                                           parseInt(secretId, 10)),
                        active: true
                    };
                    createTab(options, function(ignored){});
                }
            };
            if (data.orgId) {
                var shareData = {secretId: secretId,
                                 groupIdList: [],
                                 identityList: [],
                                 orgGroupId: data.orgId};
                editSiteShares(shareData, maybeShareSecret, maybeShareSecret);
            } else {
                maybeShareSecret();
            }
        }, onError);
    } else if (type === 'replaceServiceAccepted') {
        delete formRecorder[sender.id];
        var thisSecretId = data.secretId;
        var newPassword = data.passwordField.value;
        fe.workerInvokeOnIdentity(userIdentity, 'editSitePassword', thisSecretId, newPassword,
            function() {console.log("updated secret");},
            function(error) {reportError(undefined, 'Error saving password' + error.userVisibleError);}
            );
    } else if (type === 'saveServiceRejected') {
        delete formRecorder[sender.id];
    } else if (type === 'saveServiceBlacklisted') {
        delete formRecorder[sender.id];
        addSiteToSaveBlacklist(data.before_page);
    } else if (type === 'formSubmit') {
        console.log('formSubmit');
        var formData = data;
        // let's see if we have an action
        if (formData.after_page) {
            // The action may not be absolute
            formData.after_page = resolveUri(sender.url, formData.after_page);
        }

        formRecorder[sender.id] = formData;
        // show the dropdown after a little while: useful for AJAXy forms.
        setTimeout(function() {
            // TODO: this should actually check to ensure that the password field
            // is not on the page (heuristic for failed logins), 
            // but that problem is orthogonal to the ajax form stuff.
            injectSaveDialog(sender.id);
        }, 5000);

    } else {
        return false;
    }
    return true;
});

var processAPIMessage = function (message, callback) {
    var sendResponse = function(response){
        if(typeof(callback) !== 'undefined'){
            callback({data: response});
        } else {
            message.sendResponse({data: response});
        }
    };
    
    var onError = function (error) {
        sendResponse({error: error});
    };

    var onSuccess = function (data) {
        sendResponse({data: data});
    };

    if (message.type === 'getIdentity') {
        getIdentity(onSuccess, onError);
    } else if (message.type === 'getLoginState') {
        getLoginState(onSuccess, onError);
    } else if (message.type === 'mitroLogout') {
        mitroLogout(onSuccess, onError);
    } else if (message.type === 'listUsersGroupsAndSecrets') {
        listUsersGroupsAndSecrets(onSuccess, onError);
    } else if (message.type === 'getSiteSecretData') {
        getSiteSecretData(message.data, onSuccess, onError);
    } else if (message.type === 'getSiteSecretDataForDisplay') {
        getSiteSecretDataForDisplay(message.data, onSuccess, onError);
    } else if (message.type === 'addSecret') {
        addSecret(message.data, onSuccess, onError);
    } else if (message.type === 'addSecretToGroups') {
        addSecretToGroups(message.data, onSuccess, onError);
    } else if (message.type === 'editSecret') {
        editSecret(message.data, onSuccess, onError);
    } else if (message.type === 'removeSecret') {
        removeSecret(message.data, onSuccess, onError);
    } else if (message.type === 'editSiteShares') {
        editSiteShares(message.data, onSuccess, onError);
    } else if (message.type === 'getGroup') {
        getGroup(message.data, onSuccess, onError);
    } else if (message.type === 'addGroup') {
        addGroup(message.data, onSuccess, onError);
    } else if (message.type === 'removeGroup') {
        removeGroup(message.data, onSuccess, onError);
    } else if (message.type === 'editGroup') {
        editGroup(message.data, onSuccess, onError);
    } else if (message.type === 'addIssue') {
        addIssue(message.data, onSuccess, onError);
    } else if (message.type === 'getAuditLog') {
        getAuditLog(message.data, onSuccess, onError);
    } else if (message.type === 'createOrganization') {
        createOrganization(message.data, onSuccess, onError);
    } else if (message.type === 'getOrganizationInfo') {
        getOrganizationInfo(message.data, onSuccess, onError);
    } else if (message.type === 'getOrganization') {
        getOrganization(message.data, onSuccess, onError);
    } else if (message.type === 'selectOrganization') {
        selectOrganization(message.data, onSuccess, onError);
    } else if (message.type === 'mutateOrganization') {
        mutateOrganization(message.data, onSuccess, onError);
    } else if (message.type === 'changeRemotePassword') {
        changeRemotePassword(message.data, onSuccess, onError);
    } else {
        return false;
    }

    return true;
};


client.addListener(['extension', 'page'], processAPIMessage);


setInterval(listUsersGroupsAndSecrets, SERVICE_LIST_REFRESH_PERIOD);

updateIconState();
loadSettings();


// do this every 1 to 2 hours
var MIN_REJECTS_REFRESH_MS = 1000*60*60;
var MAX_REJECTS_REFRESH_MS = 3000*60*60;
var SERVER_REJECTS = [
{   'regex' : '.*',
    'reject' : {
        'login_submit' : [],
        'submit' : [], //signup or login
        'password' : [],
        'username' : [
        //TODO: put default reject rules in here.
        ]}
},
// TODO: move this to the server.
{   'regex' : 'https://www.mint.com$',
    'additional_submit_button_ids' : ['login_button'],
    'reject' : {
        // login only:
        'login_submit' : [
        [{'attributeName':'value', 'exactMatch' : 'Sign up'}],
        [{'attributeName':'id', 'exactMatch' : 'signup_button'}]
        ],
        'submit' : [], //signup or login
        "password" : [
        ],
        "username" : [
        ]
    }
},
{   'regex' : '^https://accounts[.]google[.]com/ServiceLogin',
    'allow_empty_username' : true,
    'reject' : {
        // login only:
        'login_submit' : [
        ],
        'submit' : [], //signup or login
        "password" : [
        ],
        "username" : [
        ]
    }
},
{   'regex' : '^https://www.budget.com.*',
    'reject' : {
        // login only:
        'login_submit' : [], 
        'submit' : [], //signup or login
        'password' : [], 
        'username' : [],
        'form' : [
        [{'attributeName': 'name', 'exactMatch' : 'reservationForm'}]
        ]
    }
}
];


var refreshServerHints = function () {
    try {
        mitro.fe.getDeviceIdAsync(function(deviceId) {
            $.getJSON(
              "https://" + MITRO_HOST + ":"  + MITRO_PORT + "/mitro-core/ServerRejects?deviceId=" + deviceId,
              function(data) {
                SERVER_REJECTS = data;
              });
        });
    } catch (e)  {
        console.log(e);
        console.log(e.stack);
    } finally {
        setTimeout(refreshServerHints, 
            MIN_REJECTS_REFRESH_MS  + (MAX_REJECTS_REFRESH_MS - MIN_REJECTS_REFRESH_MS) * Math.random());
    }
};



var getServerHintsForUrl = function(url) {
    // TODO: disallow potentially polynomial-time regexes
    var outs = {'reject' : {'submit' : [], 'password' : [], 'username' : [], 'form' : []},
                'additional_submit_button_ids' : [],
                'allow_empty_username' : false,
                'highlightSelectedForms':highlightSelectedForms
            };
    try {

        for (var i = 0 ; i < SERVER_REJECTS.length; ++i) {
            if (url.match(SERVER_REJECTS[i].regex)) {
                if (SERVER_REJECTS[i].reject.submit)
                    outs.reject.submit = outs.reject.submit.concat(SERVER_REJECTS[i].reject.submit);
                if (SERVER_REJECTS[i].reject.password)
                    outs.reject.password = outs.reject.password.concat(SERVER_REJECTS[i].reject.password);
                if (SERVER_REJECTS[i].reject.username)
                    outs.reject.username = outs.reject.username.concat(SERVER_REJECTS[i].reject.username);
                if (SERVER_REJECTS[i].reject.form)
                    outs.reject.form = outs.reject.form.concat(SERVER_REJECTS[i].reject.form);
                if (SERVER_REJECTS[i].additional_submit_button_ids) {
                    outs.additional_submit_button_ids = outs.additional_submit_button_ids.concat(SERVER_REJECTS[i].additional_submit_button_ids);
                }
                if (SERVER_REJECTS[i].allow_empty_username) {
                    outs.allow_empty_username = true;
                }
                if (SERVER_REJECTS[i].empty_password_username_selector) {
                    // TODO: allow a list.
                    outs.empty_password_username_id = SERVER_REJECTS[i].empty_password_username_selector;
                }
            }
        }
    } catch (e)  {
        console.log(e);
        console.log(e.stack);
    }
    return outs;
};

var _automaticLoginTimerId = null;
var automaticLoginLoop = function() {
    // re-try logins every 5 minutes at most, 2 min at min.
    var MAX_LOGIN_DELAY_MS = 5*60*1000;
    var MIN_LOGIN_DELAY_MS = 2*60*1000;

    // avoid multiple retry loops (e.g. if triggered from online event)
    clearTimeout(_automaticLoginTimerId);
    _automaticLoginTimerId = null;

    console.log('starting auto-login attempt');
    loadEncryptedKey(function(keyUid) {
        if (!keyUid) {
            console.log('autologin: no local key, abandoning efforts to auto-login');
            return;
        }
        if (isLoggedIn()) {
            console.log("looks like we're actually logged in. abandoning auto-login efforts");
            return;
        }

        var automaticLoginError = function(e) {
            attemptingLogin = false;
            console.log('error w/ background login; exception:', e);
            if (e.exceptionType === 'DoEmailVerificationException') {
              // if this is an automated login attempt and we get an email verification exception,
              // we should stop trying to log in (this device is unauthorised)
              saveEncryptedKey(null, null);
              console.log('device was invalidated; removing key');
            } else {
              // TODO: maybe we should only automatically retry when we don't
              // have a permission error from the server?  For now this is probably okay.
              var retryDelay = MIN_LOGIN_DELAY_MS + (MAX_LOGIN_DELAY_MS - MIN_LOGIN_DELAY_MS) * Math.random();
              console.log('trying again in', retryDelay, 'ms');
              _automaticLoginTimerId = setTimeout(automaticLoginLoop, retryDelay);
            }
        };

        getLoginToken(keyUid.uid, function(token) {
            attemptingLogin = true;
            mitro.fe.workerLoginWithTokenAndLocalKey(keyUid.uid, null, token, MITRO_HOST, MITRO_PORT, keyUid.key,
                // tfaCode:
                null,
                function(identity, rememberMe) {
                    commonOnLoginCode(identity, rememberMe, function() {
                        if (WEBPAGE) {
                            window.parent.__extension.location.reload();
                        }
                    }, automaticLoginError);
                },
                automaticLoginError);
        });
    });
};

// When reconnected to network, attempt to log in (if not already)
window.addEventListener('online', function(e) {
    // automaticLoginLoop fails gracefully if we are logged in or don't have key
    // Chrome XMLHttpRequest fails with net::ERR_NETWORK_CHANGED if retried immediately
    var NETWORK_CHANGED_DELAY_MS = 50;
    setTimeout(function() {
        console.log('online event; triggering automatic login');
        automaticLoginLoop();
    }, NETWORK_CHANGED_DELAY_MS);
});

// Starts a background task to generate keys
// and log in.
getDeviceId(function(deviceId) {
    mitro.fe.setFailover(FAILOVER_MITRO_HOST, FAILOVER_MITRO_PORT);
    mitro.fe.setDeviceId(deviceId);
    refreshServerHints();
    automaticLoginLoop();
});

// Check to see if any tabs have the install page open on extension load and
// redirect them to the signup page.
var checkForNewInstall = function() {
    helper.tabs.getAll(function(tabs) {
        for (var i = 0; i < tabs.length; i++) {
            if (isInstallPage(tabs[i].url)) {
                var redirectUrl = getInstallRedirectUrl(tabs[i].url);
                helper.tabs.setUrl(tabs[i].id, redirectUrl);
            }
        }
    });
};

checkForNewInstall();
