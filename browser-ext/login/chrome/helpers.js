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

var _CHROME_VERSION = parseInt(navigator.userAgent.match(/Chrom(e|ium)\/([0-9]+)\./)[2], 10);

var LAST_AUTOCOMPLETE_COMPLIANT_CHROME_VERSION = 33;

function getExtensionId() {
    return chrome.runtime.id;
}

var getURL = function(path){
    path = path ? path : '';
    return chrome.extension.getURL(path);
};

/** @constructor */
function ExtensionHelper() {
    this.tabs = chrome.tabs;
    this.getURL = getURL;
    this.runPopupActions = function() {return;};
    
    this.isPopup = function(callback) {
        chrome.tabs.getCurrent(function(tab) {
            callback(!tab);
        });
    };
    

    this.setLocation = function(path){
        window.location = '/html/' + path;
    };
    
    this.copyFromInput = function($element, callback) {
        $element.select();
        document.execCommand('copy');
        
        if (typeof(callback) !== 'undefined') {
            callback();
        }
    };

    this.onPopupHidden = function (callback) {
        window.onunload = callback;
    };

    this.bindClient = function(client){
        client.directAccess = true;
        client.background = chrome.extension.getBackgroundPage();
    };
}

/** @constructor */
function ContentHelper() {
    this.getURL = getURL;
    var that = this;
    this.redirectTo = function(url) {
        window.location.href = url;
    };

    this.createTab = function(url) {
        that.background.createTab({url: url});
    };
    
    this.bindClient = function(client){
        chrome.extension.onMessage.addListener(function(message, sender, sendResponse){
            client.processIncoming(message);
        });
        
        client.addSender('background', function(message){
            chrome.extension.sendMessage(message);
        });
        client.initRemoteCalls('background', ['createTab', 'addSecretFromSelection']);
        this.background = client;
    };

    this.preventAutoFill = function($passwordField, $form) {
        $form.attr('autocomplete', 'off');
        if (_CHROME_VERSION > LAST_AUTOCOMPLETE_COMPLIANT_CHROME_VERSION) {
            console.log('using extreme measures to prevent browser form saving');
            var newInput = document.createElement('input');
            newInput.setAttribute('type', 'password');
            newInput.setAttribute('style', 'display: none;');
            $form[0].appendChild(newInput);
        }
    };
}
