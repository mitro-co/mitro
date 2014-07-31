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
    'use strict';

    // check for username/password hash: If it exists redirect to the popup
    // TODO: Remove this once the emails send the user directly to the popup
    var up = userpass.hashToUsernamePassword();
    if (up.username && up.password) {
        helper.setLocation('popup.html' + window.location.hash);
    } else {
        background.getIdentity(function (identity) {
            if (identity !== null) {
                // user is logged in: send them to the secrets pages
                helper.setLocation('secrets.html');
            }
        });
    }

    // process the "stay logged in" checkbox
    $('#remember-me').click(function() {
        var $checkboxEl = $(this);
        var $checkboxInput = $checkboxEl.find('#remember-me-input');
        var $alertEl = $('#remember-me-alert');
        if ($checkboxEl.hasClass('checked')) {
            $checkboxEl.removeClass('checked');
            $checkboxInput.prop('checked', false);
            $alertEl.hide();
        } else {
            $checkboxEl.addClass('checked');
            $checkboxInput.prop('checked', true);
            $alertEl.show();
        }
    });

    // set the checkbox to "checked" by default
    $('#remember-me').click();

    // process the "stay logged in" checkbox if user clicks "enter" while box is "focused"
    $('#remember-me').keypress(function(e) {
        var key = e.which;
        if (key === KeyCodes.RETURN) {
            $(this).click();
        }
    });

    $('#signup-form').submit(function (event) {
        var showSignupError = function (message) {
            $('#signup-error').text(message);
            $('#signup-error').show();
        };

        var username = $(this).find('input[name="email"]').val();
        var password = $(this).find('input[name="password"]').val();
        var password2 = $(this).find('input[name="password2"]').val();

        if (!username) {
            showSignupError('Please enter your email address');
            $('#email').focus();
        } else if (password !== password2) {
            showSignupError('Passwords do not match');
            $('#password').focus();
        } else if (!passwords.validatePassword(password)) {
            showSignupError('Password is too weak');
            $('#password').focus();
        } else {
            $('#signup-error').hide();
            var $spinny = showSpinny($('#signup-form-button'));
            var onSignup = function (identity) {
                helper.setLocation('secrets.html');
            };
            var onSignupError = function (error) {
                var params = {
                    error: error,
                    loginUrl: 'popup.html#' + encodeURIComponent(String(username))
                };
                var html = templates['signup-error-template'].render(params);
                $('#signup-error').html(html);
                $('#signup-error').show();

                hideSpinny($('#signup-form-button'), $spinny);
            };
            var rememberMe = $('#remember-me-input').is(':checked');
            background.mitroSignup(username, password, rememberMe, onSignup, onSignupError);
        }
        return false;
    });
});
