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

var fs = require('fs');
var vm = require('vm');
var assert = require('assert');

var myEval = function(dir) {
    var code = fs.readFileSync(dir);
    vm.runInThisContext(code, dir);
}.bind(this);

myEval(__dirname + "/../common/utils.js");
myEval(__dirname + "/contentscriptbase.js");

window = {addEventListener: function () {}};
chrome = {extension: {onMessage: {addListener: function() {}}}};

function testBackgroundMessage() {
    var contentScript = new ContentScript();

    var counter = 0;
    var incrementCounter = function (message, sender, sendResponse) {
        ++counter;
        sendResponse(counter); 
    };

    var savedResponse;
    var sendResponse = function (response) {
        savedResponse = response;
    };

    contentScript.addBackgroundMessageListener('messagetype', incrementCounter);

    _onMessageFromBackground({type: 'messagetype'}, null, sendResponse);

    assert.equal(counter, 1);
    assert.equal(savedResponse, 1);

    assert.doesNotThrow(function () {
        _onMessageFromBackground({type: 'unknowntype'}, null, sendResponse);
    });
}

function testPageMessage() {
    var contentScript = new ContentScript();

    var counter = 0;
    var incrementCounter = function (event) {
        ++counter;
    };

    contentScript.addPageMessageListener('messagetype', incrementCounter);

    var event = {source: window, data: {fromPage: true, type: 'messagetype'}};
    _onMessageFromPage(event);

    assert.equal(counter, 1);

    event.data.type = 'unknowntype';
    assert.doesNotThrow(function () {_onMessageFromPage(event);});
}

testBackgroundMessage();
testPageMessage();
