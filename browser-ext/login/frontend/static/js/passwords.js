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

// password utils
var passwords = {};
(function(){
  'use strict';

/// Password strength stuff

var TYPES_OF_CHARACTERS_SCORE = 2;
var bloom = new BloomFilter(badPasswordBloomArray, 3);

passwords.scoreToText = function(score) {
  if (score < 33) {
    return 'Unacceptable';
  } else if (score < 67) {
    return 'Weak';
  } else if (score < 100) {
    return 'Good';
  } else {
    return 'Excellent';
  }
};

passwords.scoreToPercent = function (score) {
    return Math.min(Math.max(score, 5), 100);
};

passwords.scoreToColor = function (score) {
  if (score < 33) {
    return '#a91717';
  } else if (score < 67) {
    return '#c2c21a';
  } else if (score < 100) {
    return '#2cba19';
  } else {
    return '#246e24';
  }
};

passwords.scorePassword = function(pwd) {
  var score = -1 * TYPES_OF_CHARACTERS_SCORE;
  if (!pwd) {
    return score;
  }
  if (pwd.length < 8) {
    return -1;
  }

  // look for different kinds of stuff.
  var regexes = [
    /[a-z]/,   // lcase
    /\d/,      // numbers
    /[A-Z]/,   // ucase
    /\W/       // all kinds of random crap incl. unicode chars
  ];

  for (var r in regexes) {
    score += regexes[r].test(pwd) ? TYPES_OF_CHARACTERS_SCORE : 0;
  }

  // ensure we have a lots of different characters
  var charCount = {};
  for (var i = 0; i < pwd.length; ++i) {
    if (!charCount[pwd[i]]) {
      charCount[pwd[i]] = 0;
    }

    ++charCount[pwd[i]];
    
    if (i > 0 && (
      Math.abs(pwd.charCodeAt(i) - pwd.charCodeAt(i-1)) < 2)) {
      // adjacent repeated (or one-off) characters suck
      continue;
    }
    score += 1/(charCount[pwd[i]]);
  }

  var MIN_SIZE = 4;
  var MAX_SIZE = 10;
  for (var begin = 0; begin < pwd.length - MIN_SIZE; ++begin) {
    for (var end = MIN_SIZE + begin; end <= pwd.length && end <= begin + MAX_SIZE; ++end) {
      if (bloom.test(pwd.substr(begin, end).toLowerCase())) {
        console.log('punished password ');
        score -= 7;
        // don't search for other things within this fragment anymore.
        begin += (end - begin) - 1;
        break;
      }
    }
  }

  // normalize score (100 ~= strong)
  return score * (100 / 18);
};

passwords.validatePassword = function (password) {
  var score = passwords.scorePassword(password);
  return score >= 33;
};

passwords.updatePasswordMeter = function (password, $passwordText, $passwordBar) {
    var score = passwords.scorePassword(password);
    var text = passwords.scoreToText(score);
    var percent = passwords.scoreToPercent(score);
    var color = passwords.scoreToColor(score);

    // if password input is empty, drain the bar
    if (password.length === 0) {
      percent = 0;
      color = '';
      // unicode escape for non-breaking space
      text = '\u00a0';
    }

    $passwordText.text(text);
    $passwordBar.attr('aria-valuenow', percent).css('width', percent + '%');
    $passwordBar.css('background-color', color);
    $passwordText.css('color', color);
};

  // define node.js module for testing
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = passwords;
  }
})();
