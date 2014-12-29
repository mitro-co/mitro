#!/bin/bash

set -e

NODE_DATA=`pwd`/tools/node

# Get NPM packages
# TODO: It would probably be better to go with node's defaults:
# - use package.json to specify dependencies
# - live with a top-level node_modules directory getting created
mkdir -p $NODE_DATA
npm --prefix $NODE_DATA --global true install less
