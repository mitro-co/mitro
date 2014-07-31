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

var crypto = require('./crypto.js');
var assert = require('assert');

function testCryptoError() {
  assert(new crypto.CryptoError('message') instanceof crypto.CryptoError);
  assert(new crypto.CryptoError('message') instanceof Error);
}

function testKeyczar() {
  // generate a key
  var privateKey = crypto.generate();
  var privateKeyB = crypto.generate();
  var publicKey = privateKey.exportPublicKey();
  var publicKeyB = privateKeyB.exportPublicKey();
  
  // serialize/deserialize the keys
  var publicKeyString = publicKey.toJson();
  var privateKeyString = privateKey.toJson();
  var publicDeserialized = crypto.loadFromJson(publicKeyString);
  var publicDeserialized2 = crypto.loadFromJson(publicKeyString);
  var privateDeserialized = crypto.loadFromJson(privateKeyString);


  var publicKeyBString = publicKeyB.toJson();
  var privateKeyBString = privateKeyB.toJson();
  var publicBDeserialized = crypto.loadFromJson(publicKeyBString);
  var publicGDeserialized2 = crypto.loadFromJson(publicKeyBString);
  var privateBDeserialized = crypto.loadFromJson(privateKeyBString);

  assert (privateBDeserialized != privateDeserialized);

  // use the keys, verifying that serialized versions interoperate
  var MESSAGE = 'plain ASCII message';
  // var MESSAGE = 'Emoji key: \ud83d\udd11';
  var e1 = publicKey.encrypt(MESSAGE);
  assert.equal(MESSAGE, privateDeserialized.decrypt(e1));
  var e2 = publicDeserialized.encrypt(MESSAGE);
  assert.equal(MESSAGE, privateKey.decrypt(e2));

  // verify that encryption uses sessions
  var longMessage = 'this is a long message ';
  while (longMessage.length < 1000) {
    longMessage += longMessage;
  }
  var e = publicKey.encrypt(longMessage);
  assert.equal(longMessage, privateKey.decrypt(e));

  // test encrypting the key
  privateKeyString = privateKey.toJsonEncrypted('hellopass');
  privateDeserialized = crypto.loadFromJson(privateKeyString, 'hellopass');
  assert.equal(MESSAGE, privateDeserialized.decrypt(e2));

  // TODO: Make sure this is CryptoError?
  try {
    crypto.loadFromJson(privateKeyString, 'badpass');
    assert(false, 'expected exception');
  } catch (err) {
    assert(err.userVisibleError === 'Password incorrect');
  }

  // Test signatures
  var signature = privateKey.sign(MESSAGE);
  assert(publicDeserialized.verify(MESSAGE, signature));
  assert(!publicDeserialized.verify(MESSAGE + 'a', signature));
}

var tests = [testCryptoError, testKeyczar];
for (var i = 0; i < tests.length; i++) {
  tests[i]();
  process.stdout.write('.');
}
console.log('SUCCESS');
