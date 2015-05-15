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
 * Creates a new tab with the desired parameters
 * 
 * @param options {object}:
 *   options.active {boolean} should the newly created tab be active (or background)
 *   options.index {number} the desired index of the tab
 *   options.url {string} the desired url
 * 
 * @callback callback (optional) {function}
 * 
 */
function createTab(options, callback){
    var visibility = 'foreground'; //default value
    if(typeof(options.active) !== 'undefined'){
        visibility = options.active ? 'foreground' : 'background';
    }
    var index = (typeof(options.index) !== 'undefined') ? options.index : undefined;
    
    var newTab = safari.application.activeBrowserWindow.openTab(visibility, index);
    newTab.url = options.url;
    if(typeof(callback) !== 'undefined'){
        callback({
            url: options.url,
            id: newTab.id,
            index: newTab.index
        });
    }
}

function _createMitroTab(safariTab) {
    var tab = new helpers_common.MitroTab(safariTab.id);
    tab.windowId = null;
    tab.index = safariTab.index;
    tab.url = safariTab.url;
    tab.title = safariTab.title;
    return tab;
}

/** 
 * Returns the object with the url property equaling the active tab url
 */
function getSelectedTab(callback) {
    var tab = safari.application.activeBrowserWindow.activeTab;
    callback(_createMitroTab(tab));
}

/**
 * Get all tabs over all windows
 */
function getAllTabs(callback) {
    var allTabs = [];
    for (var i = 0; i < safari.application.browserWindows.length; i++) {
        var win = safari.application.browserWindows[i];
        for (var j = 0; j < win.tabs.length; j++) {
            allTabs.push(_createMitroTab(win.tabs[j]));
        }
    }
    callback(allTabs);
}


/**
 * Returns a SafariBrowserTab object for the given id, or null if tab not found.
 */
function _getSafariTab(tabId) {
    for (var i = 0; i < safari.application.browserWindows.length; i++) {
        var win = safari.application.browserWindows[i];
        for (var j = 0; j < win.tabs.length; j++) {
            if (win.tabs[j].id === tabId) {
                return win.tabs[j];
            }
        }
    }
    return null;
}

/**
 * Open url in the tab given by tabId.
 */
function setTabUrl(tabId, url) {
    var tab = _getSafariTab(tabId);
    if (tab) {
        tab.url = url;
    }
}

/**
 * Generates the absolute URL matching the given relative path
 */
function getURL (path){
    path = path ? path : '';
    var base_url = safari.extension.baseURI;
    return base_url + path;
}

/**
 * Returns the extension id
 * 
 * @returns {string}
 * 
 * To be reimplemented!
 */
function getExtensionId() {
    return safari.extension.baseURI.match(/\/([^\/]+)\/$/)[1];
}

/**
 * Handles 'contextmenu' event.
 * Adds context menu item when there's a selected text.
 */
function contextMenuEventHandler(event) {
    if (event.userInfo.selection) {
        event.contextMenu.appendContextMenuItem("save_selected_as_secret", "Save selection as secure note");
    }
}

/**
 * Handles context menu commands
 */
function contextMenuCommandHandler(event) {
    // Save selected text as secret
    if (event.command === 'save_selected_as_secret') {
        // Send command to the active tab to copy selected text
        helper.tabs.getSelected(function(tab) {
            helper.tabs.sendMessage(tab.id,
                    client.composeMessage('content', 'copySelection', {tabUrl: tab.url}));
        });
    }
}


/**
 * The helper object containing the browser-specific code
 * to be used in the popup and on the extension pages
 */
