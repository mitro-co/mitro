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
  mitro.importer.attachEventHandlers(handleFileSelect);
});

function handleFileSelect(evt) {
  var files = evt.target.files;
  var f = files[0];
  var reader = new FileReader();
  if (!f.name.match('.csv')){
    alert("You must upload a lastpass CSV file.");
    throw new Error("Unsupported file type");
  }

  reader.onload = (function (theFile) {
    return function (e) {
      var csvExport = e.target.result;
      var passwords = parseCSV(csvExport);
      appendPasswordsToPage(passwords);
    };
  })(f);

  reader.readAsText(f, 'UTF-8');
  $("#password-list").html("");
  $('#upload-progress').empty();
  passwords = [];
}

function parseCSV(csvText) {
  try {
    $("#password-list").empty();

    csvText = mitro.importer.convertLineEndingsToUnix(csvText);
    var objects = $.csv.toObjects(csvText);
    var rval = [];
    for (var i = 0; i < objects.length; ++i) {
      var title = objects[i].name;
      if (objects[i].grouping) {
        title += ' [' + objects[i].grouping + ']';
      }
      if (!objects[i].password) {
        continue;
      }
      passwords.push(new Password(objects[i].username, objects[i].password, objects[i].url, title, objects[i].extra));
    }
    return passwords;
  } catch (e) {
    console.log(e);
    console.log(e.stack);
    showErrorDialog(e);
  }
}
