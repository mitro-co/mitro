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
 * The code below does the following things:
 * 
 * 1. Tests the BackgroundHelper features
 * 2. Performs the supporting operations
 *    for the ExtensionHelper and ContentHelper tests
 *    like starting ones, collecting test results,
 *    registering events etc.
 */

var helper = new BackgroundHelper();
var client = new Client('background');
helper.bindClient(client);

var tester = new Tester(true, 1000);

// these values will be used to check
// the scripts extraction correctness
var EXTRACTION_TEST_PAGE = 'scripts_extraction_test.html';
var EXTRACTION_TEST_CHECK_SCRIPTS = [
         "test_script_1.js",
         "test_script_2.js",
         "test_script_3.js",
         "test_script_4.js"];

/**
 * Saves the given feature test result.
 * We need this to collect results
 * from the extension and the content pages.
 * 
 * @param feature {string} Feature name
 * @param message {string} Test result
 */
var setResult = function(feature, message) {
    tester.setResult(feature, message);
};

// TODO: remove this
var getSelectedTab = function(callback) {
    helper.tabs.getSelected(callback);
};

/**
 * This is a placeholder for the function handling
 * the BackgroundHelper.tabs.sendMessage test success report
 * 
 * See 'BackgroundHelper.tabs.sendMessage' test
 */
var reportSendMessageSuccess;

/**
 * Registers the ExtensionHelper.setLocation test success
 */
var reportSetLocationSuccess = function() {
    tester.setResult('extension.setLocation', 'OK');
};

/**
 * The function to be called on ExtensionHelper test finish.
 * 
 * Launches the ContentHelper test.
 */
var reportExtensionFinished = function() {
    console.log('extension test finished');
    helper.tabs.create({
        url: TEST_URL
    }, function(tab) {
        // we have to wait for at least 500 ms
        // until the page gets ready to receive the message
        setTimeout(function(tab_id) {
            return function() {
                helper.tabs.sendMessage(tab_id,
                        client.composeMessage('content', 'run_test'));
            };
        }(tab.id), 500);
    });
};

/**
 * The function to be called on ContentHelper test finish.
 * 
 * Opens the results page in a new tab.
 */
var reportContentFinished = function() {
    helper.tabs.create({
        url: helper.getURL('html/results.html')
    });
};

/**
 * Registers the cookie setting page has loaded.
 * This way we make sure the cookie is ready
 * to be tested for correctness.
 */
var reportSetCookieLoaded = function() {
    helper.cookies.get({
        url: cookie_domain,
        name: cookie_name
    }, tester.wrapCallback(function(cookie) {
        assert(cookie.value === cookie_value);
        tester.reportSuccess();
    }));
};

/**
 * Passes the current test state data
 * to the given callback function
 * 
 * @param callback {function}
 */
var getState = function(callback) {
    var data = {};
    if (tester.current_feature) {
        data.current_feature = tester.current_feature;
    }
    data.results = tester.results;
    callback(data);
};

// making the listed functions reachable for the extension and content scripts
client.initRemoteExecution(['extension', 'content'],
        ['setResult', 'reportSendMessageSuccess', 'reportSetLocationSuccess',
         'reportExtensionFinished', 'reportContentFinished', 'getSelectedTab',
         'getState', 'reportSetCookieLoaded']);

tester.addTest(['background.ajax', 'background.getURL'], function(tester) {
    var json_url = helper.getURL('test_data.json');
    assert(json_url.length, 'background.getURL returned an empty value');

    // now testing ajax
    helper.ajax({
        url: json_url,
        complete: tester.wrapCallback(function(response){
            assert(response.text.length, 'Ajax response is empty');
            assert(JSON.parse(response.text).test === 'test',
                    'The value doesn\'t match the one we\'ve expected');
            tester.reportSuccess();
        })
    });
});

var storage_types = ['local', 'sync'];

