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

var mitro = mitro || {};
/** @suppress{duplicate} */
var assert = assert || /** @type {function(boolean)} */ (require('assert'));

(function () {
  'use strict';
  mitro.password = mitro.password || {};
  mitro.password.background = typeof background !== 'undefined' ? background : {};

  // TODO: Save password generation settings.
  mitro.password.DEFAULT_PASSWORD_LENGTH = 12;

  mitro.password.generatePasswordWithOptions = function(options, onSuccess, onError) {
    var getOption = function(name, defaultValue) {
        var optionType = typeof defaultValue;
        assert(optionType !== 'undefined');
        assert(typeof name === 'string');

        if (name in options) {
            assert(typeof options[name] === optionType);
            return options[name];
        } else {
            return defaultValue;
        }
    };

    var numCharacters = getOption('numCharacters', mitro.password.DEFAULT_PASSWORD_LENGTH);
    var useUppercase = getOption('uppercase', true);
    var useDigits = getOption('digits', true);
    var useSymbols = getOption('symbols', true);

    var data = {};
    // TODO:
    //data.url =  url of active tab if available.
    data.passwordReqs = {};
    data.passwordReqs.numCharacters = numCharacters;
    data.passwordReqs.minUppercase = useUppercase ? 3 : 0;
    data.passwordReqs.maxUppercase = useUppercase ? 1000 : 0;
    data.passwordReqs.minDigits = useDigits ? 1 : 0;
    data.passwordReqs.maxDigits = useDigits ? 1000 : 0;
    data.passwordReqs.minSymbols = useSymbols ? 1 : 0;
    data.passwordReqs.maxSymbols = useSymbols ? 1000 : 0;

    mitro.password.background.generatePassword(data, onSuccess);
  };

  if (typeof(module) !== 'undefined' && module.exports) {
    module.exports = mitro.password;
  }
})();
