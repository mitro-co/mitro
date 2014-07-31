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

'use strict';

// fake console log for debugging
var console;
if (typeof console === 'undefined') {
  console = {
    log: function(message) {
      self.postMessage({
        request: 'log',
        message: Array.prototype.slice.call(arguments)
      });
    }
  };
}

self.addEventListener('message', function (e) {
  try {
    if (e.data.request == 'load') {
      if (e.data.type == 'crappy') {
        importScripts('crappycrypto.js');
      } else {
        importScripts('lru_cache.js', 'jsbn.js', 'asn1.js', 'sha1.js', 'sha256.js', 'util.js', 'rsa.js', 'oids.js', 'pki.js',
          'aes.js', 'prng.js', 'random.js', 'keyczar_util.js', 'keyczar.js', 'crypto.js');
      }
    } else if (e.data.request == 'generate') {
      // console.log('generating key');
      if (e.data.seed) {
        forge.random.collect(e.data.seed);
      }

      var key = mitro.crypto.generate();
      self.postMessage({
        request: 'key',
        key: key.toJson()
      });
    } else if (e.data.request == 'seed') {
      // seed the random number generator with entropy
      forge.random.collect(e.data.seed);
    } else {
      console.log('keycache worker unexpected message:', e.data);
    }
  } catch (e) {
    // Report errors. Web worker errors don't report the stack; very helpful.
    // TODO: Some browsers may not support e.stack?
    self.postMessage({request: 'error', message: e.stack});
    throw e;
  }
});
