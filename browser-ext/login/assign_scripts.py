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


import os
import json
import plistlib
import sys
import contextlib
from xml.dom.minidom import parseString

CHROME_MANIFEST = 'manifest.json'
SAFARI_MANIFEST = 'Info.plist'
SAFARI_BACKGROUND_PAGE = 'background.html'

# json file containing background and content scripts lists
SCRIPTS_FILE = os.path.join('common', 'config', 'paths.json')

def chrome(manifest_path, build_dir):
    """
    Injects background and content scripts into Chrome's manifest.json
    and saves the resulting json to the build directory
    
    """
    resulting_manifest_path = os.path.join(build_dir, CHROME_MANIFEST)
    
    #with open(manifest_path, 'r+') as manifest_file, \
    #     open(SCRIPTS_FILE, 'r') as scripts_file, \
    #     open(resulting_manifest_path, 'w') as resulting_manifest:
    with contextlib.nested(open(manifest_path, 'r+'), open(SCRIPTS_FILE, 'r'), open(resulting_manifest_path, 'w')) as (manifest_file, scripts_file, resulting_manifest):

        # clean the manifest contents of the comments
        cleaned_contents = ''
        for line in manifest_file.readlines():
            if not line.strip()[:2] == '//':
                cleaned_contents += line
        
        # starting manifest data
        manifest = json.loads(cleaned_contents)
        # scripts to inject
        scripts = json.load(scripts_file)
        
        # Add scripts to manifest
        manifest['background']['scripts'] = scripts['background_scripts']
        manifest['content_scripts'][0]['js'] = scripts['content_scripts']
        
        # Save resulting manifest in Chrome extension build dir
        json.dump(manifest, resulting_manifest)

def safari(manifest_path, build_dir):
    """
    Injects the content scripts to the Safari's Info.plist,
    generates the background page html with the appropriate
    script included in it and saves both files in Safari extension build dir
    
    """
    
    # Read original Info.plist
    plist = plistlib.readPlist(manifest_path)
    
    # Safari background.html file path
    background_path = os.path.join(build_dir, SAFARI_BACKGROUND_PAGE)
    
    #with open(SCRIPTS_FILE, 'r') as scripts_file, \
    #    open(background_path, 'w') as background:
    with contextlib.nested(open(SCRIPTS_FILE, 'r'), open(background_path, 'w')) as (scripts_file, background):
        
        scripts = json.load(scripts_file)
        
        plist['Content']['Scripts']['Start'] = scripts['content_scripts']
        
        resulting_plist_path = os.path.join(build_dir, SAFARI_MANIFEST)
        plistlib.writePlist(plist, resulting_plist_path)
        
        background_dom = parseString('<html><head></head></html>')
        head = background_dom.getElementsByTagName('head')[0]
        
        for script in scripts['background_scripts']:
            script_element = background_dom.createElement('script')
            script_element.attributes['src'] = script
            # This is a hack to avoid generating self closing script tag
            script_element.appendChild(background_dom.createTextNode(''))
            head.appendChild(script_element)
        
        # Another hack to remove xml starting tag
        xml = '\n'.join(node.toxml('utf-8') for node in background_dom.childNodes)
        background.write(xml)
        


if __name__ == '__main__':
    if not len(sys.argv) == 4:
        sys.stderr('assign_scripts.py browser_name manifest_path build_dir \n')
        sys.exit(1)

    [browser_name, manifest_path, build_dir] = sys.argv[1:]
    
    if browser_name == 'chrome':
        chrome(manifest_path, build_dir)
    elif browser_name == 'safari':
        safari(manifest_path, build_dir)
