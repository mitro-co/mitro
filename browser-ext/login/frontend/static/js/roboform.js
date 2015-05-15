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

  if (f.name.match('.html')) {
    
  } else {
    alert("Currently the tool only supports html uploads.");
    throw new Error("Unsupported file type");
  }

  reader.onload = (function (theFile) {
      return function (e) {
        var htmlRoboformExport = e.target.result;
        var passwords = parseHtml(htmlRoboformExport);
        appendPasswordsToPage(passwords);
      };
    })(f);

  reader.readAsText(f, 'UTF-8');
  $("#password-list").html("");
  $('#upload-progress').empty();
  passwords = [];
}

function parseHtml(html) {
  var roboformEntries = [];
  var titles = findLoginDataInRoboformTable(html,'caption');
  var loginUrls = findLoginDataInRoboformTable(html,'subcaption');
  var roboformPasswords = [];
  var usernames = [];
  var roboformSecretData = findLoginDataInRoboformTable(html,'wordbreakfield');
  var loginUrlsHtml = findLoginDataHtmlString(html,'subcaption');
  var i;
  for (i = 0; i < loginUrls.length; i++){
    var usernameHtml = $(loginUrlsHtml[i].parent().next().html());
    var passwordHtml = $(loginUrlsHtml[i].parent().next().next().html());
    usernames.push($(usernameHtml[usernameHtml.length-1]).text());
    roboformPasswords.push($(passwordHtml[passwordHtml.length-1]).text());
  }

  titles = cleanArray(titles);
  loginUrls = cleanArray(loginUrls);
  roboformPasswords = cleanArray(roboformPasswords);
  usernames = cleanArray(usernames);

  for (i = 0; i < titles.length; ++i) {
    var username = usernames[i];
    var password = roboformPasswords[i];
    var loginUrl = loginUrls[i];
    var title = titles[i];
    // TODO: extract roboform note
    var comment = '';
    
    passwords.push(new Password(username,password,loginUrl,title, comment));
  }
  return passwords;
}



function cleanArray(array){
  var rval = [];

  for(var i = 0;i<array.length;i++){
    if(array[i] !== undefined){
        rval.push(array[i]);
    }
  }
  return rval;
  }

var uploadedHtml;

function findLoginDataInRoboformTable(html,className){
    var rval = [];
    uploadedHtml = html;

    $.each($(html).find('.' + className),function(index,value){
      rval.push($(value).text());
  });

    return rval;
}

function findLoginDataHtmlString(html,className){
    var rval = [];

    uploadedHtml = html;

    $.each($(html).find('.' + className),function(index,value){
      rval.push($(value));
  });


    return rval;
}
