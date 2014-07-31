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

    var $spinny = showSpinny($('.spinny'));

    background.fetchServices(function(services) {
        background.listGroups(function (groups) {
            hideSpinny($('.spinny'), $spinny);

            // figure out the counts of which groups contain which secrets
            var groupSecretCounts = {};
            for (var i = 0; i < services.length; i++) {
                var service = services[i];
                for (var j = 0; j < service.groups.length; j++) {
                    var groupId = service.groups[j];
                    if (!(groupId in groupSecretCounts)) {
                        groupSecretCounts[groupId] = 0;
                    }
                  groupSecretCounts[groupId] += 1;
                }
            }

            filterSortAndRenderTeams(groups, groupSecretCounts);

            addTags($('.teams-list .team'), '.name',
                function ($element) {
                    var groupId = parseInt($element.attr('data-id'), 10);
                    var team = groups[groupId];
                    return team.owningOrgName ? [team.owningOrgName] : [];
                });
        }, onBackgroundError);
    }, onBackgroundError);
});
