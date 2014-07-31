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

/**
 * This tricky code below is used to make the selenium test possible.
 * It gets inserted at the end of the background.js with 'make safari-test'
 * 
 * This method maps the external static html pages to the extension internals
 * by binding the extension scripts using the safari content scripts mechanism
 */



/**
 * 
 * This function programmatically registers the content scripts.
 * 
 * @param scripts {array} the list of content script file names
 * @param whitelist {array} the whitelist filter value
 * @param blacklist {array} the blacklist filter value
 * @param runAtEnd {boolean} Should the scripts run at the end of page loading?
 */
function addContentScripts(scripts, whitelist, blacklist, runAtEnd) {
    // this will contain all scripts contents
    var scripts_str = '';

    for (var i=0; i<scripts.length; i++){
        // fetching the extension scripts contents
        $.get(scripts[i], (function() {
            var index = i;
            return function(data){
                console.log('Script attached: ' + scripts[index]);
                scripts_str += data;
                if(index + 1 === scripts.length){
                    // bind the resulting string once we reach the end of the scripts array
                    safari.extension.addContentScript(scripts_str, whitelist, blacklist, runAtEnd);
                }
            };
        })()

        );
    }
}

// the extension base url. This will be used to compose absolute paths
var base_url = window.location.href.substr(0, window.location.href.length - 'html/background.html'.length);

/**
 * Converts relative paths array
 * to the absolute paths array
 */
var getFullUrls = function(urls){
    var fullUrls = [];
    for(var i=0; i<urls.length; i++){
        fullUrls.push(base_url + urls[i]);
    }
    
    return fullUrls;
};

// the popup.html, signup.html and change-password.html pages
addContentScripts(getFullUrls(['js/config.js',
                               'js/querystring.js',
                               'js/userpass.js',
                               'js/logging.js',
                               'js/jquery.min.js',
                               'js/jquery.ba-resize.min.js',
                               'js/bootstrap.min.js',
                               'js/underscore-min.js',
                               'js/URI.js',
                               'js/admin-common.js',
                               'js/bloomfilter.js',
                               'js/bad_password_bloom_data.js',
                               'js/passwords.js',
                               'js/client.js',
                               'js/helpers.js',
                               'js/popup.js']), ['http://selenium.mitro.co/html/popup.html', 'http://selenium.mitro.co/html/signup.html', 'http://selenium.mitro.co/html/change-password.html'], [], true, false, false);

// services.html
addContentScripts(getFullUrls(['js/config.js',
                               'js/logging.js',
                               'js/jquery.min.js',
                               'js/jquery-ui.min.js',
                               'js/underscore-min.js',
                               'js/URI.js',
                               'js/bootstrap.min.js',
                               'js/json2.min.js',
                               'js/utils.js',
                               'js/domain.js',
                               'js/admin-common.js',
                               "js/client.js",
                               'js/helpers.js',
                               'js/template.js',
                               'js/services.js',
                               'js/add-service.js']), ['http://selenium.mitro.co/html/services.html'], [], true, false, false);

// groups.html
addContentScripts(getFullUrls(['js/config.js',
                               'js/logging.js',
                               'js/jquery.min.js',
                               'js/jquery-ui.min.js',
                               'js/underscore-min.js',
                               'js/URI.js',
                               'js/bootstrap.min.js',
                               'js/json2.min.js',
                               'js/utils.js',
                               'js/domain.js',
                               'js/admin-common.js',
                               "js/client.js",
                               'js/helpers.js',
                               'js/template.js',
                               'js/groups.js']), ['http://selenium.mitro.co/html/groups.html'], [], true, false, false);

// the terms.html and privacy.html
addContentScripts(getFullUrls(['js/config.js',
                               'js/logging.js',
                               'js/jquery.min.js',
                               'js/jquery-ui.min.js',
                               'js/underscore-min.js',
                               'js/URI.js',
                               'js/bootstrap.min.js',
                               'js/json2.min.js',
                               'js/utils.js',
                               'js/domain.js',
                               'js/admin-common.js',
                               "js/client.js",
                               'js/helpers.js',
                               'js/template.js']), ['http://selenium.mitro.co/html/terms.html', 'http://selenium.mitro.co/html/privacy.html'], [], true, false, false);
