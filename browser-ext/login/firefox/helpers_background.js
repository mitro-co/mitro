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

helpers_background.BackgroundHelper = function() {
    var _self = this;
    this.getURL = getURL;
    
    var storage = {
        get: function(key, callback){
            _self.main.storageGet(key, callback);
        },
        set: function(data, callback){
            if (typeof(callback) !== 'undefined') {
                _self.main.storageSet(data, callback);
            } else {
                _self.main.storageSet(data);
            }
        },
        remove: function(keys) {
            _self.main.storageRemove(keys);
        }
    };
    
    this.storage = {
        sync: storage,
        local: storage
    };
  
    this.ajax = function(params) {
        _self.main.ajax(params, params.complete);
    };

    this.cookies = {
        get: function(params, callback){
            _self.main.cookiesGet(params, callback);
        }
    };
    
    this.setIcon = function(icons){
        _self.main.setIcon(icons);
    };
  
    this.hidePopup = function() {
        _self.main.hidePopup();
    };
  
    this.tabs = {
        onUpdated: function(listener){
            self.port.on('tab_updated', function(tabId){
                listener(tabId);
            });
        },
        sendMessage: function(tabId, message){
            _self.main.tabsSendMessage(tabId, message);
        },
        onRemoved: function(listener){
            self.port.on('tab_removed', function(tabId){
                listener(tabId);
            });
        },
        create: function(options, callback){
            if (typeof(callback) !== 'undefined') {
                _self.main.createTab(options, callback);
            } else {
                _self.main.createTab(options);
            }
        },
        remove: function(tabId, callback) {
            _self.main.removeTab(tabId, callback);
        },
        getSelected: function(callback){
            _self.main.getSelectedTab(function(tab) {
                callback(tab);
            });
        },
        getAll: function(callback){
            _self.main.getAllTabs(function(tabs) {
                callback(tabs);
            });
        },
        setUrl: function(tabId, url) {
            _self.main.setTabUrl(tabId, url);
        }
    };
  
    this.setPopupHeight = function(newHeight){
        _self.main.setPopupHeight(newHeight);
    };
    
    this.getClientIdentifier = function(){
        return 'extension:[' + self.options.ID + ',' + self.options.VERSION +']';
    };
    
    this.addContextMenu = function() {
        _self.main.addContextMenu();
    };
    
    this.removeContextMenu = function() {
        _self.main.removeContextMenu();
    };
    
    this.bindClient = function(client){
        self.on('message', function(message){
            message.sendResponse = function(data){
                var newMessage = client.composeResponse(this, data);
                client.sendMessage(newMessage);
            };
            
            client.processIncoming(message);
        });
        client.addSender(['main', 'extension', 'content', 'page'], self.postMessage);
        
        client.initRemoteCalls('main', [
            'storageGet', 'storageSet', 'storageRemove', 'ajax', 'cookiesGet',
            'setIcon', 'hidePopup', 'tabsSendMessage', 'setPopupHeight',
            'createTab', 'getSelectedTab', 'getAllTabs', 'removeTab',
            'setTabUrl', 'addContextMenu', 'removeContextMenu']);
        this.main = client;
    };
};

})();
