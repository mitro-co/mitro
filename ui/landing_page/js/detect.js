// Functions for detecting user's browser and platform.
// Copied from jquery since it was removed in jquery 1.9.
var getBrowser = function () {
  var ua = navigator.userAgent.toLowerCase();

  var match = /(chrome)[ \/]([\w.]+)/.exec( ua ) ||
    /(safari)[ \/]([\w.]+)/.exec( ua ) ||
    /(opera)(?:.*version|)[ \/]([\w.]+)/.exec( ua ) ||
    /(msie) ([\w.]+)/.exec( ua ) ||
    /(firefox)[ \/]([\w.]+)/.exec( ua ) ||
    ua.indexOf("compatible") < 0 && /(mozilla)(?:.*? rv:([\w.]+)|)/.exec( ua ) ||
    [];

  return {
    browser: match[ 1 ] || "",
    version: match[ 2 ] || "0"
  };
};

var getPlatform = function () {
  var ua = navigator.userAgent.toLowerCase();

  var PLATFORMS = ['android', 'iphone', 'ipad', 'windows', 'macintosh', 'linux'];
  for (var i = 0; i < PLATFORMS.length; i++) {
    if (ua.indexOf(PLATFORMS[i]) !== -1) {
      return PLATFORMS[i];
    }
  }
  return 'unknown';
};
