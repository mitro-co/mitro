/** @const */
var exports = {};

/** @const */
var module = {};
/** @const */
module.externs = null;

/**
 * Suppresses compiler warning for multiple Hogan template files
 * @suppress {duplicate}
 * @noalias
 * @dict
 * TODO: Declare the type correctly: type {Object.<string, !Hogan.Template>}
 * @type {Object}
 */
var templates;

/**
@param {string} moduleName
@return {!Object}
*/
var require = function(moduleName) {};


/** Firefox extensions must use unsafeWindow.worker
@const */
var unsafeWindow = {};
/**
@const
@constructor
@return {!WebWorker}
*/
unsafeWindow.Worker = function(){};


/** From Node. Required by some of our scripts
@const */
var process = {};
/** @type {Object.<string, string>} */
process.env = {};
