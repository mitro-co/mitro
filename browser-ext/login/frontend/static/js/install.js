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
    var helper = new ExtensionHelper();
    var background = new Client('extension');
    helper.bindClient(background);
    background.initApiCalls();

    var uri = new URI(document.location.toString());
    var hash = uri.getFragment();
    var invited = false;
    if (hash) {
        var params = decodeQueryString(hash);
        if (params.p) {
            invited = true;
        }
    }
    $('.chrome-no-extension').show();
    if (invited) {
        $('.for-invited-users').show();
        $('.for-new-users').hide();
    } else {
        $('.for-invited-users').hide();
        $('.for-new-users').show();
    }
    $('.extension-signup').click(function () {
        this.href = ('signup.html') + document.location.hash;
        return true;
    });

    $('.extension-login').click(function () {
        this.href = ('popup.html') + document.location.hash;
        return true;
    });

    $('.extension-services').click(function () {
        this.href = ('secrets.html');
        return true;
    });

    $('.open-extension').click(function () {
        this.href = ('popup.html');
        return true;
    });

    var updateLoginState = function () {
        background.getIdentity(function (identity) {
            if (identity) {
               $('.step-two').removeClass('step-active');
               $('.step-three').addClass('step-active');
               $('.step-two .check-image').attr('src', '../img/icon_check.png');
            }
        });
    };

    updateLoginState();

    setInterval(updateLoginState, 1000);
});
