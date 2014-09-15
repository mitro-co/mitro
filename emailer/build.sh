#!/bin/sh
# A shitty script to make our stuff ready for deployment

# Exit on first error; Unset variables are an error
set -e -u

# Directory to build into
BUILD_DIR=build

echo "Build.sh: Setting up in $BUILD_DIR"

# From:
# http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in
SCRIPT_DIR=$( cd "$( dirname "$0" )" && pwd )
ABSOLUTE_BUILD="$SCRIPT_DIR/$BUILD_DIR"

echo "Build.sh: Making Python virtualenv in $BUILD_DIR/venv"
ABSOLUTE_VENV="$ABSOLUTE_BUILD/venv"
virtualenv $ABSOLUTE_VENV
# --no-deps because we want to ensure we only use packages with hashes
$ABSOLUTE_VENV/bin/pip install -r $SCRIPT_DIR/requirements.txt --no-deps
