
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
The Selenium part in helpers test is to perform the following actions:

1. Start the static server
2. Initialize the Web Driver
3. Start the browser with the test extension installed
4. Start the Selenium Standalone Server for safari test
   in case of local Safari installation
5. Close the browser window and terminate
   all the subprocesses on key press when the job is done
"""

import argparse
import os
import sys
import subprocess

import selenium.webdriver.support.ui as ui

# appending parent directory to python path
sys.path.append(
    os.path.abspath(os.path.join(os.path.dirname(__file__), os.path.pardir)))

from common import init_webdriver

arg_parser = argparse.ArgumentParser('Mitro helpers test')
arg_parser.add_argument('--browser', required=True,
                        help='The options are: chrome, safari, firefox')
arg_parser.add_argument('--selenium-server-host', required=False, default='localhost',
                        help='The host on which the selenium server is running')
args = arg_parser.parse_args()

# This will hold the links to the subprocesses we open
subprocesses = []
# This will hold test results
results = {}

_PATH = os.path.dirname(os.path.realpath(__file__))

CHROME_WEBDRIVER_PATH = os.path.join(_PATH, '..', 'build', 'selenium', 'chromedriver')

# extension files paths
CHROME_EXTENSION_PATH = os.path.join(_PATH, 'build', 'chrome.crx')
SAFARI_EXTENSION_PATH = os.path.join(_PATH, 'build', 'safari.safariextz')
FIREFOX_EXTENSION_PATH = os.path.join(_PATH, 'build', 'firefox.xpi')

STATIC_SERVER_PATH = os.path.join(_PATH, '..', '..', 'server', 'server.py')
STATIC_SERVER_ROOT = os.path.join(_PATH, 'build', 'static')
STATIC_SERVER_PORT = '8001'

SELENIUM_SERVER_JAR = os.path.join(_PATH, '..', '..', 'test', 'build',
                                   'selenium', 'selenium-server-standalone-2.35.0.jar')

# each browser test is expected
# to be completed in 30 seconds
RESULTS_TIMEOUT = 30

def result_ready_condition(driver):
    """
    Selenium WebDriverWait method to wait for test results
    
    """
    for handle in driver.window_handles:
        driver.switch_to_window(handle)
        try:
            driver.find_element_by_id('summary')
            return True
        except:
            continue
    
    return False


def main():
    browsers_to_test = args.browser.split(',')
    
    # starting the static server to power up the 'external' pages we need for our tests
    subprocesses.append(subprocess.Popen([STATIC_SERVER_PATH, '--static-root',
                                      STATIC_SERVER_ROOT, '--port', STATIC_SERVER_PORT]))
    for browser in browsers_to_test:
        try:
            # initializing the Web Driver
            if browser == 'chrome':
                driver = init_webdriver.chrome(CHROME_WEBDRIVER_PATH, [CHROME_EXTENSION_PATH])
            elif browser == 'safari':
                driver = init_webdriver.safari(SELENIUM_SERVER_JAR, args.selenium_server_host,
                                            SAFARI_EXTENSION_PATH, subprocesses)
            elif browser == 'firefox':
                driver = init_webdriver.firefox(FIREFOX_EXTENSION_PATH)
            else:
                raise Exception("Unknown browser %" % browser)
            
            # wait for test results
            wait = ui.WebDriverWait(driver, RESULTS_TIMEOUT)
            wait.until(result_ready_condition)
            
            # save result
            results[browser] = driver.find_element_by_id('summary').text
        except Exception as e:
            print e
        finally:
            # closing the browser window
            try:
                driver.quit()
            except: pass
    
    # add some space
    for i in range(4): print ''
    
    # print results
    print "#################################"
    print "Test results:"
    print ''
    
    for key, value in results.items():
        print "%s: %s" % (key, value)

    print "#################################"
        
    # terminating subprocesses
    for process in subprocesses:
        process.kill()


if __name__ == '__main__':
    main()