#!/bin/bash
#
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
#

# Example usage: ./runtests.sh chrome,safari,firefox
#
# The first argument is a string containing the desired
# browsers names separated by comma (no spaces)
#
# The second argument is selenium server host (optional).
# It is only required if remote Safari installation is used. 
# Usage: ./runtest.sh chrome,safari,firefox 123.456.789

TEST_DIR=$( cd "$( dirname "$0" )" && pwd )
PARENT_DIR=$TEST_DIR/..
BUILD_DIR=build
ABSOLUTE_BUILD="$PARENT_DIR/$BUILD_DIR"

BROWSERS=$1
SELENIUM_SERVER_HOST=''

if [ $# -eq 2 ];then
    SELENIUM_SERVER_HOST="--selenium-server-host $2"
fi

$PARENT_DIR/setuptestenv.sh $ABSOLUTE_BUILD
$PARENT_DIR/build/venv/bin/python test.py --browser $BROWSERS $SELENIUM_SERVER_HOST