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
Wraps PhantomJS's web page API to provide reliable "navigation completed" events, ignoring
iframes and following JavaScript redirects.
*/

/** @suppress{duplicate} */
var kew = kew || require('./kew.js');
/** @const */
var phantompage = {};
(function(){
  'use strict';

  /**
  @param {...} var_args
  */
  var debugLog = function(var_args) {
    // console.log.apply(console, arguments);
  };

  /**
  @param {boolean} assertion
  */
  var debugAssert = function(assertion) {
    if (!assertion) {
      throw new Error('Assertion failed');
    }
  };

  /**
  @param {!kew.Promise} promise
  @param {string} status
  */
  // TODO: Use this to fail loads?
  var resolveWithLoadStatus = function(promise, status) {
    if (status === 'success') {
      promise.resolve(status);
    } else {
      promise.reject(new Error('Error from page.open. Status: ' + status));
    }
  };

  /**
  @constructor
  @struct
  */
  var LoadedStateMachine = function() {
    this.lastLoadedStatus = 'success';
    this.loadListener = null;
  };
  LoadedStateMachine.prototype.onLoadStarted = function() {
    debugLog('LoadedStateMachine onLoadStarted');
    debugAssert(this.lastLoadedStatus !== null);
    this.lastLoadedStatus = null;
  };
  LoadedStateMachine.prototype.onLoadFinished = function(status) {
    debugLog('LoadedStateMachine onLoadFinished', status);
    debugAssert(this.lastLoadedStatus === null);
    debugAssert(status !== null);
    this.lastLoadedStatus = status;

    if (this.loadListener) {
      var callback = this.loadListener;
      this.loadListener = null;
      callback(status);
    }
  };
  LoadedStateMachine.prototype.waitForLoaded = function() {
    var promise = new kew.Promise();
    if (this.lastLoadedStatus !== null) {
      promise.resolve(this.lastLoadedStatus);
    } else {
      debugAssert(this.loadListener === null);
      this.loadListener = function(status) {
        promise.resolve(status);
      };
    }
    return promise;
  };

  /**
  @constructor
  @struct
  @param {!phantomjs.Page} page
  */
  phantompage.Navigator = function(page) {
    /** @type {!phantomjs.Page} */
    this.page = page;
    // Set the user agent to be Chrome, not PhantomJS
    this.page.settings.userAgent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36';
    // defaults to true; shouldn't break logins but should avoid excess network traffic
    this.page.settings.loadImages = false;
    this.page.settings.resourceTimeout = 5000;  // ms

    // Replaced for unit testing
    this.eventLoopDefer = function(callback) {
      setTimeout(callback, 0);
    };

    // Aliasing setTimeout as an object property raises Illegal Invocation
    this.eventLoopDelay = function(callback, delayMs) {
      return setTimeout(callback, delayMs);
    };

    this.statusCodes = {};

    // maintain state about if we are navigating or not
    this.navigating = false;
    this.navigateListener = null;

    this.loadedState = new LoadedStateMachine();

    var self = this;
    self.page.onNavigationRequested = function(url, type, willNavigate, main) {
      debugLog('Navigator onNavigationRequested:', url, type, willNavigate, main);
      if (willNavigate && main) {
        self.navigating = true;
      }
    };

    self.page.onInitialized = function() {
      debugLog('Navigator onInitialized; navigating:', self.navigating);
      debugAssert(self.navigating === true);
      self.navigating = false;

      if (self.navigateListener) {
        var callback = self.navigateListener;
        self.navigateListener = null;

        // transfer the navigate listener to the load listener
        var navigationEnded = self.waitForNavigationEnd();
        navigationEnded.then(callback);
      }
    };

    self.page.onLoadStarted = function() {
      self.loadedState.onLoadStarted();
    };
    self.page.onLoadFinished = function(status) {
      self.loadedState.onLoadFinished(status);
    };

    self.page.onResourceReceived = function(resource) {
      if (resource.stage == 'end') {
        debugLog('onResourceReceived', resource.url, resource.status);
        self.statusCodes[resource.url] = resource.status;
      }
    };
  };

  phantompage.Navigator.prototype.waitForNavigationEnd = function() {
    var self = this;
    var waitForLoaded = self.loadedState.waitForLoaded();
    var waitDone = waitForLoaded.then(function(status) {
      // run the event loop to give JS time to redirect
      debugLog('loaded done; running event loop');
      var deferred = new kew.Promise();
      self.eventLoopDefer(function() {
        if (self.navigating) {
          debugLog('still navigating; calling waitForNavigate()');
          deferred.resolve(self.waitForNavigate());
        } else {
          debugLog('navigation done; resolving promise');
          deferred.resolve(status);
        }
      });
      return deferred;
    });
    return waitDone;
  };

  /**
  @param {string} url
  @return {!kew.Promise.<string>}
  */
  phantompage.Navigator.prototype.open = function(url) {
    var result = this.waitForNavigate();
    this.page.open(url, function(status) {
      debugLog('page.open callback status:', status);
    });
    return result;
  };

  phantompage.Navigator.prototype.isNavigating = function() {
    return this.navigating;
  };

  phantompage.Navigator.prototype.getPageData = function() {
    return {
      url: this.page.url,
      statusCode: this.statusCodes[this.page.url]
    };
  };

  phantompage.Navigator.prototype.waitForNavigate = function() {
    var self = this;
    debugAssert(self.navigateListener === null);

    var promise = new kew.Promise();
    self.navigateListener = function(status) {
      promise.resolve(status);
    };
    return promise;
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = phantompage;
  }
})();
