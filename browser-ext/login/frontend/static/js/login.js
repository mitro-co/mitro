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

var initLoginForm;
(function() {
    'use strict';

    // Invited users click a link that auto-fills the username and password.
    // We only want one automatic login attempt, not an infinite loop on error.
    // True if we attempted to log an invited user in, so we don't infinite loop on error.
    var didAutoLoginForInvitedUser = false;
    var settings = {};

    initLoginForm = function () {
        background.loadSettingsAsync(function(backgroundSettings) {
            settings = backgroundSettings;

            var $loginForm = $('#mitro-login-form');
            // display form after settings are loaded (avoid clobbering user input)
            $loginForm.removeClass('hide');

            var $usernameField = $loginForm.find('input[name="username"]');

            var up = userpass.hashToUsernamePassword();

            if (up.username && up.token && up.token_signature && !up.password) {
                up.password='**********';
            }

            if (!up.username) {
                // no hash? default to ''
                up.username = '';
            }

            // Try to load username (only for the login page).
            if (!up.username) {
                if ('username' in settings) {
                    up.username = settings.username;
                }
            }

            // Sets username for login and signup page.
            $usernameField.val(up.username);

            var focusElement = null;
            if (up.username) {
                var $pwElement = $loginForm.find('input[name="password"]');
                focusElement = $pwElement;
                if (up.password && !didAutoLoginForInvitedUser) {
                    // submit the form if we have username and password
                    $pwElement.val(up.password);
                    $loginForm.submit();
                    // don't attempt more than once: avoids an infinite loop
                    didAutoLoginForInvitedUser = true;
                }
            } else {
                focusElement = $usernameField;
            }
            popupInitialFocus(focusElement);

            // set the remember me prompt based on old settings
            if (settings.rememberMe === undefined)  {
                settings.rememberMe = true;
            }
            if (!settings.rememberMe) {
                $('.remember-me').prop('checked', false);
                // trigger change handler to set the correct message state
                $('.remember-me').trigger('change');
            }
        });
    };

    $(document).ready(function() {
        var saveUsernameAndRememberMe = function (username) {
            settings.username = username;
            background.saveSettingsAsync(settings);
        };

        $('#mitro-login-form').submit(function (event) {
            $('#login-error').hide();
            var showLoginError = function (message) {
                $('#login-error').text(message);
                $('#login-error').show();
            };

            var $form = $(this);

            var username = $form.find('input[name="username"]').val();
            var password = $form.find('input[name="password"]').val();
            var rememberMe = $('.remember-me').is(':checked');
            var up = userpass.hashToUsernamePassword();

            if (!username) {
                showLoginError('Please enter your email address');
            } else if (!validateEmail(username)) {
                showLoginError('Invalid email address');
            } else if (!password) {
                showLoginError('Please enter your password');
            } else {
                var onLogin = function (identity) {
                    // Gross hack: attempts to wait for services to be loaded
                    setTimeout(updateLoginState, 100);
                    // Clear password field
                    $form.find('input[name="password"]').val('');
                };
                var onLoginError = function (error) {
                    if (error.userVisibleError) {
                        showLoginError(error.userVisibleError);
                    } else {
                        showLoginError('Login error');
                    }
                    updateLoginState();
                };
                var onTwoFactor = function(url) {
                    // show the TFA box
                    showLoginError('Enter the 2-step verification code from your mobile application.');
                    $('#tfa-code').show();
                    updateLoginState();
                    // #tfa-code in #logged-out; updateLoginState() makes it visible: focus after
                    $('#tfa-code input[type=text]').focus();
                };
                var tfaCode;
                if ($('#tfa-code').is(':visible')) {
                    tfaCode = $('#tfa-code input[type=text]').val();
                }
                $('#tfa-code').hide();
                background.mitroLogin(username, password, onLogin, onLoginError, onTwoFactor,
                    up.token, up.token_signature, rememberMe, tfaCode
                    );
                // Refresh the display to show the "logging in" view
                updateLoginState();
            }
            saveUsernameAndRememberMe(username);

            return false;
        });

        $('.remember-me').change(function() {
          if ($(this).is(':checked')) {
            $(this).closest('.checkbox').addClass('checked');
            $('#remind-alert').removeClass('hide');
            settings.rememberMe = true;
          } else {
            $(this).closest('.checkbox').removeClass('checked');
            $('#remind-alert').addClass('hide');
            settings.rememberMe = false;
          }
        });
    });

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = initLoginForm;
    }
})();
