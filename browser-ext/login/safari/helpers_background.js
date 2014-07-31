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
(function() {

/**
 * The helper object containing the browser-specific code
 * to be used in the background script
 */
helpers_background.BackgroundHelper = function() {
    var _self = this;
    
    this.getURL = getURL;
    
    /**
     * Local storage browser-specific implementation
     */
    var storage = helpers_common.storage(localStorage);
    
    // The helper has to provide
    // the storage.local and storage.sync interfaces
    // to keep the chrome browser compatibility
    //
    // For firefox and safari, the local and sync storage
    // are exactly the same object
    this.storage = {
        local: storage,
        sync: storage
    };
    
    // composing clientIdentifier by parsing the Info.plist file
    $.get(safari.extension.baseURI + 'Info.plist', function(data){
        $('dict > key', data).each(function() {
            if($(this).text() === 'CFBundleShortVersionString') {
                _self.extensionVersion = $(this).next().text();
            }
            if($(this).text() === 'CFBundleIdentifier'){
                _self.extensionID = $(this).next().text();
            }
        });
        _self.clientIdentifier = 'extension:[' + _self.extensionID + ',' + _self.extensionVersion +']';
    });
    
    this.getClientIdentifier = function(){
        return this.clientIdentifier;
    };
  
    /**
     * The jquery-based ajax implementation
     * 
     * @param params {object} The set of parameters as following:
     *   type {string} the request type
     *   url {string} The request url
     *   data {string, object} The request data, eather serialized or not
     *   dataType {string} Request dataType
     *   complete {function} The onComplete callback function
     */
    this.ajax = helpers_common.ajax;
  
    // The Safari tabs API doesn't provide anything like unique tab id.
    // The code below implements the manual tabs tracking
    
    // the object containing all the currently opened tabs. The keys are tabs ids.
    this.activeTabs = {};
    // placeholder. The real 'tab updated' event listener function will be set here later
    this.tabUpdatedListener = function() {};
    // The same as above. The real 'tab removed' event listener function will be set here late
    this.tabRemovedListener = function() {};
    
    /**
     * Adds the tab to the activeTabs to keep it tracked.
     * Sets the appropriate tab event listeners.
     * 
     * @param tab {SafariBrowserTab} The tab to track
     */
    this.trackTab = function(tab){
        tab.id = randomString(10);
        console.log('Tab index is ' + tab.index);
        this.activeTabs[tab.id] = tab;
        tab.addEventListener("navigate", function(event){
            _self.tabUpdatedListener(tab.id);
        }, true);
        tab.addEventListener("close", function(event){
            _self.tabRemovedListener(tab.id);
            delete _self.activeTabs[tab.id];
        });
    };
    
    // Start tracking all existing tabs
    for(var i=0; i<safari.application.browserWindows.length; i++){
        var browser_window = safari.application.browserWindows[i];
        for(var ii=0; ii<browser_window.tabs.length; ii++){
            var tab = browser_window.tabs[ii];
            tab.index = ii;
            this.trackTab(tab);
        }
    }
  
    // Making the newly created tabs get tracked
    safari.application.addEventListener('open', function(event){
        for(var i=0; i<safari.application.browserWindows.length; i++){
            var browser_window = safari.application.browserWindows[i];
            for(var ii=0; ii<browser_window.tabs.length; ii++){
                if(browser_window.tabs[ii] === event.target){
                    event.target.index = ii;
                }
            }
        }
        _self.trackTab(event.target);
    }, true);
    
    /**
     * The set of browser-specific methods to deal with tabs
     * from the background script
     */
    this.tabs = {
        /**
         * Sets the listener to the 'tab updated' event.
         * The previously assigned listener will be replaced (if present)
         * 
         * @param listener {function}
         */
        onUpdated: function(listener){
            _self.tabUpdatedListener = listener;
        },
        /**
         * Sends the message to the tab
         * 
         * @param tabId {string} The id of the tab to send the message to
         * @param message {object} The message
         */
        sendMessage: function(tabId, message){
            try {
                _self.activeTabs[tabId].page.dispatchMessage('message', message);
            } catch(e){
                console.log('Can not send message. Tab #' + tabId + ' not found');
            }
        },
        /**
         * Sets the listener to the 'tab removed' event.
         * The previously assigned listener will be replaced (if present)
         * 
         * @param listener {function}
         */
        onRemoved: function(listener){
            _self.tabRemovedListener = listener;
        },
        /**
         * The shortcut. See the createTab function above
         */
        create: createTab,
        /**
         * The shortcut. See the getSelectedTab function above
         */
        getSelected: getSelectedTab,
        getAll: getAllTabs,
        remove: function(tabId, callback) {
            try {
                _self.activeTabs[tabId].close();
            } catch(e){
                console.log('Can not close tab. Tab #' + tabId + ' not found');
            } finally {
                if (typeof(callback) !== 'undefined') {
                    callback();
                }
            }
        },
        setUrl: setTabUrl
    };
  
    // The functions below are the placeholders
    //TODO(ivan): check this thing
    this.cookies = {
        get: function(params, callback){
            callback(null);
        },
        set: function() {return;}
    };
    
    // the placeholder
    // currently we don't change the icon in safari
    this.setIcon = function() {};
    
    /**
     * Activate context menu features
     */
    this.addContextMenu = function() {
        safari.application.addEventListener("contextmenu", contextMenuEventHandler, false);
        safari.application.addEventListener("command", contextMenuCommandHandler, false);
    };
    
    /**
     * Deactivate context menu features
     */
    this.removeContextMenu = function() {
        safari.application.removeEventListener("contextmenu", contextMenuEventHandler, false);
        safari.application.removeEventListener("command", contextMenuCommandHandler, false);
    };
    
    /**
     * Sets up the Client object to be used from backround script
     * 
     * This code does the following things:
     * 
     * - sets the listener to the incoming messages
     * - when the message arrives, fills in the sender tab data
     *   and sets the sendResponse method
     * - passes the resulting message object
     *   to the client's processIncoming method
     */
    this.bindClient = function(client){
        safari.application.addEventListener("message", (function() {
            return function(request){
                var message = request.message;
                
                // Sender tab data
                message.sender = {
                    id: request.target.id,
                    index: request.target.index,
                    url: request.target.url
                };
                // This function will be used to send reply to the sender tab
                message.sendResponse = function(data){
                    var new_message = client.composeResponse(this, data);
                    request.target.page.dispatchMessage("message", new_message);
                };
                client.processIncoming(message);
            };
        })() ,false);
        
        client.initRemoteExecution('extension', 'createTab');
    };
};

})();
