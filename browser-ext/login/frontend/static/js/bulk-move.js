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
  background.fetchServices(function(serviceInstances) {
    _.each(serviceInstances, processServiceInstanceForRendering);
    renderServiceList(serviceInstances, $('#all-secrets'), 'bulk-move-services-template');
  }, onBackgroundError);

  // load all groups that I have access to.
  background.listGroups(function (groups) {
      groups = filterVisibleGroups(groups);
      $('#acl-groups').html(templates['bulk-move-groups-template'].render({groups:groups}));
  }, onBackgroundError);

  var orgId = null;
  mitro.loadOrganizationInfo(function (orgInfo) {
      var selOrgInfo = orgInfo.getSelectedOrganization();
      if (selOrgInfo) {
        orgId = selOrgInfo.id;
        $('#org-transfer').show();
        $('#org-name').text(selOrgInfo.name);
      }
  }, onBackgroundError);

  $('#commit-acls').click(function() {
    // get all the list of secrets that are selected
    var $checkedServices = $('#all-secrets').find('input[type=checkbox]:checked');
    var i;
    var secretIds = [];
    for (i = 0; i < $checkedServices.length; ++i) {
      secretIds.push(parseInt(($($checkedServices[i])).attr('data-secret-id'), 10));
    }

    var addMyOrg = $('#transfer-to-my-org').is(':checked');

    // get the list of all the groups that have been selected
    var $checkedGroups = $('#acl-groups').find('input[type=checkbox]:checked');
    var groupIds = [];
    for (i = 0; i < $checkedGroups.length; ++i) {
      groupIds.push(parseInt(($($checkedGroups[i])).attr('data-group-id'), 10));
    }

    // applying 
    if (confirm("I am about to OVERWRITE all access control lists for " + secretIds.length + " secrets with the " + groupIds.length +
        " groups which you selected. You CANNOT UNDO THIS OPERATION")) {
      $('#everything').hide();
        var updateOne = function() {
        $('#messages').text('Updating data: ' + secretIds.length + " secrets remain.");
        var secretId = secretIds.pop();
        if (!secretId) {
          $('#messages').text('Done!');
          return;
        }
        background.editSiteShares(secretId, groupIds, [], addMyOrg ? orgId : null, updateOne, onBackgroundError);
      };
      updateOne();
    } else {
      return;
    }


  });

});
