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
 * Html and js snippets used to render test results
 * on the results page and in the popup
 */

var CONTAINER_ID = 'results';
var NODE_CLASS = 'node';
var FEATURE_CLASS = 'feature';
var RESULT_CLASS = 'result';

var node_template = '<div class="' + NODE_CLASS +'" style="display: none;"> \
                        <div class="' + FEATURE_CLASS +'"></div> \
                        <div class="' + RESULT_CLASS +'"></div> \
                    </div>';

/**
 * Renders single test result
 * The function also creates the container element
 * when called for the first time
 * 
 * @param feature {string} The feature name
 * @param result {string} The test result
 */
var render_node = function(feature, result) {
    if (!$('#' + CONTAINER_ID).length) {
        $('body').append($('<div>').attr('id', CONTAINER_ID));
    }
    var node = $(node_template);
    $('.' + FEATURE_CLASS, node).text(feature);
    $('.' + RESULT_CLASS, node).text(result);
    $('#' + CONTAINER_ID).append(node.show());
};
