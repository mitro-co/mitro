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

(function () {
    'use strict';

    $(document).ready(function () {
        // this needs to be run here to prevent the click handler from being bound
        // multiple times
        var $modal = $('#add-members-modal');
        var $memberList = $modal.find('.member-list');
        initAddAcl($memberList, $modal.find('.search'));

        var params = decodeQueryString(window.location.search.slice(1));
        if (!('id' in params)) {
            showErrorDialog('Missing group id parameter'); 
            return;
        }
        var groupId = parseInt(params.id, 10);
        var userData = null;
        var nextCheckboxId = 0;

        var $memberSpinny = showSpinny($('.team-member-list'));
        var $secretSpinny = showSpinny($('.team-secret-list'));

        var renderTeamInfo = function (team) {
            var $icon = $('.large-team-icon');
            $icon.attr('data-icon-text', team.name);
            $icon.attr('src', EMPTY_IMAGE);
            replaceBlankImages($icon);
            
            var $form = $('#team-info-form');
            $form.find('input[name="name"]').val(team.name);
            $form.find('input[name="description"]').val(team.description);
        };

        var renderTeamMembers = function (team) {
            var members = processUsersForRendering(userIdsToUserMap(team.users));
            var $teamMemberList = $('.team-member-list');
            $teamMemberList.html(templates['team-members-template']
                    .render({members: members}));
            replaceBlankImages($teamMemberList.find('.member-icon'));
        };

        var processSecretsForRendering = function (secrets) {
            for (var i = 0; i < secrets.length; ++i) {
                var secret = secrets[i];
                secret.renderedTitle = getRenderedTitle(secret);
                secret.host = getCanonicalHost(secret.clientData.loginUrl);

                if (secret.hints && secret.hints.icons && secret.hints.icons.length > 0) {
                    secret.icon = secret.hints.icons[0];
                } else {
                    secret.icon = EMPTY_IMAGE;
                }
            }
            return secrets;
        };

        var checkSecretsSharedWithTeam = function (secrets, team) {
            for (var i = 0; i < secrets.length; ++i) {
                var secret = secrets[i];
                secret.checkboxId = 'checkbox' + nextCheckboxId++;

                if (secret.groups.indexOf(team.groupId) !== -1) {
                    secret.checked = 'checked';
                } else {
                    secret.checked = '';
                }
            }
        };

        var renderTeamSecrets = function (secrets) {
            var secrets = processSecretsForRendering(secrets);
            secrets.sort(secretSortFunc);
            var $teamSecretList = $('.team-secret-list');
            $teamSecretList.html(templates['team-secrets-template']
                    .render({secrets: secrets}));
            replaceBlankImages($teamSecretList.find('.secret-icon'));
        };

        var renderTeam = function (team, teamSecrets) {
            renderTeamInfo(team);
            renderTeamMembers(team);
            renderTeamSecrets(teamSecrets);
        };

        var onDataLoaded = function (group) {
            hideSpinny($('.team-member-list'), $memberSpinny);
            hideSpinny($('.team-secret-list'), $secretSpinny);

            if (!group) {
                showErrorDialog('Invalid group id: ' + groupId);
                return;
            }

            var orgs;
            if (typeof group.owningOrgId === 'number') {
                orgs = [{id: group.owningOrgId, name: group.owningOrgName}];
            } else {
                orgs = userData.getOrganizations();
            }

            initOrgDropdown($('.org-dropdown-group'), orgs, group.owningOrgId);
            var groupSecrets = userData.getSecretsForGroup(groupId);
            renderTeam(group, groupSecrets);
        };

        mitro.loadUserDataAndGroup(groupId, function(ud, group) {
            userData = ud;
            onDataLoaded(group);
        }, onBackgroundError);

        var editTeamInfo = function (team, name, description, orgId) {
            background.editGroup(team.groupId, name, orgId,
                                 team.users, reload, onBackgroundError);
        };

        var editTeamMembers = function (team, members) {
            background.editGroup(team.groupId, team.name, team.owningOrgId,
                                 members, reload, onBackgroundError);
        };

        $('#team-info-form').submit(function () {
            var $form = $('#team-info-form');
            var name = $form.find('input[name="name"]').val();
            if (!name) {
                showErrorDialog("Team name cannot be empty");
                return false;
            }
            var description = $form.find('input[name="description"]').val();
            var orgId = getOrgDropdownId($('.org-dropdown'));

            var group = userData.getGroup(groupId);
            editTeamInfo(group, name, description, orgId);
            // prevent default browser submit
            return false;
        });

        $('.delete-team-link').click(function () {
            var group = userData.getGroup(groupId);
            var title = 'Delete Team';
            var message = 'Really delete team "' + group.name + '"?';
            showDeleteDialog(title, message, function () {
                showSpinny($(this));
                background.removeGroup(groupId, function () {
                    helper.setLocation('teams.html');
                }, onBackgroundError);
            });
        });

        var showAddMembersModal = function (group, members) {
            members = processMembersForRendering(members);
            checkAndAddTeamMembers(members, group);
            $memberList.html(templates['add-members-template'].render(
                {members: members}));
            replaceBlankImages($memberList.find('.member-icon'));

            showModal($modal);
        };

        // Add member
        $('#add-members-button').click(function () {
            var users = userIdsToUserMap(userData.getUsersVisibleToGroup(groupId));
            var existingUsers = userIdsToUserMap(userData.getGroup(groupId).users);

            // Merge existing users
            for (var userId in existingUsers) {
              users[userId] = existingUsers[userId];
            }
            var members = processUsersForRendering(users, existingUsers);

            $memberList.html(templates['add-members-template'].render(
                {users: members}));
            replaceBlankImages($('.member-icon'));

            showModal($modal);
        });

        // Remove member
        $(document).on('click', '.remove-user', function (event) {
            var memberItem = $(event.target).closest('.member');
            var userId = memberItem.attr('data-id');
            var message = 'Remove ' + userId + ' from team?';

            var $modal = $('#delete-confirm-modal');
            $modal.find('.delete-message').text(message);
            $modal.find('input[name="email"]').val(String(userId));
            $modal.modal('show');

            return false;
        });

        var removeMemberFromTeam = function (member, team) {
            var identityList = _.without(team.users, member);
            background.editGroup(team.groupId, team.name, team.owningOrgId,
                                 identityList, reload, onBackgroundError);
        };

        var setTeamsOnSecretAcl = function (groupIdList, secret, onSuccess,
                                            onError) {
            background.editSiteShares(secret.secretId, groupIdList,
                    secret.users, secret.owningOrgid, onSuccess, onError);
        };

        var addTeamToSecretAcl = function (team, secret, onSuccess, onError) {
            setTeamsOnSecretAcl([team.groupId].concat(secret.groups), secret,
                                onSuccess, onError);
        };

        var removeTeamFromSecretAcl = function (team, secret, onSuccess,
                                                onError) {
            setTeamsOnSecretAcl(_.without(secret.groups, team.groupId), secret,
                                onSuccess, onError);
        };

        var mapDifference = function (map1, map2) {
            var values = [];
            var key;
            for (key in map1) {
                if (map1.hasOwnProperty(key)) {
                    if (!map2.hasOwnProperty(key)) {
                        values.push(map1[key]);
                    }
                }
            }
            return values;
        };

        var arrayToMap = function (a, property) {
            var map = {};
            for (var i = 0; i < a.length; ++i) {
                map[a[i][property]] = a[i];
            }
            return map;
        };

        $('#save-members-button').click(function () {
            showSpinny($(this));
            var $modal = $(this).closest('.modal');
            var $checkboxes = $modal.find('input[type="checkbox"]:checked');

            var members = _.map($checkboxes.toArray(), function (checkbox) {
                return $(checkbox).closest('.member').attr('data-user-id');
            });

            var group = userData.getGroup(groupId);
            editTeamMembers(group, members);
        });

        $('#remove-user-button').click(function (event) {
            var $modal = $(event.target).closest('.modal');
            var email = $modal.find('input[name="email"]').val();
            console.log('removing user ' + email + ' from team');
            var group = userData.getGroup(groupId);
            removeMemberFromTeam(email, group);
            showSpinny($('.spinny'));
        });

        var showAddSecretsModal = function (group, secrets) {
            var $modal = $('#add-secrets-modal');
            var $secretList = $modal.find('.secret-list');
            var group = userData.getGroup(groupId);
            var secrets = userData.getSecretsVisibleToGroup(groupId);
            secrets = processSecretsForRendering(secrets);
            secrets.sort(secretSortFunc);
            checkSecretsSharedWithTeam(secrets, group);

            $secretList.html(templates['add-secrets-template'].render(
                {secrets: secrets}));
            replaceBlankImages($secretList.find('.secret-icon'));

            showModal($modal);
        };

        $('#add-secrets-button').click(function () {
            var group = userData.getGroup(groupId);

            if (userData.userBelongsToSameOrgAsGroup(groupId)) {
                background.getOrganization(group.owningOrgId, function (org) {
                    showAddSecretsModal(group,
                      userData.getOrganizationSecrets(group.owningOrgId, org));
                }, onBackgroundError);
            } else {
                showAddSecretsModal(group, userData.secrets);
            }
        });

        $('#save-secrets-button').click(function () {
            showSpinny($(this));
            var $modal = $(this).closest('.modal');
            var $checkboxes = $modal.find('input[type="checkbox"]:checked');

            var newSecrets = _.map($checkboxes.toArray(), function (checkbox) {
                var $secret = $(checkbox).closest('.secret');
                var secretId = parseInt($secret.attr('data-id'), 10);
                return userData.getSecret(secretId);
            });

            var groupSecrets = userData.getSecretsForGroup(groupId);
            var oldSecretsMap = arrayToMap(groupSecrets, 'secretId');
            var newSecretsMap = arrayToMap(newSecrets, 'secretId');

            var removeList = mapDifference(oldSecretsMap, newSecretsMap);
            var addList = mapDifference(newSecretsMap, oldSecretsMap);

            var removeSecretsFromTeam = function () {
                if (removeList.length === 0) {
                    reload();
                    return;
                }

                var secret = removeList.pop();
                var group = userData.getGroup(groupId);
                removeTeamFromSecretAcl(group, secret, addSecretsToTeam,
                                        onBackgroundError);
            };

            var addSecretsToTeam = function () {
                if (addList.length === 0) {
                    removeSecretsFromTeam();
                    return;
                }

                var group = userData.getGroup(groupId);
                var secret = addList.pop();
                addTeamToSecretAcl(group, secret, addSecretsToTeam, 
                                   onBackgroundError);
            };

            addSecretsToTeam();
        });

        // Remove secret
        $(document).on('click', '.remove-secret', function (event) {
            var secretItem = $(event.target).closest('.secret');
            var secretId = parseInt(secretItem.attr('data-id'), 10);
            var group = userData.getGroup(groupId);
            var secret = userData.getSecret(secretId);
            removeTeamFromSecretAcl(group, secret, reload, onBackgroundError);
        });

        registerLiveSearch($('#add-secrets-modal .search'), '.secret-list > .secret');
        registerLiveSearch($('#add-members-modal .search'), '.member-list > .acl-item');
    });
})();