function ExtensionHelper() {
    var _self = this;
    
    this.getURL = getURL;

    this.isPopup = function(callback){
        var isPopup = (typeof(safari.application) !== 'undefined');
        
        if (typeof(callback) !== 'undefined') {
            callback(isPopup);
            return true;
        }
        
        return isPopup;
    };
    
    /**
     * The set of browser-specific methods to deal with tabs
     * from the popup and the extension pages
     */
    this.tabs = {
        /**
         * Creates a new tab. See the createTab() func above
         */
        create: function(options, callback){
            if(_self.isPopup()){
                // with the popup page privileges, we can call createTab() directly
                createTab(options, callback);
            } else {
                // if it's not the popup (the extension page is running in a tab),
                // we have to use the background.createTab method
                _self.background.createTab(options, callback);
            }
            // the hack making the popup hide after the new tab
            // is opened. Mimics the chrome popup behavior
            if(_self.isPopup()) safari.self.hide();
        },
        /**
         * Passes the active browser tab object to the given callback func
         *
         * @param callback {function}
         */
        getSelected: function(callback) {
            if(_self.isPopup()){
                // eather we call getSelectedTab() directly
                getSelectedTab(callback);
            } else {
                // or calling the background method
                _self.background.getSelectedTab(callback);
            }
        },
        getAll: function(callback) {
            if(_self.isPopup()){
                getAllTabs(callback);
            } else {
                _self.background.getAllTabs(callback);
            }
        }
    };
    
    // this is a placeholder
    this.copyFromInput = function($element, callback) {
        callback();
    };

    /**
     * This code contains all the hacks we need to
     * make the popup behave the same way for all
     * the browsers we support.
     * 
     * Here, in safari helper, I've also placed
     * the tabs hack code to make links with target='_blank'
     * open in a new tab, not the new window
     * 
     * TODO: maybe give it some more verbose name
     */
    this.runPopupActions = function() {
        if(_self.isPopup()){
            // make the popup contents reload each time the toolbar button is pressed
            $(window).focus(function(event){
                window.location = 'popup.html';
            });
            
            // make the popup resize according to its content's size
            $('body').resize(function() {
                safari.self.height = $('body').height();
            });
            
            $('body').resize();
        }
        
        $('a').click(function() {
            var newHref = $(this).attr('href');
            if (!newHref) {
                return true;
            }
            // make the popup pages load in the same frame
            var matched = false;
            var urls = ['popup.html', 'change_password.html'];
            while (!matched && (urls.length > 0)) {
                matched = newHref.indexOf(urls.pop()) !== -1;
            }
            if (matched) {
                window.location.href = newHref;
                return false;
            // make the target='_blank' links open in a new tab, not the new window
            } else if($(this).attr('target') === '_blank'){
                _self.tabs.create({url: getURL('html/' + $(this).attr('href'))});
                if(_self.isPopup()) safari.self.hide();
                return false;
            }
        });
    };

    /**
     * Set the callback to be called when the popup is closed.
     */
    this.onPopupHidden = function (callback) {
        window.onblur = callback;
    };

    /**
     * Sets up the Client object to be used in the popup and the extension pages
     */
    this.bindClient = function(client){
        try {
            // trying to get direct access to the background page
            client.background = safari.extension.globalPage.contentWindow;
            client.directAccess = true;
        } catch(e){
            // if we have no direct access,
            // we set the client {Client} to message with the background page
            safari.self.addEventListener("message", function(request){
                client.processIncoming(request.message);
            }, false);

            client.addSender('background', function(message){
                safari.self.tab.dispatchMessage("message", message);
            });
            
            client.initRemoteCalls('background', 'createTab');
        }
        
        this.background = client;
    };
    
    /**
     * Sets the frame location to the given path
     *
     * @param path {string}
     */
    this.setLocation = function(path){
        window.location = getURL('html/' + path);
    };
}



/**
 * The helper object containing the browser-specific code
 * to be used from the content scripts
 */
function ContentHelper() {
    var _self = this;
    
    /**
     * Returns the absolute url matching the given relative path
     */
    this.getURL = getURL;
    
    this.redirectTo = function(url) {
        document.location = url;
    };
    this.createTab = function(url) {
        var win = window.open(url, '_blank');
        win.focus();
    };
    this.preventAutoFill = function($passwordField, $form) {
        $form.attr('autocomplete', 'off');

        console.log('using extreme measures to prevent browser form saving');
        // Safari's password manager triggers if this element is display: none
        // hide it above the top and left of the page
        var newInput = document.createElement('input');
        newInput.setAttribute('type', 'password');
        newInput.setAttribute('style', 'position: absolute; top: -10000px; left: -10000px;');
        $form[0].appendChild(newInput);
    };

    /**
     * Sets up the Client object to be used from the content scripts
     */
    this.bindClient = function(client){
        safari.self.addEventListener("message", function(request){
            client.processIncoming(request.message);
        }, false);
        client.addSender('background', function(message){
            safari.self.tab.dispatchMessage("message", message);
        });
        
        client.initRemoteCalls('background', ['createTab', 'addSecretFromSelection']);
        
        _self.background = client;
    };
    
    // Set userInfo param which the background 'contextmenu' event listener
    // will use to find out if there is selected text or not
    document.addEventListener('contextmenu', function (event) {
        var selection = window.getSelection().toString() ? true : false;
        safari.self.tab.setContextMenuEventUserInfo(event, {selection: selection});
    }, false);
}
