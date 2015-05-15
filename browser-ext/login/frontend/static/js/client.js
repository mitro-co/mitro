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
 * Generates random string of the given length
 * The resulting string contains capital letters,
 * small letters and numbers
 * 
 * @param length {integer} the desired string length
 * 
 * TODO: move this function to the utils
 */
var randomString = function(length) {
    var text = "";
    var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    for(var i=0; i<length; i++) {
        text += possible.charAt(Math.floor(Math.random() * possible.length));
    }

    return text;
};

/**
 * Manages the messages transport
 * from/to different extension parts in case
 * when direct communication is not possible
 * 
 * @constructor
 * @param {string} address the local client address
 * ('background', 'extension', 'content', 'page')
 */
var Client = function(address) {
    // setting the local address
    this.address = address;
    // the dictionary containing the sender functions.
    // The keys are the receivers addresses the sender can send messages to
    this.senders = {};
    // the incoming message listeners dictionary.
    // The keys are the sender addresses from which
    // the listener expects the messages
    this.listeners = {};
    // the pending callbacks dictionary. The keys are the messages ids.
    this.callbacks = {};
    // the direct access flag. This should be set to true in case when we have
    // the ability to communicate with the remote script directly
    this.directAccess = false;
    
    /**
     * Sends the message to its destination
     * using the appropriate sender from the Client.senders dictionary
     * 
     * @param {Object} message
     * @param {function(Object)=} callback
     */
    this.sendMessage = function(message, callback) {

        if (message.to !== undefined && this.senders[message.to]) {
            if (typeof(callback) !== 'undefined') {
                // putting the callback to wait for the response
                this.addCallbackListener(message, callback);
            }
            // sending the message using the matching sender
            this.senders[message.to](message);
        } else {
            console.log('ERROR. No sender found for the message ' + message.type + ' from ' + message.from + ' to ' + message.to);
        }
    };
     
    /**
     * Processes the the incoming messages.
     * Either passes the function to the appropriate listener
     * or forwarding it to the destination (proxying)
     * if message.to address doesn't match the local address.
     * 
     * We need the proxy functionality for the mitro pages messages
     * which are being proxied by the content scripts
     * and for the firefox messaging implementation
     * where every message passes through the main.js
     * before getting to their destinations.
     */
    this.processIncoming = function(message) {
        if (message.type != 'console_log') {
            console.log(this.address + ' has got ' + (message.response ? 'response' : 'message') +
                    ' "' + message.type + '" from ' + message.from);
        }

        // try to handle the message if the message.to addrespos
        // matches the client address (means the message has come
        // to it's final location)
        if (message.to === this.address) {
            // first check if we have the message id
            // in the pending callbacks dict. If so,
            // pass the message to the stored callback function.
            if (message.response && message.id in this.callbacks) {
                this.callbacks[message.id](message);
                delete this.callbacks[message.id];
            } else {
                // if the message is not the response we expect for, then we try
                // to find the handler for it among the listeners
                // matching the message.from address
                
                // the listener function able to handle the message has to return true.
                // That's how we know the message has got handled and we don't need
                // to pass it to the next registered listener.
                if (message.from in this.listeners) {
                    var listeners = this.listeners[message.from];
                    for (var i=0; i<listeners.length; i++) {
                        try {
                            //console.log('TRYING to send message to listener, ',i);
                            if (listeners[i] && listeners[i](message)) {
                                return true;
                            }
                        } catch (e) {
                            // sometimes there can be errors where we have a stale content script?
                            console.log('ignoring bad event listener; exception:', e);
                        }
                    }
                }
                console.log('ERROR processing message. No handler found', message);
            }
        } else {
            // if the message.to param doesn't match the local address,
            // we just forward the message to it's destination
            this.sendMessage(message);
        }
    };
};

/**
 * This is for the local settings storage.
 * And this doesn't seem to work right.
 * TODO(ivan): fix this
 */
Client.prototype.settings = {};

/**
 * Turns the request message into the response
 * 
 * @param {!Object} message original message
 * @param {!Object} data the response data
 * @returns {!Object} the recomposed response message
 */
Client.prototype.composeResponse = function(message, data) {
    message.response = true;
    
    // interchanging the sender and the receiver addresses
    var temp = message.from;
    message.from = message.to;
    message.to = temp;
    
    // removing the request data
    delete message.data;
    if (typeof(message.sendResponse) === 'function'){
        delete message.sendResponse;
    }
    
    // setting the response data
    for (var i in data) {
        message[i] = data[i];
    }
    
    return message;
};

