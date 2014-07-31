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

$(function() {
    var SECONDS_PER_KEY = 10;
    // background page
    var background = chrome.extension.getBackgroundPage();
    background.getIdentity(function (identity) {

        var displayUsers = function(userList, $div) {
            var $ul = $('<ul>');
            for (var i = 0; i < userList.length; ++i) {
                var $li = $('<li>');
                $li.text(userList[i]);
                $ul.append($li);
            }
            $ul.appendTo($div);
        };
            
        var displayGroups = function(diffs) {
            for (var gn in diffs) {
                var group = diffs[gn];
                var $div = null;
                switch (group.groupModification) {
                    case 'IS_NEW':
                        $div = $('#new-org-groups');
                        break;
                    case 'IS_DELETED':
                        $div = $('#del-org-groups');
                        break;
                    case 'MEMBERSHIP_MODIFIED':
                        $div = $('#modified-org-groups');
                        break;
                    case 'IS_UNCHANGED':
                    // UNCHANGED should never be part of the diffs sent to the client.
                    default:
                        throw "unexpected type" + group.groupModification;
                }
                $div.append(templates['admin-sync-group-template'].render(group));
            }
        };

        background.getPendingGroupDiffs(identity, function(diffs) {
            var numModified = (diffs.newOrgMembers.length + diffs.deletedOrgMembers.length + Object.keys(diffs.diffs).length);
            if (numModified === 0) {
                $('.result').text('Your organization is up to date.');
                return;
            }
            displayUsers(diffs.newOrgMembers, $('#new-members'));
            displayUsers(diffs.deletedOrgMembers, $('#del-members'));
            displayGroups(diffs.diffs);
            var nonce = diffs.syncNonce;
            var checksum = null; // TODO: put a checksum in here somehow.

            $('.result').hide();
            $('.show-diffs').show();
            $('#sync-memberships').click(function() {
                // TODO: figure out which group mods will result in new keys
                var estKeysRequired = 2 * diffs.newOrgMembers.length + Object.keys(diffs.diffs).length;
                var singular = estKeysRequired === 1;
                var endTime = new Date();
                endTime.setSeconds(endTime.getSeconds() + SECONDS_PER_KEY * estKeysRequired);

                $('.show-diffs').hide();
                $('.result').show();
                $('.result').text('Updating data. We have to generate approximately ' + estKeysRequired +
                    ' new key' + (singular ? '' : 's') + ', which is time consuming.\n' +
                    'We expect this operation to be complete by ' + endTime.toLocaleString());
                background.pregenerateKeys(estKeysRequired, function() {
                    $('.result').text('Keys generated; updating server. This should not take long');
                    background.commitPendingGroupDiffs(identity, nonce, checksum, function() {
                        $('.result').text('Operation completed successfully.');
                    }, function(e) {
                        $('.result').text('Error. Please try again ' + JSON.stringify(e));
                    });
                }, function(e) {
                    $('.result').text('Error. Please try again ' + JSON.stringify(e));
                }
                );
            });
         }, function(e) {
            $('.result').text('Error: ' + e.userVisibleError);
        }
        );
    });
});
