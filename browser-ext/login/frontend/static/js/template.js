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

var originalSidebarHeight = $('.sidebar').outerHeight();

var fixSidebarHeight = function () {
    // TODO: try to remove ephemeral scrollbar when window is downsized.
    var docHeight = $(document).height() - $('.navbar').outerHeight();
    var leftHeight = $('.left-col').outerHeight();

    var sidebarHeight = Math.max(originalSidebarHeight, docHeight, leftHeight);
};

$(document).ready(function() {
    console.log('template ready');
    
    fixSidebarHeight();
    $(window).resize(fixSidebarHeight);

    $('.dropdown-toggle').dropdown();

    background.getIdentity(function (identity) {
        if (identity === null) {
            // Do not redirect to sign in from the install page.
            if (window.location.pathname === '/html/install.html') {
                $('#account-menu').hide();
            } else if(window.location.pathname !== '/html/preferences.html'){
                // Redirect to login page
                // Except for preferences so we can set the server without needing to be logged in first
                helper.setLocation('popup.html');
            }
        } else {
            $('.email').text(identity.uid);
        }
    });

    $('.logout-link').click(function () {
        background.mitroLogout(function () {
            helper.setLocation('popup.html');
        });
        return false;
    });
});
