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

/** @suppress{duplicate} */
var mitro = mitro || {};
(function() {

/**
@interface
*/
mitro.Forge = function() {};

/**
@return {number}
*/
mitro.Forge.prototype.getRandomByte = function() {};

/** @const */
var UPPERCASE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
/** @const */
var LOWERCASE = 'abcdefghijklmnopqrstuvwxyz';
/** @const */
var DIGITS    = '0123456789';
/** @const */
var SYMBOLS   = '!@#$%^&*()';
/**
*@const
*/
var MAX_RANDOM_VALUE = 256;

// TODO: This is polluting the global namespace and should not be.
/** @param {boolean} expressionResult */
var _pw_assert = function(expressionResult) {
  if (!expressionResult) {
    throw new Error('Assertion failed');
  }
};

/**
*@param {mitro.Forge} forge
*@param {number} under
*@return {number}
*/
var getUnbiasedShortUnder = function(forge, under) {
  _pw_assert(under <= MAX_RANDOM_VALUE);
  _pw_assert(under > 0);
  // any unbiased random value must be below this number
  // (this is important to prevent low values from being chosen more often)
  var randomValueMustBeBelow = MAX_RANDOM_VALUE - (MAX_RANDOM_VALUE % under);
  var rnd;
  do {
    rnd = forge.getRandomByte();
  } while (rnd >= randomValueMustBeBelow);
  return (rnd % under);
};

/**
@constructor
@struct
*/
mitro.PasswordRequirements = function() {
  /** @type {number} */
  this.numCharacters = 8;
  
  /** @type {number} */
  this.minUppercase = 1;

  /** @type {number} */
  this.minDigits = 1;

  /** @type {number} */
  this.minSymbols = 1;
  /** @type {number} */

  /** @type {string} */
  this.symbolSet = '!#';

  // TODO: enable max values in generator; not currently used
  this.maxUppercase = -1;
  this.maxDigits = -1;
  this.maxSymbols = -1;
};
// TODO: add validation code for pwd reqs.

/** @const */
var DEFAULT_PASSWORD_REQUIREMENTS = new mitro.PasswordRequirements();
/** @const */
var MIN_CHARACTERS = 5;
/** @const */
var INVALID_REQUEST = '';

/**
@param {mitro.Forge} forge
@param {mitro.PasswordRequirements=} requirements
@return {string} password
*/
mitro.generatePassword = function(forge, requirements) {

  var i;
  if (!requirements) {
    requirements = DEFAULT_PASSWORD_REQUIREMENTS;
  }

  // if there are fewer than MIN_CHARACTERS characters, the password won't have enough entropy so we don't create one.
  // Also sum(mins) must be <= numCharacters otherwise the request is invalid.
  if ((requirements.numCharacters - (requirements.minDigits + requirements.minSymbols) < MIN_CHARACTERS) ||
        (requirements.minDigits + requirements.minUppercase + requirements.minSymbols) > requirements.numCharacters) {
    return INVALID_REQUEST;
  }

  var buffer = [];
  for (i = 0; i < requirements.numCharacters; ++i) {
    buffer.push(LOWERCASE[getUnbiasedShortUnder(forge, LOWERCASE.length)]);
  }
 

  // TODO: this currently ignores all max values.
  // keep track of which indexes in the buffer have been replaced.
  var replaced = {};

  // make a list of mutation operations, each element is a list of candidates to select replacements from
  var toReplace = [];
  for (i = 0; i < requirements.minUppercase; ++i) {
    toReplace.push(UPPERCASE);
  }
  for (i = 0; i < requirements.minDigits; ++i) {
    toReplace.push(DIGITS);
  }
  // TODO: verify symbolSet is a subset of SYMBOLS
  for (i = 0; i < requirements.minSymbols; ++i) {
    toReplace.push(requirements.symbolSet);
  }

  while (toReplace.length > 0) {
    _pw_assert(Object.keys(replaced).length < buffer.length);

    do {
      i = getUnbiasedShortUnder(forge, buffer.length);
    } while (i in replaced);

    replaced[i] = true;
    var candidates = toReplace.pop();
    buffer[i] = candidates[getUnbiasedShortUnder(forge, candidates.length)];
  }
  return buffer.join('');
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = mitro;
}

})();
