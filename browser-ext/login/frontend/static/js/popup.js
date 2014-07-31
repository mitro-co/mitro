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

// ms to wait before removing the tabindex=1 for initial focused elements
var INITIAL_FOCUS_REMOVE_TABINDEX_DELAY_MS = 750;
// The popup width must match the width set in less/partials/variables.less
// @total-popup-width: 290px;
var POPUP_WIDTH = 290;
var SERVICES_PATH = 'secrets.html';

// Safari does NOT reload the popup automatically when it's pressed.  this causes all kinds of strange issues to happen.
// However, safari.application is not defined when the popup is loaded in a "normal" tab.
// TODO: move this into a generic popup init method in helpers.js
if (SAFARI && safari.application) {
  safari.application.addEventListener("popover", function() {
    console.log('reloading');
    window.location.reload();
  }, true);
}

// Causes jqElement to be the initially focused element in an extension popup
var popupInitialFocus = function(jqElement) {
  // Chrome focuses on the element with tabindex=1 in the popup
  jqElement.attr('tabindex', '1');
  jqElement.on('focus', function() {
    // remove tabindex=1 so tab works as expected
    // TODO: trigger AFTER the popup displays somehow? Tuning this timeout is hard
    setTimeout(function(){
      if (jqElement.attr('tabindex') === '1') {
        jqElement.removeAttr('tabindex');
      }
    }, INITIAL_FOCUS_REMOVE_TABINDEX_DELAY_MS);
  });
  // This should work on Windows Chrome, and hopefully Firefox/Safari
  // .focus() forces a layout (why?) delay until later to make popup appear snappier
  setTimeout(function(){jqElement.focus();}, 10);
};

var updateLoginState = function () {
  background.getLoginState(function (loginState) {
    var identity = loginState.identity;

    if (loginState.attemptingLogin) {
      $('#logging-in').removeClass('hide');
      $('#logged-out').addClass('hide');
      $('#logged-in').addClass('hide');
    } else if (identity === null) {
      $('#logged-out').removeClass('hide');
      $('#logged-in').addClass('hide');
      $('#logging-in').addClass('hide');
      initLoginForm();
      // the identity.shouldChangePassword() method should be used here
      // but we can not call the identity object methods from the frontend
      // due to the restrictions imposed by the use of messaging
    } else if (identity.changePwd) {
      var hash = '#required=true';
      if (window.location.hash) {
        // pass the existing hash through; passes username and password
        hash = window.location.hash;
      }
      helper.tabs.create({url: helper.getURL('html/change-password.html' + hash)});
    } else {
      if (($(window).width() > POPUP_WIDTH) && debugMode === false) { // Check to see if this in browser window, not popup
         // Redirect to services page if user is logged in and this is not inside the popup
        helper.setLocation(SERVICES_PATH);
      } else {
        var showLoggedInPane = function () {
          $('#logged-out').addClass('hide');
          $('#logging-in').addClass('hide');
          $('#logged-in').removeClass('hide');
          popupInitialFocus($('#secret-filter-string'));
        };

        background.getServiceInstances(undefined, function(allInstances) {
          // fill up context specific stuff
          helper.tabs.getSelected(function(tabObject) {
            if (tabObject &&
                // this can sometimes run before a user is logged in, so disable that case.
                background.getServiceInstances) {
              var host = getCanonicalHost(tabObject.url);

              // for testing
              // host = getCanonicalHost('https://appleid.apple.com/');
              background.getServiceInstances(host, function(instances) {
                showLoggedInPane();

                var selectActiveSecret = function (instances) {
                  if (instances.length >= 1) {
                    // render the most recently used instance. This should
                    // work for most sites except for stuff like Google that
                    // supports multiple concurrently-logged-in accounts.
                    var activeInstance = 0;
                    for (var i = 0; i < instances.length; ++i) {
                      if (instances[i].mostRecent) {
                        activeInstance = i;
                        break;
                      }
                    }
                    return instances[activeInstance];
                  }
                  return null;
                };

                var activeSecret = selectActiveSecret(instances);
                var $activeSecretPane = $('[data-pane="secrets"] .active-account');

                // Empty host matches all instances resulting in false positives.
                if (activeSecret && host) {
                  $activeSecretPane.removeClass('hide');
                  // don't .hide(): vertical center uses display: table
                  document.getElementById('no-account-banner').classList.add('hide');
                  renderActiveSecret(activeSecret, $activeSecretPane);
                } else {
                  // hide active account, make the scroll list occupy the entire height
                  $activeSecretPane.addClass('hide');
                  // don't show(): vertical center uses display: table
                  document.getElementById('no-account-banner').classList.remove('hide');
                  $('.secrets .list').addClass('list-no-site');
                }
                populateServiceList(allInstances);
              });
            } else if (!WEBPAGE) {
              showLoggedInPane();
              populateServiceList(allInstances);
            } else {
              window.location.href = helper.getURL('html/secrets.html');
            }
          });
        });
      }
    }
  }, onBackgroundError);
};