/**
 * Registers the sender function
 * 
 * @param to {string, array} the receiver(s) to be served by the sender
 * @param sender {function} the sender function
 */
Client.prototype.addSender = function(to, sender) {
    var _to = this.forceArray(to);
    for(var i=0; i<to.length; i++){
        if (_to[i] === undefined) {
            continue;
        }
        this.senders[_to[i]] = sender;
    }
};

/**
 * Puts the callback to the callbacks queue
 * 
 * @param {!Object} message
 * @param {function(!Object)} callback
 * 
 * TODO(ivan): we don't need to pass the entire message here
 * The message.id would be enough
 */
Client.prototype.addCallbackListener = function(message, callback) {
    this.callbacks[message.id] = callback;
};

/**
 * Registers the incoming messages handler
 * 
 * @param from {string} the sender address the handler expects messages from
 * @param handler {function} the handler function
 */
Client.prototype.addListener = function(from, handler) {
    from = this.forceArray(from);
    for (var i=0; i<from.length; i++) {
        if (this.listeners[from[i]]) {
            this.listeners[from[i]].push(handler);
        } else {
            this.listeners[from[i]] = [handler];
        }
    }
};

/**
 * This is a legacy code used for the background API calls
 * 
 * @param {string} type keeps the background API call type
 * @param {Object} data
 * @param {function(Object)} onSuccess
 * @param {function(Object)} onError
 */
Client.prototype.dispatchMessage = function (type, data, onSuccess, onError) {
    var message = this.composeMessage('background', type, data);
    
    var responseCallback = function (message) {
        var response = message.data;
        if (typeof response === 'object' && 'error' in response) {
            if (typeof onError !== 'undefined') {
                onError(response.error);
            }
        } else if (typeof onSuccess !== 'undefined') {
            onSuccess(response.data);
        }
    };

    // If running in the background script, call process message directly.
    if (this.directAccess) {
        this.background.processAPIMessage(message, responseCallback);
    } else {
        this.sendMessage(message, responseCallback);
    }
};

/**
 * Creates the message object having the desired parameters
 * 
 * @param {string} to message destination address
 * @param {string} type message type
 * @param {Object} data
 * @returns {Object} message
 */
Client.prototype.composeMessage = function(to, type, data) {
    var message = {
        id: randomString(10),
        type: type,
        data: data,
        from: this.address,
        to: to
    };
    
    return message;
};

/**
 * Sets up the background API calls methods
 */
