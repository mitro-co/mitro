$(function() {
    // See http://stackoverflow.com/questions/105034/how-to-create-a-guid-uuid-in-javascript
    var generateUuid = function () {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
            return v.toString(16);
        });
    };

    // Generate a uuid to track users across signup.
    var uuid = $.cookie('gauuid');
    if (!uuid) {
        var referrer = '';
        try {
          referrer = document.referrer;
          referrer = encodeURIComponent(referrer);
        } catch(e) {
          console.log(e);
        }
        uuid = generateUuid() + '&ref=' + referrer;
        $.cookie('gauuid', uuid, {expires: 10 * 365, path: '/'});

        _gaq.push(['_setCustomVar', 1, 'gauuid', uuid, 1]);
    }
    var glcid = '';
    if (window.location.search) {
        glcid = window.location.search.slice(1) + '&';
    }
    glcid += 'gauuid=' + uuid;

    $.cookie('glcid', glcid, {path: '/'});
});
