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

    registerLiveSearch($('#secret-filter-string'), '.secret');

    // Map from secretId -> secret
    var secretsMap = {};

    var getSecretForListItem = function ($secretItem) {
        return secretsMap[$secretItem.attr('data-secret-id')];
    };

    var addOrgTags = function () {
        addTags($('.secrets-list .secret'), '.name', function ($element) {
            var secret = getSecretForListItem($element);
            return secret.owningOrgName ? [secret.owningOrgName] : [];
        });
    };

    background.fetchServices(function (secrets) {
        console.log('fetched ' + secrets.length + ' secrets');
        hideSpinny($('.spinny'), $spinny);

        if (secrets.length > 0) {
            $('.has-secrets').removeClass('hide');
        } else {
            $('.has-no-secrets').removeClass('hide');
            $('#tutorial-modal').modal({backdrop: 'static'}).modal('show');
        }

        _.each(secrets, processServiceInstanceForRendering);
        secretsMap = createMapFromArrayOnAttribute(secrets, 'secretId');

        renderServiceList(secrets, $('.secrets-list'), 'secrets-template');
        replaceBlankImages($('.secret-icon'));
        addOrgTags();
    }, onBackgroundError);

    $(document).on('click', '.action-button', function () { // TODO: Adam - Remove? Is the secrets-template mustache being used
        var $button = $(this);
        var $secretItem = $button.closest('.secret');
        var secret = getSecretForListItem($secretItem);
        executeSecretAction(secret, $button);  // TODO: Adam - remove? This is the only place where execute secret action is being used with button element parameter
    });
});
