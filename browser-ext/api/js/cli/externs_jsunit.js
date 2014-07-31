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

/** Externs for Google Closure JSUnit, so we don't need to compile it. */

/**
 * @param {*} a The expected value (2 args) or the debug message (3 args).
 * @param {*} b The actual value (2 args) or the expected value (3 args).
 * @param {*=} opt_c The actual value (3 args only).
 */
var assertNotEquals = function(a, b, opt_c) {};

/**
 * @param {*} a The value to assert (1 arg) or debug message (2 args).
 * @param {*=} opt_b The value to assert (2 args only).
 */
var assertNotNull = function(a, opt_b) {};

/**
 * @param {*} a The value to assert (1 arg) or debug message (2 args).
 * @param {*=} opt_b The value to assert (2 args only).
 */
var assertNull = function(a, opt_b) {};

/**
 * @param {*} a The value to assert (1 arg) or debug message (2 args).
 * @param {*=} opt_b The value to assert (2 args only).
 */
var assertUndefined = function(a, opt_b) {};

/**
 * @param {*} a The value to assert (1 arg) or debug message (2 args).
 * @param {*=} opt_b The value to assert (2 args only).
 */
var assertTrue = function(a, opt_b) {};

/**
 * @param {*} a The value to assert (1 arg) or debug message (2 args).
 * @param {*=} opt_b The value to assert (2 args only).
 */
var assertFalse = function(a, opt_b) {};

/**
@param {string} message
*/
var fail = function(message) {};

/**
@param {*} a
@param {*} b
*/
var assertEquals = function(a, b) {};

/**
@param {Array} a
@param {Array} b
*/
var assertArrayEquals = function(a, b) {};

// Disabled due to conflicts with our own global assert function
//var assert = function(a) {};

/**
@param {function()} callable
@returns {Error}
*/
var assertThrows = function(callable) {};

/**
@param {Object} a
@param {Object} b
*/
var assertObjectEquals = function(a, b) {};

/**
 * Checks if the given element is the member of the given container.
 * @param {*} a Failure message (3 arguments) or the contained element
 *     (2 arguments).
 * @param {*} b The contained element (3 arguments) or the container
 *     (2 arguments).
 * @param {*=} opt_c The container.
 */
var assertContains = function(a, b, opt_c) {};
