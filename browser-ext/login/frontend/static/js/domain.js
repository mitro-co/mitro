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

// From the URL
var getCanonicalHost = function(full_url) {
    var host = new URI(full_url).getAuthority();

    if (!host) {
        return null;
    }

    // Cookies are not isolated by port, but the chrome cookies API will not 
    // match the domain if the port is included.
    var index = host.indexOf(':');
    if (index !== -1) {
        host = host.slice(0, index); 
    } 

    // TODO: The right way to implement this is using public suffix.
    //
    // http://publicsuffix.org 
    return host.substring(0, 4) === 'www.' ? host.substring(4) : host;
};
