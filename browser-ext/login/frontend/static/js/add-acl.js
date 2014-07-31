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

var initAddAcl = function ($addMemberList, $searchBox) {
    'use strict';

    var nextCheckboxId = 0;

    $searchBox.keyup(function(e) {
        if (e.keycode === 13) {
            // if there's text in the text box, don't do anything.
            return false;
        }
        $('.add-item').remove();
        var term = $searchBox.val();
        var addItem = templates['add-acl-template'].render({name: term});
        $addMemberList.append(addItem);

    });

    $addMemberList.on('click', '.add-item', function () {
        var uid = $(this).find('.name').text();
        console.log('add user ' + uid);
        $(this).remove();
        $searchBox.val('');
        $('.acl-item').attr('data-matches', true);
        $('.acl-item').show();


        var newUsers = userIdsToUserMap([uid]);
        var members = processUsersForRendering(newUsers, newUsers);

        var $newMember = templates['add-members-template'].render(
            {users: members});
        $addMemberList.prepend($newMember);
        replaceBlankImages($addMemberList.find('.member-icon'));
    });
};