var openPane = function (paneName) {
  var $previousPane = $('[data-pane]').not('.hide');
  var $newPane = $('[data-pane="' + paneName + '"]');

  // update pane view states
  $previousPane.addClass('hide');
  $newPane.removeClass('hide');

  // set current pane attribute on parent element
  $('[data-current-pane]').attr('data-current-pane', paneName);

  // the "back" functionality for invite pane may be secrets (if the user came from share button on secrets page, in the case they have no team members)
  // or team (if the user came from the invite button on the team page)
  // set the back pane attribute accordingly
  if (paneName === 'invite') {
    $newPane.find('[data-open-pane].back').attr('data-open-pane', $previousPane.attr('data-pane'));
  }

};

$(document).ready(function() {
  'use strict';

  $('.logout').click(function () {
    background.mitroLogout(updateLoginState, onBackgroundError);
  });

  updateLoginState();
  helper.runPopupActions();

  // pane navigation
  $(document).on('click', '[data-open-pane]', function() {
    var $clickedButton = $(this);
    var paneName = $clickedButton.attr('data-open-pane');

    // Send a pane change event
    var eventData = {
      newPane: paneName,
      oldPane: $('[data-pane]').not('.hide').attr('data-pane')
    };

    var event = document.createEvent('CustomEvent');
    event.initCustomEvent('popup_pane_changed', true, true, eventData);
    this.dispatchEvent(event);

    openPane(paneName);

    // toggle icon states
    if ($clickedButton.hasClass('toggle')) {
      $clickedButton.toggleClass('hide');
      $clickedButton.siblings().toggleClass('hide');
    }

    return false;
  });

  // select generated password on field focus
  $('.generate-password input').click(function() {
    $(this).select();
  });

  $('.icon-settings').click(function() {
    // toggle settings icon based on current pane
    var $this = $(this);
    if ($this.hasClass('icon-settings')) {
      $this.removeClass('icon-settings');
      $this.addClass('icon-secrets');
      $this.attr('data-open-pane-id', 'secrets');
    } else {
      $this.removeClass('icon-secrets');
      $this.addClass('icon-settings');
      $this.attr('data-open-pane-id', 'settings');
    }
  });

  // Show full width page if this is not in the popup window
  var $wrapEl = $('#wrap');
  var $htmlEl = $wrapEl.parents('html');
  var $loggedOutPaneEl = $wrapEl.find('#logged-out');
  var $logoEl = $loggedOutPaneEl.find('#logo');
  var $loginFormEl = $loggedOutPaneEl.find('#mitro-login-form');
  var $signUpBtn = $loggedOutPaneEl.find('.sign-up');
  var $remindAlertEl = $loginFormEl.find('#remind-alert');

  if (($(window).width() > POPUP_WIDTH) && debugMode === false) {
    // change the width of the wrap container to be the full size of the screen
    $wrapEl.addClass('web-wrap');
    $htmlEl.addClass('web');
    $logoEl.wrap('<header class="simple-header"></header>');
    $('<h1>Sign In</h1>').insertAfter($logoEl);
    $signUpBtn.insertAfter($loginFormEl);
    $signUpBtn.find('a').removeAttr('target').removeClass('button');
    $signUpBtn.prepend('Don\'t have an account?');
    $loginFormEl.wrap('<div class="basic-form-container"></div>');
    $remindAlertEl.insertAfter($loginFormEl.find('.actions .left'));
  }

});
