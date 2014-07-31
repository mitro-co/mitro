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
 * The code below tests the ContentHelper features
 * and reports each test result to the background page.
 */


var helper = new ContentHelper();
var client = new Client('content');
helper.bindClient(client);
client.initRemoteCalls('background', ['setResult', 'reportContentFinished', 'reportSendMessageSuccess',
                                      'reportSetCookieLoaded']);

// handling special messages from background
client.addListener('background', function(message) {
    switch (message.type) {
        case 'test_sendMessage':
            client.reportSendMessageSuccess();
            break;

        // We don't want the content test to run each time
        // the new page/tab is opened. That's why we only launch
        // the test if the appropriate message is received.
        case 'run_test':
            tester.run();
            break;
    }
    return true;
});

var tester = new Tester(false, 1000);

tester.addTest('content.getURL', function(tester) {
    var test_path = 'test_page.html';
    var url = helper.getURL(test_path);
    assert(url.length > 0, 'The returned url is empty');
    assert(url.indexOf(test_path) != -1, 'The returned url in incorrect');
    tester.reportSuccess();
});

tester.addAction(function(tester) {
    helper.redirectTo(helper.getURL('html/redirect_to_test.html'));
    tester.next();
});


/**
 * The code below is for the BackgroundHelper.cookies test.
 * It's purpose is to report the cookie setting page finished loading
 * which means the cookie has been set and it's time to test it's correctness.
 */
$(function() {
    switch (window.location.pathname) {
        case "/set_cookie.html":
            client.reportSetCookieLoaded();
            break;
        case "/html/scripts_extraction_test.html":
            var _window = typeof(unsafeWindow) === 'undefined' ? window : unsafeWindow;
            var message = "Not all the scripts have been removed";
            var feature = 'Scripts extraction';
            
            // The variables below are defined in the scripts
            // we expect to be removed from the page. We expect them to be undefined.
            try {
                assert(typeof(_window.test_var_1) === 'undefined', message);
                assert(typeof(_window.test_var_2) === 'undefined', message);
                assert(typeof(_window.test_var_3) === 'undefined', message);
                assert(typeof(_window.test_var_4) === 'undefined', message);
                assert(typeof(_window.test_var_5) === 'undefined', message);
                
                client.setResult(feature, 'OK');
            } catch(e) {
                client.setResult(feature, e);
            }
    }
});