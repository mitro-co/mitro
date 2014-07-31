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

    var params = decodeQueryString(window.location.search.slice(1));
    var selOrgId = parseInt(params.orgId, 10);
    if (isNaN(selOrgId)) {
        selOrgId = null;
    }

    $(document).ready(function () {
        background.getIdentity(function (identity) {
            mitro.loadOrganizationInfo(function (orgInfo) {
                var orgs = orgInfo.getOrganizations();
                var $orgDropdown = $('.org-dropdown-group');
                initOrgDropdown($orgDropdown, orgs, selOrgId);

                $('#create-team-form').submit(function () {
                    var $form = $(this);
                    var name = $form.find('input[name="name"]').val();
                    if (!name) {
                        showErrorDialog('Team name cannot be empty');
                        return false;
                    }
                    var description = $form.find('textarea[name="description"]').val();
                    var orgId = getOrgDropdownId($orgDropdown);
                    var identityList = [identity.uid];

                    background.addGroup(name, function (groupId) {
                        var onSuccess = function () {
                            helper.setLocation('manage-team.html?id=' + groupId);
                        };

                        background.editGroup(groupId, name, orgId,
                                identityList, onSuccess, onBackgroundError);
                    }, onBackgroundError);

                    showSpinny($('#create-team-button'));

                    return false;
                }); 
            }, onBackgroundError);
    }, onBackgroundError);
    });
})();
