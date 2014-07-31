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

var DATA_PATH = 'resource://mitro-login-manager-at-jetpack/mitro-login-manager/data/';

var getExtensionId = function() {
    return EXTENSION_ID;
};

var getURL = function(path){
    return DATA_PATH + path;
};

function BackgroundHelper() {
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
            _self.main.getSelectedTab(function(tab){
                callback(tab);
            });
        },
        getAll: function(callback){
            _self.main.getAllTabs(function(tabs){
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
        client.addSender(['main', 'extension', 'content', 'page'], function(message) {
            self.postMessage(message);
        });
        
        client.initRemoteCalls('main', [
            'storageGet', 'storageSet', 'storageRemove', 'ajax', 'cookiesGet',
            'setIcon', 'hidePopup', 'tabsSendMessage', 'setPopupHeight',
            'createTab', 'getSelectedTab', 'getAllTabs', 'removeTab',
            'setTabUrl', 'addContextMenu', 'removeContextMenu']);
        this.main = client;
    };
}

function ExtensionHelper() {
    var _self = this;
    
    this.getURL = getURL;
    
    this.tabs = {
        create: function(params, callback){
            if (typeof(callback) !== 'undefined') {
                _self.main.createTab(params, callback);
            } else {
                _self.main.createTab(params);
            }
        },
        getSelected: function(callback){
            _self.background.getSelectedTab(callback);
        },
        getAll: function(callback){
            _self.background.getAllTabs(callback);
        }
    };
    
    this.copyFromInput = function ($element, callback) {
        $element.select();
        var value = $element.val();
        if (typeof(callback) !== 'undefined') {
            _self.main.clipboardSet(value, callback);
        } else {
            _self.main.clipboardSet(value);
        }
        
    };
  
    this.runPopupActions = function() {
        $('body').resize(function() {
            _self.background.setPopupHeight($('body').height());
        });
        
        $('body').resize();
        
        $('a').click(function() {
            var new_href = $(this).attr('href');
            if(['popup.html', 'signup.html', 'change_password.html'].indexOf(new_href) === -1 &&
                    $(this).attr('target') === '_blank'){
               _self.background.hidePopup();
            }
        });
    };
    
    this.setLocation = function(path){
        unsafeWindow.location = path;
    };

    this.onPopupHidden = function (callback) {
      var eventFired = false;

      var callbackWrapper = function () {
        if (!eventFired) {
          eventFired = true;
          callback();
        }
      };

      // Listen for two events to trigger the popup hidden event.  The first
      // event to occur will make the callback.
      //
      // The popuphidden event is sent from the content script when the
      // popup is hidden, but is susceptible to a race condition if the popup
      // gets reloaded before the popuphidden event is delivered.
      //
      // On the other hand, the onblur event is guaranteed to be called, but
      // only gets triggered immediately before the popup is reloaded.  Using
      // it alone would cause an unacceptable delay.  In combination with the
      // popuphidden event, it is a failsafe against the case where the
      // popup gets reloaded before the popuphidden event is sent.
      self.port.on('popuphidden', callbackWrapper);
      window.onblur = callbackWrapper;
    };
    
    this.bindClient = function(client){
        self.on('message', function(message) {
            client.processIncoming(message);
        });
        client.addSender(['background', 'main'], function(message){
            self.postMessage(message);
        });
        
        client.initRemoteCalls('main', ['createTab', 'clipboardSet']);
        
        this.background = this.main = client; // the aliases
    };
}

function ContentHelper() {
    var _self = this;
    
    this.getURL = getURL;

    // content scripts are unable to change window.location to a resource url on FF
    this.redirectTo = function(url) {
        _self.background.redirectActiveTab(url);
    };
    this.createTab = function(url) {
        _self.background.createTab({url: url});
    };

    this.preventAutoFill = function($passwordField, $form) {
        // turn off autocomplete now that we have submitted a form. This prevents us
        // from blocking chrome from autofilling passwords that we don't have saved!
        $form.attr('autocomplete', 'off');
    };

    this.bindClient = function(client){
        console.log('BINDING CONTENT CLIENT');
        self.on("message", function(message) {
            client.processIncoming(message);
        });
        client.addListener('background', this.messageLoop);
        client.addSender('background', function(message){
            self.postMessage(message);
        });
        client.addSender('main', function(message){
            self.postMessage(message);
        });

        client.initRemoteCalls('background', ['createTab']);
        client.initRemoteCalls('main', ['redirectActiveTab']);
        
        this.background = client;
    };
}
