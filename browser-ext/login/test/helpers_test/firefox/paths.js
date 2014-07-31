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

var self = require("sdk/self");
var data = self.data;

var HTML_PREFIX = 'resource://mitro-helpers-test-at-jetpack/mitro-helpers-test/data/html/';

var popup_scripts = [data.url('jquery.min.js'),
                     data.url('client.js'),
                     data.url('helpers.js'),
                     data.url('render_results.js'),
                     data.url('popup.js')];

var background_scripts = [data.url("config.js"),
                          data.url("tester.js"),
                          data.url("jquery.min.js"),
                          data.url("utils.js"),
                          data.url("client.js"),
                          data.url("helpers.js"),
                          data.url("background_test.js")];

var content_scripts = [data.url("tester.js"),
                       data.url("jquery.min.js"), 
                       data.url("utils.js"),
                       data.url("client.js"),
                       data.url("helpers.js"),
                       data.url("content_script_test.js")];

exports.HTML_PREFIX = HTML_PREFIX;
exports.popup_scripts = popup_scripts;
exports.background_scripts = background_scripts;
exports.content_scripts = content_scripts;