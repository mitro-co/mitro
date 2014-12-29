    $(document).ready(function() {
    var EXTENSION_ID = 'iljkkpbfidmehafdbcacnhcaipdgbeij';
    var CHROME_EXTENSION_URL = 'https://chrome.google.com/webstore/detail/' + EXTENSION_ID;
    var SAFARI_EXTENSION_URL = '/safari/downloads/mitro-safari-latest.safariextz';
    var FIREFOX_EXTENSION_URL = '/firefox/downloads/mitro-login-latest.xpi';
    var INSTALL_PATH = '/install.html';
    var INSTALL_CANCEL_PATH = '/why-install.html';

    var browser = getBrowser().browser;
    var platform = getPlatform();

    var invited = false;
    var hash = document.location.hash.slice(1);
    if (hash) {
        var params = decodeQueryString(hash);
        if (params.p) {
            invited = true;
        }
    }

    if (browser === 'chrome') {
        // add <link> tag required for Chrome inline installs
        // https://developers.google.com/chrome/web-store/docs/inline_installation
        var webstoreLink = document.createElement('link');
        webstoreLink.rel = 'chrome-webstore-item';
        webstoreLink.href = CHROME_EXTENSION_URL;
        document.getElementsByTagName("head")[0].appendChild(webstoreLink);
    } else if (browser === 'safari') {
        $('.install-extension').attr('href', SAFARI_EXTENSION_URL);
    } else if (browser === 'firefox') {
        $('.install-extension').attr('href', FIREFOX_EXTENSION_URL);
    } else {
    }

    $('.install-extension').click(function() {
        var openInstallPage = function() {
            window.location = INSTALL_PATH + window.location.hash;
        };

        if (browser === 'chrome') {
            chrome.webstore.install(CHROME_EXTENSION_URL,
                function() {
                  openInstallPage();
                },
                function(e) {
                    window.location = INSTALL_CANCEL_PATH + window.location.hash;
                    console.log(e);
                }
            );
        } else {
            // Delay so that default handler runs that opens the download link.
            setTimeout(openInstallPage, 1000);
        }
    });
});
