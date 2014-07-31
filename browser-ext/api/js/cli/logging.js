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

var mitro = mitro || {};

/** @suppress{duplicate} */
var debugMode;
(function() {
// define mitro
if(typeof(window) !== 'undefined') {
  if (typeof(mitro) === 'undefined') {
    mitro = {};
  }
  mitro.log = {};
}
// define node.js module
else if(typeof(module) !== 'undefined' && module.exports) {
  mitro = {};
  module.exports = mitro.log = {};
}

var makeCircularBuffer = function(size) {
  var loc = 0;
  var data = [];
  var obj = {
    get  : function(k){
      if (k < 0 || data.length <= k) {
        return undefined;
      }

      // the "oldest" entry in the array is the one that loc points at
      return data[(loc + k) % data.length];
    },
    push : function(){
      var dat = Array.prototype.slice.call(arguments);
      data[loc] = dat;
      loc = (loc + 1) % size;
    },
    size : function() {
      return data.length;
    }
  };

  obj.toArray = function() {
    var rval = [];
    for (var i=0; i < obj.size(); ++i) {
      rval.push(obj.get(i));
    }
    return rval;
  };

  obj.toString = function() {
    var outputArray = obj.toArray();
    var output = '';
    for (var i = 0; i < outputArray.length; i++) {
      // don't modify the actual data in the buffer
      var rowArrayCopy = outputArray[i].slice();
      for (var j = 0; j < rowArrayCopy.length; j++) {
        var element = rowArrayCopy[j];
        if (element instanceof Error) {
          // Browser's default JSON.stringify doesn't convert native Error instances
          element = {
            name: element.name,
            message: element.message,
            stack: element.stack
          };
        }

        // recursively converts arrays and objects
        if (typeof element == 'object') {
          rowArrayCopy[j] = JSON.stringify(element);
        }
      }

      output += rowArrayCopy.join(' ');
      output += '\n';
    }
    return output;
  };

  return obj;
};

var logBuffer = makeCircularBuffer(500);
mitro.log.makeCircularBuffer = makeCircularBuffer;
mitro.log.logBuffer = logBuffer;
var oldLog = console.log;
mitro.log.captureLogsToBuffer = function() {
  console.log = logBuffer.push;
};
mitro.log.stopCapturingLogsToBuffer = function() {
  console.log = oldLog;
};

if (!debugMode) {
  mitro.log.captureLogsToBuffer();
}

})();
