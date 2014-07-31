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

$(document).ready(function() {
  if ($('#files').length > 0) {
    $('#files')[0].addEventListener('change', handleFileSelect, false);
  }
  $("#add-to-mitro-button").click( function(){
    $("#add-to-mitro-button").hide();
    addDataToMitro();
  });
});

function handleFileSelect(evt) {
  var files = evt.target.files;
  var f = files[0];
  var reader = new FileReader();

  if (f.name.match('.csv')) {

  } else {
    alert("Currently the tool only supports csv  uploads.");
    throw new Error("Unsupported file type");
  }

  reader.onload = (function (theFile) {
      return function (e) {
        var csvPasspackExport = e.target.result;
        appendPasswordsToPage(parseCSV(csvPasspackExport));
      };
    })(f);

  reader.readAsText(f, 'UTF-8');
  $("#password-list").html("");
  $('#upload-progress').empty();
  passwords = [];
}

function parseCSV(csv){
  if (csv !== ""){
    csv = mitro.importer.convertLineEndingsToUnix(csv);
    var passwordsArray = $.csv.toArrays(csv);

    for (var i = 0; i < passwordsArray.length; i++) {
      var passwordArray = passwordsArray[i];

      var title = passwordArray[0];
      var username = passwordArray[1];
      var password = passwordArray[2];
      var loginUrl = passwordArray[3];
      // TODO: extract passpack note
      var comment = '';

      // TODO: Don't reference this global variable!
      passwords.push(new Password(username, password, loginUrl, title, comment));
    }
  }

  return passwords;
}


