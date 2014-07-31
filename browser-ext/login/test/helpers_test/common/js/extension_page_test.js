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
 * The code below tests the ExtensionHelper features
 * and reports each result to the background page.
 */

var helper = new ExtensionHelper();
var client = new Client('extension');
helper.bindClient(client);

client.initRemoteCalls('background', ['setResult', 'reportExtensionFinished']);

var tester = new Tester(false, 1000);

tester.addTest('extension.tabs', function(tester) {
    helper.tabs.create({
        url: TEST_URL
    }, tester.wrapCallback(function(tab) {
        assert(tab.id, 'Tab.id is empty');
        assert(typeof(tab.index) !== 'undefined' && tab.index >= 0, 'Tab index is incorrect');
        assert(tab.url === TEST_URL, 'Tab.url is incorrect');
        tester.reportSuccess();
    }));
});

// testing setLocation
tester.addAction(function() {
    // See the 'set_location_test.js' for the rest of the test actions
    helper.setLocation('set_location_test.html');
});

// launching the test
tester.run();

