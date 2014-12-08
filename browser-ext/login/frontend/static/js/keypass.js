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
  if (f.name.match('.xml')) {
    
  } else {
    alert("Currently the tool only supports xml uploads.");
    throw new Error("Unsupported file type");
  }

  reader.onload = (function (theFile) {
      return function (e) {
        var xmlKeepassExport = e.target.result;
        var passwords = parseXML(xmlKeepassExport);
        appendPasswordsToPage(passwords);
      };
    })(f);

  reader.readAsText(f, 'UTF-8');
  $("#password-list").html("");
  $('#upload-progress').empty();
  passwords = [];
}

function parseXML(xmlKeepassExport) {
  $("#password-list").empty();
  var $xml = $($.parseXML(xmlKeepassExport));
  var passwords = parseKeepassVersion2x($xml);
  if (passwords.length !== 0) {
    return passwords;
  }

  passwords = parseKeepassVersion1x($xml);
  return passwords;
}

// I'm not sure what format this is? KeePass 2.x?
function parseKeepassVersion2x($xml) {
  var values = [];
  var keys = [];

  //Creates two arrays of corresponding values - one for xml keys and one for the values of the keys
  $("Entry", $xml).each(function () {
    var entries = [];
    $("Key", this).each(function() {
      entries.push(this.textContent);
    });
    keys.push(entries);
  });

  $("Entry", $xml).each(function () {
    var entries = [];
    $("Value", this).each(function() {
      entries.push(this.textContent);
    });
    values.push(entries);
  });

  var passwords = [];
  for (var i = 0; i < values.length; i++) {
    var username = values[i][keys[i].indexOf("UserName")];
    var password = values[i][keys[i].indexOf("Password")];
    var loginurl = values[i][keys[i].indexOf("URL")];
    var title = values[i][keys[i].indexOf("Title")];
    // TODO: extract keepass note
    var comment = '';

    passwords.push(new Password(username, password, loginurl, title, comment));
  }

  return passwords;
}

// KeePass 1.x from http://keepass.info/help/base/importexport.html#xml
// This also parses KeePassX 0.4.3, even though they look quite different
function parseKeepassVersion1x($xml) {
  var passwords = [];
  $("pwentry,entry", $xml).each(function (index, element) {
    var username = $("username", element).text();
    var password = $("password", element).text();
    var title = $("title", element).text();
    var url = $("url", element).text();
    // TODO: extract keepass note
    var comment = '';

    passwords.push(new Password(username, password, url, title, comment));
  });

  return passwords;
}


