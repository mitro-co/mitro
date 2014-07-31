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

/** @suppress{duplicate} */
var csvutil = csvutil || require('./csvutil');

describe('toCSV', function() {
  it('empty', function() {
    var rows = [];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('');
  });

  it('number', function() {
    var rows = [[5]];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('5\n');
  });

  it('multiple rows', function() {
    var rows = [['a value', 'b'], [5, 6]];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('a value,b\n5,6\n');
  });

  it('quotes', function() {
    var rows = [["a ' quote", 'b "quoted" string']];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('a \' quote,"b ""quoted"" string"\n');
  });

  it('commas', function() {
    var rows = [['a,b,c', 'd']];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('"a,b,c",d\n');
  });

  it('line endings', function() {
    var example = 'hello';
    var rows = [['a \n line', 'b \r line', 'c \r\n line']];
    var output = csvutil.toCSV(rows);
    expect(output).toBe('"a \n line","b \r line","c \r\n line"\n');
  });

  it('unicode', function() {
    var toUtf8 = function(s) {
      return unescape(encodeURIComponent(s));
    };
    var twoByteUtf8 = '\u00e9'; // e with acute
    var threeByteUtf8 = '\u2192'; // rightwards arrow
    var fourByteUtf8 = '\uD83D\uDC96'; // sparkling heart emoji \U+1F496

    expect(toUtf8(twoByteUtf8).length).toBe(2);
    expect(toUtf8(threeByteUtf8).length).toBe(3);
    expect(toUtf8(fourByteUtf8).length).toBe(4);

    var example = 'emoji: ';
    var rows = [['hello ' + twoByteUtf8, 'b ' + threeByteUtf8, 'c ' + fourByteUtf8]];
    var output = csvutil.toCSV(rows);
    var expected = 'hello ' + twoByteUtf8 + ',b ' + threeByteUtf8 + ',c ' + fourByteUtf8 + '\n';
    expect(output).toBe(expected);
  });
});
