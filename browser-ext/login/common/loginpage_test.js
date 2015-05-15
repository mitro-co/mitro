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

var assert = require('assert');
var fs = require('fs');
var jsdom = require('jsdom');
var loginpage = require('./loginpage');

var TEST_DIR = './testdata/';
var TEST_FILE = TEST_DIR + 'loginpage.json';
var JQUERY_PATH = './jquery.min.js';

var ClassificationStats = function (name, numExamples) {
    this.name = name;
    this.positives = 0;
    this.negatives = 0;
    this.numExamples = numExamples;
};

ClassificationStats.prototype.passExample = function () {
    this.positives++;
    this._finishExample();
};

ClassificationStats.prototype.failExample = function () {
    this.negatives++;
    this._finishExample();
};

ClassificationStats.prototype.accuracy = function () {
    return this.positives / this.numExamples;   
};

ClassificationStats.prototype.printResults = function () {
    console.log('\n' + this.name + ' results:');
    console.log('positive: ' + this.positives);
    console.log('negative: ' + this.negatives);
    console.log('total:    ' + this.numExamples);
    console.log('accuracy: ' + this.accuracy());
};

ClassificationStats.prototype._finishExample = function () {
    if (this.positives + this.negatives === this.numExamples) {
        this.printResults();
    }
};

var testLoginPage = function (testData, stats) {
    var path = TEST_DIR + testData.path;
    var contents = fs.readFileSync(path, 'utf-8');

    var onLoad = function (errors, window) {
        console.log(testData.path);
        global.$ = window.$;

        assert.equal(errors, null);

        var loginForm = loginpage.guessLoginForm();

        if (loginForm &&
            loginForm.usernameField.name === testData.username_name &&
            loginForm.passwordField.name === testData.password_name) {
            stats.passExample(); 
        } else {
            console.log(loginForm);
            stats.failExample();
        }
    };

    jsdom.env(
        contents,
        [JQUERY_PATH],
        onLoad
    );
};

var testNonLoginPage = function (path, stats) {
    var contents = fs.readFileSync(TEST_DIR + 'html/' + path, 'utf-8');

    var onLoad = function (errors, window) {
        console.log(path);
        global.$ = window.$;

        assert.equal(errors, null);

        var loginForm = loginpage.guessLoginForm();

        if (loginForm === null) {
            stats.passExample();
        } else {
            console.log(loginForm);
            stats.failExample();
        }
    };

    jsdom.env(
        contents,
        [JQUERY_PATH],
        onLoad
    );
};

var testLoginPages = function () {
    var tests = JSON.parse(fs.readFileSync(TEST_FILE, 'utf-8'));
    var stats = new ClassificationStats('login test', tests.length);

    for (var i = 0; i < tests.length; i++) {
        testLoginPage(tests[i], stats);
    }
};

var endsWith = function (str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
};

var testNonLoginPages = function (stats) {
    fs.readdir(TEST_DIR + '/html', function(error, files) {
        var signupPages = files.filter(function (path) {
            return endsWith(path, 'nonlogin.html');
        });
        var stats = new ClassificationStats('nonlogin test', signupPages.length);
        
        signupPages.forEach(function (path) {
            testNonLoginPage(path, stats);
        });
    });
};

testLoginPages();
testNonLoginPages();
