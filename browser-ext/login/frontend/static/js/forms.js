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

    // Select contents of input on focus (tabbing)
    $('input[type=email], input[type=password]').focus(function() {
        $(this).select();
    });

    //show password box
    $('#password').focus(function() {
        $('#password-hints-box').show();
        $('#progress').show();
    });
    $('#password').blur(function() {
        $('#password-hints-box').hide();
    });

    $('#password').bind('keyup', function() {
        var val = $(this).val();

        if (val.length > 7) {
            $('#min').addClass('selected');
        } else {
            $('#min').removeClass('selected');
        }

        if (val.match(/[A-Z]/)) {
            $('#up').addClass('selected');
        } else {
            $('#up').removeClass('selected');
        }

        if (val.match(/[0-9]/)) {
            $('#num').addClass('selected');
        } else {
            $('#num').removeClass('selected');
        }

        if (val.match(/[^A-Za-z0-9]/)) {
            $('#sp').addClass('selected');
        } else {
            $('#sp').removeClass('selected');
        }

        passwords.updatePasswordMeter(val, $('.progress-msg'), $('.progress-bar'));
    });

    //show remember password box
    $('#confirm-password').focus(function() {
        $('#remember-password-alert').show();
    });
    $('#confirm-password').blur(function() {
        $('#remember-password-alert').hide();
    });

    $('#show-password').click(function() {
        var $showPwdEl = $(this);
        if ($showPwdEl.hasClass('show-password')) {
            $showPwdEl.removeClass('show-password');
            $showPwdEl.removeClass('icon-eye');
            $showPwdEl.addClass('icon-eye-blocked');
            $showPwdEl.addClass('hide-password');
            $showPwdEl.attr('title', 'Hide Password');
            $('input#password').attr('type', 'text');  // TODO: does this work on IE?
        } else {
            $showPwdEl.removeClass('hide-password');
            $showPwdEl.removeClass('icon-eye-blocked');
            $showPwdEl.addClass('icon-eye');
            $showPwdEl.addClass('show-password');
            $showPwdEl.attr('title', 'Show Password');
            $('input#password').attr('type', 'password');  // TODO: does this work on IE?     
        }
    });
});
