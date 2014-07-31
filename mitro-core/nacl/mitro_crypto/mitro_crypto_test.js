(function() {
  'use strict';

  var module = null;
  var isLoaded = false;
  var statusText = 'NO-STATUS';

  // Set the global status message.  If the element with id 'statusField'
  // exists, then set its HTML to the status message as well.
  // opt_message The message test.  If this is null or undefined, then
  // attempt to set the element with id 'statusField' to the value of
  // |statusText|.
  function updateStatus(opt_message) {
    if (opt_message)
      statusText = opt_message;
    var statusField = document.getElementById('statusField');
    if (statusField) {
      statusField.innerHTML = statusText;
    }
  }

  // Indicate load success.
  function moduleDidLoad() {
    var MitroCryptoModule = document.getElementById('mitro-crypto');
    isLoaded = true;
    updateStatus('LOADED');
  }

  // If the page loads before the Native Client module loads, then set the
  // status message indicating that the module is still loading.  Otherwise,
  // do not change the status message.
  var onLoad = function () {
    var naclElement = createNaclElement('mitro-crypto',
                                        'pnacl/Release/mitro_crypto.nmf');
    naclElement.addEventListener('load', moduleDidLoad, true);
    document.body.appendChild(naclElement);

    module = new NaClModule(naclElement);

    updateStatus('LOADING...');

    var runButton = document.getElementById('run-button');
    runButton.addEventListener('click', onRunClicked, true);
  };

  var onFormSubmit = function () {
    if (!isLoaded) {
      alert('NaCl module not loaded');
      return false;
    }

    var passwordElement = document.getElementById('password');
    var data = {
        password: passwordElement.value,
        encrypted_key: ENCRYPTED_TEST_KEY
    };

    var onSuccess = function (response) {
      alert('Result: ' + response.result);  
    };

    var onError = function (error) {
      alert('Error: ' + error.message);
    };

    module.postMessage('loadPrivateKeyFromJson', data, onSuccess, onError);
    return false;
  };

  var isRunning = false;
  var numMessages = 0 ;
  var startTime = null;

  var onRunClicked = function () {
    if (!isLoaded) {
      alert('NaCl module not loaded');
      return false;
    }

    isRunning = !isRunning;
    numMessages = 0;
    startTime = Date.now();

    var updateMessages = function () {
      ++numMessages;
      if (numMessages % 1000 === 0) {
        var elapsedTime = Date.now() - startTime;
        var messagesPerSec = numMessages / elapsedTime;
        console.log(numMessages + ' ' + messagesPerSec);
      }
    }

    var onSuccess = function (response) {
      updateMessages();
      if (isRunning) {
        module.postMessage('testMessage', {return_error: false}, onSuccess, onError);
      }
    };

    var onError = function (error) {
      updateMessages();
      if (isRunning) {
        module.postMessage('testMessage', {return_error: true}, onSuccess, onError);
      }
    };

    if (isRunning) {
      module.postMessage('testMessage', {return_error: true}, onSuccess, onError);
    }
  };

  window.onLoad = onLoad;
  window.onFormSubmit = onFormSubmit;
})();