Client.prototype.initApiCalls = function() {
    this.doExtensionLogin = function (item) {
        if (this.directAccess) {
            var self = this;
            helper.isPopup(function(is_popup) {
                if (is_popup) {
                    // if invoked from popup, we can't get the current tab, so we 
                    // give it a very large tab number to ensure that the new tab forms 
                    // to the right of all the other tabs.
                    var VERY_LARGE_TAB_NUMBER = 999999999;
                    self.background.doLogin(item, VERY_LARGE_TAB_NUMBER);
                } else {
                    helper.tabs.getSelected(function(tab) {
                        self.background.doLogin(item, tab.index + 1);
                    });
                }
            });
        } else {
            this.sendMessage(this.composeMessage('background', 'login', item));
        }
    };

    this.getIdentity = function (onSuccess, onError) {
        this.dispatchMessage('getIdentity', null, onSuccess, onError);
    };

    this.getLoginState = function (onSuccess, onError) {
        this.dispatchMessage('getLoginState', null, onSuccess, onError);
    };

    this.mitroLogout = function (onSuccess, onError) {
        this.dispatchMessage('mitroLogout', null, onSuccess, onError);
    };

    this.getSiteSecretData = function (secretId, onSuccess, onError) {
        this.dispatchMessage('getSiteSecretData', secretId, onSuccess, onError);
    };
    this.getSiteSecretDataForDisplay = function (secretId, onSuccess, onError) {
        this.dispatchMessage('getSiteSecretDataForDisplay', secretId, onSuccess, onError);
    };

    // TODO: this should allow us to share the site with an org right away
    this.addSecret = function (serverData, clientData, secretData, onSuccess, onError) {
        var data = {serverData: serverData,
                    clientData: clientData,
                    secretData: secretData};
        this.dispatchMessage('addSecret', data, onSuccess, onError);
    };

    this.addSecretToGroups = function (data, onSuccess, onError) {
        this.dispatchMessage('addSecretToGroups', data, onSuccess, onError);
    };

    this.editSecret = function (secretId, serverData, clientData, secretData, onSuccess, onError) {
        var data = {secretId: secretId,
                    serverData: serverData,
                    clientData: clientData,
                    secretData: secretData};
        this.dispatchMessage('editSecret', data, onSuccess, onError);
    };

    this.removeSecret = function (secretId, onSuccess, onError) {
        this.dispatchMessage('removeSecret', secretId, onSuccess, onError);
    };

    this.editSiteShares = function (secretId, groupIdList, identityList, orgGroupId, onSuccess, onError) {
        var siteData = {secretId: secretId,
                        groupIdList: groupIdList,
                        identityList: identityList,
                        orgGroupId: orgGroupId};
        this.dispatchMessage('editSiteShares', siteData, onSuccess, onError);
    };


    this.listUsersGroupsAndSecrets = function (onSuccess, onError) {
        this.dispatchMessage('listUsersGroupsAndSecrets', null, onSuccess, onError);
    };

    // TODO: remove this
    this.listGroups = function (onSuccess, onError) {
        this.dispatchMessage('listUsersGroupsAndSecrets', null, function(r) {onSuccess(r.groups);}, onError);
    };

    // TODO: remove this
    this.listUsers = function (onSuccess, onError) {
        this.dispatchMessage('listUsersGroupsAndSecrets', null, function(r) {onSuccess(r.users);}, onError);
    };


    // TODO: remove this
    this.fetchServices = function (onSuccess, onError) {
        this.dispatchMessage('listUsersGroupsAndSecrets', null, function(r) {onSuccess(r.secrets);}, onError);
    };



    this.getGroup = function (groupId, onSuccess, onError) {
        this.dispatchMessage('getGroup', groupId, onSuccess, onError);
    };

    this.addGroup = function (groupName, onSuccess, onError) {
        this.dispatchMessage('addGroup', groupName, onSuccess, onError);
    };

    this.removeGroup = function (groupId, onSuccess, onError) {
        this.dispatchMessage('removeGroup', groupId, onSuccess, onError);
    };

    this.editGroup = function (groupId, groupName, orgId, identityList, onSuccess, onError) {
        var groupData = {groupId: groupId,
                         name: groupName,
                         groupIdList: orgId ? [orgId] : null,
                         identityList: identityList};
        this.dispatchMessage('editGroup', groupData, onSuccess, onError);
    };

    this.addIssue = function (type, url, description, email, onSuccess, onError) {
        var data = {type: type,
                    url: url,
                    description: description,
                    email: email};
        this.dispatchMessage('addIssue', data, onSuccess, onError);
    };

    this.getAuditLog = function (orgId, offset, limit, startTimeMs, endTimeMs, onSuccess, onError) {
        var data = {orgId: orgId,
                    offset: offset,
                    limit: limit,
                    startTimeMs: startTimeMs,
                    endTimeMs: endTimeMs};
        this.dispatchMessage('getAuditLog', data, onSuccess, onError);
    };

    this.createOrganization = function(request, onSuccess, onError) {
        this.dispatchMessage('createOrganization', request, onSuccess, onError);
    };

    this.getOrganizationInfo = function (onSuccess, onError) {
        this.dispatchMessage('getOrganizationInfo', null, onSuccess, onError);
    };

    this.getOrganization = function (orgId, onSuccess, onError) {
        this.dispatchMessage('getOrganization', orgId, onSuccess, onError);
    };

    this.selectOrganization = function (orgId, onSuccess, onError) {
        this.dispatchMessage('selectOrganization', orgId, onSuccess, onError);
    };

    this.mutateOrganization = function (orgId, membersToPromote, newMembers, adminsToDemote,
            membersToRemove, onSuccess, onError) {
        var data = {
            orgId: orgId,
            membersToPromote: membersToPromote,
            newMembers: newMembers,
            adminsToDemote: adminsToDemote,
            membersToRemove: membersToRemove
        };
        this.dispatchMessage('mutateOrganization', data, onSuccess, onError);
    };

    this.changeRemotePassword = function (request, onSuccess, onError) {
        this.dispatchMessage('changeRemotePassword', request, onSuccess, onError);
    };
};


/**
 * The function replaces every callback
 * identification string among the arguments
 * with the function sending the appropriate response
 * 
 * @param {Object} message
 */
