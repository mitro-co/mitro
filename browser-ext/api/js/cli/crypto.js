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

/*
Friendly Crypto wrapper, implemented using KeyczarJS.

Generating a key:
	var key = crypto.generate();

Converting it to a string:
	var jsonString = key.toJson();

Getting the public key as a string:
	var publicJson = key.exportPublicKey().toJson();

Encrypting the key with a password:
	var encryptedKey = key.toJsonEncrypted(password);

Loading from JSON:
	var key = crypto.loadFromJson(jsonString);
	var key = crypto.loadFromJson(encryptedKey, password);

Encrypting:
	var encrypted = key.encrypt('plaintext');

Decrypting:
	var plaintext = key.decrypt(encrypted);
*/

/** @suppress{duplicate} */
var mitro = mitro || {};
/** @suppress{duplicate} */
var keyczar = keyczar || require('keyczarjs');
(function() {
mitro.crypto = {};
if (typeof module !== 'undefined' && module.exports) {
	// define node.js module
	mitro.cache = require('./lru_cache.js');
	module.exports = mitro.crypto;
}
var crypto = mitro.crypto;

/** Only for type checking. TODO: Use this? Make it an interface?
@constructor
@struct
*/
mitro.crypto.Key = function() {};
/**
@param {Object} otherKey
@return {string}
*/
mitro.crypto.Key.prototype.encryptWith = function(otherKey) {};
/**
@param {string} message
@return {string}
*/
mitro.crypto.Key.prototype.sign = function(message) {};

/** Define a custom error type that "inherits" from Error
@constructor
@extends Error
@param {string} message
*/
function CryptoError(message) {
	// get the stack and transfer it to this
	var err = new Error();
	err.name = 'CryptoError';
	err.message = message;
	this.stack = err.stack;

	this.message = message;
}
CryptoError.prototype = new Error();


// cache the result of _makeKey for a number of keys
var cryptoKeyCache = new mitro.cache.LRUCache(2048);

// Keyczar uses massive 4096 RSA keys by default, which are super slow to
// generate. To make things less painful, we'll use 2048-bit keys
var RSA_KEY_BITS = 2048;

// Returns a new private key
function generate() {
	var options = {size: RSA_KEY_BITS};
	var encryptionKey = keyczar.create(
			keyczar.TYPE_RSA_PRIVATE, keyczar.PURPOSE_DECRYPT_ENCRYPT, options);
	var signingKey = keyczar.create(
			keyczar.TYPE_RSA_PRIVATE, keyczar.PURPOSE_SIGN_VERIFY, options);
	return _makeKey(encryptionKey, signingKey);
}

/**
@param {string} jsonString
@param {string=} password
@return {!Object} TODO: Make a Mitro key type
*/
function loadFromJson(jsonString, password) {
	var obj = JSON.parse(jsonString);
	try {
		var encryptionKey = keyczar.fromJson(obj.encryption, password);
		var signingKey = keyczar.fromJson(obj.signing, password);
		return _makeKey(encryptionKey, signingKey);
	} catch (err) {
		// use String(err) because it works with any object
		if (String(err).indexOf('decryption failed') !== -1) {
			// most likely cause: show this to end users
			err.userVisibleError = 'Password incorrect';
		}
		throw err;
	}
}

function decryptWith(json, otherKey) {
	var str = otherKey.decrypt(json);
	return loadFromJson(str);
}

// converts a pair of keyczar keys into a single Mitro key object.
function _makeKeyNoMemo(encryptionKey, signingKey) {

	var cache = new mitro.cache.LRUCache(1024);
	// TODO: Add a signing key
	var key = {
		encryption: encryptionKey,
		signing: signingKey
	};

	key.encryptNoMemo = function(plaintext) {
		return keyczar.encryptWithSession(encryptionKey, plaintext);
	};

	key.encrypt = function(plaintext) {
		var memoKey = mitro.cache.makeKey('encrypt', plaintext);
		var memoResult = cache.getItem(memoKey);
		if (!memoResult) {
			memoResult = key.encryptNoMemo(plaintext);
			cache.setItem(memoKey, memoResult);
		}
		return memoResult;
	};
	
	key.verify = function(message, signature) {
		return signingKey.verify(message, signature);
	};

	key.toJson = function() {
		var obj = {
			encryption: encryptionKey.toJson(),
			signing: signingKey.toJson()
		};
		return JSON.stringify(obj);
	};

	key.toJsonEncrypted = function(password) {
		var obj = {
			encryption: encryptionKey.toJsonEncrypted(password),
			signing: signingKey.toJsonEncrypted(password)
		};

		return JSON.stringify(obj);
	};
  key.encryptWith = function(otherKey) {
		if (!encryptionKey.metadata.encrypted) {
			return otherKey.encrypt(key.toJson());
		}
		var obj = {
			encryption: encryptionKey.exportDecryptedJson(),
			signing: signingKey.exportDecryptedJson()
		};
		return otherKey.encrypt(JSON.stringify(obj));
  };

	// Add private key routines
	if (encryptionKey.metadata.type == keyczar.TYPE_RSA_PRIVATE) {
		key.decryptNoMemo = function(message) {
			return keyczar.decryptWithSession(encryptionKey, message);
		};

		key.decrypt = function(message) {
			var memoKey = mitro.cache.makeKey('decrypt', message);
			var memoResult = cache.getItem(memoKey);
			if (memoResult) {
				
			} else {
				memoResult = key.decryptNoMemo(message);
				cache.setItem(memoKey, memoResult);
			}
			return memoResult;
		};

		key.sign = function(message) {
			return signingKey.sign(message);
		};

		key.exportPublicKey = function() {
			var publicEncryption = encryptionKey.exportPublicKey();
			var publicSigning = signingKey.exportPublicKey();
			return _makeKey(publicEncryption, publicSigning);
		};
	} else if (encryptionKey.metadata.type == keyczar.TYPE_RSA_PUBLIC) {
		// do nothing?
	} else {
		throw new CryptoError('Unexpected key type: ' + encryptionKey.metadata.type);
	}

	return key;
}
function _makeKey(encryptionKey, signingKey) {
	var _toJson = function(k) {
		try {
			// TODO: this is really stupid. we should be able to ask the key if it's encrypted or not.
			return k.toJson();
		} catch(e) {
			return k.toJsonEncrypted('PASSWORD');
		}
	};
	var memoKey = mitro.cache.makeKey('makeKey', _toJson(encryptionKey), _toJson(signingKey));
	var memoResult = cryptoKeyCache.getItem(memoKey);
	if (!memoResult) {
		memoResult = _makeKeyNoMemo(encryptionKey, signingKey);
		cryptoKeyCache.setItem(memoKey, memoResult);
	} else {
		//console.log('key recycled from cache!')
	}
	return memoResult;
}

crypto.CryptoError = CryptoError;
crypto.generate = generate;
crypto.loadFromJson = loadFromJson;
crypto.decryptWith = decryptWith;
// end module
})();
