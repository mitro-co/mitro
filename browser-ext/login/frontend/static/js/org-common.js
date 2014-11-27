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
    background.getIdentity(function (identity) {
        if (identity === null) {
            helper.setLocation('popup.html');
        } else {
            $('.email').text(identity.uid);
        }
    });

    mitro.loadOrganizationInfo(function (orgInfo) {
        var selOrgInfo = orgInfo.getSelectedOrganization();
        if (selOrgInfo !== null) {
            $('.org-name').text(selOrgInfo.name);
            $('.org-icon').attr('data-icon-text', selOrgInfo.name);

            var onSelect = function () {
                var $orgName = $('.org-admin-dropdown .org-name');
                var $orgIcon = $('.org-admin-dropdown .org-icon');

                $orgIcon.attr('data-icon-text', /** @type {string} */($orgName.text()));
                replaceBlankImages($orgIcon);

                var orgId = parseInt($orgName.attr('data-id'), 10);
                background.selectOrganization(orgId, reload, onBackgroundError);
            };

            var adminOrgs = _.filter(orgInfo.getOrganizations(),
                                     function (org) {return org.isAdmin;});
            if (adminOrgs.length > 0) {
                $('.org-admin').removeClass('hide');
                if (adminOrgs.length === 1) {
                    $('.org-no-dropdown').removeClass('hide');
                } else {
                    initOrgDropdown($('.org-admin-dropdown'), adminOrgs,
                                    selOrgInfo.id, onSelect);
                }
            }
            replaceBlankImages($('.org-icon'));

            // If on an org page, create team defaults to the current org.
            if ($('.org-admin .selected').length > 0) {
                $('.create-team-link').attr('href',
                    '../html/create-team.html?orgId=' + selOrgInfo.id);
            }
        } else {
            $('.org-upgrade').removeClass('hide');
        }
        // These menus are initially hidden because the content depends on
        // org admin status.
        // TODO: to remove all flicker, we need to render the pages from
        // separate templates.
        $('.nav-menu').removeClass('hide');
        $('.user-menu').removeClass('hide');
    }, onBackgroundError);

    $('.logout-link').click(function () {
        background.mitroLogout(function () {
            helper.setLocation('popup.html');
        });
        return false;
    });

    $('.nav-menu a').each(function () {
        var path = document.location.pathname;
        var href = $(this).attr('href').slice(2);

        if (path === href) {
            $(this).addClass('selected');
        }
    });
});