Client.prototype.processCallbacks = function(message) {
    var args = message.data;
    var argFunction = function() {
        var callback_number = args[i].substr('_callback_'.length);
            return function(data) {
                message.sendResponse({
                    data: {
                        callback: callback_number,
                        data: data
                    }
                });
            };
    };
    
    for (var i=0; i<args.length; i++) {
        if (typeof(args[i]) === 'string' && args[i].indexOf('_callback_') === 0) {
            args[i] = argFunction();
        }
    }

    return args;
};


/**
 * Sets up the remote call method
 * 
 * @param {string} to the remote calls destination address
 * @param {string} methodName
 */
Client.prototype.setMethod = function(to, methodName) {
    // we just make a link to the desired remote method
    // if we have direct access
    var that = this;
    if (this.directAccess) {
        this[methodName] = this[to][methodName];
    } else {
        this[methodName] = function() {
            var args = Array.prototype.slice.call(arguments);
            
            // here we keep the callback functions to use on response
            var callbacks = [];
    
            // the code below replaces every callback function among the arguments
            // by the string containing the callback order number.
            // This string will be used to invoke the proper callback on response
            for(var i=0; i<args.length; i++){
                if(typeof(args[i]) === 'function') {
                    callbacks.push(args[i]);
                    args[i] = '_callback_' + (callbacks.length - 1);
                }
            }
            
            /**
             * Invokes the right callback on response
             * 
             * @param {Object} message the response message
             */
            var invoke_callback = function(message) {
                callbacks[message.data.callback](message.data.data);
            };
            
            var message = that.composeMessage(to, methodName, args);
            
            that.sendMessage(message, invoke_callback);
        };
    }
};


/**
 * Sets up the bunch of remote call methods
 * 
 * @param to {string} remote script address
 * @param methods {array} the desired methods names
 */
Client.prototype.initRemoteCalls = function(to, methods) {
    methods = this.forceArray(methods);
    for (var i=0; i<methods.length; i++) {
        this.setMethod(to, methods[i]);
    }
};

/**
 * Converts the given plain value (if it is so)
 * into one element array or returns the value unchanged
 * 
 * This function is used to avoid checking
 * if the argument is an array or a single value
 * in every function accepting both types of values.
 * 
 * @param value {*}
 * @returns {!Array}
 * TODO(ivan): implement the more explicit array check
 */
Client.prototype.forceArray = function(value) {
    switch (typeof(value)) {
        case 'object':
            if (Object.prototype.toString.call(value) === '[object Array]') {
                return value;
            } else {
                return [value];
            }
        case 'undefined':
            return [];
        default:
            return [value];
    }
};

/**
 * Returns true if the user is logged in of false if not
 * @returns {boolean}
 * 
 * TODO(ivan): this needs to be reimplemented
 */
Client.prototype.isLoggedIn = function() {
    try {
        if(this.background && this.background.isLoggedIn()) {
            return true;
        }
    } finally {
        return false;
    }
};

/**
 * Sets up the remote calls execution
 * for the given method names.
 * 
 * The method names, requiring the user to be logged in,
 * are passed separately. The isLoggedIn check
 * will be performed before executing those methods.
 * 
 * @param {string|!Array.<string>} from remote caller address
 * @param {string|!Array.<string>} methods
 * @param {Object=} self (optional) the top level object which methods are to be executed remotely
 * The window object will be used if no self object provided
 * 
 * TODO(ivan): interchange the methods_login_required and methods_no_login_required order
 * for more convenient usage.
 */
Client.prototype.initRemoteExecution = function(from, methods, self) {
    var _self = self ? self : window;
    methods = this.forceArray(methods);
    from = this.forceArray(from);
    
    for (var i = 0; i < methods.length; ++i) {
        if (!_self[methods[i]]) {
            // TODO: throw an exception here? 
            console.log('WARNING: could not initRemoteExecution for method:', methods[i]);
        }
    }
    // registering listener
    var clientSelf = this;
    this.addListener(from, (function(client) {
        return function(message) {
            var method = message.type;
            
            // return false if the requested method
            // is not in the available methods lists
            if (methods.indexOf(method) === -1) {
                return false;
            }

            if (method != 'console_log') {
                console.log('< remote_call > ' + method);
            }
            if (!_self[method]) {
                console.log('unknown method' + method);
                return false;
            }
            // replacing every callback identifying string
            // by the function sending the appropriate response
            var args = clientSelf.processCallbacks(message);
            
            // invoking the requested method
            _self[method].apply(null, args);
            return true;
        };
    })(this));
};

/**
 * setting the CommonJS module
 * to be used by the firefox main.js
 */
if (typeof(exports) !== 'undefined') {
    exports.Client = Client;
}
