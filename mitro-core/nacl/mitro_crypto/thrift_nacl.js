(function () {
  'use strict';

  var NaClModule = function (element) {
    this.element = element;
    this.messageMap = {}; 
    this.nextMessageId = 1;

    element.addEventListener('message', this.handleMessage.bind(this), true);
  };

  // The 'message' event handler.  This handler is fired when the NaCl module
  // posts a message to the browser by calling PPB_Messaging.PostMessage()
  // (in C) or pp::Instance.PostMessage() (in C++).
  NaClModule.prototype.handleMessage = function (message) {
    var response = message.data;
    //console.log('handleMessage: ' + response.id);

    if (response.id in this.messageMap) {
      var callbacks = this.messageMap[response.id];
      delete this.messageMap[message.id];

      if (callbacks) {
        if (response.data && callbacks.onSuccess) {
          callbacks.onSuccess(response.data); 
        } else if (callbacks.onError) {
          callbacks.onError(response.error);
        }
      }
    } else {
      throw new Error('Received message with unknown id: ' + response.id);
    }
  };

  NaClModule.prototype.postMessage = function (type, data, onSuccess, onError) {
    var message = {
        id: this.nextMessageId.toString(),
        type: type,
        data: data
    };

    if (message.id in this.messageMap) {
      throw new Error('Duplicate message id: ' + message.id);
    }

    this.messageMap[message.id] = {
        onSuccess: onSuccess,
        onError: onError
    };
    this.nextMessageId++;

    this.element.postMessage(message);
  };

  var createNaClElement = function (id, manifestPath) {
    var naclElement = document.createElement('embed');
    naclElement.setAttribute('id', id);
    naclElement.setAttribute('type', 'application/x-pnacl');
    naclElement.setAttribute('width', 0);
    naclElement.setAttribute('height', 0);
    naclElement.setAttribute('src', manifestPath);
    return naclElement;
  };

  window.createNaclElement = createNaClElement;
  window.NaClModule = NaClModule;
})();
