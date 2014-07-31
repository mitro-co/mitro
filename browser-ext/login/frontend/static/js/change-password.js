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

$(document).ready(function () {
    'use strict';

    $('#change-password-form').submit(function (event) {
        var showPasswordError = function (message) {
            $('#password-error').text(message);
            $('#password-error').show();
        };
        var oldPassword = null;
        if ($(this).find('input[name="old-password"]').length !== 0) { 
            oldPassword = $(this).find('input[name="old-password"]').val();
        }
        var newPassword = $(this).find('input[name="new-password"]').val();
        var newPassword2 = $(this).find('input[name="new-password2"]').val();

        if (oldPassword === newPassword) {
            showPasswordError('New password must be different from old password');
        } else if (newPassword !== newPassword2) {
            showPasswordError('Passwords do not match');
        } else if (!passwords.validatePassword(newPassword)) {
            showPasswordError('Password is too weak');
        } else {
            $('#password-error').hide();
            var $spinny = showSpinny($('#change-password-form-button'));
            var onSuccess = function (response) {
                helper.setLocation('secrets.html');
            };
            var onError = function (errorMessage) {
                showPasswordError(errorMessage);
                hideSpinny($('#change-password-form-button'), $spinny);
            };
			var up = userpass.hashToUsernamePassword();
            background.changePassword(oldPassword, newPassword, up, onSuccess, onError);
        }
        return false;
    });

    var up = userpass.hashToUsernamePassword();
    if (up.password) {
        // used for invited users: fill the value and hide it
        $('input[name="old-password"]').val(up.password);
        $('#change-old-password-group').hide();
        $('#password').focus();
    }
});
