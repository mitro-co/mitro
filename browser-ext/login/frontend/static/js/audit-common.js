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

var AuditEventLoader;
(function () {
    'use strict';

    var USER_ACTION_STRINGS = {
        'SIGNUP': 'User signed up',
        'MITRO_LOGIN': 'User logged in',
        'MITRO_AUTO_LOGIN': 'User auto-logged in',
        'EDIT_PASSWORD': 'User changed password',
        'NEW_DEVICE': 'User added a device',
        'INVITED_BY_USER': 'User was invited'
    };

    var SECRET_ACTION_STRINGS = {
        'CREATE_SECRET': 'Secret created',
        'DELETE_SECRET': 'Secret removed',
        'EDIT_SECRET': 'Secret edited',
        'GET_SECRET_CRITICAL_DATA_FOR_LOGIN': 'Secret accessed',
        'GRANTED_ACCESS_TO': ' was granted access to ',
        'REVOKED_ACCESS_TO': ' was revoked access to '
    };

    var GROUP_ACTION_STRINGS = {
        'CREATE_GROUP': 'Group created',
        'DELETE_GROUP': 'Group removed',
        'EDIT_GROUP': 'Group edited'
    };

    var ACTION_STRINGS = USER_ACTION_STRINGS;
    $.extend(ACTION_STRINGS, SECRET_ACTION_STRINGS);
    $.extend(ACTION_STRINGS, GROUP_ACTION_STRINGS);

    /** @constructor */
    AuditEventLoader = function (orgId) {
        this.SCROLL_MARGIN = 200;
        this.loadSize = 50;
        this.eventIds = {};
        this.events = [];
        this.earliestTimestamp = null;
        this.loadInProgress = false;
        this.hasMoreEvents = true;
        this.orgId = orgId;

        var self = this;
        $(window).scroll(function(event) {
            var pos = $(window).scrollTop();
            var margin = $(document).height() - pos - $(window).height();
            if (margin < self.SCROLL_MARGIN && !self.loadInProgress && self.hasMoreEvents) {
                self.loadMoreEvents();
            }
        });
    };

    AuditEventLoader.prototype.renderAuditEvents = function () {
        var $table = $('#audit-table');
        $table.html(templates['audit-template'].render({events: this.events}));
    };

    // Returns true if the event could be processed for rendering.
    AuditEventLoader.prototype.processAuditEvent = function (event) {
        // Ignore unknown actions
        if (!(event.action in ACTION_STRINGS)) {
            return false;
        }

        event.date = formatTimestamp(event.timestampMs);
        event.actionString = ACTION_STRINGS[event.action]; 

        if (typeof event.affectedUserId === 'number') {
            event.actionString = event.affectedUsername + event.actionString;
            event.linkText = event.secretTitle;
            event.linkUrl = '../html/admin-manage-secret.html?secretId=' + event.secretId;
        } else if (typeof event.secretId === 'number') {
            event.actionString += ': ';
            event.linkText = event.secretTitle;
            event.linkUrl = '../html/admin-manage-secret.html?secretId=' + event.secretId;
        } else if (typeof event.groupId === 'number') {
            event.actionString += ': ';
            event.linkText = event.groupName;
            event.linkUrl = '../html/manage-team.html?id=' + event.groupId;
        }

        return true;
    };

    AuditEventLoader.prototype.loadMoreEvents = function () {
        var self = this;
        this.loadInProgress = true;
        background.getAuditLog(this.orgId, 0, this.loadSize, null, this.earliestTimestamp, function (results) {
            console.log('audit log returned ' + results.events.length + ' events');
            var numOldEvents = self.events.length;

            for (var i = 0; i < results.events.length; ++i) {
                var event = results.events[i];
                if (!(event.id in self.eventIds)) {
                    self.eventIds[event.id] = true;

                    if (self.processAuditEvent(event)) {
                        self.events.push(event);
                    }

                    if (self.earliestTimestamp === null ||
                        event.timestampMs < self.earliestTimestamp) {
                        self.earliestTimestamp = event.timestampMs;
                    }
                }
            }
            self.renderAuditEvents(self.events);

            self.loadInProgress = false;
            var numNewEvents = self.events.length - numOldEvents;

            if (numNewEvents === 0) {
                // Results returned was less than the limit, so there are no
                // more results.
                if (results.events.length < self.loadSize) {
                    self.hasMoreEvents = false;
                } else {
                    // If there were no new events, all events must share the same
                    // timestamp.  Increase the load size and load again.
                    self.loadSize *= 2;
                    self.loadMoreEvents();
                }
            }
        }, onBackgroundError);
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = AuditEventLoader;
    }
})();
