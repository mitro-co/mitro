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

// Original source: https://github.com/Obvious/kew
// License: MIT

// Export all variables in the kew namespace in the browser
var kew = kew || {};
(function(){
/**
 * An object representing a "promise" for a future value
 *
 * @param {?function(T, ?)=} onSuccess a function to handle successful
 *     resolution of this promise
 * @param {?function(!Error, ?)=} onFail a function to handle failed
 *     resolution of this promise
 * @constructor
 * @template T
 */
kew.Promise = function(onSuccess, onFail) {
  this.promise = this;
  this._isPromise = true;
  this._successFn = onSuccess;
  this._failFn = onFail;
  this._scope = this;
  this._boundArgs = null;
  this._hasContext = false;
  this._nextContext = undefined;
  this._currentContext = undefined;
};

/**
 * Specify that the current promise should have a specified context
 * @param  {*} context context
 * @private
 */
kew.Promise.prototype._useContext = function (context) {
  this._nextContext = this._currentContext = context;
  this._hasContext = true;
  return this;
};

kew.Promise.prototype.clearContext = function () {
  this._hasContext = false;
  this._nextContext = undefined;
  return this;
};

/**
 * Set the context for all promise handlers to follow
 *
 * NOTE(dpup): This should be considered deprecated.  It does not do what most
 * people would expect.  The context will be passed as a second argument to all
 * subsequent callbacks.
 *
 * @param {*} context An arbitrary context
 */
kew.Promise.prototype.setContext = function (context) {
  this._nextContext = context;
  this._hasContext = true;
  return this;
};

/**
 * Get the context for a promise
 * @return {*} the context set by setContext
 */
kew.Promise.prototype.getContext = function () {
  return this._nextContext;
};

/**
 * Resolve this promise with a specified value
 *
 * @param {*} data
 */
kew.Promise.prototype.resolve = function (data) {
  if (this._error || this._hasData) throw new Error("Unable to resolve or reject the same promise twice");

  var i;
  if (data && isPromise(data)) {
    this._child = data;
    if (this._promises) {
      for (i = 0; i < this._promises.length; i += 1) {
        data._chainPromise(this._promises[i]);
      }
      delete this._promises;
    }

    if (this._onComplete) {
      for (i = 0; i < this._onComplete.length; i+= 1) {
        data.fin(this._onComplete[i]);
      }
      delete this._onComplete;
    }
  } else if (data && isPromiseLike(data)) {
    data.then(
      function(data) { this.resolve(data); }.bind(this),
      function(err) { this.reject(err); }.bind(this)
    );
  } else {
    this._hasData = true;
    this._data = data;

    if (this._onComplete) {
      for (i = 0; i < this._onComplete.length; i++) {
        this._onComplete[i]();
      }
    }

    if (this._promises) {
      for (i = 0; i < this._promises.length; i += 1) {
        this._promises[i]._withInput(data);
      }
      delete this._promises;
    }
  }
};

/**
 * Reject this promise with an error
 *
 * @param {!Error} e
 */
kew.Promise.prototype.reject = function (e) {
  if (this._error || this._hasData) throw new Error("Unable to resolve or reject the same promise twice");

  var i;
  this._error = e;

  if (this._ended) {
    setTimeout(function onPromiseThrow() {
      throw e;
    }, 0);
  }

  if (this._onComplete) {
    for (i = 0; i < this._onComplete.length; i++) {
      this._onComplete[i]();
    }
  }

  if (this._promises) {
    for (i = 0; i < this._promises.length; i += 1) {
      this._promises[i]._withError(e);
    }
    delete this._promises;
  }
};

/**
 * Provide a callback to be called whenever this promise successfully
 * resolves. Allows for an optional second callback to handle the failure
 * case.
 *
 * @param {?function(this:void, T, ?): RESULT|undefined} onSuccess
 * @param {?function(this:void, !Error, ?): RESULT=} onFail
 * @return {!kew.Promise.<RESULT>} returns a new promise with the output of the onSuccess or
 *     onFail handler
 * @template RESULT
 */
kew.Promise.prototype.then = function (onSuccess, onFail) {
  var promise = new kew.Promise(onSuccess, onFail);
  if (this._nextContext) promise._useContext(this._nextContext);

  if (this._child) this._child._chainPromise(promise);
  else this._chainPromise(promise);

  return promise;
};

/**
 * Provide a callback to be called whenever this promise successfully
 * resolves. The callback will be executed in the context of the provided scope.
 *
 * @param {function(this:SCOPE, T, ?): RESULT} onSuccess
 * @param {SCOPE} scope Object whose context callback will be executed in.
 * @param {...*} var_args Additional arguments to be passed to the promise callback.
 * @return {!kew.Promise.<RESULT>} returns a new promise with the output of the onSuccess
 * @template SCOPE, RESULT
 */
kew.Promise.prototype.thenBound = function (onSuccess, scope, var_args) {
  var promise = new kew.Promise(onSuccess);
  if (this._nextContext) promise._useContext(this._nextContext);

  promise._scope = scope;
  if (arguments.length > 2) {
    promise._boundArgs = Array.prototype.slice.call(arguments, 2);
  }

  // Chaining must happen after setting args and scope since it may fire callback.
  if (this._child) this._child._chainPromise(promise);
  else this._chainPromise(promise);

  return promise;
};

/**
 * Provide a callback to be called whenever this promise is rejected
 *
 * @param {function(this:void, !Error, ?)} onFail
 * @return {!kew.Promise.<T>} returns a new promise with the output of the onFail handler
 */
kew.Promise.prototype.fail = function (onFail) {
  return this.then(null, onFail);
};

/**
 * Provide a callback to be called whenever this promise is rejected.
 * The callback will be executed in the context of the provided scope.
 *
 * @param {function(this:SCOPE, Error, ?)} onFail
 * @param {SCOPE} scope Object whose context callback will be executed in.
 * @param {...?} var_args
 * @return {!kew.Promise.<T>} returns a new promise with the output of the onSuccess
 * @template SCOPE
 */
kew.Promise.prototype.failBound = function (onFail, scope, var_args) {
  var promise = new kew.Promise(null, onFail);
  if (this._nextContext) promise._useContext(this._nextContext);

  promise._scope = scope;
  if (arguments.length > 2) {
    promise._boundArgs = Array.prototype.slice.call(arguments, 2);
  }

  // Chaining must happen after setting args and scope since it may fire callback.
  if (this._child) this._child._chainPromise(promise);
  else this._chainPromise(promise);

  return promise;
};

/**
 * Provide a callback to be called whenever this promise is either resolved
 * or rejected.
 *
 * @param {function()} onComplete
 * @return {!kew.Promise.<T>} returns the current promise
 */
kew.Promise.prototype.fin = function (onComplete) {
  if (this._hasData || this._error) {
    onComplete();
    return this;
  }

  if (this._child) {
    this._child.fin(onComplete);
  } else {
    if (!this._onComplete) this._onComplete = [onComplete];
    else this._onComplete.push(onComplete);
  }

  return this;
};

/**
 * Mark this promise as "ended". If the promise is rejected, this will throw an
 * error in whatever scope it happens to be in
 *
 * @return {!kew.Promise.<T>} returns the current promise
 * @deprecated Prefer done(), because it's consistent with Q.
 */
kew.Promise.prototype.end = function () {
  this._end();
  return this;
};


/**
 * Mark this promise as "ended".
 * @private
 */
kew.Promise.prototype._end = function () {
  if (this._error) {
    throw this._error;
  }
  this._ended = true;
  return this;
};

/**
 * Close the promise. Any errors after this completes will be thrown to the global handler.
 *
 * @param {?function(this:void, T, ?)=} onSuccess a function to handle successful
 *     resolution of this promise
 * @param {?function(this:void, !Error, ?)=} onFailure a function to handle failed
 *     resolution of this promise
 * @return {void}
 */
kew.Promise.prototype.done = function (onSuccess, onFailure) {
  var self = this;
  if (onSuccess || onFailure) {
    self = self.then(onSuccess, onFailure);
  }
  self._end();
};

/**
 * Return a new promise that behaves the same as the current promise except
 * that it will be rejected if the current promise does not get fulfilled
 * after a certain amount of time.
 *
 * @param {number} timeoutMs The timeout threshold in msec
 * @param {string=} timeoutMsg error message
 * @return {!kew.Promise.<T>} a new promise with timeout
 */
 kew.Promise.prototype.timeout = function (timeoutMs, timeoutMsg) {
  var deferred = new kew.Promise();
  var isTimeout = false;

  var timeout = setTimeout(function() {
    deferred.reject(new Error(timeoutMsg || 'Promise timeout after ' + timeoutMs + ' ms.'));
    isTimeout = true;
  }, timeoutMs);

  this.then(function (data) {
    if (!isTimeout) {
      clearTimeout(timeout);
      deferred.resolve(data);
    }
  },
  function (err) {
    if (!isTimeout) {
      clearTimeout(timeout);
      deferred.reject(err);
    }
  });

  return deferred.promise;
};

/**
 * Attempt to resolve this promise with the specified input
 *
 * @param {*} data the input
 */
kew.Promise.prototype._withInput = function (data) {
  if (this._successFn) {
    try {
      this.resolve(this._call(this._successFn, [data, this._currentContext]));
    } catch (e) {
      this.reject(e);
    }
  } else this.resolve(data);

  // context is no longer needed
  delete this._currentContext;
};

/**
 * Attempt to reject this promise with the specified error
 *
 * @param {!Error} e
 * @private
 */
kew.Promise.prototype._withError = function (e) {
  if (this._failFn) {
    try {
      this.resolve(this._call(this._failFn, [e, this._currentContext]));
    } catch (thrown) {
      this.reject(thrown);
    }
  } else this.reject(e);

  // context is no longer needed
  delete this._currentContext;
};

/**
 * Calls a function in the correct scope, and includes bound arguments.
 * @param {Function} fn
 * @param {Array} args
 * @return {*}
 * @private
 */
kew.Promise.prototype._call = function (fn, args) {
  if (this._boundArgs) {
    args = this._boundArgs.concat(args);
  }
  return fn.apply(this._scope, args);
};

/**
 * Chain a promise to the current promise
 *
 * @param {!kew.Promise} promise the promise to chain
 * @private
 */
kew.Promise.prototype._chainPromise = function (promise) {
  var i;
  if (this._hasContext) promise._useContext(this._nextContext);

  if (this._child) {
    this._child._chainPromise(promise);
  } else if (this._hasData) {
    promise._withInput(this._data);
  } else if (this._error) {
    promise._withError(this._error);
  } else if (!this._promises) {
    this._promises = [promise];
  } else {
    this._promises.push(promise);
  }
};

/**
 * Utility function used for creating a node-style resolver
 * for deferreds
 *
 * @param {!kew.Promise} deferred a promise that looks like a deferred
 * @param {Error=} err an optional error
 * @param {*=} data optional data
 */
function resolver(deferred, err, data) {
  if (err) deferred.reject(err);
  else deferred.resolve(data);
}

/**
 * Creates a node-style resolver for a deferred by wrapping
 * resolver()
 *
 * @return {function(?Error, *)} node-style callback
 */
kew.Promise.prototype.makeNodeResolver = function () {
  return resolver.bind(null, this);
};

/**
 * Return true iff the given object is a promise of this library.
 *
 * Because kew's API is slightly different than other promise libraries,
 * it's important that we have a test for its promise type. If you want
 * to test for a more general A+ promise, you should do a cap test for
 * the features you want.
 *
 * @param {*} obj The object to test
 * @return {boolean} Whether the object is a promise
 */
function isPromise(obj) {
  return !!obj._isPromise;
}

/**
 * Return true iff the given object is a promise-like object, e.g. appears to
 * implement Promises/A+ specification
 *
 * @param {*} obj The object to test
 * @return {boolean} Whether the object is a promise-like object
 */
function isPromiseLike(obj) {
  return typeof obj === 'object' && typeof obj.then === 'function';
}

/**
 * Static function which creates and resolves a promise immediately
 *
 * @param {T} data data to resolve the promise with
 * @return {!kew.Promise.<T>}
 * @template T
 */
function resolve(data) {
  var promise = new kew.Promise();
  promise.resolve(data);
  return promise;
}

/**
 * Static function which creates and rejects a promise immediately
 *
 * @param {!Error} e error to reject the promise with
 * @return {!kew.Promise}
 */
function reject(e) {
  var promise = new kew.Promise();
  promise.reject(e);
  return promise;
}

/**
 * Replace an element in an array with a new value. Used by .all() to
 * call from .then()
 *
 * @param {!Array} arr
 * @param {number} idx
 * @param {*} val
 * @return {*} the val that's being injected into the array
 */
function replaceEl(arr, idx, val) {
  arr[idx] = val;
  return val;
}

/**
 * Replace an element in an array as it is resolved with its value.
 * Used by .allSettled().
 *
 * @param {!Array} arr
 * @param {number} idx
 * @param {*} value The value from a resolved promise.
 * @return {*} the data that's being passed in
 */
function replaceElFulfilled(arr, idx, value) {
  arr[idx] = {
    state: 'fulfilled',
    value: value
  };
  return value;
}

/**
 * Replace an element in an array as it is rejected with the reason.
 * Used by .allSettled().
 *
 * @param {!Array} arr
 * @param {number} idx
 * @param {*} reason The reason why the original promise is rejected
 * @return {*} the data that's being passed in
 */
function replaceElRejected(arr, idx, reason) {
  arr[idx] = {
    state: 'rejected',
    reason: reason
  };
  return reason;
}

/**
 * Takes in an array of promises or literals and returns a promise which returns
 * an array of values when all have resolved. If any fail, the promise fails.
 *
 * @param {!Array.<!kew.Promise>} promises
 * @return {!kew.Promise.<!Array>}
 */
kew.all = function(promises) {
  if (arguments.length != 1 || !Array.isArray(promises)) {
    promises = Array.prototype.slice.call(arguments, 0);
  }
  if (!promises.length) return resolve([]);

  var outputs = [];
  var finished = false;
  var promise = new kew.Promise();
  var counter = promises.length;
/*The below functions have been pulled out of the for loop to prevent 
function creation in a loop*/
  var decrement = function decrementAllCounter() {
    counter--;
    if (!finished && counter === 0) {
          finished = true;
          promise.resolve(outputs);
    }
  };

  var anyError = function onAllError(e){
    if (!finished) {
      finished = true;
      promise.reject(e);
    }
  };

  for (var i = 0; i < promises.length; i += 1) {
    if (!promises[i] || !isPromiseLike(promises[i])) {
      outputs[i] = promises[i];
      counter -= 1;
    } else {
      promises[i].then(replaceEl.bind(null, outputs, i))
      .then(decrement,anyError);
    }
  }

  if (counter === 0 && !finished) {
    finished = true;
    promise.resolve(outputs);
  }

  return promise;
};

/**
 * Takes in an array of promises or values and returns a promise that is
 * fulfilled with an array of state objects when all have resolved or
 * rejected. If a promise is resolved, its corresponding state object is
 * {state: 'fulfilled', value: Object}; whereas if a promise is rejected, its
 * corresponding state object is {state: 'rejected', reason: Object}.
 *
 * @param {!Array} promises or values
 * @return {!kew.Promise.<!Array>} Promise fulfilled with state objects for each input
 */
function allSettled(promises) {
  if (!Array.isArray(promises)) {
    throw Error('The input to "allSettled()" should be an array of Promise or values');
  }
  if (!promises.length) return resolve([]);

  var outputs = [];
  var promise = new kew.Promise();
  var counter = promises.length;
  var resolution = function(){
    if ((--counter) === 0) promise.resolve(outputs);
  };

  for (var i = 0; i < promises.length; i += 1) {
    if (!promises[i] || !isPromiseLike(promises[i])) {
      replaceElFulfilled(outputs, i, promises[i]);
      if ((--counter) === 0) promise.resolve(outputs);
    } else {
      promises[i]
        .then(replaceElFulfilled.bind(null, outputs, i), replaceElRejected.bind(null, outputs, i))
        .then(resolution);
    }
  }

  return promise;
}

/**
 * Create a new Promise which looks like a deferred
 *
 * @return {!kew.Promise}
 */
kew.defer = function() {
  return new kew.Promise();
};

/**
 * Return a promise which will wait a specified number of ms to resolve
 *
 * @param {number} delayMs
 * @param {*} returnVal
 * @return {!kew.Promise} returns returnVal
 */
function delay(delayMs, returnVal) {
  var defer = new kew.Promise();
  setTimeout(function onDelay() {
    defer.resolve(returnVal);
  }, delayMs);
  return defer;
}

/**
 * Return a promise which will evaluate the function fn in a future turn with
 * the provided args
 *
 * @param {function(...)} fn
 * @param {...*} var_args a variable number of arguments
 * @return {!kew.Promise}
 */
function fcall(fn, var_args) {
  var rootArgs = Array.prototype.slice.call(arguments, 1);
  var defer = new kew.Promise();
  setTimeout(function onNextTick() {
    defer.resolve(fn.apply(undefined, rootArgs));
  }, 0);
  return defer;
}


/**
 * Returns a promise that will be invoked with the result of a node style
 * callback. All args to fn should be given except for the final callback arg
 *
 * @param {function(...)} fn
 * @param {...*} var_args a variable number of arguments
 * @return {!kew.Promise}
 */
function nfcall(fn, var_args) {
  // Insert an undefined argument for scope and let bindPromise() do the work.
  var args = Array.prototype.slice.call(arguments, 0);
  args.splice(1, 0, undefined);
  return bindPromise.apply(undefined, args)();
}


/**
 * Binds a function to a scope with an optional number of curried arguments. Attaches
 * a node style callback as the last argument and returns a promise
 *
 * @param {function(...)} fn
 * @param {Object} scope
 * @param {...*} var_args a variable number of arguments
 * @return {function(...)}: !kew.Promise}
 */
function bindPromise(fn, scope, var_args) {
  var rootArgs = Array.prototype.slice.call(arguments, 2);
  return function onBoundPromise(var_args) {
    var defer = new kew.Promise();
    fn.apply(scope, rootArgs.concat(Array.prototype.slice.call(arguments, 0), defer.makeNodeResolver()));
    return defer;
  };
}

// If we are on node, export kew as module.exports
if (typeof module !== 'undefined') {
  module.exports = kew;
}
})();
