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

var showSpinny = function($loginButton) {
    var $img = $('<img src="../img/ajax-loader.gif">');
    $loginButton.after($img);
    $loginButton.hide();
    return $img;
};

var hideSpinny = function($loginButton, $spinny) {
    $spinny.remove();
    $loginButton.show();
};

var reload = function () {
    window.location.reload();
};

/**
@param {function(Event)=} onCancelButtonClicked
*/
var showDialogWithButtons = function (title, message, primaryButtonText,
        cancelButtonText, onPrimaryButtonClicked, onCancelButtonClicked) {
    var $dialog = $(templates['modal-dialog-template'].render(
        {title: title,
         message: message,
         primaryButtonText: primaryButtonText,
         cancelButtonText: cancelButtonText}));
    var $primaryButton = $dialog.find('.btn-primary');
    var $cancelButton = $dialog.find('.btn-cancel');

    if (!primaryButtonText) {
        $primaryButton.addClass('hide');
    }
    if (!cancelButtonText) {
        $cancelButton.addClass('hide');
    }

    if (typeof onPrimaryButtonClicked !== 'undefined') {
        $primaryButton.click(onPrimaryButtonClicked);
    }
    if (typeof onCancelButtonClicked !== 'undefined') {
        $cancelButton.click(onCancelButtonClicked);
    }
    $dialog.modal('show');

    return $dialog;
};

var showDialog = function (title, message, onDismiss) {
    return showDialogWithButtons(title, message, 'OK', null, onDismiss);
};

/**
@param {function(Event)=} onDismiss
*/
var showErrorDialog = function (message, onDismiss) {
    return showDialog('Error', message, onDismiss);
};

var showDeleteDialog = function (title, message, onDelete) {
    return showDialogWithButtons(title, message, 'Delete', 'Cancel', onDelete);
};

var onBackgroundError = function (error) {
    console.log('background error', error);

    showErrorDialog(error.userVisibleError ? error.userVisibleError : error);
};

var reloadOnError = function (error) {
    console.log('reloadOnError', error);
    showErrorDialog(error, function () {
        reload();
    });
};
        
var validateEmail = function (emailString) {
    // The HTML5 regexp, must be in sync with the Python code.
    // http://www.whatwg.org/specs/web-apps/current-work/multipage/states-of-the-type-attribute.html#valid-e-mail-address
    var regex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/;
    return emailString.match(regex);
};

var isVisibleGroup = function (group) {
    return !(group.isNonOrgPrivateGroup || group.isOrgPrivateGroup || group.isTopLevelOrg || group.autoDelete);
};

var filterVisibleGroups = function (groups) {
    return _.filter(groups, isVisibleGroup);
};

var showModal = function ($modal) {
    $modal.modal({backdrop: 'static'}).modal('show');
};

var resetAndShowModal = function ($modal) {
    var $form = $modal.find('form');
    $form[0].reset();
    showModal($modal);
};

// Formats a timestamp in ms for the user's locale.
var formatTimestamp = function (timestampMs) {
    var d = new Date(timestampMs);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
};

if (typeof module !== 'undefined' && module.exports) {
    module.exports.isVisibleGroup = isVisibleGroup;
}
