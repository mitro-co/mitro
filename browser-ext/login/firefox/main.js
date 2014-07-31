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
 * The main script is a heart of the firefox addon. 
 * In case of mitro, it is the second heart, because we've
 * already have one - the background page.
 * 
 * The thing is that the firefox addons environment doesn't
 * provide the background page functionality in its chrome
 * of safari meaning. The main script is its closest analogue,
 * but it has a number of valuable differences as listed below:
 *   *the main script is a CommonJs thing (http://en.wikipedia.org/wiki/CommonJS). 
 *    It is running in a special sandbox, not within the window object
 *   *you can not load/connect/import external scripts to the main.js
 *    like you can do it in the background page. The scripts
 *    are required to be imported as the js modules instead
 * 
 * The things listed above make the main script operate
 * in a totally different way then the background page does.
 * That's why we still have the background page in firefox, but also
 * have to provide the main script support for it.
 * 
 * The firefox background page is implemented using the page-worker module
 * (See https://addons.mozilla.org/en-US/developers/docs/sdk/latest/modules/sdk/page-worker.html)
 * The main thing about it is that it doesn't have that browser-level access privileges
 * like it has in chrome or safari.
 * That's why every firefox BackgroundHelper method
 * requiring that kind of privileges is supported
 * by the corresponding function here in main script.
 */


/** @suppress {duplicate|const} */
var self = require("sdk/self");
var data = self.data;
var pageMod = require("sdk/page-mod");
var Request = require("sdk/request").Request;
var ss = require("sdk/simple-storage");
var utils = require("utils");
var tabs = require("sdk/tabs");
var MatchPattern = require("sdk/util/match-pattern").MatchPattern;
var clipboard = require("sdk/clipboard");
var cm = require("sdk/context-menu");
var helpers_common = require("helpers_common").helpers_common;
var prefs = require("sdk/preferences/service");
var system = require("sdk/system");
var toggle = require("sdk/ui/button/toggle");

/** @suppress {duplicate|const} */
var chrome = require("chrome");
var Cc = chrome.Cc;
var Ci = chrome.Ci;
var cookieSvc = Cc["@mozilla.org/cookiemanager;1"].getService(Ci.nsICookieService);
var ios = Cc["@mozilla.org/network/io-service;1"].getService(Ci.nsIIOService);

var DATA_PREFIX = 'resource://mitro-login-manager-at-jetpack/mitro-login-manager/data/';
var HTML_PREFIX = DATA_PREFIX + 'html/';

// "WINNT" on Windows, "Linux" on GNU/Linux; and "Darwin" on Mac OS X.
var osString = Cc["@mozilla.org/xre/app-info;1"].getService(Ci.nsIXULRuntime).OS;

// Disable ff password manager from saving passwords when autocomplete=off.
prefs.set('signon.storeWhenAutocompleteOff', false);

// values for Windows: needed to avoid scrollbars. We set body { overflow: hidden; } so they don't
// appear, but these seem to be required to get "perfect" display on Windows 8.1/FF 28
var popupExtraWidth = 7;
var popupExtraHeight = 0;
if (osString === 'Darwin') {
    popupExtraWidth = 2;
    popupExtraHeight = 0;
}

// var POPUP_WIDTH = 295 + 2 * POPUP_BORDER_SIZE;
var POPUP_WIDTH = 290 + popupExtraWidth;
// var POPUP_START_HEIGHT = 378 + 2 * POPUP_BORDER_SIZE;
var POPUP_START_HEIGHT = 378 + popupExtraHeight;

/**
 * Get scripts mapping dictionary
 * from the json file
 * and convert relative paths to absolute ones
 * 
 * @param jsonPath {string} Json file relative path
 * @returns {object} 
 */
function getPaths(jsonPath) {
    var json = JSON.parse(data.load(jsonPath));
    for (var key in json) {
        for (var i=0; i<json[key].length; i++) {
            json[key][i] = data.url(json[key][i]);
        }
    }
    
    return json;
}

/**
 * Delete the page object from the given list, if present
 * 
 * @param page {Worker} https://addons.mozilla.org/en-US/developers/docs/sdk/latest/modules/sdk/content/worker.html
 * @param trackingArray {array}
 */
function detachPage(page, trackingArray) {
  var index = trackingArray.indexOf(page);
  if(index !== -1) {
      trackingArray.splice(index, 1);
  }
}

/**
 * The functions below provide the background script support
 * in case where the background has no enough privileges
 */

/**
 * The firefox ajax implementation
 * 
 * See BackgroundHelper.ajax
 */
var ajax = function(params, callback){
    var _params = {
        url: params.url,
        content: params.data,
        overrideMimeType: "text/plain; charset=utf-8",
        onComplete: function(response){
            callback({
                status: response.status ? response.status : -1,
                text: response.text
            });
        }
    };
    if (typeof(params.type) !== 'undefined' && params.type === 'POST') {
        Request(_params).post();
    } else {
        Request(_params).get();
    }
};

var parseValue = function(value) {
  return value;
};

var serializeValue = function(value) {
  return value;
};

var mitroStorage = helpers_common.storage(ss.storage, parseValue, serializeValue);

 // TODO test that the object returned if the value is not found does 
 // not contain the key, even if the value is undefined.
var storageGet = mitroStorage.get;
var storageSet = mitroStorage.set;
var storageRemove = mitroStorage.remove;


/**
 * Firefox analogue to the chrome.cookies.get
 * 
 * See BackgroundHelper.cookies.get
 */
var cookiesGet = function(params, callback){
    var uri = ios.newURI(params.url, null, null);
    var cookie = cookieSvc.getCookieString(uri, null);
    if(cookie){
        var entries = cookie.split('&');
        for(var i=0; i < entries.length; i++){
            var entry = entries[i];
            var parts = entry.split('=');
            var name = parts[0];
            var value = parts.slice(1).join('=');
            
            if(name === params.name){
                callback({
                    name: name,
                    value: value,
                    domain: params.url
                });
                return;
            }
        }
    }

    console.log('Replying empty');
    callback({data: {}});
};

/**
 * Sends the message to the tab by the given tab id
 * 
 * See BackgroundHelper.tabs.sendMessage
 */
var tabsSendMessage = function(tabId, message){
  console.log('MESSAGING sending messages to. ', tabId);
    for(var i in contentPages[tabId]) {
      try {
        var page = contentPages[tabId][i];
        if (page.tab && page.tab.id === tabId) {
//            console.log('MESSAGING sending message ', JSON.stringify(message), ' ', tabId, ' ', i);
            page.postMessage(message);
            break;
        } else {    
          console.log('MESSAGING not sending message due to page mismatch or staleness');
        }
      }  catch (e) {
        console.log('Error SENDING MESSAGE');
        console.log('MESSAGING ',  e.message);
        console.log('MESSAGING ', e.stack);
      }

    }
};

/**
 * Creates the new tab with the given parameters
 * 
 * See BackgroundHelper.tabs.create
 */
var createTab = function(params, callback){
    tabs.open({
        url: params.url,
        onOpen: function(tab){
            if(typeof(callback) !== 'undefined'){
                callback({
                    id: tab.id,
                    index: tab.index,
                    url: params.url
                });
            }
        }
    });
    popup.hide();
};

var removeTab = function(tabId, callback) {
    var tab_found = false;
    for (var i=0; i<tabs.length; i++) {
        if (tabs[i].id === tabId) {
            tabs[i].close();
            tab_found = true;
            break;
        }
    }
    if(!tab_found) {
        console.log('Can not remove tab #' + tabId + '. Not found.');
    }
    
    if (typeof(callback) === 'function') {
        callback();
    }
};

/**
 * Updates the popup height
 * 
 * See BackgroundHelper.setPopupHeight
 */
var setPopupHeight = function(newHeight){
    popup.height = newHeight + popupExtraHeight;
};

/**
 * Makes the popup hide
 * 
 * See BackgroundHelper.hidePopup
 */
var hidePopup = function() {
    popup.hide();
};

/**
 * Helper for creating a mitro tab object from a firefox tab object.
 */
var _createMitroTab = function(firefoxTab) {
    var tab = new helpers_common.MitroTab(firefoxTab.id);
    // Firefox windows don't have ids.
    tab.windowId = null;
    tab.index = firefoxTab.index;
    tab.url = firefoxTab.url;
    tab.title = firefoxTab.title;
    return tab;
};

/**
 * Invokes the callback function with the
 * currently active tab as an argument
 * 
 * See BackgroundHelper.tabs.getSelected
 */
var getSelectedTab = function(callback){
    callback(_createMitroTab(tabs.activeTab));
};

var getAllTabs = function(callback) {
    var allTabs = [];
    for (var i = 0; i < tabs.length; i++) {
        allTabs.push(_createMitroTab(tabs[i]));
    }
    callback(allTabs);
};

/**
 * Returns a tab object for the given id, or null if tab not found.
 */
var _getFirefoxTab = function(tabId) {
    for (var i = 0; i < tabs.length; i++) {
        if (tabs[i].id === tabId) {
            return tabs[i];
        }
    }
    return null;
};

/**
 * Open url in the tab given by tabId.
 */
var setTabUrl = function(tabId, url) {
    var tab = _getFirefoxTab(tabId);
    if (tab) {
        tab.url = url;
    }
};

var clipboardSet = function(value, callback) {
    clipboard.set(value);
    
    if (typeof(callback) !== 'undefined') {
        callback();
    }
};

// Backgroun and content scripts
var paths = getPaths('paths.json');
// Extension pages scripts mappind
var mappingData = getPaths('html/scripts.json');

var Client = require("client").Client;
var client = new Client('main');

client.initRemoteCalls('background', 'addSecretFromSelection');

/**
 * Redirects currengly active tab
 * to the new location
 * 
 * @param url {string} New url
 */
var redirectActiveTab = function(url) {
  console.log('redirecting current tab to ', url);
  tabs.activeTab.url = url;
  return true;
};

var menu; // This holds the context menu instance

var addContextMenu = function() {
    var child = cm.Item({
        label: "Save selection as secure note",
        context: cm.SelectionContext(),
        contentScript: [
            'self.on("click", function() {',
            '  var text = window.getSelection().toString();',
            '  self.postMessage({',
            '      url: document.URL,',
            '      text: text',
            '  });',
            '})'].join('\n'),
        onMessage: function(data) {
            client.addSecretFromSelection(data.url, data.text);
        }
    });
    menu = cm.Menu({
        label: "Mitro",
        image: DATA_PREFIX + "img/mitro_logo-16.png",
        items: [child],
    	context: cm.SelectionContext()
    });
};

var removeContextMenu = function() {
    menu.destroy();
};

/**
 * The background page
 */
var background = require("sdk/page-worker").Page({
    contentScriptFile: paths.background_scripts,
    contentURL: data.url('background.html'),
    // these values will be used to generate clientIdentifier
    contentScriptOptions: {ID: self.id, VERSION: self.version}
});

// binding client
background.on('message', function(message){
    message.sendResponse = function(data){
        var new_message = client.composeResponse(this, data);
        background.postMessage(new_message);
    };
    client.processIncoming(message);
});
client.addSender('background', function(message) {
    background.postMessage(message);
});
client.addSender('extension', function(message){
    // the hack to tell if the message has to go
    // to the popup or the extension page
    if(message.sender.id === 'popup'){
        popup.postMessage(message);
    } else {
        for(var i=extensionPages.length-1; i>=0; i--){
            if(extensionPages[i].tab.id === message.sender.id){
                //TODO: make a cleanup or something. Or just the try...catc
                extensionPages[i].postMessage(message);
                break;
            }
        }
    }
});
client.addSender(['content', 'page'], function(message){
    if (message.sender.id in contentPages) {
        var tabWorkers = contentPages[message.sender.id];
        for (var i=0; i<tabWorkers.length; i++) {
          try {
            //console.log('MESSAGING trying to post message', JSON.stringify(message));
            tabWorkers[i].postMessage(message);
            //console.log('MESSAGING successfully posted');
          } catch (e) {
            //console.log('MESSAGING got stale worker, ignoring');
          }
        }
    } else {
        console.log('MESSAGING Error. No tab with id ', message.sender.id);
    }
});

// here we let the listed methods to be called from the background page
client.initRemoteExecution('background', [
  'storageGet', 'storageSet', 'storageRemove', 'ajax', 'cookiesGet',
  'hidePopup', 'tabsSendMessage', 'setPopupHeight', 'getSelectedTab',
  'redirectActiveTab', 'removeTab', 'getAllTabs', 'setTabUrl',
  'setIcon', 'addContextMenu', 'removeContextMenu'], this);

client.initRemoteExecution('content', ['redirectActiveTab'], this);
client.initRemoteExecution('extension', 'clipboardSet', this);
client.initRemoteExecution(['extension', 'background'], 'createTab', this);


/**
 * The popup
 */
// setting up the popup
var popup = require("sdk/panel").Panel({
    width: POPUP_WIDTH,
    height: POPUP_START_HEIGHT,
    contentURL: data.url("html/popup.html"),
    contentScriptFile: mappingData['popup.html']
});

// the hack to make the popup refresh on every activation
// just like the chrome browser popup behaves
popup.on('show', function() {
    if(popup.contentURL.indexOf('popup.html') !== -1){
        popup.contentURL = 'about:blank';
    }
    popup.contentURL = HTML_PREFIX + 'popup.html';
});

popup.on('hide', function() {
    popup.port.emit('popuphidden');
    onPopupHidden();
});

/**
 * The code below is a huge hack on firefox. In general,
 * the firefox doesn't provide the so-called
 * extension pages functionality. In other words,
 * the firefox doesn't associate the extension pages
 * with the extension itself. The scripts, if attached
 * using the <script> tag from within the page, 
 * do not have access to the extension functionality
 * even if the page is hosted inside the extension folder.
 * 
 * That's why we have to programmatically bind the scripts 
 * we need on that pages . This is also the reason
 * why every extension html file is being cleaned
 * of the <script> tags for the firefox builds
 */


/**
 * Extension pages
 */
//the array containing all active extension pages workers
var extensionPages = [];

/**
 * Binding scripts to pages
 */

for (var fileName in mappingData) {
    var fileScripts = mappingData[fileName];
    
    var matchingPaths = [HTML_PREFIX + fileName,
                         HTML_PREFIX + fileName + '#*',
                         HTML_PREFIX + fileName + '?*'];

    
    pageMod.PageMod({
        include: matchingPaths,
        contentScriptFile: fileScripts,

        onAttach: function(worker) {
            // keep the page worker for tracking purposes
            extensionPages.push(worker);
            worker.on('detach', function () {
                // stop tracking the worker on tab close
                detachPage(this, extensionPages);
            });
            worker.on("message", function(message){
                // passing the message to the client
                message.sender = {
                    id: worker.tab.id,
                    index: worker.tab.index,
                    url: worker.tab.url
                };

                message.sendResponse = function(data){
                    var new_message = client.composeResponse(this, data);
                    worker.postMessage(new_message);
                };
                
                client.processIncoming(message);
            });
        }
    });
}


/**
 * Content scripts
 */

 // this object will contain the arrays of the content pages frames:
 // the parent frame and the iframes if present
 // the keys are the tabs ids
 var contentPages = {};

pageMod.PageMod({
    include: ['*'],
    contentScriptFile: paths.content_scripts,
    contentScriptWhen: 'ready',
    onAttach: function(worker){
        // the code below is for tracking the content pages
        if (typeof(contentPages[worker.tab.id]) === 'undefined') {
            contentPages[worker.tab.id] = [];
        }
//        console.log('MESSAGING!! ADDING CONTENT LISTENER FOR ', worker.tab.id);
        contentPages[worker.tab.id].push(worker);
        
        worker.on("message", function(message){
            // bind client
            message.sender = {
                id: worker.tab.id,
                index: worker.tab.index,
                url: worker.tab.url
            };
            client.processIncoming(message);
        });
        
        worker.on("error", function(error) {
            console.log(error.message);
        });
    }
});

var cleanUpWorkers = function(tab_id) {
  try {
    var tabWorkers = contentPages[tab_id];
    var good_workers = [];
    if (tabWorkers) {
      for (var i = 0; i < tabWorkers.length; ++i) {
        if (tabWorkers[i].tab) {
          good_workers.push(tabWorkers[i]);
        }
      }
      if (good_workers.length) {
        contentPages[tab_id] = good_workers;
      } else {
        delete contentPages[tab_id];
      }
      console.log('cleaned up ', tabWorkers.length - good_workers.length, ' workers.' );
    }
  } catch (e) {
    console.log('ERROR ', e.message, e.stack);
  }
};


//tabs events
tabs.on('load', function(tab) {
//    console.log('***** got LOAD');
    cleanUpWorkers(tab.id);
});

tabs.on('ready', function(tab){
    background.port.emit('tab_updated', tab.id);
});

tabs.on('close', function(tab){
//  console.log('***** got CLOSE');
    cleanUpWorkers(tab.id);
    background.port.emit('tab_removed', tab.id);
});


//messages proxy to background
popup.on('message', function(message){
    message.sender = {
        id: 'popup'
    };
    
    message.sendResponse = function(data){
        var new_message = client.composeResponse(this, data);
        popup.postMessage(new_message);
    };
    
    client.processIncoming(message);
});

try {
  var firefoxVersion = parseInt(system.version.split('.')[0]);
} catch (e) {
  firefoxVersion = 0;
}

// Version specific data.
var toolbarButton;
var setIcon;
var onPopupHidden;

// Firefox 29 introduced the Australis ToggleButton, but adding a popup to
// the toolbar button isn't supported until FF 30
if (firefoxVersion >= 30) {
    var onToolbarButtonToggled = function(state) {
        if (state.checked) {
            popup.show({
                position: toolbarButton
            });
        }
    };

    toolbarButton = toggle.ToggleButton({
        id: 'mitro-toolbar-button',
        label: 'Mitro',
        icon: {
            '16': data.url('img/mitro_logo_gray-32.png')
        },
        onChange: onToolbarButtonToggled
    });

    setIcon = function(icons) {
        toolbarButton.icon = data.url(icons.path[32]);
    };

    onPopupHidden = function() {
        toolbarButton.state('window', {checked: false});
    };
} else {
    // Create the button in the toolbar
    toolbarButton = require('toolbarwidget').ToolbarWidget({
        toolbarID: 'nav-bar',
        id: 'mitro-toolbar-button',
        label: 'Mitro',
        contentURL: data.url('button.html'),
        contentScriptFile: data.url('button.js'),
        contentScriptWhen: 'ready',

        width: 28,
        height: 32,
        autoShrink: true,

        panel: popup
    });

    /**
     * Updates the popup icon
     *
     * See BackgroundHelper.setIcon
     */
    setIcon = function(icons) {
        console.log('setIcon?');
        toolbarButton.port.emit('setIcon', data.url(icons.path[32]));
    };

    onPopupHidden = function() {
    };
}
