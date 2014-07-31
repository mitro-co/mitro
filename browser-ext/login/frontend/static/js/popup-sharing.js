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

  var activeSecretId = null;
  var secretMutater = null;

  $(document).ready(function() {
    var $shareList = $('.share-list');
    var $invitePane = $('[data-pane="invite"]');
    var $inviteItem = $('#invite-item');

    var inviteForm = {
      emailElement: $invitePane.find('input[name="email"]'),
      nameElement: $invitePane.find('input[name="name"]'),
      messageElement: $invitePane.find('input[name="message"]')
    };

    // fill in the "invite name" with typed query
    $('#team-filter-string').keyup(function(e) {
      var queryString = $(this).val();
      $('#invite-item #invite-name').html(queryString);
    });

    // clear "invite name" when clear link is clicked
    $('#team-filter-clear').click(function() {
      $('#invite-item #invite-name').html('');
    });

    var renderSecretAcl = function () {
      if (!secretMutater) {
        return;
      }

      var templateData = secretMutater.makeSecretAclTemplateData();
      $shareList.html(templates['popup-share-secret-template'].render(
        templateData));
      replaceBlankImages($('.share-list .user-icon, .share-list .group-icon'));
      registerLiveSearch($('#team-filter-string'), '.share-list > .item',
                         $('#team-filter-clear'));
    };

    var onSecretLoaded = function (userData, secret) {
      // Load requests may have been in progress from previous secrets.
      // Ignore those.
      if (secretMutater !== null || secret.secretId !== activeSecretId) {
        return;
      }

      secretMutater = new SecretMutater(secret, background, userData);
      renderSecretAcl();
      $inviteItem.removeClass('hide');
    };

    // Save any pending changes.  This should be called whenever leaving the
    // share secret pane.
    var maybeSaveChanges = function () {
      if (secretMutater !== null) {
        secretMutater.saveChanges();
      }
    };

    // Triggered when the popup is closed
    helper.onPopupHidden(function () {
      maybeSaveChanges();
    });

    var loadSecret = function (secret) {
      // Don't reload if same secret already loaded
      if (secretMutater === null || secret.secretId !== activeSecretId) {
        activeSecretId = secret.secretId;
        secretMutater = null;
        $shareList.empty();
        $inviteItem.addClass('hide');
        renderActiveSecret(secret, $('[data-pane="team"] .active-account'));
        mitro.loadUserDataAndSecret(activeSecretId, onSecretLoaded, onBackgroundError);
      }
    };

    var initInviteForm = function (email) {
      var secret = getSecretById(activeSecretId);
      renderActiveSecret(secret, $invitePane.find('.active-account'));

      inviteForm.emailElement.val(email);
      inviteForm.nameElement.val('');
      inviteForm.messageElement.val('');
    };

    // Triggered when a new pane is opened in the popup
    $(document).on('popup_pane_changed', function(event) {
      var $target = $(event.target);
      var paneName = event.originalEvent.detail.newPane;
      var secret = getSecretForItem($target);

      if (paneName === 'team') {
        loadSecret(secret);
      } else if (paneName === 'invite') {
        // fill in the typed invite name in the email field on invite form,
        // if there was anything typed in query box
        var email = $target.find('#invite-name').text();
        initInviteForm(email);
        // clear team query field so that the typed query doesn't remain there
        // when the user goes back from the invite form
        $('#team-filter-clear').trigger('click');
      } else {
        maybeSaveChanges();
      }
    });

    $shareList.on('click', '[data-action="grant"]', function() {
      var $a = $(this);
      var parent = $a.parents('.item');
      $a.attr('data-action', 'revoke');
      parent.addClass('shared');

      var $icon = $a.find('.icon-add');
      $icon.removeClass('icon-add').addClass('icon-check');

      var $item = $a.closest('.item');
      secretMutater.addAclItem($item);

      // show temporary 'shared' notification
      $('<div class="item msg-item"><div class="success-msg">shared!</div></div>').hide().insertAfter($item).fadeTo(400, 0.95).delay(300).fadeOut(1600, function() {
          $(this).remove();
        });

      return false;
    });

    var removeItem = function ($item) {
      var $a = $(this);
      $item.find('.action a').attr('data-action', 'grant'); // must be more specific about which "a" to alter - do not alter the overlay "a" attr

      var $icon = $item.find('.icon-check');
      $icon.removeClass('icon-check').addClass('icon-add');

      secretMutater.removeAclItem($item);

      return false;
    };

    // revoke person's access
    $shareList.on('click', '[data-action="revoke"]', function() {
      var $this = $(this),
          overlay = $this.parents('.item').children('.overlay'),
          parent = $this.parents('.item'),
          confirmRemoval = false;

      if ($this.hasClass('confirm')) {
        confirmRemoval = true;
      }

      $this.toggleClass('confirm');
      overlay.toggleClass('hide');

      if (confirmRemoval) {
        removeItem($this.closest('.item'));
        parent.removeClass('shared');
      }

      return false;
    });

    $shareList.on('click', '[data-action="cancel-revoke"]', function() {
      var item = $(this).parents('.item');
      $('[data-action="revoke"]', item).toggleClass('confirm');
      $('.overlay', item).toggleClass('hide');
      return false;
    });

    $('.send-invite-button').click(function () {
      var email = inviteForm.emailElement.val();
      var name = inviteForm.nameElement.val();
      var message = inviteForm.messageElement.val();

      if (!validateEmail(email)) {
        // TODO: display an error message in the popup.
        //showErrorDialog('Invalid email address');
        return;
      }
      secretMutater.addUser(email);
      openPane('team');
      renderSecretAcl();
    });
  });
})();
