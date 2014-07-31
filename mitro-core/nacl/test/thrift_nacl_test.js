(function() {
  'use strict';

  var MitroCryptoModule = null;
  var nextMessageId = 1;
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

  // The 'message' event handler.  This handler is fired when the NaCl module
  // posts a message to the browser by calling PPB_Messaging.PostMessage()
  // (in C) or pp::Instance.PostMessage() (in C++).  This implementation
  // simply displays the content of the message in an alert panel.
  function handleMessage(message_event) {
    console.log(message_event.data);
    var output = document.getElementById('output');
    output.textContent = output.textContent + message_event.data;
  }

  // Indicate load success.
  function moduleDidLoad() {
    MitroCryptoModule = document.getElementById('nacl-module');
    updateStatus('LOADED');
  }

  function createNaclElement() {
    var naclElement = document.createElement('embed');
    naclElement.setAttribute('id', 'nacl-module');
    naclElement.setAttribute('type', 'application/x-pnacl');
    naclElement.setAttribute('width', 0);
    naclElement.setAttribute('height', 0);
    naclElement.setAttribute('src', 'pnacl/Release/thrift_nacl_test.nmf');
    naclElement.addEventListener('load', moduleDidLoad, true);
    naclElement.addEventListener('message', handleMessage, true);
    return naclElement;
  }

  // If the page loads before the Native Client module loads, then set the
  // status message indicating that the module is still loading.  Otherwise,
  // do not change the status message.
  var onLoad = function () {
    var naclElement = createNaclElement();
    document.body.appendChild(naclElement);

    if (MitroCryptoModule === null) {
      updateStatus('LOADING...');
    } else {
      // It's possible that the Native Client module onload event fired
      // before the page's onload event.  In this case, the status message
      // will reflect 'SUCCESS', but won't be displayed.  This call will
      // display the current message.
      updateStatus();
    }
  };

  window.onLoad = onLoad;
})();
