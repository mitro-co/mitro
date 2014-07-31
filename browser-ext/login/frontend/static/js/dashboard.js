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
        mitro.loadOrganizationInfo(function (orgInfo) {
            var selOrgInfo = orgInfo.getSelectedOrganization();
            if (selOrgInfo === null) {
                helper.setLocation('secrets.html');
            } else {
                background.getOrganization(selOrgInfo.id, function (org) {
                    var membersCount = org.members.length;
                    var teamsCount = filterVisibleGroups(org.groups).length;
                    var secretsCount = _.values(org.orgSecretsToPath).length;

                    $('.members-count').text(membersCount.toString());
                    $('.members-label').text('MEMBER' +
                                             (membersCount !== 1 ? 'S' : ''));
                    $('.teams-count').text(teamsCount.toString());
                    $('.teams-label').text('TEAM' +
                                           (teamsCount !== 1 ? 'S' : ''));
                    $('.secrets-count').text(secretsCount.toString());
                    $('.secrets-label').text('SECRET' +
                                             (secretsCount !== 1 ? 'S' : ''));
                }, onBackgroundError);
            }
        }, onBackgroundError);
    });
}());
