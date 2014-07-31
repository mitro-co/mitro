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

var _backgroundMessageListeners = {};
var _pageMessageListeners = {};

var _onMessageFromPage = function (message) {
    console.log('onMessageFromPage: ' + message.type);

    if (message.type in _pageMessageListeners) {
        _pageMessageListeners[message.type](message);
    } else {
        return false;
    }
};

var _onMessageFromBackground = function (message) {
    //console.log('CONTENT SCRIPT onMessageFromBackground: ', JSON.stringify(message));

    if (message.type in _backgroundMessageListeners) {
        var handler = _backgroundMessageListeners[message.type];
        handler(message);
        return true;
    } else {
        return false;
    }
};

/**
@constructor
*/
var ContentScript = function ()  {
};

ContentScript.prototype.activatePageMessages = function (client) {
// TODO: figure out what this is doing here and why we had it?
/*    window.addEventListener("message", function(event){
        if(typeof(event.data.toPage) == 'undefined'){
            client.processIncoming(event.data);
        }
    }, false);

    client.addSender('page', function(message){
        message.toPage = true;
        window.postMessage(message, '*');
    });
    client.addListener('page', _onMessageFromPage);*/
};

ContentScript.prototype.addBackgroundMessageListener = function (messageType, handler) {
    assert(typeof messageType === 'string');
    assert(typeof handler === 'function');
    _backgroundMessageListeners[messageType] = handler;
};

ContentScript.prototype.addPageMessageListener = function (messageType, handler) {
    assert(typeof messageType === 'string');
    assert(typeof handler === 'function');
    _pageMessageListeners[messageType] = handler;
};

ContentScript.prototype.sendMessageToBackground = function (messageType, data, responseCallback) {
    assert(typeof messageType === 'string');

    // Data parameter was omitted, but there is a callback
    if (typeof data === 'function') {
        assert(typeof responseCallback === 'undefined');
        responseCallback = data;
        data = undefined;
    }

    var message = client.composeMessage('background', messageType, data);

    if (typeof responseCallback === 'undefined') {
        client.sendMessage(message);
    } else {
        client.sendMessage(message, responseCallback);
    }
};

ContentScript.prototype.sendMessageToPage = function (messageType, data) {
    assert(typeof messageType === 'string');
    var message = client.composeMessage('page', messageType, data);
    client.sendMessage(message);
};
