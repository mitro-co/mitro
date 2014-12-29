$(document).ready(function () {
  'use strict';

  var params = decodeQueryString(document.location.search.slice(1));
  if (params.error) {
    $('input[type="submit"]').hide();
    $('.error-message').show();

    $('input[type="text"]').focus(function () {
        $('.error-message').hide();
        $('input[type="submit"]').show();
    });
  }
});
