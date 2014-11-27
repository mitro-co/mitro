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

/** @suppress{duplicate|const} */
var system = system || require('system');
/** @suppress{duplicate|const} */
var webPage = webPage || require('webpage');
/** @suppress{duplicate|const} */
var fs = fs || require('fs');

var printTrace = function(prefix, message, trace) {
  var msgStack = [prefix + message];
  if (trace && trace.length) {
    msgStack.push('TRACE:');
    trace.forEach(function(t) {
      msgStack.push(' -> ' + (t.file || t.sourceURL) + ': ' + t.line + (t.function ? ' (in function ' + t.function +')' : ''));
    });
  }
  console.error(msgStack.join('\n'));
};

phantom.onError = function(msg, trace) {
  printTrace('PHANTOM ERROR: ', msg, trace);
  phantom.exit(1);
};

/** @suppress{duplicate|const} */
var phantompage = phantompage || require('./phantompage.js');

var DEBUG_TRACE = false;
var DEBUG_REQUESTS = false;
var TIMEOUT_S = 120;

if (system.args.length != 4 && system.args.length != 5) {
  console.error('Usage: guess_form.js URL username password [cookies JSON path]');
  phantom.exit(1);
}

var url = system.args[1];
var username = system.args[2];
var password = system.args[3];

// read and set optional cookies
if (system.args.length == 5) {
  var cookiesData = fs.read(system.args[4]);
  var loadedCookies = JSON.parse(cookiesData);
  if (!Array.isArray(loadedCookies)) {
    throw new Error('restored cookies must be an array: ' + loadedCookies);
  }

  for (var i = 0; i < loadedCookies.length; i++) {
    var success = phantom.addCookie(loadedCookies[i]);
    if (!success) {
      throw new Error('failed to load cookie: ' + JSON.stringify(loadedCookies[i]));
    }
  }
}

var tryLoginPage = function(page, username, password) {
  return page.evaluate(function(username, password) {
    return __testing__.guessLoginForm(username, password);
  }, username, password);
};

var pageNavigator = new phantompage.Navigator(webPage.create());
pageNavigator.page.onError = function(msg, trace) {
  if (DEBUG_TRACE) {
    printTrace('PAGE ERROR: ', msg, trace);
  }
  // TODO: Exit if something weird fails here?
};

if (DEBUG_REQUESTS) {
  pageNavigator.page.onResourceRequested = function(request) {
    console.log('onResourceRequested:', JSON.stringify(request, undefined, 4));
  };
}
if (DEBUG_TRACE) {
  pageNavigator.page.onConsoleMessage = function(message) {
    console.log('PAGE log:', message);
  };
}

var pageOpened = pageNavigator.open(url);
// var redirectedAfterOpen = pageOpened.then(function() {
//   return pageNavigator.waitForRedirects();
// });
var submittedForm = pageOpened.then(function() {
  pageNavigator.page.injectJs('../frontend/static/js/jquery.min.js');
  pageNavigator.page.injectJs('../common/domain.js');
  pageNavigator.page.injectJs('../common/URI.js');
  pageNavigator.page.injectJs('../common/utils.js');
  var success = pageNavigator.page.injectJs('../common/loginpage.js');
  if (!success) {
    console.log('failed to injectjs');
    phantom.exit(1);
  }
  setTimeout(function() {
    rval = tryLoginPage(pageNavigator.page, username, password);
    console.log(JSON.stringify(rval));
    phantom.exit(1);
  }, 1000);
}).fail(function(e) {
  // PhantomJS adds stackArray to Errors
  var stackArray = [];
  if (e.stackArray) {
    stackArray = e.stackArray;
  }
  printTrace('Failure: ', e.message, stackArray);
  phantom.exit(1);
}).done();

setTimeout(function() {
  console.error('TIMED OUT!');
  phantom.exit(1);
}, TIMEOUT_S * 1000);
