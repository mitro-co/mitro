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

(function(){
  'use strict';

  function setCheckbox(checkbox, value) {

  }

  function loadInputs(storageResult) {
    var inputs = document.querySelectorAll('input');
    for (var i = 0; i < inputs.length; i++) {
      var storedPreference = storageResult[inputs[i].name];
      if (storedPreference) {
        if (inputs[i].type == 'checkbox') {
          inputs[i].checked = storedPreference;
        } else if (inputs[i].type == 'text') {
          inputs[i].value = storedPreference;
        }
      }
    }
  }

  function alertOnError() {
    if (chrome.runtime.lastError) {
      alert('Error saving perferences? ' + chrome.runtime.lastError);
    }
  }

  function onload() {
    // load preferences with the same name as the inputs
    var inputs = document.querySelectorAll('input');
    var preference_names = [];
    for (var i = 0; i < inputs.length; i++) {
      preference_names.push(inputs[i].name);
    }
    chrome.storage.sync.get(preference_names, loadInputs);

    var save = document.getElementById('savebutton');
    save.addEventListener('click', savePreferencesForm);

    var clearBlacklistButton = document.getElementById('clearBlacklistButton');
    clearBlacklistButton.addEventListener('click', clearBlacklist);

    var reloadButton = document.getElementById('reloadButton');
    reloadButton.addEventListener('click', reloadExtension);
  }

  function reloadExtension() {
    chrome.runtime.reload();
  }

  function savePreferencesForm() {
    var inputs = document.querySelectorAll('input');
    var output = {};
    var remove_preferences = [];
    for (var i = 0; i < inputs.length; i++) {
      var value = null;
      if (inputs[i].type == 'checkbox' && inputs[i].checked) {
        value = true;
      } else if (inputs[i].type == 'text' && inputs[i].value !== '') {
        value = inputs[i].value;
      }

      if (value === null) {
        remove_preferences.push(inputs[i].name);
      } else {
        output[inputs[i].name] = value;
      }
    }

    // The "save preferences" callback gets called twice
    var outputErrors = [];
    var outputAfterCalledTwice = function() {
      var error = null;
      if (chrome.runtime.lastError) {
        error = chrome.runtime.lastError;
      }
      outputErrors.push(error);

      if (outputErrors.length == 2) {
        var outputHtml = '';
        for (var i = 0; i < outputErrors.length; i++) {
          if (outputErrors[i] !== null) {
            outputHtml += outputErrors[i];
          }
        }

        if (outputHtml.length === 0) {
          outputHtml = 'Preferences saved; Click Reload below to apply the changes.';
          document.getElementById('reloadButton').disabled = false;
        } else {
          outputHtml = 'Error: ' + outputHtml;
        }
        var outputElement = document.getElementById('saveOutput');
        outputElement.innerHTML = outputHtml;
      }
    };

    chrome.storage.sync.set(output, outputAfterCalledTwice);
    chrome.storage.sync.remove(remove_preferences, outputAfterCalledTwice);
  }

  function clearBlacklist() {
    chrome.storage.sync.remove('save_blacklist', function() {
      var output = 'Blacklist cleared. Click Reload below to apply the changes.';
      if (chrome.runtime.lastError) {
        output = 'Error: ' + chrome.runtime.lastError;
      } else {
        document.getElementById('reloadButton').disabled = false;
      }

      var outputElement = document.getElementById('clearOutput');
      outputElement.textContent = output;
    });
  }

  // Load the form when the document loads
  $(onload);
})();
