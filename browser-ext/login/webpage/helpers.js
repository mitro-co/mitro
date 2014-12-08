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

// dummy
function createTab(options, callback){
    if(typeof(callback) !== 'undefined'){
        callback();
    }
}

// dummy
function getSelectedTab(callback){
    callback();
}

/**
 * Generates the absolute URL matching the given relative path
 * 
 * @param path {string} Relative path
 * @param suffix {string} (optional) Additional suffix of the page
 *    on which the function acts against the extension base path
 */
function getURL(path, suffix) {
    suffix = typeof(suffix) !== 'undefined' ? suffix : '';
    var root_path = location.pathname.substring(
            0, location.pathname.lastIndexOf('/') + 1 - suffix.length); // +1 is for trailing '/' which we want to keep
    return root_path + path;
}

/**
 * Returns the extension id
 * 
 * @returns {string}
 * 
 * To be reimplemented!
 */
function getExtensionId() {
    return EXTENSION_ID;
}

/**
 * The helper object containing the browser-specific code
 * to be used in the popup and on the extension pages
 */
function ExtensionHelper() {
    var _self = this;

    // The extension pages are always served from the 'html' folder
    // That's why we're passing 'html/' as a suffix argument
    this.getURL = function(path) {
        return getURL(path, 'html/');
    };
    
    /**
     * This contains the set of dummy functions
     * required for compatibility purposes
     */
    this.tabs = {
        create: createTab,
        getSelected: getSelectedTab
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
        
    };
    
    // dummy
    this.onPopupHidden = function() {};
    
    /**
     * Sets up the Client object to be used in the popup and the extension pages
     */
    this.bindClient = function(client){
        // if we have no direct access,
        // we set the client {Client} to message with the background page
        window.onmessage = function(event){
            client.processIncoming(event.data);
        };

        client.addSender('background', function(message){
            parent.postMessage(message, '*');
        });
        
        client.initRemoteCalls('background', 'createTab');
        
        this.background = client;
    };
    
    /**
     * Sets the frame location to the given path
     *
     * @param path {string}
     */
    this.setLocation = function(path){
        window.location = _self.getURL('html/' + path);
    };
}
