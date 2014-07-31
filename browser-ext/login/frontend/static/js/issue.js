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

    background.getIdentity(function (identity) {
        if (identity) {
            $('#issue-form').find('input[name="email"]').val(identity.uid);
        }
    });

    $('#issue-form').submit(function (event) {
        var $form = $(this);

        background.getIdentity(function (identity) {
            var email = $form.find('input[name="email"]').val();
            var type = $form.find('select').val();
            var url = $form.find('input[name="url"]').val();

            // The user can modify the email address; it's possible they might want to have a
            // different email used for communication than their uid, but we need their uid to debug.
            var description = 'uid:' + (identity ? identity.uid : 'unknown') + '\n\n\n' + $form.find('textarea').val();

            if (type === 'none') {
                showErrorDialog('Please select an issue type');
                return;
            }
            background.addIssue(type, url, description, email, function() {
                $form = $('#issue-form');
                $('<span> Thanks! Issue submitted</span>').insertAfter($form);
                $($form).hide();
            }, onBackgroundError);
        }, onBackgroundError);
        return false;
    });

    var uri = new URI(document.location.toString());
    var hash = uri.getFragment();

    if (hash) {
        var url = window.atob(hash);
        $('#issue-form input[name="url"]').val(url);
    }
});
