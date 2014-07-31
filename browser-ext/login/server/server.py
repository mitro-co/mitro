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


import SimpleHTTPServer
import SocketServer
import sys
import os.path
import argparse
import time

from urlparse import urlparse, parse_qs

arg_parser = argparse.ArgumentParser()

arg_parser.add_argument('--static-root', required=False,
                        help='Path to the static files root')
arg_parser.add_argument('--port', required=False,
                        help='The port number to use')
args = arg_parser.parse_args()

DEFAULT_PORT = (args.port and [int(args.port)] or [8001])[0]
STATIC_ROOT = (args.static_root and [args.static_root] or ['static'])[0]

class TimeoutHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    def do_GET(self):
        params = parse_qs(urlparse(self.path).query)
        if params.get('timeout'):
            time.sleep(float(params['timeout'][0]) / 1000)
        SimpleHTTPServer.SimpleHTTPRequestHandler.do_GET(self)

def main():
    port = DEFAULT_PORT

    static_path = os.path.join(os.path.dirname(__file__), STATIC_ROOT)
    os.chdir(static_path)

    httpd = SocketServer.TCPServer(('', port), TimeoutHandler)
    print 'serving files from %s on http://localhost:%d/html/popup.html' % (static_path, port)
    httpd.serve_forever()


if __name__ == '__main__':
    main()