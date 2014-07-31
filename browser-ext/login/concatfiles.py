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


'''
Joins multiple files contents into one file
'''

import json
import os
import re
import sys

def concatenate_files(files_to_concatenate, resulting_file_path):
    with open(resulting_file_path, 'w') as outfile:
        for fname in files_to_concatenate:
            with open(fname) as infile:
                for line in infile:
                    outfile.write(line)
    
    
def main():
    if len(sys.argv) < 4:
        sys.stderr('concatfiles.py resulting_file file_name_1 file_name_2 (file_name_3) ... (file_name_n)\n')
        sys.exit(1)

    resulting_file_path = sys.argv[1]
    files_to_concatenate = sys.argv[2:]
    concatenate_files(files_to_concatenate, resulting_file_path)


if __name__ == '__main__':
    main()