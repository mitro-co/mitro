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

(function() {
  'use strict';

  var downloadTextFile = function(text, filename) {
    // Create link to a blob then "click" it. From: 
    // https://github.com/eligrey/FileSaver.js
    // Blob encodes text as UTF-8: http://dev.w3.org/2006/webapi/FileAPI/#constructorBlob
    var blob = new Blob([text], {type: "text/plain;charset=utf-8"});
    var url = window.URL.createObjectURL(blob);

    var save_link = document.createElement('a');
    save_link.href = url;
    save_link.download = filename;

    var event = document.createEvent('MouseEvents');
    event.initMouseEvent('click', true, false, window, 0, 0, 0, 0, 0,
        false, false, false, false, 0, null);
    save_link.dispatchEvent(event);
  };

  var alertOnError = function(e) {
    alert('Unknown error occurred: ' + e);
  };

  document.addEventListener('DOMContentLoaded', function() {
    var servicesPromise = exportsecrets.fetchServicesForExport();
    servicesPromise.then(function(serviceInstances) {
      _.each(serviceInstances, processServiceInstanceForRendering);

      var li;
      var container = document.getElementById('all-secrets');
      if (serviceInstances.length > 0) {
        for (var i = 0; i < serviceInstances.length; i++) {
          li = document.createElement('li');
          li.textContent = serviceInstances[i].renderedTitle;
          if (serviceInstances[i].host) {
            li.textContent += ' (' + serviceInstances[i].host + ')';
          }
          container.appendChild(li);
        }
      } else { // No secrets in the account - show no secrets message
        li = document.createElement('li');
        li.textContent = 'No secrets to export';
        container.appendChild(li);
        $('#export-csv').hide(); // hide the export button
      }
    }).fail(alertOnError).done();

    $('#export-csv').click(function() {
      var $exportBtn = $(this);
      $('#export-complete-msg').hide(); // hide loading complete message
      $('#exporting-msg').show(); // show loading message
      $exportBtn.hide(); // hide the button so the user does not click again
      var exportSecrets = exportsecrets.exportAllSecretsPromise();
      exportSecrets.then(function(exportedSecrets) {
        var csvArray = exportsecrets.convertToLastPassCSVArray(exportedSecrets);
        var csvText = csvutil.toCSV(csvArray);

        downloadTextFile(csvText, 'mitro-passwords.csv');
      }).fail(alertOnError).done(function() {
        $('#exporting-msg').hide(); // hide loading message
        $('#export-complete-msg').show(); // show loading complete message
        $exportBtn.show(); // show export button again
      });
    });
  });
})();
