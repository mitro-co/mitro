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

var initOrgDropdown;
var getOrgDropdownId;
var selectOrgDropdownId;
(function () {
    'use strict';

    var getOrgDropdownSelection = function ($orgDropdown) {
        return $orgDropdown.find('.select');
    };

    var getOrgDropdownList = function ($orgDropdown) {
        return $orgDropdown.find('.dropdown-select');
    };

    var createOrgDropdownItem = function (name, value) {
        var $a = $('<a></a>').attr('data-id', value).text(name);
        return $('<li></li>').append($a);
    };

    var populateOrgDropdown = function ($orgDropdown, orgs) {
        var $orgList = getOrgDropdownList($orgDropdown);
        $orgList.children().remove();

        for (var i = 0; i < orgs.length; i++) {
            var org = orgs[i];
            $orgList.append(createOrgDropdownItem(org.name, org.id));
        }
    };

    var getOrgIdFromItem = function ($orgItem) {
        var orgId = parseInt($orgItem.attr('data-id'), 10);
        return isNaN(orgId) ? null : orgId;
    };

    getOrgDropdownId = function ($orgDropdown) {
        return getOrgIdFromItem(getOrgDropdownSelection($orgDropdown));
    };

    var selectOrgDropdownItem = function ($orgDropdown, $orgItem) {
        var $orgSelection = getOrgDropdownSelection($orgDropdown);
        $orgSelection.attr('data-id', $orgItem.attr('data-id'));
        $orgSelection.text($orgItem.text());
    };

    // Select the org item with the given org id, or create a new item if it
    // doesn't exist.
    /**
    @param {!jQuery} $orgDropdown
    @param {?number} selOrgId
    */
    selectOrgDropdownId = function ($orgDropdown, selOrgId) {
        var $orgSelection = getOrgDropdownSelection($orgDropdown);
        var $orgList = getOrgDropdownList($orgDropdown);
        var $selOrgItem = null;

        $orgList.find('a').each(function () {
            var orgId = getOrgIdFromItem($(this));
            if (orgId === selOrgId) {
               $selOrgItem = $(this);
            }
        });

        if ($selOrgItem === null) {
            if (selOrgId) {
                // Item belongs to an org that user is not a member of.
                // Show the org id since we don't know its name.
                $selOrgItem = createOrgDropdownItem(selOrgId, selOrgId);
            } else {
                $selOrgItem = createOrgDropdownItem('None', null);
            }
            $orgList.append($selOrgItem);
        }

        selectOrgDropdownItem($orgDropdown, $selOrgItem);
    };

    /**
    @param {!jQuery} $orgDropdown
    @param {!Array.<!Object>} orgs
    @param {?number} selOrgId
    @param {function(!jQuery)=} onSelect
    */
    initOrgDropdown = function ($orgDropdown, orgs, selOrgId, onSelect) {
        populateOrgDropdown($orgDropdown, orgs);
        selectOrgDropdownId($orgDropdown, selOrgId);

        $orgDropdown.find('li').click(function () {
            selectOrgDropdownItem($orgDropdown, $(this).find('a'));
            if (onSelect) {
                onSelect($orgDropdown);
            }
        });

        // Hide orgs field if user doesn't belong to any orgs and secret
        // doesn't belong to an org.
        if (!selOrgId && orgs.length === 0) {
            $orgDropdown.addClass('hide');
        } else {
            $orgDropdown.removeClass('hide');
        }
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            initOrgDropdown: initOrgDropdown,
            getOrgDropdownId: getOrgDropdownId,
            selectOrgDropdownId: selectOrgDropdownId};
    }
})();
