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

$(function() {
  'use strict';

  // this needs to be run here to prevent the click handler from being bound
  // multiple times
  var $modal = $('#add-secret-members-modal');
  initAddAcl($('#acl-items'), $modal.find('.search'));

  $('.content').hide();
  $('.ugly-message').show();
  var secretId = NaN;
  var userData = null;

  // the URI code sometimes throws strange exceptions like TypeError and stuff :(.
  try {
    // TODO: manage-teams uses decodeQueryString; change this?
    var query = new URI(window.location.href).parseQuery();
    var secretIdString = query.getParam('secretId');
    secretId = parseInt(secretIdString, 10);
  } catch (ignored) {
  }
  if (isNaN(secretId)) {
    showErrorDialog('invalid secret id');
    return;
  }

  var secretDataDirty = false;
  var criticalDataDirty = false;
  var noteDataDirty = false;
  var secretMutater = null;
  
  var applyUpdates = function() {
    $('.content').hide();
    $('.ugly-message').text('Saving changes ...').show();
    var applyAclChanges = function() {
      secretMutater.setOrg(getOrgDropdownId($('.org-dropdown')));
      secretMutater.saveChanges(reload, onBackgroundError);
    };

    $('#add-secret-members-modal').modal('hide');

    if (secretDataDirty) {
      var args = getSecretDataFromPage();
        
      background.editSecret(secretId, args.serverData, args.clientData,
        // we only push new password information if it's marked as being dirty
        (secretMutater.getType() === 'note' && noteDataDirty || criticalDataDirty) ? args.secretData : null,
        applyAclChanges, onBackgroundError);
    } else {
      applyAclChanges();
    }
  };
  var setViewableLabelColor = function(checked) {
    if (checked) {
      $('#is-not-viewable-label').css('color', '#4c5961');
    } else {
      $('#is-not-viewable-label').css('color', '#a1a6aa');
    }
  };
  $('#is-not-viewable').change(function() {
    secretDataDirty = true;
    setViewableLabelColor($('#is-not-viewable').is(':checked'));
    $('.apply-changes-button').show();

  });
  $('#secret-note').change(function() {
    noteDataDirty = true;
  });
  $('#secret-password').change(function() {
    criticalDataDirty = true;
  });
  $('#secret-password').keyup(function() {
    $('#secret-password').attr('placeholder', '');
  });
  $('.editable').keyup(function() {
    console.log('secret data is dirty. Should update when the user clicks apply.');
    $('.apply-changes-button').show();
    secretDataDirty = true;
  });

  // search should _only_ target items in the modal
  registerLiveSearch($('#acl-item-search'), '.modal-list > .acl-item');
  $('.add-to-acl').click(function() {
    var templateData = secretMutater.makeSecretAclTemplateData();

    $('#acl-items').html(templates['add-members-template'].render(
          templateData));
    replaceBlankImages($('.team-icon, .member-icon'));

    $modal.modal('show');

    $(document).on('click', 'input[type=checkbox],.add-item', function() {
      $('#save-new-acls-button').show();
    });

    $('#save-new-acls-button').click(function(){
      // figure out what people clicked on.
      var $checkedBoxes = $('#acl-items').find('input[type=checkbox]:checked');
      var modified = false;
      // apply newly checked items to the stuff from before and commit it.
      // Important: Do NOT remove items that are not checked from the ACLs.
      for (var i = 0; i < $checkedBoxes.length; ++i) {
        var $box = $($checkedBoxes[i]);
        var $item = $box.closest('.acl-item');
        secretMutater.addAclItem($item);
      }

      applyUpdates();
    });
    setTimeout(function() {
      $('#acl-item-search').focus();
    }, 100);
  });

  // TODO: Determining state from the DOM seems gross; Do this some other way?
  var HIDE_TEXT = 'HIDE';
  var SHOW_TEXT = 'SHOW';
  $('#show-password-button,#show-note-button').click(function(){
    var MESSAGE = 'Decrypting data ...';
    if ($('#is-web-password').is(':checked')) {
      // check if we have data
      if ($('#show-password-button').text() === HIDE_TEXT) {
        // hide the password!
        $('#secret-password').val('');
        $('#secret-password').attr('placeholder', '(password not shown)');
        $('#show-password-button').text(SHOW_TEXT);
        return;
      } else {
        $('#show-password-button').text(HIDE_TEXT);
        $('#secret-password').val(MESSAGE);
        // if they had edited the password, it is overwritten: clear this
        // TODO: Hide "apply changes" if there are no changes to display?
        criticalDataDirty = false;
        $('#secret-password').attr('placeholder', '');  // required for empty password
      }
    } else {
      $('#secret-note').val(MESSAGE);
    }

    secretMutater.getCriticalDataForDisplay(function (data) {
      if (secretMutater.getType() === 'note') {
        $('#secret-note').val(data);
      } else {
        $('#secret-password').val(data);
      }
    }, onBackgroundError);
  });

  $('#copy-username-button').click(function () {
    var $button = $(this);
    var $spinny = showSpinny($button);

    helper.copyFromInput($('#secret-username'), function () {
        hideSpinny($button, $spinny);
        $button.text('COPIED');
        console.log('username copied');
    });
  });

  $('#copy-password-button').click(function () {
    var $button = $(this);
    var $spinny = showSpinny($button);

    secretMutater.getCriticalDataForDisplay(function (data) {
      copyText(data, function () {
        hideSpinny($button, $spinny);
        $button.text('COPIED');
        console.log('password copied');
      });
    }, onBackgroundError);
  });

  var renderSecretInfo = function (secret) {
    var type = secret.clientData.type;
    var username = secret.clientData.username;
    //var password = secret.criticalData.password;
    var url = secret.clientData.loginUrl;
    //var note = secret.criticalData.note;
    var title = secret.clientData.title;
    var renderedTitle = getRenderedTitle(secret);

    $('#secret-name').attr('placeholder', renderedTitle);

    $('#secret-name').val(title);
    $('#secret-url').val(url);
    $('#secret-username').val(username);
    if (!secret.isViewable) {
      $('#is-not-viewable').prop('checked', true);
    } else {
      $('#is-not-viewable').prop('checked', false);
    }
    setViewableLabelColor($('#is-not-viewable').is(':checked'));

    if (!secret.canEditServerSecret || !url || type === 'note') {
      $('#is-not-viewable-row').remove();
    }

    if (type !== 'note') {
      $('.for-manual').show();
      $('#show-note-button').addClass('hide');
      $('#secret-note').val(secret.clientData.comment);
      $('#is-web-password').prop('checked', true);
    } else {
      $('.for-manual').hide();
      $('#show-note-button').removeClass('hide');
      $('#secret-note').val('(secret hidden)');
      $('#is-secure-note').prop('checked', true);
    }

    var orgs;
    if (typeof secret.owningOrgId === 'number') {
        orgs = [{id: secret.owningOrgId, name: secret.owningOrgName}];
    } else {
        orgs = userData.getOrganizations();
    }

    initOrgDropdown($('.secret-org-dropdown-group'), orgs, secret.owningOrgId);
    $('.org-dropdown li').click(function () {
      $('.apply-changes-button').show();
    });
  };

  var renderSecretAcl = function (secret) {
    var users = processUsersForRendering(userIdsToUserMap(secret.users));
    var groups = processGroupsForRendering(secret.groupMap);

    // set the ACL part.
    $('#secret-acl-groups-members').html(templates['secret-acl-template']
        .render({users: users, groups: groups}));
    replaceBlankImages($('.member-icon, .team-icon'));

    if (!secret.groups.length && !secret.users.length)  {
      $('.no-existing-teams').show();
      $('.existing-teams').hide();
    } else {
      $('.no-existing-teams').hide();
      $('.existing-teams').show();
    }
  };

  var renderSecret = function (secret) {
    renderSecretInfo(secret);
    renderSecretAcl(secret);
  };

  var onSecretLoaded = function (secret, userData) {
    secretMutater = new SecretMutater(secret, background, userData);

    renderSecret(secret);

    $('.content').show();
    $('.ugly-message').hide();

    $(document).on('click', '.apply-changes-button', applyUpdates);

    $(document).on('click', '.remove-user', function() {
      var $item = $(this);
      secretMutater.removeAclItem($item);

      $item.closest('li').hide();
      $('.apply-changes-button').show();
    });

    $('.delete-secret-link').click(function () {
      var title = 'Delete Secret';
      var message = 'Really delete secret "' + getRenderedTitle(secret) + '"?';
      showDeleteDialog(title, message, function () {
        showSpinny($(this));
        background.removeSecret(secretId, function () {
          helper.setLocation('secrets.html');
        }, onBackgroundError);
      });
    });

    background.getIdentity(function(identity) {
      var isRemotePasswordChangeAllowedForEmail = function(email) {
        var ALLOWED_SUFFIXES = [
          '@mitro.co',
          '@lectorius.com',
          '@example.com'
        ];
        for (var i = 0; i < ALLOWED_SUFFIXES.length; i++) {
          var suffix = ALLOWED_SUFFIXES[i];
          if (email.indexOf(suffix, email.length - suffix.length) !== -1) {
            return true;
          }
        }
        return false;
      };

      var allowRemotePasswordChange = function(secret, email) {
        var url = secret.clientData.loginUrl;
        return url && isRemotePasswordChangeAllowedForEmail(email);
      };

      if (allowRemotePasswordChange(secret, identity.uid)) {
        $('.change-password-button').show();

        // TODO: Change remote password should be part of apply updates.
        $('.change-password-button').click(function () {
          var doChangePassword = function (newPassword) {
            var data = {
              secretId: secretId,
              newPassword: newPassword
            };
            var $spinny = showSpinny($button);

            background.changeRemotePassword(data, reload, function (error) {
              hideSpinny($button, $spinny);
              onBackgroundError(error);
            });
          };

          var $button = $(this);
          mitro.password.generatePasswordWithOptions({}, function (password) {
            doChangePassword(password);
          }, onBackgroundError);
        });
      }
    });
  };

  mitro.loadUserDataAndSecret(secretId, function (ud, secret) {
    userData = ud;
    onSecretLoaded(secret, ud);
  }, onBackgroundError);
});
