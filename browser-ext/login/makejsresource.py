#!/usr/bin/env python

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


'''Makes a file available as a JS variable with the name:

__resource_(input file base name)'''

import json
import os
import re
import sys


def makejsresource(input_path, output_path):
    input = open(input_path)
    data = input.read()
    input.close()

    if os.path.exists(output_path):
        raise Exception('output path %s exists' % (output_path))

    variable_name = '__' + re.sub('[^A-Za-z0-9_]', '_', os.path.basename(input_path))
    # print 'JS variable:', variable_name

    out = open(output_path, 'w')
    out.write('var %s=' % (variable_name))
    json.dump(data, out)
    out.write(';\n')
    out.close()


def main():
    if len(sys.argv) != 3:
        sys.stderr('makejson.py (input file name) [output js file]\n')
        sys.exit(1)

    makejsresource(sys.argv[1], sys.argv[2])


if __name__ == '__main__':
    main()