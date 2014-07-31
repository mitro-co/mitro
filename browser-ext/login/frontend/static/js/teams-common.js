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

var teamSortFunc;
var filterSortAndRenderTeams;
(function () {
    'use strict';

    teamSortFunc = function (a, b) {
        return lowercaseCompare(a.name, b.name);
    };

    var renderTeams = function (teams) {
        $('.teams-list').html(templates['teams-template'].render(
                {teams: teams}));
        replaceBlankImages($('.team-icon'));
    };

    var processTeamForRendering = function (team, secretCounts) {
        team.userCount = team.users.length;
        team.userCountString = team.userCount +
                ' Member' + (team.userCount !== 1 ? 's' : '');

        team.secretCount = (team.groupId in secretCounts) ?
            secretCounts[team.groupId] : 0;
        team.secretCountString = team.secretCount +
                ' Secret' + (team.secretCount !== 1 ? 's' : '');
    };

    filterSortAndRenderTeams = function (teams, secretCounts) {
        teams = filterVisibleGroups(teams);
        teams.sort(teamSortFunc);

        if (teams.length > 0) {
            $('.has-teams').removeClass('hide');

            _.each(teams, function (team) {
                processTeamForRendering(team, secretCounts);
            });
            renderTeams(teams);
        } else {
            $('.has-no-teams').removeClass('hide');
        }
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            teamSortFunc: teamSortFunc,
            filterSortAndRenderTeams: filterSortAndRenderTeams};
    }
})();
