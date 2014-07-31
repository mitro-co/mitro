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

function loadFormFromDocument() {
  return {
    formElement: document.getElementById('create-org-form'),
    nameInput: document.getElementById('name-input'),
    saveButton: document.getElementById('create-org-button'),
    outputDiv: document.getElementById('output')
  };
}

function saveOrganization(form) {
  // read name from the form
  var name = form.nameInput.value;
  name = name.trimRight();
  name = name.trimLeft();

  // read owners and members from the form
  background.getIdentity(function (identity) {
      var owners = [identity.uid];
      var members = [];

      var request = {
        'name': name,
        'owners': owners,
        'members': members
      };
      background.createOrganization(request, function (response) {
        window.console.log('createOrganization response', response);
        hideSpinny($(form.saveButton), $spinny);
        helper.setLocation('admin-dashboard.html');
      }, function (error) {
        window.console.log('error', error);
        showErrorDialog(error.userVisibleError);
        hideSpinny($(form.saveButton), $spinny);
      });
      form.outputDiv.textContent = 'submitted request, please be patient ...';
      var $spinny = showSpinny($(form.saveButton));
    }, onBackgroundError);
}

$(function(){
  var form = loadFormFromDocument();
  form.formElement.addEventListener('submit', function(event) {
    saveOrganization(form);
    event.preventDefault();
  });
});
