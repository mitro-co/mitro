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

lib = require('./mitro_lib');
function arraysEqual(a1,a2) {
    return JSON.stringify(a1)==JSON.stringify(a2);
}

var failIfThree = function(val, s,e) {
  setTimeout(function() {
    if (val ===3) 
      e(3); 
    else 
      s(1);
  }, 100);
};

var failIfRun = function() { throw new Error('fail');};

lib.parallel([
  [failIfThree, [1]],
  [failIfThree, [2]],
  [failIfThree, [3]]
  ], failIfRun,

  function(r) { 
    (r===3) || (failIfRun());
  });


lib.parallel([
  [failIfThree, [1]],
  [failIfThree, [2]],
  [failIfThree, [2]]
  ], 
  function(r) { 
    if (!arraysEqual(r, [1,1,1])) 
      failIfRun();
  },
    failIfRun);