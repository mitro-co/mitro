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

// the web worker has no access to the console,
// so we just make a dummy function instead
// to avoid the exceptions when the console.log is called from the client.js
var client = null;
console = {
    log: function(data) {
        try {
            client && client.console_log && client.console_log(Array.prototype.slice.call(arguments));
        } catch (ignored) {
        }
    }
};

if (typeof(window) === 'undefined') window = {};

importScripts('js/client.js');
client = new Client('worker');

onmessage = function (event) {
    var message = event.data;
    message.sendResponse = function(data){
        var new_message = client.composeResponse(this, data);
        postMessage(JSON.parse(JSON.stringify(new_message)));
    };
    client.processIncoming(message);
};

client.addSender('background', function(message){
    postMessage(JSON.parse(JSON.stringify(message)));
});

try {
    // IMPORTANT: Do not load logging.js here because it captures console.log
    importScripts(
         "js/config.js",
         "lru_cache.js",
         "jsbn.js", "asn1.js", "pkcs1.js", "rsa.js", "oids.js", "pki.js", "util.js", "sha1.js",
         "sha256.js", "prng.js", "aes.js", "random.js", "pbkdf2.js", "hmac.js",
         "keyczar_util.js","keyczar.js",
         "URI.js", "utils.js", "domain.js", "cookielib.js",
         "crypto.js", "helpers.js", "mitro_lib.js", "kew.js", "mitroclient.js", 
         "mitro_legacyapi.js", "mitro_fe.js"
        );
} catch (e) {
    console.log('exception' + JSON.stringify(e));
    console.log('exception' + JSON.stringify(e.stack));
}

var extensionId = 'unknown';
var setExtensionId = function(eid) {
    extensionId = eid;
};
// yuck:
mitro.fe.setExtensionId = setExtensionId;


client.initRemoteCalls('background', ['getNewRSAKeysAsync', 'console_log', 'ajax', 'getRandomness']);
client.initRemoteExecution('background', ['setExtensionId',  'signMessageAsync',
        'startCacheFiller', 'setFailover', 'setDeviceId', 'getDeviceId', 'getDeviceIdAsync',
        'workerInvokeOnIdentity', 'createIdentity', 'workerCreateIdentity', 'workerLogin',
        'workerLoginWithToken', 'workerLoginWithTokenAndLocalKey', 'login', 'loginWithToken',
        'loginWithTokenAndLocalKey', 'addIssue', 'initCacheFromFile', 'initCacheFromJson',
        'clearCaches', 'bidirectionalSetDiff', 'workerLogout'
    ], mitro.fe);

var keyCacheProxy = {};
keyCacheProxy.getNewRSAKeysAsync = function(numKeys, onSuccess, onError) {
    client.getNewRSAKeysAsync(numKeys, function(keys) {
        var rval = [];
        for (var i = 0; i < keys.length; ++i)
            rval.push(mitro.crypto.loadFromJson(keys[i]));
        onSuccess(rval);
    }, onError);
};

mitro.fe.setKeyCache(keyCacheProxy);

// override getRandomness to get seeds from the background_api
mitro.fe.getRandomness = client.getRandomness;

// set ajax to point to the post code in the background thread
mitro.rpc = mitro.rpc || {};
mitro.rpc._PostToMitro = client.ajax;

getExtensionId = function() { return extensionId;};
