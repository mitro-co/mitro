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
 * The tester's main features are:
 *  * enable running tests one by one, not all at the same time
 *  * get the callbacks under control:
 *      manage timeouts,
 *      catch exceptions inside callbacks,
 *      report if callback not invoked etc.
 *  * collect and manage test results 
 * 
 * @param is_background {boolean} Is tester running in the background page?
 * @param default_timer {integer} The default timeout in milliseconds
 */
var Tester = function(is_background, default_timeout) {
    this.background = is_background;
    this.default_timeout = default_timeout;
    this.tests_sequence = [];
    this.counter = 0; // current test index
    this.current_feature = undefined;
    // this will keep the link to the timeout we'll use to revoke/refresh it
    this.watchdog = undefined;
    // This will hold all tests results
    this.results = {};
};

/**
 * Run the entry in sequence
 */
Tester.prototype.step = function() {
    var current_test = this.tests_sequence[this.counter];
    this.current_feature = current_test[0];
    
    var timeout = current_test[2];
    if (timeout > 0) {
        this.updateTimer(timeout);
    }
    
    try {
        current_test[1](this);
    } catch(e) {
        this.reportError(e);
    }
};

/**
 * Starts the test
 */
Tester.prototype.run = function() {
    this.step();
};

/**
 * Saves the test result.
 * 
 * @param feature {string, undefined} Feature being tested
 * @param message {string} Result message
 */
Tester.prototype.setResult = function(feature, message) {
    if (feature) {
        // All the test results are stored in the background page.
        // We just save the result if the tester is running in background.
        if (this.background) {
            if (typeof(this.results[feature] !== 'undefined')) {
                this.results[feature] = message;
            } else {
                console.log('Result already set. Feature: ' + message);
            }
        // If the tester is running on the extension page or the content page,
        // we send results to the background page
        } else {
            client.setResult(feature, message);
        }
    } else {
        console.log('No feature is set, ignoring result');
    }
    
    this.next();
};

/**
 * Increments the step index and launches the next sequence entry if present.
 */
Tester.prototype.next = function() {
    clearTimeout(this.watchdog);
    this.counter++;
    if (this.counter < this.tests_sequence.length) {
        this.step();
    } else {
        console.log('finished');
    }
};

/**
 * Report test success. Sets current feature result to 'OK'
 * and triggers tester.next()
 */
Tester.prototype.reportSuccess = function() {
    this.setResult(this.current_feature, 'OK');
};

/**
 * Report test fail. Sets current feature result
 * to the given error message and triggers tester.next()
 * 
 * @param message {string} The error message
 */
Tester.prototype.reportError = function(message) {
    this.setResult(this.current_feature, message);
};

/**
 * Sets/refreshes the timeout watchdog
 * 
 * @param timeout {integer} Desired timeout in milliseconds
 * @param description (optional) {string} Description
 */
Tester.prototype.updateTimer = function(timeout, description) {
    var that = this;
    if(this.watchdog !== undefined) {
        clearTimeout(this.watchdog);
    }
    
    this.watchdog = setTimeout(function(feature, description) {
        return function() {
            if(feature && feature === that.current_feature) {
                var error = 'Timeout';
                
                if (typeof(description) !== 'undefined') {
                    error += ' in: ' + description;
                }
                
                that.reportError(error);
            }
        };
    }(this.current_feature, description), timeout);
};

/**
 * Creates a wrapper over the given function
 * to get it's invocation under control.
 * Usable for the callback functions to
 * control their invocation and catch the exceptions.
 * 
 * @param func {function} Original callback function
 * @param timeout (optional) {integer, undefined} The desired timeout in milliseconds.
 *        The default timeout will be used if argument not provided
 * @param description (optional) {string} Callback description (mostly for debug purposes)
 * @returns {Function} The wrapped function
 */
Tester.prototype.wrapCallback = function(func, timeout, description) {
    var that = this;
    timeout = typeof(timeout) === 'undefined' ? this.default_timeout : timeout;
    
    if (typeof(description) !== 'undefined') {
        this.updateTimer(timeout, description);
    } else {
        this.updateTimer(timeout);
    }
    
    return function() {
        try {
            func.apply(this, arguments);
        } catch(e) {
            if(typeof(e) === 'string') {
                that.reportError(e);
            } else {
                that.reportError(e.message);
            }
        }
    };
};

/**
 * Adds the test to the sequence
 * 
 * @param feature {string} The name of the feature to be tested
 * @param test {function} The test function
 * @param timer (optional) {integer} Desired timeout in milliseconds.
 *        The default will be used if argument not provided.
 */
Tester.prototype.addTest = function(feature, test, timer) {
    if (typeof(timer) === 'undefined') {
        timer = 0;
    }
    this.tests_sequence.push([feature, test, timer]);
};

/**
 * Adds the utility action to the sequence.
 * 
 * @param func {function} The action function
 * @param timer {integer} The desired timeout.
 *        The default will be used if argument not provided
 */
Tester.prototype.addAction = function(func, timer) {
    if (typeof(timer) === 'undefined') {
        timer = 0;
    }
    this.tests_sequence.push([undefined, func, timer]);
};

/**
 * Assert two objects are equal
 * 
 * @param obj1 {object} First object to compare
 * @param obj2 {object} Second object to compare
 * @param message {string} Error message
 */
Tester.prototype.assertObjectsEqual = function(obj1, obj2, message) {
    message = (message === undefined) ? 'Objects not equal' : message;
    
    if (JSON.stringify(obj1) !== JSON.stringify(obj2)) {
        throw message;
    }
};
