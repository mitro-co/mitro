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

/**
 * The fact this code is being invoked
 * means the ContentHelper.redirectTo test had succeeded.
 * So all we need is report success.
 * 
 * Also the ContentHelper.redirectTo is the last test
 * in the content tests sequence. That's why we also 
 * report the content test finished.
 */

var helper = new ExtensionHelper();
var client = new Client('extension');
helper.bindClient(client);

client.initRemoteCalls('background', ['setResult', 'reportContentFinished']);
client.setResult('content.redirectTo', 'OK');
client.reportContentFinished();
