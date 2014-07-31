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

	$('#change-pwd-two-factor').click(function() {
        background.getIdentity(function (identity) {
            var onSuccess = function (response) {
                if (response === null || response === undefined) {
                    helper.tabs.create({url: helper.getURL('html/change-password.html')});
                } else {
                    // TODO: we currently use complicated redirects to do this.
                    // fixing this requires a server change, so just disable changing passwords
                    // for accounts on which tfa is enabled for now.
                    if (SAFARI || FIREFOX) {
                        $('#change-password-error').text('Use Chrome to change password for TFA-enabled accounts.');
                    } else {
                        helper.tabs.create({'url': response + "&changePassword=true"},
                            function(tab) {
                                console.log('opened tab');
                            }
                        );
                    }
                }
            };
            background.checkTwoFactor(onSuccess, onBackgroundError);
        });
    });

    $('#two-factor-prefs').click(function() {
        background.getIdentity(function (identity) {
            // todo: this is a horrible, disgusting hack.
            // see mitro_fe.js in wrapLogin() for how this token gets generated.
            var url = 'https://' + MITRO_HOST + ":" + MITRO_PORT + identity.tfaAccessUrlPath;
            helper.tabs.create({'url':url},
               function(tab) {
                    console.log('opened tab');
               });
        });
    });
});
