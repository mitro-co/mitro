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

    this.getURL = function(path) {
        return getURL(path);
    };
    
    /**
     * Local storage browser-specific implementation
     */
    var storage = helpers_common.storage(localStorage);
    
    // The helper has to provide
    // the storage.local and storage.sync interfaces
    // to keep the chrome browser compatibility
    //
    // For firefox, safari and the webpage implementation, the local and sync storage
    // are exactly the same object
    this.storage = {
        local: storage,
        sync: storage
    };
    
    this.getClientIdentifier = function(){
        return 'extension:[' + EXTENSION_ID + ',' + EXTENSION_VERSION +']';
    };
    
    this.ajax = helpers_common.ajax;
    
    /**
     * This contains the set of dummy functions
     * required for compatibility purposes
     */
    this.tabs = {
        onUpdated: function(listener){},
        sendMessage: function(tabId, message){},
        onRemoved: function(listener){},
        create: createTab,
        getSelected: getSelectedTab,
        remove: function(tabId, callback) {
            if (typeof(callback) !== 'undefined') {
                callback();
            }
        }
    };

    /**
     * This contains the set of dummy functions
     * required for compatibility purposes
     */
    this.cookies = {
        get: function(params, callback){
            callback(null);
        },
        set: function() {return;}
    };
    
    // dummy
    this.setIcon = function() {};

    // dummy
    this.addContextMenu = function() {};

    // dummy
    this.removeContextMenu = function() {};
    
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
        window.onmessage = (function() {
            return function(event){
                var message = event.data;
                
                // Sender tab data
                message.sender = {};
                // This function will be used to send reply to the sender tab
                message.sendResponse = function(data){
                    var newMessage = client.composeResponse(this, data);
                    parent.postMessage(newMessage, '*');
                };
                client.processIncoming(message);
            };
        })();
        
        client.initRemoteExecution('extension', 'createTab');
    };
};

})();
