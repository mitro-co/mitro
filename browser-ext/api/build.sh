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

set -e

ABSOLUTE_BUILD=`pwd`/build
NODE_DATA="$ABSOLUTE_BUILD/node"

# Get NPM packages
# TODO: It would probably be better to go with node's defaults:
# - use package.json to specify dependencies
# - live with a top-level node_modules directory getting created
mkdir -p $NODE_DATA
npm --prefix $NODE_DATA --global true install less
npm --prefix $NODE_DATA --global true install jasmine-node
npm --prefix $NODE_DATA --global true install optimist
npm --prefix $NODE_DATA --global true install webworker-threads
npm --prefix $NODE_DATA --global true install jsdom
# required for hogan.js
npm --prefix $NODE_DATA --global true install nopt mkdirp
npm --prefix $NODE_DATA --global true install karma-jasmine karma-phantomjs-launcher karma-chrome-launcher
npm --prefix $NODE_DATA --global true install 'git+https://github.com/mitro-co/keyczarjs.git'
# to develop from a local keyczarjs:
# ln -s ../keyczarjs $NODE_DATA/lib/node_modules
# link Keyczarjs's version of forge so we don't accidentally depend on two different versions
# TODO: We must decide on ONE way of managing dependencies and use it everywhere!
ln -s $NODE_DATA/lib/node_modules/keyczarjs/node_modules/node-forge $NODE_DATA/lib/node_modules
#npm --prefix $NODE_DATA --global true install node-forge

echo "To run JS stuff:"
echo "export NODE_PATH=$NODE_DATA/lib/node_modules"
