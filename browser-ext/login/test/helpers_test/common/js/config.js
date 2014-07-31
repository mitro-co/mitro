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
 * Static server configuration
 * 
 * The static server is used to serve
 * the non-extension pages we need to test content scripts
 * and some other stuff like cookies.
 */
// The only case you'll need this different then 'localhost'
// is running Safari browser on a separate machine.
var STATIC_SERVER_HOST = 'localhost';
var STATIC_SERVER_PORT = '8001';
// Frequently used URLs:
var STATIC_ROOT = "http://" + STATIC_SERVER_HOST + ":" + STATIC_SERVER_PORT;
var TEST_URL = STATIC_ROOT + "/test_page.html";

// The icons to test BackgroundHelper.setIcon
var test_icons = {'19': 'mitro_logo-19.png',
                  '38': 'mitro_logo-38.png'};
