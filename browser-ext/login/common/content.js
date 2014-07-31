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

// Must be in manifest "web_accessible_resources" to redirect from a content script
var HOME_PATH = 'html/popup.html';
var ISSUE_PATH = 'html/issue.html';
var SERVICES_PATH = 'html/secrets.html';
var ADMIN_SYNC_PATH = 'html/admin-sync.html';
var ADMIN_ORG_PATH = 'html/admin-dashboard.html';

var IS_TOP_FRAME = (window.top === window);
var FRAME_ID = IS_TOP_FRAME ? '' : randomString(20);



    var openLinkInTab = function(url) {
        helper.createTab(url);
    };

    var VISIBILITY_TIMER_INTERVAL = 500;
    var tryingToLogIn = false;
    var popupInitiatedLoginInfo = null;
    var cs = new ContentScript();
    client.initRemoteCalls('background',['generatePassword']);
    client.addListener('background', _onMessageFromBackground);

    var infobar = null;

    var isInfobarShown = function () {
        return infobar !== null;
    };

    var showMitroLoginWarningInfobar = function () {
        if (isInfobarShown()) {
            return;
        }

        var message = 'Warning: this password will not be saved because you are not logged in';

        var loginAction = function () {
            openLinkInTab(helper.getURL(HOME_PATH));
        };
        var closeAction = function () {
            infobar = null;
        };

        var buttons = [{text: 'Log in to Mitro', action: loginAction}];

        infobar = displayInfobar(message, [], buttons, closeAction);
    };

    var openIssueWithHash = function(urlForHash) {
        var hash = urlForHash;
        // for some reason, %escape codes mess up firefox
        hash = btoa(hash);
        var issueUrl = helper.getURL(ISSUE_PATH) + '#' + hash;
        openLinkInTab(issueUrl);
    };



    var showReplacePasswordInfobar = function (formData, username, orgInfo, replacedSecretData) {
        if (isInfobarShown()) {
            return;
        }
        var message = 'Do you want Mitro to replace your password for \'' + username + '\'?';
        var replaceAction = function (selection) {
            formData.secretId = replacedSecretData.secretId;
            if (isNaN(formData.orgId)) {
                formData.orgId = null;
            }
            cs.sendMessageToBackground('replaceServiceAccepted', formData);
        };
        var blacklistAction = function () {
            cs.sendMessageToBackground('saveServiceBlacklisted', formData);
        };
        var closeAction = function() {
            cs.sendMessageToBackground('saveServiceRejected', formData);
            infobar = null;
        };
        // previously used with: {text: 'Report an issue', action: reportIssueAction}
        // var reportIssueAction = function () {
        //     openIssueWithHash(formData.before_page);
        // };

        var buttons = [{text: 'Replace', action: replaceAction},
                       {text: 'Never for this site', action: blacklistAction}];

        infobar = displayInfobar(message, [], buttons, closeAction);
    };


    var showSavePasswordInfobar = function (formData, username, orgInfo, replacedSecretData) {
        if (isInfobarShown()) {
            return;
        }
        var message = 'Do you want Mitro to save your password for \'' + username + '\'?';

        var saveAction = function (selection) {
            formData.orgId = parseInt(selection ? selection.value : null, 10);
            if (isNaN(formData.orgId)) {
                formData.orgId = null;
            }
            cs.sendMessageToBackground('saveServiceAccepted', formData);
        };
        var saveAndShareAction = function(selection) {
            formData.showShareDialog = true;
            return saveAction(selection);
        };

        var blacklistAction = function () {
            cs.sendMessageToBackground('saveServiceBlacklisted', formData);
        };
        var closeAction = function() {
            cs.sendMessageToBackground('saveServiceRejected', formData);
            infobar = null;
        };

        var selectOptions = [];
        // Allow user to save secret to an org.
        if (orgInfo && orgInfo.organizations) {
            selectOptions.push({
                text: 'for Me',
                value: null
            });

            for (orgId in orgInfo.organizations) {
                var org = orgInfo.organizations[orgId];
                selectOptions.push({
                    text: 'for ' + org.name,
                    value: org.id
                });
            }
        }

        var buttons = [{text: 'Save', action: saveAction},
                       // disable save and share to make room for orgs dropdown
                       /*{text: 'Save & Share', action:saveAndShareAction},*/
                       {text: 'Never for this site', action: blacklistAction}];

        infobar = displayInfobar(message, selectOptions, buttons, closeAction);
    };


    var showLoginInfobar = function (selectOptions) {
        if (isInfobarShown()) {
            return;
        }

        console.log('login infobar', selectOptions);
        var message = 'Log in with Mitro as:';

        var loginAction = function(selection) {
            console.log('--> loginAccepted', selection.value);
            tryingToLogIn = true;

            // this will result in a callback to the loginAccepted below.
            cs.sendMessageToBackground('loginAccepted', selection);
        };
        var closeAction = function() {
            cs.sendMessageToBackground('loginRejected');
            infobar = null;
        };

        var buttons = [{text: 'Log In', action: loginAction}];
        infobar = displayInfobar(message, selectOptions, buttons, closeAction);
    };
    cs.addBackgroundMessageListener('loginAccepted', function(message) {
        if (message.frameId === FRAME_ID) {
            console.log('attempting to log in due to frame id match.  act:' + FRAME_ID + ", exp:" + message.frameId);
            guessAndFillLoginForm(message.data);
        } else {
            console.log('not attempting to log in due to frame id mismatch. act:' + FRAME_ID + ", exp:" + message.frameId);
        }
    });

    function maybeShowLoginInfobar(serviceInstances) {
        if (popupInitiatedLoginInfo) {
            var rval = guessAndFillLoginForm(popupInitiatedLoginInfo);
            if (rval) {
                popupInitiatedLoginInfo = null;
            }
            return rval;
        }
        if (!serviceInstances) {
            return;
        }


        // load the accounts into the select box
        var selectOptions = [];
        for (var i = 0; i < serviceInstances.length; i++) {
            var instance = serviceInstances[i];
            var type = instance.clientData.type ? instance.clientData.type : 'auto';

            if ((type === 'auto' || type === 'manual') && isLoginPageForService(instance)) {
                selectOptions.push({
                    text: instance.clientData.username,
                    value: instance.secretId,
                    isSelected: instance.mostRecent,
                    frameId: FRAME_ID
                });
            }
        }



        if (selectOptions.length > 0) {

            selectOptions.sort(function(a, b) {
                var getLCaseOptionName = function(instance) {
                    if (instance && instance.text) {
                        return instance.text.toLowerCase();
                    }
                    return '';
                };
                var lOption = getLCaseOptionName(a);
                var rOption = getLCaseOptionName(b);
                if (lOption === rOption) {
                    return 0;
                }
                if (lOption > rOption) {
                    return 1;
                }
                return -1;
            });
            // turn off autocomplete (though this may not do anything since chrome may have already tried to fill it)
            $('input[type="password"]').closest('form').each(function () {
               $(this).attr('autocomplete', 'off');
            });
            // this should only ever be show on the "top" iframe.
            if (IS_TOP_FRAME) {
                showLoginInfobar(selectOptions);
            } else {
                // we need to send a message to the top frame
                cs.sendMessageToBackground('showInfoBarOnTopFrame', selectOptions);
            }
        }
    }

    cs.addBackgroundMessageListener('showMitroLoginWarningInfobar', function (message) {
        if (IS_TOP_FRAME && isLoginPage()) {
            showMitroLoginWarningInfobar();
        }
    });

    cs.addBackgroundMessageListener('showInfoBarOnTopFrame', function(message) {
        if (IS_TOP_FRAME) {
            console.log('should show infobar cause I am the top frame');
            showLoginInfobar(message.data);
        } else {
            console.log('ignoring infobar message because I am not top frame');
        }
    });

    cs.addBackgroundMessageListener('showSaveServiceDialog', function (message) {
        console.log('CONTENT got show service dialog');

        if (IS_TOP_FRAME) {
            try {
                console.log('showSaveServiceDialog');
                var data = JSON.parse(message.data.recordedData);
                assert(data.usernameField);
                $(document).ready(function() {
                    try {
                        var func = (message.data.replacedSecretData ? showReplacePasswordInfobar : showSavePasswordInfobar);
                        func(data, data.usernameField.value,
                            message.data.orgInfo,
                            message.data.replacedSecretData);

                    }  catch (e) {
                        console.log('Error:', e.message);
                    }
                });
            }  catch (e) {
                console.log('Error: ', e.message);
            }

        } else {
            console.log('ignoring save password infobar request for frame:', FRAME_ID);
        }
    });


    //TODO: deal with this
    cs.addBackgroundMessageListener('refreshOnMitroLogin', function (event) {
        console.log('CONTENT::  refreshOnMitroLogin');
        if (isInfobarShown()) {
            infobar.close(function () {
                setServiceInstancesAndShowInfobar(event.data.services);
            });
            infobar = null;
        } else {
          setServiceInstancesAndShowInfobar(event.data.services);
        }
    });

    cs.addBackgroundMessageListener('copySelection', function(message) {
        var text = window.getSelection().toString();
        if (text) {
            helper.background.addSecretFromSelection(message.data.tabUrl, text);
            console.log('Successfully copied the text. Sending back to background.');
        } else {
            console.log('The selection is empty. Must be wrong frame. Ignoring.');
        }
    });

    var serviceInstances = null;

    function setServiceInstancesAndShowInfobar(services) {
        serviceInstances = services;
        maybeShowLoginInfobar(services);
    }


    // Handler for response from background page.
    // Response may request an auto-login or show a login infobar.
    cs.onInitResponse = function (message) {
        var formData = message.data;
        if (message.data.frameId !== FRAME_ID) {
            console.log('got message for different frame; ignoring. target:' + FRAME_ID + ' me:' + FRAME_ID);
            return;
        }
        console.log('onInitResponse');
        $(function() {
            onBackgroundFormHintsForDomain(formData.serverHints);
            if (formData.login) {
                tryingToLogIn = true;
                popupInitiatedLoginInfo = formData.login;
                assert(popupInitiatedLoginInfo);
                maybeShowLoginInfobar(null);
            } else if (formData.services) {
                setServiceInstancesAndShowInfobar(formData.services);
            }
        });
    };

    // Listen for initial messages only on mitro.co and localhost
    // the regexp matches mitro.co, foo.mitro.co, but not foomitro.co or mitro.com
    if (window.location.hostname.match(/(^|\.)mitro\.co$/) ||
            (debugMode && (window.location.hostname === 'localhost'))) {
        cs.activatePageMessages(client);

        // Convert the 'get started' button into a 'sign in' button.
        $(function() {
            if (window.location.hostname.match(/(^|\.)mitro\.co$/) ||
                    (debugMode && (window.location.hostname === 'localhost'))) {
                var $signinButton = $('#signin-button');
                if ($signinButton.length > 0) {
                    $signinButton.text('sign in');
                    $signinButton.attr('href', '#');
                    $signinButton.click(function() {
                        console.log('trying to redirect.....');
                        helper.redirectTo(helper.getURL(HOME_PATH));
                    });
                    
                }
            }
        });
        
        if (window.location.hostname.match(/(^|\.)mitro\.co$/)) {
            // TODO: Remove /static/html version once deployed
            var REMOTE_SERVICES_PATHS = {'/extension_services.html': true};
            var REMOTE_ADMIN_SYNC_PATH = '/extension_admin_sync.html';
            var REMOTE_ORG_PATH = '/extension_organizations.html';
            var path = window.location.pathname;
        
            if (isInstallPage(window.location.href)) {
                var redirectUrl = getInstallRedirectUrl(window.location.href);
                helper.redirectTo(redirectUrl);
            }

            if ((path in REMOTE_SERVICES_PATHS) || (window.location.href.match('^https://www[.]mitro[.]co/$'))) {
                helper.redirectTo(helper.getURL(SERVICES_PATH));
            }
            
            if (path === REMOTE_ADMIN_SYNC_PATH) {
                helper.redirectTo(helper.getURL(ADMIN_SYNC_PATH));
            }

            if (path === REMOTE_ORG_PATH) {
                helper.redirectTo(helper.getURL(ADMIN_ORG_PATH));
            }
        }
    } else {
        // We might need to fill a form on this page! Ask the background page.
        // If it has it, it will respond with the form data
        cs.addBackgroundMessageListener('init', cs.onInitResponse);
        cs.sendMessageToBackground('init', {frameId:FRAME_ID, url : window.location.href});
    }

    var submitOverrideScript = [
        // See the document.createEvent reference:
        // https://developer.mozilla.org/en-US/docs/Web/API/document.createEvent?redirectlocale=en-US&redirectslug=DOM%2Fdocument.createEvent
        'HTMLFormElement.prototype._submit_IUORWFJKLFWRUSHEOT = HTMLFormElement.prototype.submit;',
        'HTMLFormElement.prototype.submit = function () {',
        ' try {',
        '  var event = document.createEvent("Event");',
        '  event.initEvent("mitro_submit", true, true);',
        '  document.dispatchEvent(event);',
        ' } catch (e) {',
        '  /* ignore if something fails when injecting event */',
        ' } finally {',
        '  this._submit_IUORWFJKLFWRUSHEOT();',
        ' }',
        '};'
        ].join('\n');

    
    
    var hasBeenInjected = {};
    var injectScriptIntoPage = function (scriptString) {
        if (hasBeenInjected[scriptString]) {
            return;
        }
        hasBeenInjected[scriptString] = true;
        var script = document.createElement('script');
        script.setAttribute("type", "application/javascript");
        script.textContent = scriptString;
        document.head.appendChild(script);
    };

    var onBackgroundFormHintsForDomain = function(serverHints) {
        setServerHints(serverHints);

        var loginForm = null;
        var unBindLoginForm = function () {
            console.log('trying to unbind login form ' + FRAME_ID);
            if (loginForm) {
                if (loginForm.submitField) {
                    loginForm.submitField.pointer.unbind('click', bindToForm);
                }
            }
        };

        var onFormSubmit = function() {

            //alert('hello' + tryingToLogIn);
            if (tryingToLogIn) {
                console.log("got a submission event on a form whilst we're logging in. Ignoring");
                unBindLoginForm();
                // prevent bubbling and default handling of this action.
                return true;
            }
            console.log('form submission');

            try {
                var form = $(this);
                // record the id, may be undefined
                var id = form.attr('id');

                var loginForm = getLoginForm(this);
                if (!loginForm || !loginForm.usernameField || !loginForm.passwordField) {
                    return true;
                }

                // record previous page
                var before =  document.location.toString();
                // record after page
                var after =  form.attr('action');

                helper.preventAutoFill(loginForm.passwordField.pointer, form);

                loginForm.usernameField.pointer = undefined;
                loginForm.passwordField.pointer = undefined;

                if (!loginForm.usernameField.value) {
                    console.log ('no username value, rejecting submit');
                    return true;
                }
                if (!loginForm.passwordField.value) {
                    console.log ('no password value, rejecting submit');
                    return true;
                }
                var message = {
                    form_id: id,
                    usernameField: loginForm.usernameField,
                    passwordField: loginForm.passwordField,
                    before_page: before,
                    after_page: after,
                    title: document.title
                };
                cs.sendMessageToBackground('formSubmit', message);
            } catch(err) {
                console.log('error:', err);
                // Ignore but make sure we always return true
            }
            return true;
        };

        // autocomplete=off on a form element turns off the Chrome password manager
        // save password prompt.
        // we can't do this, unless we import from chrome
    /*    $('input[type="password"]').closest('form').each(function () {
            $(this).attr('autocomplete', 'off');
        });
    */

        $(document).on('submit', 'form', onFormSubmit);
        $(document).on('mitro_submit', null, onFormSubmit);


        var bindToForm = function() {
            maybeShowLoginInfobar(serviceInstances);
            $(document).off('click', 'form', bindToForm);
        };
        
        $(document).on('click', 'form', bindToForm);


        var bindLoginForm = function () {
            console.log('trying to bind login form'+ FRAME_ID);
            unBindLoginForm();
            loginForm = guessLoginForm();

            if (loginForm) {
                injectScriptIntoPage(submitOverrideScript);
                if (loginForm.submitField) {
                    var $submitButton = loginForm.submitField.pointer;
                    var $form = $submitButton.closest('form');
                    // Turn off autocomplete so browser password managers do
                    // not try to save.
                    $form.attr('autocomplete', 'off');

                    $submitButton.bind('click', function () {
                        onFormSubmit.apply($form[0]);
                    });
                }
            }
        };

        bindLoginForm();

        var checkHiddenForms = function () {
            var newHiddenForms = [];

            for (var i = 0; i < hiddenForms.length; i++) {
                var element = hiddenForms[i];
                var elem = element;
                try {
                    elem = element[0];
                } catch (e) {
                    console.log(e);
                }
                if ($(elem).is(':visible') && getLoginForm(elem)) {
                    console.log('hidden login form became visible'+ FRAME_ID);
                    bindLoginForm();
                    maybeShowLoginInfobar(serviceInstances);
                } else {
                    newHiddenForms.push(element);
                }
            }

            hiddenForms = newHiddenForms;

            if (hiddenForms.length === 0) {
                clearInterval(visibilityTimer);
                visibilityTimer = null;
            }
        };

        var maybeStartVisibilityTimer = function () {
            if (hiddenForms.length > 0 && !visibilityTimer) {
                visibilityTimer = setInterval(checkHiddenForms,
                                              VISIBILITY_TIMER_INTERVAL);
            }
        };

        var findHiddenLoginForms = function ($forms) {
            var hiddenLoginForms = [];
            $forms.each(function () {
                var loginForm = getLoginForm(this, false);
                if (loginForm && ($(this).is(':hidden') || !getLoginForm(this, true))) {
                    hiddenLoginForms.push($(this));
                }
            });

            console.log(hiddenLoginForms.length + ' hidden login forms');

            return hiddenLoginForms;
        };

        var hiddenForms = findHiddenLoginForms($('form'));
        var visibilityTimer = null;
        maybeStartVisibilityTimer();

        var lastScan = new Date(0);
        var scheduledFormScan = false;
        var TIME_BETWEEN_SCANS_MS = 1000;
        var INPUT_RE = /^input$/i;  // fastest method: http://jsperf.com/case-insensitive-string-equals
        $(document).bind('DOMNodeInserted', function(event) {
            // WARNING: performance sensitive: Gets called once for every element tree added, so this
            // gets called 10-100s of times per second for some apps (e.g. PivotalTracker).

            // Re-scan entire page if <input> added. Limit: once per event loop, once per second

            // event.target might not be an element (e.g. comment node): check for querySelector
            // tagName is UPPERCASE on HTML docs, but case-sensitive for XHTML
            // regexp matches the element itself, querySelector matches the subtree of the element
            if (!scheduledFormScan && event.target.querySelector &&
                    (INPUT_RE.test(event.target.tagName) ||
                        event.target.querySelector('input') !== null)) {
                scheduledFormScan = true;
                var timeout = 0;
                var now = new Date();
                if (now - lastScan < TIME_BETWEEN_SCANS_MS) {
                    timeout = TIME_BETWEEN_SCANS_MS - (now - lastScan);
                }
                console.log('scheduled form scan timeout:', timeout);

                window.setTimeout(function() {
                    scheduledFormScan = false;
                    lastScan = new Date();

                    bindLoginForm();
                    maybeShowLoginInfobar(serviceInstances);

                    hiddenForms = findHiddenLoginForms($('form'));
                    maybeStartVisibilityTimer();
                }, timeout);
            }
        });
    };