for (var i=0; i<storage_types.length; i++) {
    var storage = helper.storage[storage_types[i]];
    
    tester.addTest('background.storage.' + storage_types[i], function() {
        // defining test objects
        var test_object1 = {
            test1: randomString(10),
            test2: randomString(10)
        };
        var test_object2 = {
            test3: randomString(10),
            test4: randomString(10)
        };
        
        // testing storage.set
        storage.set({
            test_object1: test_object1,
            test_object2: test_object2
        }, tester.wrapCallback(function() {
            // testing storage.get with the correct single key
            storage.get('test_object1', tester.wrapCallback(function(items){
                var operation = 'Fetch single value';
                assert(Object.keys(items).length === 1, operation + 'failed. Items count mismatch');
                tester.assertObjectsEqual(items.test_object1, test_object1,
                        operation + 'failed. Item value is incorrect.');
                
                // testing storage.get with an incorrect single key
                storage.get('test_object_undefined', tester.wrapCallback(function(items){
                    assert(Object.keys(items).length === 0, 'Fetch missing value failed.');
                    
                    // testing storage.get with the array of keys, mixed correct and incorrect
                    storage.get(['test_object1',
                                 'test_object2',
                                 'test_object_undefined'], tester.wrapCallback(function(items){
                        var operation = 'Fetch values by the keys array';
                        assert(Object.keys(items).length === 2, operation + ' failed. Items count mismatch');
                        tester.assertObjectsEqual(items.test_object1, test_object1,
                                operation + 'failed. Item value is incorrect.');
                        tester.assertObjectsEqual(items.test_object2, test_object2,
                                operation + 'failed. Item value is incorrect.');
                        assert(items.test_object_undefined === undefined, 
                                operation + 'failed. Item should be undefined.');
                        
                        // testing storage.get with the object
                        // providing keys and corresponding default values
                        storage.get({
                            test_object1: {},
                            test_object2: {},
                            test_object_undefined: test_object2
                        }, tester.wrapCallback(function(items){
                            var operation = 'Fetch values by the object with default values ';
                            assert(Object.keys(items).length === 3, operation + 'failed. Items count mismatch');
                            tester.assertObjectsEqual(items.test_object1, test_object1,
                                    operation + 'failed. Item value is incorrect.');
                            tester.assertObjectsEqual(items.test_object2, test_object2,
                                    operation + 'failed. Item value is incorrect.');
                            tester.assertObjectsEqual(items.test_object_undefined, test_object2,
                                    operation + 'failed. The default value had not been used.');
                            
                            // removing all the data from the storage
                            storage.get(undefined, tester.wrapCallback(function(items){
                                var operation = 'Fetch the entire storage contents ';
                                assert(Object.keys(items).length === 2, operation + ' failed. Items count mismatch');
                                tester.assertObjectsEqual(items.test_object1, test_object1,
                                        operation + 'failed. Item value is incorrect.');
                                tester.assertObjectsEqual(items.test_object2, test_object2,
                                        operation + 'failed. Item value is incorrect.');
                                
                                for (var item in items){
                                    storage.remove(item);
                                }
                                
                                // checking if the storage had become empty
                                storage.get(undefined, tester.wrapCallback(function(items) {
                                    assert(Object.keys(items).length === 0, 'Storage removal failed');
                                    tester.reportSuccess();
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    });
}

tester.addTest('background.getClientIdentifier', function() {
    var client_identifier = helper.getClientIdentifier();
    assert(client_identifier.length > 0, 'The returned value is empty');
    tester.reportSuccess();
});

// We need this to test the BackgroundHelper.tabs.onUpdated/onRemoved events.
// By comparing the id of the tab we've just updated/removed with the
// recent tab ids contained in this object we can find out if the corresponding
// events had been invoked correctly.
var recent_tab_ids = {
    updated: undefined,
    removed: undefined
};
helper.tabs.onUpdated(function(id){
    recent_tab_ids.updated = id;
});
helper.tabs.onRemoved(function(id){
    recent_tab_ids.removed = id;
});

// testing the tabs
tester.addTest('background.tabs', function(tester) {
    helper.tabs.create({
        url: TEST_URL
    }, tester.wrapCallback(function(tab) {
        assert(tab.id, 'Tab id is empty');
        // TODO: check what's the tab indexes base is
        assert(tab.index >= 0, 'Tab index is incorrect');
        
        setTimeout(function(tab_id){
            return tester.wrapCallback(function() {
                // testing the tabs.onUpdated event
                assert(recent_tab_ids.updated === tab_id, 'helper.tabs.onUpdated seems to be broken '
                        + recent_tab_ids.updated + ' != ' + tab_id);
                
                // testing tabs.sendMessage feature
                helper.tabs.sendMessage(tab_id,
                        client.composeMessage('content', 'test_sendMessage'));
            });
        }(tab.id), 2000);
        
        // we expect this to be invoked by the content page
        // if it successfully receive the test message
        reportSendMessageSuccess = tester.wrapCallback(function(){
            // testing tabs.remove
            helper.tabs.remove(tab.id, tester.wrapCallback(function() {
                setTimeout(function(tab_id) {
                    return tester.wrapCallback(function(){
                        // testing the tabs.onRemoved event
                        assert(recent_tab_ids.removed === tab.id, 'helper.tabs.onRemoved seems to be broken '
                                + recent_tab_ids.updated + ' != ' + tab_id);
                        tester.reportSuccess();
                    }, undefined, 'tabs.remove callback');
                }(tab.id), 500);
            }));
        }, 4000, 'sendMessage success report');
    }, undefined, 'tabs.create callback'));
});

//testing cookies
var cookie_domain = 'http://' + STATIC_SERVER_HOST + '/';
var setter_page_address = 'http://' + STATIC_SERVER_HOST + ':' + STATIC_SERVER_PORT + '/set_cookie.html';

var cookie_name = 'test_cookie';
var cookie_value = 'mitro-cookie-test';

tester.addTest('background.cookies', function(tester) {
    // Here we just open the page setting the test cookie.
    // See 'reportSetCookieLoaded' function for further actions
    helper.tabs.create({
        url: setter_page_address
    });
}, 1000);

tester.addTest('background.setIcon', function(tester) {
    // The only thing we can check about the 'setIcon' method
    // at the moment is that it doesn't cause the exception
    try {
        helper.setIcon({path: test_icons});
        tester.reportSuccess();
    } catch(e) {
        tester.reportError(e.message);
    }
});

tester.addTest('Scripts extraction', function(tester) {
    helper.ajax({
        url: STATIC_ROOT + '/html/scripts.json',
        complete: tester.wrapCallback(function(response) {
            var data = JSON.parse(response.text);
            // checking the test page data is present in scripts.json
            assert(typeof(data[EXTRACTION_TEST_PAGE] !== 'undefined'), 'The test page is not found in scripts.json');
            
            // checking that all the scripts we expect to be in the list
            // are actually there
            var check_scripts = EXTRACTION_TEST_CHECK_SCRIPTS;
            for (var i=0; i<check_scripts.length; i++) {
                assert(data[EXTRACTION_TEST_PAGE].indexOf(check_scripts[i]) != -1,
                        'The test script is not found in scripts.json');
            }
            
            helper.tabs.create({
                url: STATIC_ROOT + '/html/scripts_extraction_test.html'
            });
        })
    });
}, 1000);

tester.addAction(function() {
    // now launching the extension page test
    helper.tabs.create({
        url: helper.getURL('html/extension_page_test.html')
    }, function(tab) {
        // TODO: check if the extension page triggers
        // the tab_updated event the same way in all browsers
        setTimeout(function(tab_id) {
            return function(){
                helper.tabs.sendMessage(tab_id,
                        client.composeMessage('extension', 'sendMessageTest'));
            };
        }(tab.id), 500);
        tester.next();
    });
});

// launching the test
tester.run();

