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

var helpers_background = {};
(function(){

var helpers_common;
if (typeof(module) !== 'undefined') {
    helpers_common = require('../common/helpers_common');
} else {
    helpers_common = window.helpers_common;
}

var _createMitroTab = function(chromeTab) {
    var tab = new helpers_common.MitroTab(chromeTab.id);
    tab.windowId = chromeTab.windowId;
    tab.index = chromeTab.index;
    tab.url = chromeTab.url;
    tab.title = chromeTab.title;
    return tab;
};

/** @constructor */
helpers_background.BackgroundHelper = function() {
    this.getURL = getURL;
    this.storage = chrome.storage;
    this.ajax = helpers_common.ajax;
    this.cookies = chrome.cookies;
    this.setIcon = function(details) {
        if (details.path) {
            // Chrome throws an exception if we pass unsupported sizes
            var newPath = {};
            if ('19' in details.path) {
                newPath['19'] = details.path['19'];
            }
            if ('38' in details.path) {
                newPath['38'] = details.path['38'];
            }
            details.path = newPath;
        }
        chrome.browserAction.setIcon(details);
    };

    this.tabs = {
        onUpdated: function(listener){
            chrome.tabs.onUpdated.addListener(function(tabId, changeInfo, tab){
                if(changeInfo.status === "complete"){
                    listener(tabId);
                }
            });
        },
        onRemoved: function(listener){
            chrome.tabs.onRemoved.addListener(function(tabId, changeInfo, tab){
                listener(tabId);
            });
        },
        sendMessage: function(tabId, message){
            chrome.tabs.sendMessage(tabId, message);
        },
        create: chrome.tabs.create,
        remove: chrome.tabs.remove,
        getSelected: function(callback) {
            // Pass null to get selected tab in current window.
            chrome.tabs.getSelected(null, function(tab) {
                callback(_createMitroTab(tab));
            });
        },
        getAll: function(callback) {
            // Empty query returns all tabs.
            chrome.tabs.query({}, function(tabs) {
                var allTabs = [];
                for (var i = 0; i < tabs.length; i++) {
                    allTabs.push(_createMitroTab(tabs[i]));
                }
                callback(allTabs);
            });
        },
        setUrl: function(tabId, url) {
            chrome.tabs.update(tabId, {url: url});
        }
    };
    
    this.getClientIdentifier = function(){
        var clientIdentifier;
        try {
            clientIdentifier = 'extension:[' + chrome.runtime.id + ',' + chrome.runtime.getManifest().version+']';
        } catch (e) {
            console.log('could not read chrome extension info');
            clientIdentifier = 'unknown';
        }
        
        return clientIdentifier;
    };
    
    /**
     * Activate context menu features
     */
    this.addContextMenu = function() {
        // Create menu group
        chrome.contextMenus.create({
            id: 'mitro_context_group',
            title: 'Mitro',
            contexts: ['selection']
        });

        // Add menu item to save selected text as a secret
        chrome.contextMenus.create({
            id: 'save_secret',
            title: 'Save selection as secure note',
            contexts: ['selection'],
            onclick: function() {
                // Send command to the active tab to copy selected text
                helper.tabs.getSelected(function(tab) {
                    helper.tabs.sendMessage(tab.id,
                            client.composeMessage('content', 'copySelection', {tabUrl: tab.url}));
                });
            },
            parentId: 'mitro_context_group'
        });
    };
    
    /**
     * Deactivate context menu features
     */
    this.removeContextMenu = function() {
        chrome.contextMenus.remove('mitro_context_group');
    };
    
    this.bindClient = function(client){
        chrome.extension.onMessage.addListener(function(request, sender, sendResponse){
            var message = request;
            message.sender = sender.tab;
            message.sendResponse = function(data){
                var newMessage = client.composeResponse(this, data);
                chrome.tabs.sendMessage(this.sender.id, newMessage);
            };
            client.processIncoming(message);
        });
    };
};

})();
