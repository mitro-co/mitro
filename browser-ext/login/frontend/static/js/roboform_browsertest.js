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

describe('roboform', function() {
  it('fails on bad HTML', function() {
    // test bad HTML
    try {
      parseHtml('hello world');
      throw new Error('expected exception');
    } catch (ignored) {
    }
  });

  it('parses empty XML', function() {
    // passwords is a shared global variable ... yes seriously TODO: FIX THIS
    passwords = [];
    var emptyHtml = '<TABLE width="100%"><TR align=left><TD class="caption" colspan=3></TD></TR><TR align=left><TD class="subcaption" colspan=3><WBR></TD></TR><TR><TD class=field align=left valign=top width="40%">ses<WBR>sion[use<WBR>rname_or<WBR>_email]</TD><TD></TD><TD class=wordbreakfield align=left valign=top width="55%"></TD></TR><TR><TD class=field align=left valign=top width="40%">ses<WBR>sion[pas<WBR>sword]</TD><TD></TD><TD class=wordbreakfield align=left valign=top width="55%"></TD></TR><TR><TD class=field align=left valign=top width="40%"><WBR></TD><TD></TD><TD class=wordbreakfield align=left valign=top width="55%">*</TD></TR></TABLE>';
    var parsed = parseHtml(emptyHtml);
    // TODO: This almost certainly should return 0 entries, not 1
    expect(parsed.length).toBe(1);
    expect(parsed[0].username).toBe('');
    expect(parsed[0].password).toBe('');
    expect(parsed[0].loginurl).toBe('');
    expect(parsed[0].title).toBe('');
  });

  it('parses data', function() {
    // passwords is a shared global variable ... yes seriously TODO: FIX THIS
    passwords = [];
    var roboformInput = '<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head><body><table width="100%"><tbody><tr align="left"><td class="caption" colspan="3">title</td></tr><tr align="left"><td class="subcaption" colspan="3">Exa<wbr>mple.com</td></tr><tr><td class="field" align="left" valign="top" width="40%">ses<wbr>sion[use<wbr>rname_or<wbr>_email]</td><td></td><td class="wordbreakfield" align="left" valign="top" width="55%">user</td></tr><tr><td class="field" align="left" valign="top" width="40%">ses<wbr>sion[pas<wbr>sword]</td><td></td><td class="wordbreakfield" align="left" valign="top"width="55%">password</td></tr><tr><td class="field" align="left" valign="top" width="40%">rem<wbr>ember_me</td><td></td><td class="wordbreakfield" align="left" valign="top" width="55%">*</td></tr></tbody></table></body></html>';
    var parsed = parseHtml(roboformInput);

    expect(parsed.length).toBe(1);
    expect(parsed[0].username).toBe('user');
    expect(parsed[0].password).toBe('password');
    expect(parsed[0].loginurl).toBe('Example.com');
    expect(parsed[0].title).toBe('title');
  });
});
