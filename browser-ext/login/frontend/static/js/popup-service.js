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

var scrollPane = null;
var SCROLLPANE_OPTIONS = {
        mouseWheelSpeed: 30,
        horizontalGutter: 5,
        verticalGutter: 0,
        'showArrows': false
    };

// Time to wait before loading site icons in an attempt to display faster
// TODO: Trigger after the popup *displays* somehow; this delay is impossible to tune
var LOAD_ICONS_DELAY_MS = 400;

// secretId -> secret
var secretMap = {};

// keep track of currently selected secret index
var selectedSecretIndex = 0;
// keep track of number of visible services
var visibleServicesLength = 0;
var SELECTED_CLASS = 'selected';

var highlightSecret = function(secretIndex) {
    // clear the selected class from all selected secret items
    $('#context .secret-item.' + SELECTED_CLASS).removeClass(SELECTED_CLASS);
    // set the selected class to the secret with specified index, if exists
    var $services = $('#context .secret-item:visible');
    visibleServicesLength = $services.length;
    if (visibleServicesLength && $services[secretIndex]) {
        var $selectedService = $($services[secretIndex]);
        $selectedService.addClass(SELECTED_CLASS);
        scrollPane.scrollToElement($selectedService, false);
    }
};

var getSecretById = function (secretId) {
  return secretMap[secretId];
};

var getSecretForItem = function ($item) {
  return getSecretById($item.attr('data-secret-id'));
};

var populateServiceList = function(instances){
    var backgroundLoadImages = function() {
        $('#context .secret-item').each(function () {
          var $item = $(this);
          var secret = getSecretForItem($item);
          $item.find('.service-icon').attr('src', secret.iconUrl);
        });
        replaceBlankImages($('.secret-item .service-icon'));
        // TODO: replace organization icons
    };

    for (var i = 0; i < instances.length; ++i) {
        var secret = instances[i];
        processServiceInstanceForRendering(secret);
        // override for the popup that allows more direct access to secrets.
        if (secret.action === SecretAction.VIEW.action && // TODO: Adam - remove this code
            secret.clientData.type !== 'note') {
            secret.buttonLabel = SecretAction.COPY.label;
            secret.action = SecretAction.COPY.action;
            secret.buttonImage = SecretAction.COPY.image;
        }
        // the popup doesn't display if all images haven't been loaded, WTF?
        secret.iconUrl = instances[i].icon;
        secret.icon = '';
        secretMap[secret.secretId] = secret;
        // if the secret is a note, set isNote field
        if (secret.clientData.type === 'note') {
            secret.isNote = true;
        } else {
            secret.isNote = false;
        }
    }
    disambiguateTitles(instances);
    instances.sort(secretSortFunc);
    // TODO: find out if this user has team members or not
    // set "hasNoTeam" to true if the user has no team members - this will cause the share access buttons to be set to go to the invite page
    // set "hasNoTeam" to false if the user has team members - this will cause the share access buttons to be set to go to share access page
    var params = {services: instances, hasNoTeam: false};
    var html = templates['popup-service-template'].render(params);

    $('#context').append(html);
    replaceBlankImages($('.organization'));
    scrollPane = $('.pane2').jScrollPane(SCROLLPANE_OPTIONS);
    scrollPane = scrollPane.data('jsp');

    highlightSecret(selectedSecretIndex); // highlight first visible secret (selectedSecretIndex is defaulted to 0)

    // load the images in the background. This allows the popup to display before
    // the images are loaded (good for slow connections).
    setTimeout(backgroundLoadImages, LOAD_ICONS_DELAY_MS);
};

var renderActiveSecret = function (secret, $element) {
    processServiceInstanceForRendering(secret);
    var $parentPane = $element.parent('.pane');
    var html;
    if ($parentPane.length && ($parentPane.data('pane') === 'invite')) { // render invite template
        html = templates['popup-invite-member-template'].render(secret);
    } else {
        html = templates['popup-active-service-template'].render(secret);
    }
    $element.html(html);
    replaceBlankImages($('.member-icon'));
};

