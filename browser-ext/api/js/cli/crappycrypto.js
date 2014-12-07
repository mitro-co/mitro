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

// define mitro
var mitro;
(function() {
// define node.js module
if (typeof module !== 'undefined' && module.exports) {
	mitro = {};
	module.exports = mitro.crypto = {};
} else {
	if (typeof mitro === 'undefined') {
		mitro = {};
	}
	mitro.crypto = {};
}
var crypto = mitro.crypto;
mitro.crappycrypto = crypto;

// TODO: This is copied from crypto.js; eliminate this duplication?
// Define a custom error type that "inherits" from Error
function CryptoError(message) {
	// get the stack and transfer it to this
	var err = new Error();
	err.name = 'CryptoError';
	err.message = message;
	this.stack = err.stack;

	this.message = message;
}
CryptoError.prototype = new Error();

// Decent hash function
var hash = function(s){
  return s.split("").reduce(function(a,b){a=((a<<5)-a)+b.charCodeAt(0);return a&a;},0);              
};

var MAX_VALUE = 100;

function CrappyPublicKey(name){
	this.publicKeyValue = 0;
}
CrappyPublicKey.prototype.setKeyFromString= function(key) {
	this.publicKeyValue = parseInt(key);
};
CrappyPublicKey.prototype.encryptForMe= function(message) {
	if (!this.publicKeyValue) {
		throw new Error('publicKeyValue not true?');
	}
	return JSON.stringify({message : message, crypto:(MAX_VALUE-this.publicKeyValue)});
};

CrappyPublicKey.prototype.getPublicKeyString= function() {
	return this.publicKeyValue.toString();
};

CrappyPublicKey.prototype.verifySignedByMe = function(signedMessage) {
	var m = JSON.parse(signedMessage);
	if (m.crypto != - this.publicKeyValue) {
		throw new CryptoError();
	}
	return m.message;
};


CrappyPrivateKey.prototype = new CrappyPublicKey();
CrappyPrivateKey.prototype.constructor=CrappyPrivateKey;
function CrappyPrivateKey() { 
}

CrappyPrivateKey.prototype.getPKValue=function(){ 
	return MAX_VALUE - this.publicKeyValue;
}; 
CrappyPrivateKey.prototype.decryptMessage=function(message) { 
	var m = JSON.parse(message);
	if (m.crypto != this.getPKValue()) {
		throw new CryptoError('Wrong key? Message: ' + m.crypto + ' key: ' + this.getPKValue());
	}
	return m.message;
};

CrappyPrivateKey.prototype.setPrivateKeyFromPassword = function(pwd) {
	this.publicKeyValue = (hash(pwd) % (MAX_VALUE -1)) +1 ;
};

CrappyPrivateKey.prototype.getPrivateKeyString= function() {
	return this.getPublicKeyString();
};

CrappyPrivateKey.prototype.signMessage=function(message){ 
	return JSON.stringify({message : message, crypto:-this.publicKeyValue});
};

CrappyPrivateKey.prototype.generateKey=function(){ 
	this.publicKeyValue = 1 +Math.floor(Math.random() * (MAX_VALUE-1));
};

function generate() {
	var encryption = new CrappyPrivateKey();
	encryption.generateKey();
	return makePrivateKey(encryption);
}

function makePrivateKey(encryption) {
	var keyString = encryption.getPublicKeyString();
	var encryptionPublic = new crypto.CrappyPublicKey();
	encryptionPublic.setKeyFromString(keyString);

	var key = {};
	key.encrypt = function(message) {
		return encryptionPublic.encryptForMe(message);
	};

	key.decrypt = function(message) {
		return encryption.decryptMessage(message);
	};

	key.sign = function(message) {
		var h = hash(message);
		h += encryptionPublic.publicKeyValue;
		return JSON.stringify(h);
	};

	key.exportPublicKey = function() {
		return makePublicKey(encryptionPublic);
	};

	key.toJson = function() {
		return JSON.stringify({type: 'PRV', key: encryption.getPrivateKeyString()});
	};

	key.toJsonEncrypted = function(password) {
		return JSON.stringify({type: 'PAS', key: encryption.getPrivateKeyString(), password: password});
	};

	return key;
}

function makePublicKey(encryptionPublic) {
	var publicKey = {};

	publicKey.encrypt = function(message) {
		return encryptionPublic.encryptForMe(message);
	};

	publicKey.verify = function(message, signature) {
		var h = hash(message) + encryptionPublic.publicKeyValue;
		signature = JSON.parse(signature);
		return h == signature;
	};

	publicKey.toJson = function() {
		return JSON.stringify({type: 'PUB', key: encryptionPublic.getPublicKeyString()});
	};

	return publicKey;
}

function loadFromJson(value, password) {
	value = JSON.parse(value);
	var key;

	if (value.type == 'PRV' || value.type == 'PAS') {
		if (value.type == 'PAS' && value.password != password) {
			throw new CryptoError('Password does not match?');
		}

		key = new CrappyPrivateKey();
		key.setKeyFromString(value.key);
		return makePrivateKey(key);
	} else if (value.type == 'PUB') {
		key = new CrappyPublicKey();
		key.setKeyFromString(value.key);
		return makePublicKey(key);
	} else {
		throw new CryptoError('Bad key?');
	}
}

crypto.CrappyPrivateKey = CrappyPrivateKey;
crypto.CryptoError = CryptoError;
crypto.CrappyPublicKey = CrappyPublicKey;

crypto.GetPublicKey = function() { return new CrappyPublicKey();};
crypto.GetPrivateKey = function() { return new CrappyPrivateKey();};

crypto.generate = generate;
crypto.loadFromJson = loadFromJson;

// end module
})();
