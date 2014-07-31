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

/* Provides an API for displaying the Infobar from a content script.

TODO: This embeds the infobar HTML as data. Should we demand-inject it to
avoid wasting memory on pages that don't need it?

e.g. send a message to the background script, which can use chrome.tabs.executeScript()
*/

// message: string to display in the iframe
// buttons: buttons to add on the right of the message
// selectOptions: array of {text: 'display name', value: 'value'}; pass empty array to hide
// buttons: array of {text: 'display text', action: function}; null action = submit
// closeAction: optional: called when user clicks the X to close the iframe

// Wait this long after DOMContentLoaded before showing infobar
var DOMCONTENTLOADED_DELAY_MS = 10;

var helper = new ContentHelper();
var client = new Client('content');
helper.bindClient(client);

/** @constructor */
var Infobar = function (message, selectOptions, buttons, closeAction) {
    this.HEIGHT = 36;
    this.ANIMATION_DELAY_MS = 100;
    this.ANIMATION_EASING = 'linear';

    this.message = message;
    this.selectOptions = selectOptions;
    this.buttons = buttons;
    this.closeAction = closeAction;

    this.iframe = this._createIframe(this.HEIGHT);
};

Infobar.prototype._createIframe = function (height) {
    var base_href = encodeURI(helper.getURL(''));
    var contents = __infobar_html.replace(/%BASE_HTML%/g, base_href);
    var content_class = SAFARI ? 'safari' : '';
    contents = contents.replace(/%CLASS%/g, 'class="' + content_class + '"');

    // Chrome/Safari/Webkit assigns a "data:" URL a unique origin (instead of the page's origin)
    // Old versions of Firefox (before Sept 2013) don't support srcdoc
    // Firefox Beta 27 gives a "SecurityError" when attempting iframe.contentDocument.write
    // Result: use data: URL on Firefox, srcdoc on everyone else
    var iframe = $('<iframe/>');
    if (FIREFOX) {
        iframe.attr('src', 'data:text/html;charset=utf-8,' + encodeURIComponent(contents));
    } else {
        iframe.attr('srcdoc', contents);
    }
    iframe.css({
        width: '100%',
        position: 'fixed',
        visibility: 'visible',
        display: 'block',
        top: -height + 'px',
        left: '0',
        'z-index': 2147483647,
        height: height,
        border: 'none',
        margin: 0,
        'background-color': 'transparent',
        padding: '0px',
        // suppress scroll bars on the infobar
        overflow: 'hidden'
    });

    return iframe;
};

Infobar.prototype.open = function () {
    var self = this;

    // Only after the iframe loads can we animate and fill content
    // NOTE: .ready() Does not work! The document doesn't exist.
    this.iframe.load(function() {
        // animation is choppy if we do it immediately; waiting 50ms seems to help
        setTimeout(function() {
            self.iframe.animate({top: 0}, self.ANIMATION_DELAY_MS, self.ANIMATION_EASING);
        }, 50);

        self.iframe.contents().find('#close').click(function() {
            self.close();
            if (self.closeAction) {
                self.closeAction();
            }
        });

        // customize the contents: message
        self.iframe.contents().find('#message').text(self.message);

        var $select = self.iframe.contents().find('#login');
        $select.empty();
        if (self.selectOptions.length === 0) {
            $select.hide();
        } else {
            for (var i = 0; i < self.selectOptions.length; i++) {
                var $option = $('<option>');
                $option.text(self.selectOptions[i].text);
                $option.prop('value', self.selectOptions[i].value);
                if (self.selectOptions[i].isSelected) {
                    $option.attr('selected', 'selected');
                }
                $select.append($option);
            }
        }

        var $text = self.iframe.contents().find('#text');
        self.buttons.forEach(function(button) {
            var buttonElement = $('<button>');
            buttonElement.text(button.text);
            if (button.action) {
                buttonElement.click(function() {
                    button.action(self.selectOptions[$select[0].selectedIndex]);
                    self.close();
                });
                buttonElement.attr('type', 'button');
                if(SAFARI){
                    buttonElement.addClass('safari');
                }
            }
            $text.append(buttonElement);
        });
    });

    // after the document is ready append the iframe
    $(document).ready(function() {
        // Wait until after page's onReady handlers to avoid "frame busting" code
        setTimeout(function() {
            self.iframe.appendTo('body');
        }, DOMCONTENTLOADED_DELAY_MS);
    });
};

Infobar.prototype.close = function (callback) {
    var self = this;
    // close: animate up then remove from the DOM
    this.iframe.animate({top: -this.HEIGHT}, this.ANIMATION_DELAY_MS, this.ANIMATION_EASING, function() {
        self.iframe.remove();
        self.iframe = null;

        if (callback) {
            callback();
        }
    });
};

function displayInfobar(message, selectOptions, buttons, closeAction) {
    var infobar = new Infobar(message, selectOptions, buttons, closeAction);
    infobar.open();
    return infobar;
}
