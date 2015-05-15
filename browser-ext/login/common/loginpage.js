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

// for testing
var __testing__ = {};

(function () {
    'use strict';

    var assert;
    var getCanonicalHost;

    if (typeof(window) === 'undefined') {
        assert = require('assert');
        var domain = require('./domain');
        getCanonicalHost = domain.getCanonicalHost;
    } else {
        assert = window.assert;
        getCanonicalHost = window.getCanonicalHost;
    }

var LARGE_SCORE_VALUE = 1000;

var SERVER_HINTS = {};
var setServerHints = function(hints) {
    SERVER_HINTS = hints;
};

var evaluateServerHintsForEntity = function(type, fieldDict) {
    if (!SERVER_HINTS) {
        return 0;
    }
    var reject = SERVER_HINTS.reject;
    if (!reject) {
        return 0;
    }
    var hintForType = reject[type];
    if (!hintForType) {
        return 0;
    }
    for (var i = 0; i < hintForType.length; ++i) {
        var matched = false;
        for (var j = 0; j < hintForType[i].length; ++j) {
            var attributeName = hintForType[i][j].attributeName;
            // protect against bad data matching all kinds of things
            if (hintForType[i][j].exactMatch) {
                if (fieldDict[attributeName] !== hintForType[i][j].exactMatch) {
                    // if we don't have an exact match, this set is not matching
                    matched = false;
                    break;
                } else {
                    matched = true;
                }
            } else if (hintForType[i][j].regexMatch) {
                if (fieldDict[attributeName] && fieldDict[attributeName].match(hintForType[i][j].regexMatch))  {
                    matched = true;
                } else {
                    matched = false;
                    break;
                }
            }
        }
        if (matched) {
            return -LARGE_SCORE_VALUE;
        }
    }
    return 0;
};

var getMaximumElement = function (elements, scoreFunc) {
    if (elements) {
        var maxScore = -1;
        var maxElement = null;

        for (var i = 0; i < elements.length; i++) {
            var score = scoreFunc(elements[i]);
            //console.log('SCORING:', elements[i], score);
            if (score > maxScore) {
                maxScore = score;
                maxElement = elements[i];
            }
        }
        return maxElement;
    }
    return null;
};

// Score a username input field.  Field type is the most important criteria.
// Having a non-empty value is useful to determine the username when saving a 
// form.
var usernameScoreFunc = function (a, passwordFieldHint) {
    var score = 0;
    if (evaluateServerHintsForEntity('username', a) < 0) {
        console.log('user: rejected ', a, ' due to server hint');
        return -LARGE_SCORE_VALUE;
    }

    if (a.type === 'text' || a.type === 'email') {
        score += 2;
    } else {
        score -= LARGE_SCORE_VALUE;
    }
    if (passwordFieldHint) {
        // as of jquery 1.3.2, items are returned in document order, this
        // distance will tell us the number of matching elements between
        // the two items.
        var distance = passwordFieldHint.itemNo - a.itemNo;
        if (distance > 0) {
            score += 1.0/distance;
        }
    }

    console.log('username field=', a, ' score:',score);
    return score;
};

var guessUsernameField = function (elements, passwordFieldHint) {
    var usernameField = getMaximumElement(elements, function(a) {return usernameScoreFunc(a, passwordFieldHint);});
    // Enforce a minimum threshold to prevent really bad guesses.
    if (usernameField !== null && usernameScoreFunc(usernameField) > 0) {
        assert(usernameField.type !== 'password');
        return usernameField;
    } else {
        return null;
    }
};

// Score a password input field.  Field type is the most important criteria.
// Having a non-empty value is useful to determine the username when saving a 
// form.  Non-password fields are heavily penalized.
var passwordScoreFunc = function (a) {
    if (evaluateServerHintsForEntity('password', a) < 0) {  
        console.log('password: rejected ', a, ' due to server hint');
        return -LARGE_SCORE_VALUE;
    }

    var score = a.value ? 1 : 0;
    var name = a.name ? a.name.toLowerCase(): "";
    if (a.type === 'password') {
        // catch credit card CCV fields
        if (a.maxlength <=4) {
            score -= LARGE_SCORE_VALUE;
        } else if (name.indexOf('creditcard') != -1) {
            score -= LARGE_SCORE_VALUE;
        } else {
            score += 2;
        }
    } else {
        score -= LARGE_SCORE_VALUE;
    }
    console.log('password field=', a, ' score:',score);
    return score;
};

var guessPasswordField = function (elements) {
    var passwordField = getMaximumElement(elements, passwordScoreFunc);

    if (passwordField !== null && passwordScoreFunc(passwordField) > 0) {
        return passwordField;
    } else {
        return null;
    }
};

// we allow image submit buttons for SUBMITTING once we have chosen
// a form, but ignore them for RANKING purposes.
var guessSubmitField = function (elements, allowImageButtons, $preferAfterThisField) {
    var submitScoreFunc = function (a) {
        console.log('begin scoring of ', a);
        if (evaluateServerHintsForEntity('submit', a) < 0) {
            console.log('rejected ', a, ' due to server hint');
            return -LARGE_SCORE_VALUE;
        }
        if (a.type === 'text' || a.type === 'password') {
            return -LARGE_SCORE_VALUE;
        }
        var score = (a.type === 'submit' || a.type === 'button') ? 1 : 0;
        // in some cases (when submitting the form) we want to match image
        // buttons
        score += (a.type === 'image' && allowImageButtons) ? 0.75 : 0;
        score += (a.type === 'a') ? 0.25 : 0;
        var value = a.value ? a.value.toLowerCase() : '';
        if (value.indexOf('forgot') !== -1) {
            score -= LARGE_SCORE_VALUE;
        }
        // prefer exact matches.
        // this helps avoid things like "forgot login"
        if (
            // TODO: internationalize
            (value ==='sign in') ||
            (value ==='log in') ||
            (value ==='log on') ||
            (value ==='submit') ||
            (value ==='login') ||
            (value ==='go')) {
            score += 2;
        } else if (
            (value.indexOf('sign in') != -1) ||
            (value.indexOf('log in') != -1) ||
            (value.indexOf('log on') != -1) ||
            (value.indexOf('submit') != -1) ||
            (value.indexOf('login') != -1) ||
            (value.indexOf('go') != -1)) {
            score += 1;
        }

        if ($preferAfterThisField) {
            var BITMASK = 4; //Node.DOCUMENT_POSITION_FOLLOWING is defined as 4 but not in node.
            var isAfter = $preferAfterThisField[0].compareDocumentPosition(a.pointer[0]) & BITMASK;
            score += (isAfter) ? 1 : -1;
        }
        console.log('submit field field=', a, ' score:',score);

        return score;
    };
    //console.log('>START searching for submit button');
    var submitField = getMaximumElement(elements, submitScoreFunc);
    if (submitField !== null && submitScoreFunc(submitField) > 0) {
        return submitField;
    } else {
        return null;
    }
    //console.log('>END searching for submit button');

};


var createFieldDict = function(fields) {
    var rval = [];

    for (var i = 0; i < fields.length; ++i) {
        var $field = $(fields[i]);

        if ($field.is('button')) {
            rval.push({
                name: $field.attr('name'),
                id: $field.attr('id'),
                'class':$field.attr('class'),
                value: $field.text(),
                type: 'button',
                pointer: $field,
                itemNo : i
            });
        } else if ($field.is('a')) {
            rval.push({
                name: $field.attr('id') || $field.attr('class'),
                id: $field.attr('id'),  
                'class':$field.attr('class'),              
                type: 'a',
                value: $field.text(),
                pointer: $field,
                itemNo : i
            });
        } else {
            rval.push({
                name: $field.attr('name'),
                id: $field.attr('id'),                
                'class':$field.attr('class'),
                value: ($field.attr('type') === 'image') ? $field.attr('alt') : $field.prop('value'),
                type: $field.attr('type') || 'text',
                maxlength: parseInt($field.attr('maxlength'), 10),
                pointer: $field,
                itemNo : i
            });
        }
    }
    return rval;
};

// Returns a login form dict if input form is a login form, or null otherwise.
// Setting requireFieldVisibility only considers visible form fields when 
// looking for username/password/submit fields (default: true).
var getLoginForm = function (form, requireFieldVisibility) {
    console.log('trying to get login form from ', $(form));
    if (typeof requireFieldVisibility === 'undefined') {
        requireFieldVisibility = true;
    }

    var $fields = $(form).find('input[type!=hidden],button[type!=hidden],a');
    if (requireFieldVisibility) {
        $fields = $fields.filter(':visible');
        var $fields2 = $(form).find('input[type!=hidden]:visible,button[type!=hidden]:visible,a:visible');
        // remove fields that are positioned off screen.
        assert($fields.length === $fields2.length);

        $fields = [];
        for (var i = 0; i < $fields2.length; ++i) {
            var elem = $fields2[i];
            var rect = elem.getBoundingClientRect();
            if (// NB: we use bottom instead of top and right instead of left to ensure that
                // we only eliminate those items that are COMPLETELY off the page.
                rect.bottom >= 0 && rect.right >= 0

                // TODO: enabling these will ignore login forms that are not in the viewport,
                // but that's not quite right, because sometimes login forms may 
                // be outside the user's vision.  What should we do about this?

                // && rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) 
                // && rect.right <= (window.innerWidth || document.documentElement.clientWidth)
                ) {
               $fields.push(elem);
            } else {
                console.log('rejected element ', elem, ' because it is outside the viewport');
            }
        }
    }

    if (SERVER_HINTS && SERVER_HINTS.additional_submit_button_ids) {
        for (var i = 0; i < SERVER_HINTS.additional_submit_button_ids.length; ++i) {
            assert (SERVER_HINTS.additional_submit_button_ids[i].indexOf(' ') === -1);
            $fields = $fields.add($('#' + SERVER_HINTS.additional_submit_button_ids[i]));
        }
    }
    var fieldsRecord = createFieldDict($fields);

    var passwordField = guessPasswordField(fieldsRecord);
    var usernameField = null;
    // TODO: for debugging.
    //SERVER_HINTS.empty_password_username_selector = 'input[type=text]#un';
    var goAhead = !!passwordField;
    if (passwordField) {
        usernameField = guessUsernameField(fieldsRecord, passwordField);
    } else if (SERVER_HINTS.empty_password_username_selector) {

        // if there is no password field, and we're provided with a username field selector, offer to fill it up.
        var $un = $(SERVER_HINTS.empty_password_username_selector);
        if ($un) {
            usernameField = createFieldDict($un)[0];
            usernameField.emptyPasswordPermitted = true;
            goAhead = true;
        }
    }

    goAhead = goAhead && (SERVER_HINTS.allow_empty_username || (usernameField !== null));
    if (goAhead) {
        var submitField = guessSubmitField(fieldsRecord,
            true, usernameField && usernameField.pointer);
        var fieldDict = createFieldDict((passwordField?passwordField:usernameField).pointer.closest('form'));
        // we need to find a form.
        if (!fieldDict) {
            return null;
        }
        //createfielddict returns a one-element-list.
        fieldDict = fieldDict[0];


        if (evaluateServerHintsForEntity('form', fieldDict) < 0) {
            console.log('rejected ', fieldDict, ' due to server hint');
            return null;
        }
 
        return {usernameField: usernameField,
                passwordField: passwordField,
                submitField: submitField,
                allFields: fieldsRecord,
                formDict: fieldDict,
                id: form.id};
    } else {
        return null;
    }
};

var LOGIN_BUTTON_LABELS = ['login', 'log in', 'log on', 'signin', 'sign in'];
var SIGNUP_BUTTON_LABELS = ['signup', 'sign up', 'join', 'create', 'register', 'start', 'free', 'trial'];

// Score the submit button as
var loginSubmitButtonScoreFunc = function (field) {
    var submitScore = 0;
    if (evaluateServerHintsForEntity('login_submit', field) < 0) {
        console.log('rejected ', field, ' due to server hint');
        return -LARGE_SCORE_VALUE;
    }

    if (field && field.value) {
        var label = field.value.toLowerCase();
        var i;
        var s;

        for (i = 0; i < LOGIN_BUTTON_LABELS.length; ++i) {
            s = LOGIN_BUTTON_LABELS[i];
            if (label.indexOf(s) !== -1) {
                submitScore += 2;
            }
        }
        for (i = 0; i < SIGNUP_BUTTON_LABELS.length; ++i) {
            s = SIGNUP_BUTTON_LABELS[i];
            if (label.indexOf(s) !== -1) {
                submitScore -= LARGE_SCORE_VALUE;
            }
        }
    }
    console.log('submit button score ', field, ' score ', submitScore);

    return submitScore;
};

var LOGIN_FORM_ID_STRINGS = ['login', 'signin'];
var SIGNUP_FORM_ID_STRINGS = ['signup', 'regist'];

var loginIdScoreFunc = function (formId) {
    var idScore = 0;

    if (formId) {
        var id = formId.toLowerCase();
        var i;

        for (i = 0; i < LOGIN_FORM_ID_STRINGS.length; ++i) {
            var s = LOGIN_FORM_ID_STRINGS[i];
            if (id.indexOf(s) !== -1) {
                idScore += 1;
            }
        }
        // sometimes forms have both signup and signin.  we should not punish those forms.
        if (idScore === 0) {
            for (i = 0; i < SIGNUP_FORM_ID_STRINGS.length; ++i) {
                var s = SIGNUP_FORM_ID_STRINGS[i];
                if (id.indexOf(s) !== -1) {
                    idScore -= LARGE_SCORE_VALUE;
                }
            }
        }
    }

    return idScore;
};

var countFieldsOfType = function (fields, type) {
    var count = 0;
    for (var i = 0; i < fields.length; ++i) {
        if (fields[i].type === type) {
            ++count;
        }
    }
    return count;
};
        
// Finds best match for a login form in the current document, or null if no
// login form is found.
var guessLoginForm = function (hints) {
    var loginFormScoreFunc = function (formDict) {
        // TODO:
        
        if (evaluateServerHintsForEntity('form', formDict.formDict) < 0) {
            console.log('rejected ', formDict.formDict, ' due to server hint');
            return -LARGE_SCORE_VALUE;
        }
        

        if (!formDict) {
            return 0;
        }
        var score = 0;
        // Try to find an exact match to the username/password fields.
        // If a match is not found, take the highest scoring form.
        if (hints &&
            formDict.usernameField &&
            formDict.usernameField.name === hints.clientData.usernameField &&
            formDict.passwordField && 
            formDict.passwordField.name === hints.clientData.passwordField) {
            score += 100;
        }
        if (formDict.usernameField) {
            score += usernameScoreFunc(formDict.usernameField);
        }
        if (!formDict.passwordField && formDict.usernameField.emptyPasswordPermitted) {
            
        } else {
            score += passwordScoreFunc(formDict.passwordField);
        }
        score += loginSubmitButtonScoreFunc(formDict.submitField);
        score += loginIdScoreFunc(formDict.id);
        // Prevents matches for signup and change password forms.
        if (countFieldsOfType(formDict.allFields, 'password') > 1) {
            score -= LARGE_SCORE_VALUE;
        }
        console.log('FORM FOR ', formDict, ' SCORE:', score);
        return score;
    };
    console.log('trying to guess login form');
    var scoringFunction = loginFormScoreFunc;
    var forms = $('form').map(function () {
        console.log('mapping for ', this);
        return getLoginForm(this);
    });
    var loginForm = getMaximumElement(forms, scoringFunction);
    if (loginForm && scoringFunction(loginForm) < 0) {
        return null;
    }
    if (loginForm && SERVER_HINTS && SERVER_HINTS.highlightSelectedForms) {
        if (loginForm.usernameField) {
            loginForm.usernameField.pointer.css({"border-color":"#0000ff", 'border-width' : '10px', 'border-style' : 'dotted'});
        }

        loginForm.passwordField.pointer.css({"border-color":"#00ff00", 'border-width' : '10px', 'border-style' : 'dotted'});
        if (loginForm.submitField) {
            loginForm.submitField.pointer.css({"border-color":"#ff0000", 'border-width' : '10px', 'border-style' : 'dotted'});
        }
    }
    return loginForm;
};

function isInputPresentAndVisible(name) {
    var $userInput = $('input[name="' + name + '"]:visible');
    return $userInput.length > 0;
}

var isLoginForm = function (form) {
    return getLoginForm(form) !== null;
};

var isLoginPage = function () {
    return guessLoginForm() !== null;
};

// Are we on a login page for this service?
function isLoginPageForService(service) {
    var serviceHost = getCanonicalHost(service.clientData.loginUrl);
    var curHost = getCanonicalHost(window.location.href);
    return serviceHost === curHost && isLoginPage();
}

// TODO: We should verify that the message has come from mitro using signatures
// TODO: We should verify that the the window location matches before inserting username / password
// TODO: We shxould verify that user and password forms that we've guessed are the same form;
//       if multiple forms, we should pick the best one that has both un/pw fields.
// TODO: We should log failure or success of the login based on the login attempt.
// TODO: this seems to not work in an iframe, which is collosally lame.

//// TODO: this should be in a common location. currently it is copied into
//         test_form.js in the phantom automation code!!
var fillLoginForm = function(formData) {
    // TODO: the canonical host may be overly restrictive.  Ideally we
    // would like to check against the domain of the login cookie or
    // possibly the document.domain of the login form.
    var formDomain = getCanonicalHost(formData.loginUrl);
    var pageDomain = getCanonicalHost(document.URL);

    if (formDomain !== pageDomain) {
        throw 'Domain mismatch: ' + pageDomain + ', expected: ' + formDomain;
    }

    var un = (formData.usernameField) ? formData.usernameField.value : null;
    var pw = (formData.passwordField) ? formData.passwordField.value : null;


    // forms sometimes load strangely.
    // TODO: Which ones? What does that mean exactly?
    // Lastpass can cause conflicts: previously we filled the form then delayed submit()
    // lastpass would fill the form in the middle, causing a login for the wrong account.
    setTimeout(function() {
        // Find the form with the username / password
        var sendKeyEvents = function(inputId) {

            // TODO: figure out how to enable this without horrible performance problems
            console.log('dispatching event');
            var event = new KeyboardEvent('keydown');
            event.keyCode=13;
            document.querySelector('#' + inputId).dispatchEvent(event);
            event = new KeyboardEvent('keypress');
            event.keyCode=13;
            document.querySelector('#' + inputId).dispatchEvent(event);
            event = new KeyboardEvent('keyup');
            event.keyCode=13;
            document.querySelector('#' + inputId).dispatchEvent(event);
        };

        var $user_input = formData.usernameField ? formData.usernameField.pointer : formData.usernameField;
        if ($user_input) {
            var $user_form = $user_input.closest("form");
            $user_input.val(un);
            var userInputId = $user_input.attr('id');
            if (!userInputId) {
                userInputId = 'MITRO____184379378465893';
                $user_input.attr('id', userInputId);
            }
            setTimeout(function() {
                sendKeyEvents(userInputId);
            }, 10);
        }
        if (formData.passwordField) {
            var $pass_input = formData.passwordField.pointer;
            assert($pass_input.attr('type') === 'password');
            var $pass_form = $pass_input.closest("form");
            $pass_input.val(pw);
            helper.preventAutoFill($pass_input, $pass_form);

            // simulate keypresses
            var passInputId = $pass_input.attr('id');
            if (!passInputId) {
                passInputId = 'MITRO____184379378465894';
                $pass_input.attr('id', passInputId);
            }


            setTimeout(function() {
                sendKeyEvents(passInputId);
            }, 20);
            // This call will disable autocomplete and the browser won't offer to save
            // the password on the browser's secure area
            // Tested in Chrome and Firefox
            $($pass_form[0]).attr('autocomplete', 'off');
            if ($user_input) {
                $user_input.attr('autocomplete', 'off');
            }
            $pass_input.attr('autocomplete', 'off');

        }


        // Simulate implicit form submission (e.g. pressing enter)
        // http://www.whatwg.org/specs/web-apps/current-work/multipage/association-of-controls-and-forms.html#implicit-submission
        // Click the first submit button. Submit buttons are:
        // - <input type="submit">
        // - <input type="image">
        // - <button> (with type="submit" or no type)
        // selecting buttons without type attribute is tricky; do that in a separate query
        setTimeout(function() {
            //alert('trying to click');
            var submit_button = formData.submitField;
            if (submit_button) {
                try {
                    // sometimes the code runs validators and the submit button is disabled
                    submit_button.pointer.prop('disabled', false);
                } catch (e) {
                    console.log(e);
                }
                submit_button.pointer[0].click();
            } else {
                // this may work for forms without submit buttons (e.g. buttons with onclick)
                try {
                    // if form has input[name="submit"] it shadows submit() method causing TypeError
                    $pass_form[0].submit();
                } catch (e) {
                    if (e instanceof TypeError) {
                        try {
                            //console.log('form.submit not a method; trying to click');
                            $pass_form[0].submit.click();
                        } catch (e2) {
                            //console.log('trying to click on the submit button');
                            var $submit_thing = $user_form.find(':submit');
                            $submit_thing.click();
                        }
                    }
                }
            }

            //alert('clicking done');

        }, 300);

    }, 200);
};




var guessAndFillLoginForm = function (formData) {
    var loginForm = guessLoginForm(formData);
    if (!loginForm) {
        console.log('login form not found');
        return false;
    }

    loginForm.loginUrl = formData.clientData.loginUrl;
    if (loginForm.usernameField) {
        loginForm.usernameField.value = formData.clientData.username;
    }
    if (loginForm.passwordField) {
        loginForm.passwordField.value = formData.criticalData.password;
    }

    fillLoginForm(loginForm);
    return true;
};



/* this stuff is only for testing */
__testing__.findAndSubmitLoginForm = function(username, password) {
    console.log('hello', document.URL, getCanonicalHost(document.URL));
    var data = {clientData:{loginUrl : document.URL, 'username' : username}, criticalData : {'password' : password}};
    console.log('sending data', data);
    return guessAndFillLoginForm(data);
};
__testing__.guessLoginForm = function(username, password) {
    console.log('hello', document.URL, getCanonicalHost(document.URL));
    var data = {clientData:{loginUrl : document.URL, 'username' : username}, criticalData : {'password' : password}};

    var rval = guessLoginForm(data);
    if (!rval) {
        return null;
    }
    // remove pointers
    rval.usernameField.pointer = null;
    rval.passwordField.pointer = null;
    rval.submitField.pointer = null;
    delete rval.formDict;
    delete rval.allFields;
    return rval;
};
/* end testing code */

    // define node.js module for testing
    if (typeof module !== 'undefined' && module.exports) {
        module.exports.guessUsernameField = guessUsernameField;
        module.exports.guessPasswordField = guessPasswordField;
        module.exports.getLoginForm = getLoginForm;
        module.exports.guessLoginForm = guessLoginForm;
        module.exports.isLoginPageForService = isLoginPageForService;
        module.exports.isLoginPage = isLoginPage;
        module.exports.isLoginForm = isLoginForm;
        module.exports.fillLoginForm = fillLoginForm;
        module.exports.setServerHints = setServerHints;

        module.exports.guessAndFillLoginForm = guessAndFillLoginForm;
    } else {
        // export userpass for Closure compiler
        window.guessUsernameField = guessUsernameField;
        window.guessPasswordField = guessPasswordField;
        window.getLoginForm = getLoginForm;
        window.guessLoginForm = guessLoginForm;
        window.isLoginPageForService = isLoginPageForService;
        window.isLoginPage = isLoginPage;
        window.isLoginForm = isLoginForm;
        window.fillLoginForm = fillLoginForm;
        window.setServerHints = setServerHints;
        window.guessAndFillLoginForm = guessAndFillLoginForm;
    }
})();
