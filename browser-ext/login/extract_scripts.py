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


"""
Cleans the input file html from the script tags and saves the resulting html
by the given output path. Saves the scripts-to-file mapping as json
in the output file's dir.

To collect scripts to separate json files for every html file use:
./extract_scripts.py extract input_file_path output_file_path 

To join previously collected file into a single json dictionary use:
./extract_scripts.py collect build_dir_path

"""

import json
import os
import re
import sys
import shutil
import contextlib

from tempfile import mkstemp
from buildtools import compile_html_deps as comp

SCRIPTS_DIR = '%s/frontend/gen-files/paths' % os.path.dirname(__file__)
JSON_EXT = '.json' # Json file extension
MAPPING_FILE_NAME = 'scripts%s' % JSON_EXT
HTMLS_DIR_NAME = 'html'

def extract_scripts(input_path, output_path):
    # directory with htmls
    html_dir = os.path.dirname(output_path)
    # source html file name
    input_file_name = os.path.basename(output_path)
    # json file containing page scripts
    scripts_file_path = os.path.join(SCRIPTS_DIR, '%s%s' % (input_file_name, JSON_EXT))
    
    # create scripts directory if not exists
    if not os.path.exists(SCRIPTS_DIR):
        os.makedirs(SCRIPTS_DIR)
    
    temp_filename = comp.make_temp_filename()
    
    #with open(temp_filename, 'w') as tempfile, \
    #     open(input_path) as input_file, \
    #     open(output_path, 'w') as output_file:
    with contextlib.nested(open(temp_filename, 'w'), open(input_path), open(output_path, 'w')) as (tempfile, input_file, output_file):

        html_source = input_file.read()
        
        scripts = re.findall('<script [^>]*?src=["\'](.*?)[\'"]', html_source)
        
        # remove relative '../' from scripts paths 
        for i, val in enumerate(scripts):
            scripts[i] = re.subn(r'^../', '', val)[0]

        tempfile.write(json.dumps(scripts))
        tempfile.close()
        # this works for cross-device links as well
        shutil.move(temp_filename, scripts_file_path)
          
        # remove script tags
        res_html = re.subn(r'<(script).*?</\1>(?s)', '', html_source)[0]
        # remove empty lines
        res_html = '\n'.join([line for line in iter(res_html.splitlines()) if line.strip()])
        
        output_file.write(res_html)


def collect_scripts(build_dir):
    # html files directory 
    htmls_dir = os.path.join(build_dir, HTMLS_DIR_NAME)
    # the resulting json dict containing all scripts for all pages
    mapping_file_path = os.path.join(htmls_dir, MAPPING_FILE_NAME)
    # the dictionary that will be serialized to resulting json
    mapping_dict = {}
    
    with open(mapping_file_path, 'w') as mapping_file:
        # process every single page scripts json files one by one
        for file_name in os.listdir(SCRIPTS_DIR):
            # just in case
            if file_name.endswith(JSON_EXT):
                # read a single html scripts list
                with open(os.path.join(SCRIPTS_DIR, file_name)) as file:
                    # extract html file name from json file name
                    html_file_name = file_name[:-len(JSON_EXT)]
                    scripts = json.load(file)
                    # push scripts mapping to the resulting dict
                    mapping_dict[html_file_name] = scripts

        # write out the resulting json dict
        json.dump(mapping_dict, mapping_file)


def report_error():
    message = 'ERROR. Invalid syntax. Please use one of the following commands: \n'
    message += './extract_scripts.py extract input_file_path output_file_path \n'
    message += 'or \n'
    message += './extract_scripts.py collect build_dir_path'

    sys.stderr.write(message)
    sys.exit(1)


def main():
    # we expect 2+ arguments
    if len(sys.argv) < 3:
        report_error()
    # extract scripts from a single file
    elif sys.argv[1] == 'extract':
        if not len(sys.argv) == 4:
            report_error()
        else:
            [input_path, output_path] = sys.argv[2:]
            extract_scripts(input_path, output_path)
    # collect all pages scripts pahts to a single json file
    elif sys.argv[1] == 'collect':
        if not len(sys.argv) == 3:
            report_error()
        else:
            build_dir = sys.argv[2]
            collect_scripts(build_dir)
    else:
        report_error()


if __name__ == '__main__':
    main()
