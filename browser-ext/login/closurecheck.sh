#!/bin/sh
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

set -e

make debug

JAR="../third_party/closure-compiler/compiler.jar"
LIST=`ls build/chrome/debug/html/*.html | grep -v manage-team.html | grep -v popup.html`
LIST="build/chrome/debug/manifest.json $LIST"
CLOSUREIFY_DIR="../third_party/closureify"
CLOSUREIFY="$CLOSUREIFY_DIR/closureify-`uname`-`uname -m` -closurePath=$JAR -externsPath=$CLOSUREIFY_DIR/externs -extraExterns=$CLOSUREIFY_DIR/extra_externs.js"

for i in $LIST; do
  echo $i
  $CLOSUREIFY $i || exit 1
done