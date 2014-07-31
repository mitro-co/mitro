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

var mitro = mitro || {};
$(document).ready(function() {
    'use strict';
    mitro.password = mitro.password || {};

    mitro.password.generatePassword = function() {
        var getValue = function(str) {
            var v = parseInt($('#' + str).val(), 10);
            return isNaN(v) ? mitro.password.DEFAULT_PASSWORD_LENGTH : v;
        };

        var options = {};

        if ($('#advanced-pwd-options').is(':visible')) {
            options.numCharacters = getValue("pwd-num-characters");
            options.uppercase = $('#pwd-uppercase').is(':checked');
            options.digits = $('#pwd-digit').is(':checked');
            options.symbols = $('#pwd-special').is(':checked');
        }

        mitro.password.generatePasswordWithOptions(options, function(pwd) {
            $('.generated-password').val(pwd);
        }, function (e) {
            console.log('Error generating password', e);
        });

        $('.copy-pwd-link').text('Copy to clipboard');
    };

    // Handle password generation
    $('.generate-password-link').click(function() {
        $('#settings-div').hide();
        $('#password-generator-div').show();
        mitro.password.generatePassword();
    });

    $('#show-settings-link').click(function() {
        $('#password-generator-div').hide();
        $('#settings-div').show();
    });

    $('.regenerate-pwd-link').click(mitro.password.generatePassword);
    $('.copy-pwd-link').click(function() {
        helper.copyFromInput($('.generated-password'), function() {
            $('.copy-pwd-link').text('Copied!');
            $('.copy-pwd-link').attr('disabled', true);
        });
    });

    $('#show-advanced-pwd-options').click(function() {
        if ($('#advanced-pwd-options').is(':visible')) {
            $('#show-advanced-pwd-options').text('Show Advanced Options');
            $('#advanced-pwd-options').hide();
        } else {
            $('#show-advanced-pwd-options').text('Hide Advanced Options');
            $('#advanced-pwd-options').show();
        }
    });

    $('#pwd-num-characters').val(mitro.password.DEFAULT_PASSWORD_LENGTH.toString());
});
