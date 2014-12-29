(function(){
  'use strict';

  // It seems like nearly all Javascript query string parsers are wrong. This probably is too, but
  // it seems to work for this test case. See: http://unixpapa.com/js/querystring.html
  var decodeQueryString = function (queryString) {
    var output = {};

    // stuff separated by =, separated by &
    var re = /([^=&]+)(=([^&]*))?/g;
    var match;
    while (match = re.exec(queryString)) {
      var key = decodeURIComponent(match[1].replace(/\+/g, ' '));
      // skip parsing keys without values: 'key&k1=v1' -> {"k1": "v1"}
      if (!match[2]) {
        continue;
      }
      // if missing value = empty string: 'k1=&k2=' -> {"k1": "", "k2": ""}
      var value = "";
      if (match[3]) {
        value = decodeURIComponent(match[3].replace(/\+/g, ' '));
      }

      output[key] = value;
    }

    return output;
  };

  // define node.js module for testing
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = decodeQueryString;
  } else {
    // export for Closure compiler
    window['decodeQueryString'] = decodeQueryString;
  }
})();
