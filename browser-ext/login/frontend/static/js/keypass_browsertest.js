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

describe('parseXML', function() {
  it('throws on bad XML', function() {
    try {
      parseXML('hello world');
      throw new Error('expected exception');
    } catch (ignored) {
    }
  });

  it('parses empty file', function() {
    var parsed = parseXML('<passwords></passwords>');
    expect(parsed).toEqual([]);
  });

  it('parses KeePass 2.x? Not sure what format', function() {
    var keepass_unknown = '<?xml version="1.0" encoding="UTF-8"?>' +
      '<pwlist>' +
      '<Entry>' +
      '  <Key>UserName</Key><Value>user</Value>' +
      '  <Key>Password</Key><Value>password</Value>' +
      '  <Key>URL</Key><Value>http://example.com/</Value>' +
      '  <Key>Title</Key><Value>title</Value>' +
      '</Entry></pwlist>';
    parsed = parseXML(keepass_unknown);
    expect(parsed.length).toBe(1);
    expect(parsed[0].username).toBe('user');
    expect(parsed[0].password).toBe('password');
    expect(parsed[0].loginurl).toBe('http://example.com/');
    expect(parsed[0].title).toBe('title');
  });

  it('parses KeePass 1.x from http://keepass.info/help/base/importexport.html#xml', function() {
    var keepass_1x = '<?xml version="1.0" encoding="UTF-8"?>' +
      '<pwlist>' +
      '<pwentry>' +
      '  <group>General</group>' +
      '  <title>Sample Entry</title>' +
      '  <username>Greg</username>' +
      '  <url>http://www.web.com</url>' +
      '  <password>sVoVd2HohmC7hpKYV5Bs</password>' +
      '  <notes>This entry is stored in the &#39;General&#39; group.</notes>' +
      '  <uuid>4d9a9420ac7c4a8ae688762eac8871a9</uuid>' +
      '  <image>0</image>' +
      '  <creationtime>2006-12-31T11:52:01</creationtime>' +
      '  <lastmodtime>2006-12-31T11:52:01</lastmodtime>' +
      '  <lastaccesstime>2006-12-31T11:52:01</lastaccesstime>' +
      '  <expiretime expires="false">2999-12-28T23:59:59</expiretime>' +
      '</pwentry></pwlist>';
    parsed = parseXML(keepass_1x);
    expect(parsed.length).toBe(1);
    expect(parsed[0].username).toBe('Greg');
    expect(parsed[0].password).toBe('sVoVd2HohmC7hpKYV5Bs');
    expect(parsed[0].title).toBe('Sample Entry');
    expect(parsed[0].loginurl).toBe('http://www.web.com');
  });

  it('Parses KeePassX 0.4.3', function() {
    var keepassx = '<!DOCTYPE KEEPASSX_DATABASE>' +
      '<database><group>' +
      '<entry>' +
      '  <title>Sample Entry</title>' +
      '  <username>Greg</username>' +
      '  <url>http://www.web.com</url>' +
      '  <password>sVoVd2HohmC7hpKYV5Bs</password>' +
      '  <comment>This entry is stored in the &#39;General&#39; group.</comment>' +
      '  <icon>0</icon>' +
      '  <creation>2006-12-31T11:52:01</creation>' +
      '  <lastmod>2006-12-31T11:52:01</lastmod>' +
      '  <lastaccess>2006-12-31T11:52:01</lastaccess>' +
      '  <expire>Never</expire>' +
      '</entry></group></database>';
    parsed = parseXML(keepassx);
    expect(parsed.length).toBe(1);
    expect(parsed[0].username).toBe('Greg');
    expect(parsed[0].password).toBe('sVoVd2HohmC7hpKYV5Bs');
    expect(parsed[0].title).toBe('Sample Entry');
    expect(parsed[0].loginurl).toBe('http://www.web.com');
  });
});
