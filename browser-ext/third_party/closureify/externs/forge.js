/*
Closure compiler externs for Forge:
https://github.com/digitalbazaar/forge
*/

var forge = {};

/**
@constructor
@struct
*/
forge.RSAPublicKey = function() {
  this.n = new forge.jsbn.BigInteger('0',10);
  this.e = new forge.jsbn.BigInteger('0',10);
};
/**
@param {string} data
@param {string} scheme
@param {Object=} schemeOptions
@return {string}
*/
forge.RSAPublicKey.prototype.encrypt = function(data, scheme, schemeOptions) {};
/**
@param {string} digest
@param {string} signature
@param {string=} scheme
@return {boolean}
*/
forge.RSAPublicKey.prototype.verify = function(digest, signature, scheme) {};

/**
@constructor
@struct
*/
forge.RSAPrivateKey = function() {
  this.n = new forge.jsbn.BigInteger('0',10);
  this.e = new forge.jsbn.BigInteger('0',10);
  this.d = new forge.jsbn.BigInteger('0',10);
  this.p = new forge.jsbn.BigInteger('0',10);
  this.q = new forge.jsbn.BigInteger('0',10);
  this.dP = new forge.jsbn.BigInteger('0',10);
  this.dQ = new forge.jsbn.BigInteger('0',10);
  this.qInv = new forge.jsbn.BigInteger('0',10);
};
/**
@param {string} md
@param {string=} scheme
@return {string}
*/
forge.RSAPrivateKey.prototype.sign = function(md, scheme) {};
/**
@param {string} data
@param {string} scheme
@param {Object=} schemeOptions
@return {string}
*/
forge.RSAPrivateKey.prototype.decrypt = function(data, scheme, schemeOptions) {};

/**
@constructor
@struct
*/
forge.Cipher = function() {
  this.output = new forge.util.ByteBuffer('');
};
/** @param {!forge.util.ByteBuffer} input */
forge.Cipher.prototype.update = function(input) {};
/**
@param {function(number, !forge.util.ByteBuffer, boolean)=} pad
*/
forge.Cipher.prototype.finish = function(pad) {};

/**
@constructor
@struct
*/
forge.HMAC = function() {};
/**
@param {string|forge.HashFunction} md
@param {?string} key
*/
forge.HMAC.prototype.start = function(md, key) {};
/** @param {string} bytes */
forge.HMAC.prototype.update = function(bytes) {};
/** @return {!forge.util.ByteBuffer} */
forge.HMAC.prototype.getMac = function() {};

forge.pki = {};
/**
@param {!forge.jsbn.BigInteger} modulus
@param {!forge.jsbn.BigInteger} exponent
@return {!forge.RSAPublicKey}
*/
forge.pki.setRsaPublicKey = function(modulus, exponent) {};
/**
@param {!forge.jsbn.BigInteger} n
@param {!forge.jsbn.BigInteger} e
@param {!forge.jsbn.BigInteger} d
@param {!forge.jsbn.BigInteger} p
@param {!forge.jsbn.BigInteger} q
@param {!forge.jsbn.BigInteger} dP
@param {!forge.jsbn.BigInteger} dQ
@param {!forge.jsbn.BigInteger} qInv
@return {!forge.RSAPrivateKey}
*/
forge.pki.setRsaPrivateKey = function(n, e, d, p, q, dP, dQ, qInv) {};
/**
@param {string} pem
@return {!forge.RSAPublicKey}
*/
forge.pki.publicKeyFromPem = function(pem) {};
/**
@param {string} pem
@return {!forge.RSAPrivateKey}
*/
forge.pki.privateKeyFromPem = function(pem) {};
/** @const */
forge.pki.rsa = {};
/**
@return {{keys: {privateKey}}} RSA key generation state
*/
forge.pki.rsa.createKeyPairGenerationState = function(n) {};
forge.pki.rsa.stepKeyPairGenerationState = function(generator, n) {};

/** @const */
forge.pkcs5 = {};
/**
@param {string} password
@param {string} salt
@param {number} iterations
@param {number} dkLen
@param {forge.HashFunction} md
@return {string}
*/
forge.pkcs5.pbkdf2 = function(password, salt, iterations, dkLen, md) {};

/** @const */
forge.aes = {};
/**
@param {string} key
@param {string=} mode
@return {!forge.Cipher}
*/
forge.aes.createEncryptionCipher = function(key, mode) {};
/**
@param {string} key
@param {string} iv
@param {forge.util.ByteBuffer} output
@param {string=} mode
@return {!forge.Cipher}
*/
forge.aes.startEncrypting = function(key, iv, output, mode) {};
/**
@param {string} key
@param {string} iv
@param {forge.util.ByteBuffer} output
@param {string=} mode
@return {!forge.Cipher}
*/
forge.aes.startDecrypting = function(key, iv, output, mode) {};

/** @const */
forge.hmac = {};
/**
@return {!forge.HMAC}
*/
forge.hmac.create = function() {};


/** @const */
forge.jsbn = {};
/**
@constructor
@struct
@param {string} value
@param {number} base
*/
forge.jsbn.BigInteger = function(value, base) {};
/** @return {number} */
forge.jsbn.BigInteger.prototype.bitLength = function() {};


/** @const */
forge.util = {};
/** @constructor @struct 
@param {string} b
*/
forge.util.ByteBuffer = function(b) {};
/** @param {number=} count
@return {string} */
forge.util.ByteBuffer.prototype.getBytes = function(count) {};
/** @return {string} */
forge.util.ByteBuffer.prototype.toHex = function() {};
/**
@param {string} bytes 
@return {string}
*/
forge.util.bytesToHex = function(bytes) {};
/**
@param {string} hex 
@return {string}
*/
forge.util.hexToBytes = function(hex) {};
/**
@param {string} input
@return {string}
*/
forge.util.decode64 = function(input) {};
/**
@param {string} input
@return {string}
*/
forge.util.encode64 = function(input) {};
/**
@param {string} input
@param {string=} encoding
@return {!forge.util.ByteBuffer}
*/
forge.util.createBuffer = function(input, encoding) {};
/**
@param {string} str
@return {string}
*/
forge.util.decodeUtf8 = function(str) {};
/**
@param {string} str
@return {string}
*/
forge.util.encodeUtf8 = function(str) {};

/** @const */
forge.random = {};
/**
@param {number} n
@param {function(string)=} callback
@return {string}
*/
forge.random.getBytes = function(n, callback) {};
/**
@param {number} n
@return {string}
*/
forge.random.getBytesSync = function(n) {};
/**
@param {number} needed
@return {string}
*/
forge.random.seedFileSync = function(needed) {};

/** @constructor @struct */
forge.HashFunction = function() {
  this.digestLength = 0;
};
/** @return {!forge.util.ByteBuffer} */
forge.HashFunction.prototype.digest = function() {};
/** @param {string} msg
@param {string=} encoding
@return {!forge.HashFunction}
*/
forge.HashFunction.prototype.update = function(msg, encoding) {};

/** @const */
forge.md = {};
/** @const */
forge.md.sha1 = {};
/**
@return {!forge.HashFunction}
*/
forge.md.sha1.create = function() {};
