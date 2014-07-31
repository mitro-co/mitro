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

var crypto = require('./crappycrypto.js');
var assert = require('assert');

function testCrappyCrypto() {
  var pvt = new crypto.CrappyPrivateKey();
  pvt.generateKey();
  var key = pvt.getPublicKeyString();
  var pub = new crypto.CrappyPublicKey();
  pub.setKeyFromString(key);

  var helloEncrypted = pub.encryptForMe('hello');
  var helloDecrypted = pvt.decryptMessage(helloEncrypted);
  assert.equal('hello', helloDecrypted);

  var worldSigned = pvt.signMessage('world');
  var worldUnsigned = pub.verifySignedByMe(worldSigned);
  assert.equal('world', worldUnsigned);

  pvt.generateKey();
  assert.throws(function () {
    helloDecrypted = pvt.decryptMessage(helloEncrypted);
  }, crypto.CryptoError);
  assert.throws(function () {
    var worldSigned = pvt.signMessage('world');
    pub.verifySignedByMe(worldSigned);
  }, crypto.CryptoError);

  pvt.setPrivateKeyFromPassword('password1');
  var worldEncWithPwd = pvt.encryptForMe('world');
  var pvt2 = new crypto.CrappyPrivateKey();
  pvt2.setPrivateKeyFromPassword('password1');
  assert ('world' == pvt2.decryptMessage(worldEncWithPwd));
  pvt2.setPrivateKeyFromPassword('badpwd');
  assert.throws( function () {
    pvt2.decryptMessage(worldEncWithPwd);
  }, crypto.CryptoError);
}

var tests = [testCrappyCrypto];
for (var i = 0; i < tests.length; i++) {
  tests[i]();
  process.stdout.write('.');
}
console.log('SUCCESS');
