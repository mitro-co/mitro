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
var password = require('./password-generator-lib');

var passwordData = null;
var PASSWORD = 'pwd';

var MockBackground = function() {
};

MockBackground.prototype.generatePassword = function(data, onSuccess) {
  passwordData = data.passwordReqs;
  onSuccess(PASSWORD);
}; 

password.background = new MockBackground();

var testPasswordGenerator = function(options, data) {
  password.generatePasswordWithOptions(options, function(pwd) {
    assert.equal(pwd, PASSWORD);
    assert.equal(passwordData.numCharacters, data.numCharacters);
    assert.equal(passwordData.minUppercase, data.minUppercase);
    assert.equal(passwordData.maxUppercase, data.maxUppercase);
    assert.equal(passwordData.minDigits, data.minDigits);
    assert.equal(passwordData.maxDigits, data.maxDigits);
    assert.equal(passwordData.minSymbols, data.minSymbols);
    assert.equal(passwordData.maxSymbols, data.maxSymbols);
  }, function() {
    assert(false, 'error generating password');
  });
};

var tests = [
  {options: {},
   data: {
     numCharacters: password.DEFAULT_PASSWORD_LENGTH,
     minUppercase: 3,
     maxUppercase: 1000,
     minDigits: 1,
     maxDigits: 1000,
     minSymbols: 1,
     maxSymbols: 1000
   }
  },
  {options: {
     numCharacters: 6,
     uppercase: true,
     digits: true,
     symbols: true
   },
   data: {
     numCharacters: 6,
     minUppercase: 3,
     maxUppercase: 1000,
     minDigits: 1,
     maxDigits: 1000,
     minSymbols: 1,
     maxSymbols: 1000
   }
  },
  {options: {
     numCharacters: 10,
     uppercase: false,
     digits: false,
     symbols: false
  },
  data: {
     numCharacters: 10,
     minUppercase: 0,
     maxUppercase: 0,
     minDigits: 0,
     maxDigits: 0,
     minSymbols: 0,
     maxSymbols: 0
   }
  }
];

for (var i = 0; i < tests.length; i++) {
  testPasswordGenerator(tests[i].options, tests[i].data);
}
