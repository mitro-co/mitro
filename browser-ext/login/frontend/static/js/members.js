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

    var orgId = null;

    // Map from userId -> member
    var membersMap = {};

    var getMemberForListItem = function ($listItem) {
        return membersMap[$listItem.attr('data-username')];
    };

    var renderMembers = function (members) {
        for (var i = 0; i < members.length; i++) {
            var user = members[i];
            user.id = user.name;
            user.accessLevel = user.admin ? 'Admin' : 'User';
        }

        $('.users-list').html(templates['members-template'].render(
                {users: members}));
        replaceBlankImages($('.member-icon'));

        membersMap = createMapFromArrayOnAttribute(members, 'id');
    };

    var $spinny = showSpinny($('.spinny'));

    registerLiveSearch($('#member-filter-string'), '.member');

    mitro.loadOrganizationInfo(function (orgInfo) {
        var selOrgInfo = orgInfo.getSelectedOrganization();
        orgId = selOrgInfo.id;
        background.getOrganization(selOrgInfo.id, function (org) {
            var i;
            var admins = {};
            for (i = 0; i < org.admins.length; i++) {
                admins[org.admins[i]] = 1;
            }

            var isAdmin = function (name) {
                return admins.hasOwnProperty(name);
            };

            org.members.sort();
            var members = [];
            for (i = 0; i < org.members.length; i++) {
                var member = org.members[i];
                members.push({name: member, admin: isAdmin(member)});
            }
            hideSpinny($('.spinny'), $spinny);
            renderMembers(members);
        }, onBackgroundError);
    }, onBackgroundError);

    $('#show-invite-members-button').click(function () {
        resetAndShowModal($('#invite-members-modal'));
    });

    $('#invite-members-button').click(function () {
        var $form = $('#invite-members-modal form');
        var emails = $form.find('input[name="emails"]').val().split(',');
        var membersToAdd = [];

        for (var i = 0; i < emails.length; i++) {
            var email = emails[i].trim();
            if (email) {
                if (!validateEmail(email)) {
                    showErrorDialog('Invalid email address: ' + email);
                    return false;
                }
                membersToAdd.push(email);
            }
        }

        if (membersToAdd.length === 0) {
            showErrorDialog('Please enter an email address');
            return false;
        }

        var message = $form.find('textarea[name="message"]').val();

        // TODO: send email message with request
        background.mutateOrganization(orgId, [], membersToAdd, [], [], reload, onBackgroundError);
    });

    $(document).on('click', '.user-access-level li', function (event) {
        var $listItem = $(event.target).closest('.member');
        var item = getMemberForListItem($listItem);
        var accessLevel = $(this).text().toLowerCase();

        console.log('changing access level of user ' + item.name + ' to ' + accessLevel);

        var membersToPromote = [];
        var adminsToDemote = [];

        if (accessLevel === 'admin') {
            membersToPromote.push(item.name);
        } else {
            adminsToDemote.push(item.name);
        }

        background.mutateOrganization(orgId, membersToPromote, [], adminsToDemote, [], reload, onBackgroundError);
        showSpinny($('.spinny'));
    });

    var IS_ADMIN_DATA_NAME = 'is-admin';
    $(document).on('click', '.remove-user', function (event) {
        var $listItem = $(event.target).closest('.member');
        var user = getMemberForListItem($listItem);
        var message = 'Remove ' + user.name + ' from organization?';

        var $modal = $('#delete-confirm-modal');
        $modal.find('.delete-message').text(message);
        var emailInput = $modal.find('input[name="email"]');
        emailInput.val(user.name);
        emailInput.data(IS_ADMIN_DATA_NAME, user.admin);
        $modal.modal('show');

        return false;
    });

    $('#remove-user-button').click(function (event) {
        var $modal = $(event.target).closest('.modal');
        var emailInput = $modal.find('input[name="email"]');
        var email = emailInput.val();
        var isAdmin = emailInput.data(IS_ADMIN_DATA_NAME);

        console.log('removing user', email, 'from org; is admin?', isAdmin);

        var adminsToDemote = [];
        if (isAdmin) {
            adminsToDemote.push(email);
        }
        background.mutateOrganization(orgId, [], [], adminsToDemote, [email], reload, onBackgroundError);
        showSpinny($('.spinny'));
    });
});
