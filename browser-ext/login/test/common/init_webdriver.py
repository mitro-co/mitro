
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
The set of functions initializing the Selenium Web Driver
for each browser we support (currently Chrome, Safari, Firefox)
"""

import subprocess
import base64

from selenium import webdriver

def chrome(webdriver_path, extension_paths=None):
    options = webdriver.ChromeOptions()
    # Disable any default apps in Chrome, and any "External Extensions" on this machine
    # This prevents lastpass from being installed:
    # http://developer.chrome.com/extensions/external_extensions.html
    options.add_argument('--disable-default-apps')

    if extension_paths:
        for extension_path in extension_paths:
            options.add_extension(extension_path)

    return webdriver.Chrome(executable_path=webdriver_path,
                              chrome_options=options)

def safari(selenium_server_jar, selenium_server_host, extension_path='', subprocesses=None):
    # if we're about to run safari locally,
    # we need to start the Selenium Standalone Server first
    if selenium_server_host == 'localhost' \
                  or selenium_server_host == '127.0.0.1':
        selenium_server = subprocess.Popen(['java', '-jar', selenium_server_jar])
        
        if subprocesses:
            subprocesses.append(selenium_server)
        
    desired_capabilities = webdriver.DesiredCapabilities.SAFARI
    
    if extension_path:
        with open(extension_path, 'r') as extension_file:
            extension = base64.b64encode(extension_file.read())
            desired_capabilities['safari.options'] = \
                    {'extensions':[{"filename": "safari.safariextz", "contents": extension}]}

    return webdriver.Remote("http://%s:4444/wd/hub" % selenium_server_host, desired_capabilities)

def firefox(extension_path=''):
    fp = webdriver.FirefoxProfile()
    
    if extension_path:
        fp.add_extension(extension=extension_path)
        
    return webdriver.Firefox(firefox_profile=fp)