$(document).ready(function() {
    'use strict';
    registerLiveSearch($('#secret-filter-string'), '.jspPane > .secret-item', $('#secret-filter-clear'));

    $(document).on('popup_pane_changed', function(event) {
      var paneName = event.originalEvent.detail.newPane;

      if (paneName === 'secrets') {
        // When the secrets pane is reopened, the focus needs to be returned
        //to the query field & first item is pre-selected
        popupInitialFocus($('#secret-filter-string'));
        highlightSecret(selectedSecretIndex);
      }
    });

    $('div[data-pane="secrets"]').keyup(function(e) { // when up / down arrow keys are pressed, highlight the preceding / next secret in the list
        if (e.keyCode === KeyCodes.UP) {
            if (selectedSecretIndex > 0) {
                selectedSecretIndex--;
                highlightSecret(selectedSecretIndex);
                // hide any visible item action sections
                $('.item-actions').slideUp('fast');
            }
        } else if (e.keyCode === KeyCodes.DOWN) {
            if (selectedSecretIndex < (visibleServicesLength - 1)) {
                selectedSecretIndex++;
                highlightSecret(selectedSecretIndex);
                // hide any visible item action sections
                $('.item-actions').slideUp('fast');
            }
        } else if (e.keyCode === KeyCodes.RETURN) { // if the user presses enter and we have at least one service, do the default action on the top one.
            var $selectedSecret = $('.secret-item.' + SELECTED_CLASS).first(); // do action on the first selected item
            if ($selectedSecret.length) {
                var serviceData = getSecretForItem($selectedSecret);
                executeSecretAction(serviceData);
            }            
        }
    });

    $('#secret-filter-string').keyup(function(e) {
        scrollPane.reinitialise();
        scrollPane.scrollToX(0, false);
        if ((e.keyCode >= KeyCodes.KEY_0) && (e.keyCode <= KeyCodes.KEY_Z)) { // if some characters were entered (for searching), reset the selected secret index to 0 b/c new list of results
            selectedSecretIndex = 0;      
        }
        highlightSecret(selectedSecretIndex);  // update to highlight first secret in results
    });

    $(document).on('click', '.secret-default-action', function() {
        $('.copied').removeClass('copied'); // clear the "copied" class from any copy buttons
        var $secretItem = $(this).closest('.secret-item'); // get parent secret item
        var $itemActions = $secretItem.find('.item-actions'); // get actions element
        var $contextEl = $(this).closest('#context');
        var $services = $('.item.secret-item:visible'); // get all visible services - set selected index for use with arrow keys
        selectedSecretIndex = $services.index($secretItem); // set the selectedSecretIndex to the index of this item in the list of visible elements
        if ($itemActions.length) {
            if ($itemActions.is(':hidden')) {
                // hide all visible 'item-action' elements before displaying
                $('.item-actions').slideUp('fast');
                // remove selected class from all secret items
                $('.secret-item').removeClass(SELECTED_CLASS);
                // set the class of the parent "secret item" to selected class
                $secretItem.addClass(SELECTED_CLASS);
                // TODO: set the first action as selected
                // itemActions.find('li:first-child').addClass('active');
                $itemActions.slideDown(300, function() {
                    var contentH = $contextEl.height(); // calculate whether the action area is visible in the window
                    var bottomYPos = $itemActions.offset().top - $contextEl.offset().top + $itemActions.height();
                    if (bottomYPos > contentH) { // If calculated bottom Y position is greater than the content height, it's not visible - shift it up
                        scrollPane.scrollByY(bottomYPos - contentH, true);
                    }
                    scrollPane.reinitialise();
                });
            } else {
                $itemActions.slideUp(300, function() {
                    scrollPane.reinitialise();
                });
            }
        }
    });

    $(document).on('click', '.secret-edit-action', function() {
        // open up the thing in a new page.
        var $button = $(this);
        var $item = $button.closest('.secret-item');
        var serviceData = getSecretForItem($item);
        openManageSecretPage(serviceData);
    });

    // Clicking on action bar items will execute corresponding actions
    $(document).on('click', '.item-actions li', function() {
        $('.copied').removeClass('copied'); // clear the "copied" class from any copy buttons
        var $actionItem = $(this);
        var $secretItem = $actionItem.closest('.secret-item');
        var action = $actionItem.data('item-action');
        var serviceData = getSecretForItem($secretItem);
        switch(action) {
            case 'launch':
                executeSecretAction(serviceData);
                break;
            case 'copy-user':
                var username = serviceData.clientData.username;
                copyText(username, function() {
                    $actionItem.find('.copy-msg').addClass('copied');
                });
                break;
            case 'copy-pw':
                copyPassword(serviceData, function() {
                    $actionItem.find('.copy-msg').addClass('copied');
                });
                break;
            case 'share-secret':
                // action is triggered by the data-open-pane attribute in this li element
                break;
            case 'edit-secret':
                openManageSecretPage(serviceData);
                break;
            default:
                break;
        }
    });
});
