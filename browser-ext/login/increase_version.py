#!/usr/bin/python

# *****************************************************************************
# Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
# Authors:
# Vijay Pandurangan (vijayp@mitro.co)
# Evan Jones (ej@mitro.co)
# Adam Hilss (ahilss@mitro.co)
#
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#     You can contact the authors at inbound@mitro.co.
# *****************************************************************************


import re
import sys
import os

FILE_PATTERNS = {

    'safari/Info.plist' : re.compile('''<key>CFBundleShortVersionString</key>[^<]*<string>0.10.([0-9]+)</string>[^<]*<key>CFBundleVersion</key>[^<]*<string>([0-9]+)</string>''', re.DOTALL),

    'chrome/manifest.json' : re.compile('''"version": +"0.10.([0-9]+)",'''),

    'firefox/package.json' : re.compile('''"version": +"0.10.([0-9]+)",'''),

    'webpage/config/config.js' : re.compile('''var EXTENSION_VERSION = '0.10.([0-9]+)';''') 
}

def fixup_file(fn, regex, outfile):
    olddata = open(fn).read()
    print >> sys.stderr, 'updating file', fn
    match = regex.search(olddata)
    assert (match and match.groups())
    newdata = ''
    oldloc = 0
    for i in range(1, 1 + len(match.groups())):
        old = match.group(i)
        new = str(int(old)+1)
        print >> sys.stderr, 'updating old number %s to new number %s' % (old, new)
        newdata += olddata[oldloc:match.start(i)] + new
        oldloc = match.end(i)
    newdata += olddata[oldloc:]

    open(outfile, 'w').write(newdata)


if __name__ == '__main__':
    # TODO: this should really use tempfiles elsewhere on disk
    for (k, v) in FILE_PATTERNS.iteritems():
        fixup_file(k,v, k + '.out')

    for k in FILE_PATTERNS.keys():
        os.rename(k + '.out', k)