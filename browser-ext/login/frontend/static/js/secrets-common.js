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

var SecretAction = {
    SIGN_IN: {action: 0, label: 'Sign in', image: '../img/key.png'},
    VIEW: {action: 1, label: 'View', image: '../img/eye.png'},
    COPY: {action: 2, label: 'Copy', image: '../img/eye.png'}
};

var getRenderedTitle = function (secret) {
    var renderedTitle = '';

    if (secret.clientData && secret.clientData.title !== null && secret.clientData.title !== undefined) {
        renderedTitle = secret.clientData.title;
    } else if (secret.hints && secret.hints.title) {
        renderedTitle = secret.hints.title;
    } else if (secret.clientData) {
        renderedTitle = getCanonicalHost(secret.clientData.loginUrl) || '';
    }

    return renderedTitle;
};

var processServiceInstanceForRendering = function (service) {
    var clientData = service.clientData;

    if ('loginUrl' in clientData) {
        service.host = getCanonicalHost(clientData.loginUrl);
    }

    if (service.host) {
        service.linkClass = 'icon-link';
    } else {
        service.linkClass = 'hide';
        service.host = "";
    }

    if (service.hints && service.hints.icons && service.hints.icons.length) {
        service.icon = service.hints.icons[0];
    } else {
        service.icon = EMPTY_IMAGE;
    }

    service.renderedTitle = getRenderedTitle(service);

    if (typeof clientData.type === 'undefined') {
        clientData.type = 'auto';
    }

    var supportsSignIn = function (secret) {
        return secret.clientData.type === 'auto' ||
               (secret.clientData.type === 'manual' &&
                secret.clientData.username &&
                secret.clientData.loginUrl &&
                secret.clientData.loginUrl.indexOf('http') === 0);
    };

    var secretAction;

    if (supportsSignIn(service)) {
        secretAction = SecretAction.SIGN_IN;
    } else {
        secretAction = SecretAction.VIEW;
    }

    service.action = secretAction.action;
    service.buttonLabel = secretAction.label;
    service.buttonImage = secretAction.image;

    service.flattenedUsersForDisplay = service.flattenedUsers.slice(0, 5);
    service.flattenedUsersForDisplayCount = -service.flattenedUsersForDisplay.length + service.flattenedUsers.length;
};

var disambiguateTitles = function (instances) {
    var serviceCounts = {};
    var title;
    for (var i = 0; i < instances.length; i++) {
        title = instances[i].renderedTitle;
        if (title in serviceCounts) {
            serviceCounts[title]++;
        }  else {
            serviceCounts[title] = 1;
        }
    }

    for (i = 0; i < instances.length; i++) {
        var instance = instances[i];
        title = instance.renderedTitle;

        if (serviceCounts[title] > 1 && 'username' in instance.clientData) {
            instance.renderedTitle = title + ' (' + instance.clientData.username + ')';
        }
    }
};

var secretSortFunc = function (a, b) {
    var title1 = a.renderedTitle ? a.renderedTitle : "";
    var title2 = b.renderedTitle ? b.renderedTitle : "";
    return lowercaseCompare(title1, title2);
};

var renderServiceList = function (servicesList, $list, templateName) {
    servicesList.sort(secretSortFunc);
    disambiguateTitles(servicesList);

    $list.html(templates[templateName].render({secrets: servicesList}));
    replaceBlankImages($('.service-icon'));
};

var filterSecrets = function (queryString) {
  var serviceMatches = function($svc, str) {
    var text = $svc.find('.host,.title').text().toLocaleLowerCase();
    return (text.indexOf(str) >= 0);
  };
  var $serviceItems = $('.list-item');
  var toSearch = queryString.toLocaleLowerCase();
  for (var i = 0; i < $serviceItems.length; ++i) {
    var $svc = $($serviceItems[i]);
    if (!toSearch || serviceMatches($svc, toSearch)) {
      $svc.attr('data-matches', true);
      $svc.show();
    } else {
      $svc.attr('data-matches', false);
      $svc.hide();
    }
  }
};


var openManageSecretPage = function (secret) {
    var path = 'admin-manage-secret.html?secretId=' + secret.secretId;
    // Open a new tab if initiated from the popup.
    if (window.location.href.indexOf('popup.html') != -1) {
        helper.tabs.create({'url': helper.getURL('html/' + path)},
                           function (tab) {
                                console.log('opened tab');
                           });
    } else {
        helper.setLocation(path);
    }
};

var copyText = function (text, onSuccess) {
    var $shownText = $('<input type="text">');
    $shownText.val(text);
    $('body').append($shownText);
    helper.copyFromInput($shownText, function() {
        $shownText.remove();
        if (onSuccess) {
            onSuccess();
        }
    });
};

var copyPassword = function (secret, callback) {
    console.log('copying password');

    background.getSiteSecretDataForDisplay(secret.secretId, function (data) {
        if (data.clientData.type === 'note') {
          copyText(data.criticalData.note, callback);
        } else {
          copyText(data.criticalData.password, callback);
        }
    }, function (error) {
        // TODO: How should we handle errors?
        alert('Unknown error occurred: ' + error);
    });
};

var executeSecretAction = function (secret, $button) { // TODO: update the parameters for this
    if (secret.action === SecretAction.COPY.action) {
        copyPassword(secret, $button);
        // show manual login
    } else if (secret.action === SecretAction.VIEW.action) {
        openManageSecretPage(secret);
    } else if (secret.action === SecretAction.SIGN_IN.action) {
        background.doExtensionLogin(secret);
    } else {
        throw new Error('Unknown action');
    }
};

if (WEBPAGE) {
    $(function() {
        $('body').addClass('webpage');
    });
}
