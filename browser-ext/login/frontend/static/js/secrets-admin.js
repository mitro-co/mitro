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

    var MS_PER_SEC = 1000;

    var renderSecrets = function (secrets) {
        var html = templates['secrets-admin-template'].render({secrets: secrets});
        $('#secrets-table').html(html);
    };

    var calculateSecretGroupCount = function (secret, groups) {
        var groupCount = 0;
        for (var i = 0; i < secret.groups.length; ++i) {
            if (secret.groups[i] in groups) {
                if (isVisibleGroup(groups[secret.groups[i]])) {
                    ++groupCount;
                }
            } else {
                ++groupCount;
            }
        }

        return groupCount;
    };

    var calculateSecretUserCount = function (secret, groups) {
        var users = {};

        for (var i = 0; i < secret.users.length; i++) {
            users[secret.users[i]] = 1;
        }

        for (i = 0; i < secret.groups.length; i++) {
            if (secret.groups[i] in groups) {
                var group = groups[secret.groups[i]];
                for (var j = 0; j < group.users.length; ++j) {
                    users[group.users[j]] = 1;
                }
            }
        }

        return _.keys(users).length;
    };

    var calculateSecretAttributes = function (secret, org) {
        secret.renderedTitle = getRenderedTitle(secret);
        secret.host = getCanonicalHost(secret.clientData.loginUrl);
        secret.groupCount = calculateSecretGroupCount(secret, org.groups);
        secret.userCount = calculateSecretUserCount(secret, org.groups);

        if (secret.lastAccessed && secret.lastAccessed.timestampSec) {
            secret.lastAccessedString =
                formatTimestamp(secret.lastAccessed.timestampSec * MS_PER_SEC);
        }
        if (secret.lastModified && secret.lastModified.timestampSec) {
            secret.lastModifiedString =
                formatTimestamp(secret.lastModified.timestampSec * MS_PER_SEC);
        }
    };

    $(document).ready(function () {
        $('#search-string').keyup(function () {
            filterSecrets($(this).val());
        });

        var $spinny = showSpinny($('.spinny'));

        mitro.loadOrganizationInfo(function (orgInfo) {
            var selOrgInfo = orgInfo.getSelectedOrganization();
            background.getOrganization(selOrgInfo.id, function (org) {
                var secrets = _.values(org.orgSecretsToPath);
                console.log('fetched ' + secrets.length + ' secrets');

                hideSpinny($('.spinny'), $spinny);

                if (secrets.length > 0) {
                   $('.has-secrets').removeClass('hide');
                } else {
                   $('.has-no-secrets').removeClass('hide');
                }

                for (var i = 0; i < secrets.length; i++) {
                    calculateSecretAttributes(secrets[i], org);
                }
                renderSecrets(secrets);

                // undefined for the org secrets page, which has no search
                var searchString = $('#search-string').val();
                if (searchString) {
                    filterSecrets(searchString);
                }
            }, onBackgroundError);
        }, onBackgroundError);
    });
})();
