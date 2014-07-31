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

describe('passpack parse', function() {
  it('throws on bad input', function() {
    try {
      parseHtml(',,,,');
      throw new Error('expected exception');
    } catch (ignored) {
    }
  });

  it('parses empty CSV', function() {
    var emptyCSV = '';
    var parsed = parseCSV(emptyCSV);
    expect(parsed).toEqual([]);
  });

  it('parses data', function() {
    var passpackInput= "adsf,\"as,,d,f\",\"asdf,\",,,,\nFake Secret 1,\"FakeU,ser2\",fakepassword2,reddit.com,,,\nFake Secret 2,\"Fak,,e,User\",FakePassword,google.com,,,\nFake Site w/ emoji,\ud83d\ude0d\ud83d\ude22\u21970\u20e3,\ud83c\udfe9\ud83c\udfe8\ud83c\udfe5,fake.com,,,";

    parsed = parseCSV(passpackInput);
    expect(parsed.length).toBe(4);
    expect(parsed[1].username).toBe("FakeU,ser2");
    expect(parsed[1].password).toBe("fakepassword2");
    expect(parsed[1].loginurl).toBe("reddit.com");
    expect(parsed[1].title).toBe("Fake Secret 1");
  });

  it('parses CSV with string with embedded line-breaks', function() {
    var input = 'Amazon AWS / ec2 / S3 Console Login,dev@songtrust.com,fdjksflkdsjflsda,console.aws.amazon.com,,"MFA NOT NEEDED\n' +
        '\n' +
        '\n' +
        'Account #             : fake\n' +
        'Access Key ID       : fake\n' +
        'Secret Access Key : fake",dev@songtrust.com\n' +
        'appfirst,dev@songtrust.com,rewqrewfoqopfew,https://wwws.appfirst.com/accounts/login/?next=/dashboard/,,,dev@songtrust.com';
    passwords = [];
    parsed = parseCSV(input);
    expect(parsed.length).toBe(2);
  });

  it('parses files with DOS/Mac line endings', function() {
    // Passpack seems to sometimes export files with \r line endings?
    var input = 'Amazon AWS / ec2 / S3 Console Login,dev@songtrust.com,fdjksflkdsjflsda,console.aws.amazon.com,,"MFA NOT NEEDED\n' +
        'Secret Access Key : fake",dev@songtrust.com\r' +
        'appfirst,dev@songtrust.com,rewqrewfoqopfew,https://wwws.appfirst.com/accounts/login/?next=/dashboard/,,,dev@songtrust.com';
    passwords = [];
    parsed = parseCSV(input);
    expect(parsed.length).toBe(2);
  });
});
