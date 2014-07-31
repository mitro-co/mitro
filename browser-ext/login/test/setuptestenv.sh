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

TEST_DIR=$( cd "$( dirname "$0" )" && pwd )

if [[ -n "$1" ]]; then
  ABSOLUTE_BUILD=$1
else
  ABSOLUTE_BUILD="$TEST_DIR/build"
fi

SELENIUM_DIR="$ABSOLUTE_BUILD/selenium"
POSTGRES_DIR="$ABSOLUTE_BUILD/postgres"

SYSTEM=`uname -s`
MACHINE=`uname -p`

if [ $SYSTEM = "Linux" ]; then
    if [ $MACHINE = "i386" ]; then
        CHROMEDRIVER_FILE="chromedriver_linux32_2.2.zip"
        CHROMEDRIVER_SHA1="9f6bad3a1004cd983731a7b68c0a03f9c6b45696"
    elif [ $MACHINE = "x86_64" ]; then
        CHROMEDRIVER_FILE="chromedriver_linux64_2.2.zip"
        CHROMEDRIVER_SHA1="c737452bacba963a36d32b3bc0fdb87cb6cb25a6"
    else
        echo "Error: Unsupported machine: $MACHINE" > /dev/stderr
        exit 1
    fi
elif [ $SYSTEM = "Darwin" ]; then
    if [ $MACHINE = "i386" ]; then
        CHROMEDRIVER_FILE="chromedriver_mac32_2.2.zip"
        CHROMEDRIVER_SHA1="8328d845afb2e5e124f38a2d72dbfc659c0936b0"
    else
        echo "Error: Unsupported machine: $MACHINE" > /dev/stderr
        exit 1
    fi
else
    echo "Error: Unsupported system: $SYSTEM" > /dev/stderr
    exit 1
fi

SELENIUM_SERVER_FILE="selenium-server-standalone-2.35.0.jar"
SELENIUM_SERVER_SHA1="d27ff0f4ff5c06f63ad9113f448f37e6a00147d1"
SELENIUM_SERVER_URL="http://selenium.googlecode.com/files/$SELENIUM_SERVER_FILE"
SELENIUM_SERVER_DEST="$SELENIUM_DIR/$SELENIUM_SERVER_FILE"

CHROMEDRIVER_URL="https://chromedriver.googlecode.com/files/$CHROMEDRIVER_FILE"
CHROMEDRIVER_DEST="$SELENIUM_DIR/$CHROMEDRIVER_FILE"

mkdir -p "$SELENIUM_DIR"

if [ ! -f $CHROMEDRIVER_DEST ]; then
    echo "Downloading $CHROMEDRIVER_URL"
    if [ $SYSTEM = "Darwin" ]; then
        curl -o "$CHROMEDRIVER_DEST" "$CHROMEDRIVER_URL"
        curl -o "$SELENIUM_SERVER_DEST" "$SELENIUM_SERVER_URL"
    else
        wget --no-check-certificate -O "$CHROMEDRIVER_DEST" "$CHROMEDRIVER_URL"
        wget --no-check-certificate -O "$SELENIUM_SERVER_DEST" "$SELENIUM_SERVER_URL"
    fi
fi

COMPUTED_CHROMEDRIVER_SHA1=$(openssl sha1 $CHROMEDRIVER_DEST)

if [ ! "SHA1($CHROMEDRIVER_DEST)= $CHROMEDRIVER_SHA1" = "$COMPUTED_CHROMEDRIVER_SHA1" ]
then
    echo "Error: SHA1 mismatch" > /dev/stderr
    exit 1
fi

COMPUTED_SELENIUM_SERVER_SHA1=$(openssl sha1 $SELENIUM_SERVER_DEST)

if [ ! "SHA1($SELENIUM_SERVER_DEST)= $SELENIUM_SERVER_SHA1" = "$COMPUTED_SELENIUM_SERVER_SHA1" ]
then
    echo "Error: SHA1 mismatch" > /dev/stderr
    exit 1
fi

if [ ! -f "$SELENIUM_DIR/chromedriver" ]; then
    unzip -d "$SELENIUM_DIR" "$CHROMEDRIVER_DEST"
fi

echo "Build.sh: Making Python virtualenv in $BUILD_DIR/venv"

ABSOLUTE_VENV="$ABSOLUTE_BUILD/venv"
virtualenv $ABSOLUTE_VENV
# --no-deps because we want to ensure we only use packages with hashes
$ABSOLUTE_VENV/bin/pip install -r $TEST_DIR/requirements.txt --no-deps

# Python assumes scripts are always at the "top level"
PYTHON_MODULES="
    chrome
    common
"
for MODULE in $PYTHON_MODULES; do
    ln -s -f $TEST_DIR/$MODULE $ABSOLUTE_VENV/lib/python2.7/site-packages
done