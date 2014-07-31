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
set -x  # echo
set -u  # undefined variables cause an error
#echo 'drop database mitro' | psql vijayp
#echo 'create database mitro' | psql vijayp

ABSOLUTE_BUILD=`pwd`/../../build

export NODE_PATH=$ABSOLUTE_BUILD/node/lib/node_modules
export SERVER_DIRECTORY=../../server
export LOGIN_DIRECTORY=`pwd`/../../../login/
POSTGRES_HOST=::1
POSTGRES_PORT=5433
POSTGRES_DATA_DIR="$ABSOLUTE_BUILD/postgres"
DATABASE_URL="jdbc:postgresql://[$POSTGRES_HOST]:$POSTGRES_PORT/mitro"

MITRO_HOST=localhost
MITRO_PORT=8443
MITRO_SERVER_BASE="-DgenerateSecretsForTest=true -DdisableRateLimits=true -Ddburl=$DATABASE_URL -Dport=$MITRO_PORT -ea -jar build/mitrocore.jar"
MITRO_SERVER_VERIFICATION_TEST="java -DdisableEmailVerification=test $MITRO_SERVER_BASE"
MITRO_SERVER="java -DdisableEmailVerification=true $MITRO_SERVER_BASE"

SYSTEM=`uname -s`

FAST_MODE=""
if [ $# -ge 1 ]; then
  # Avoids "unbound variable" warnings
  if [ "$1" == "FAST" ]; then
    FAST_MODE=$1
  fi
fi

if [ $SYSTEM = "Linux" ]; then
  POSTGRES_BIN_DIR=$(dirname $(locate -r initdb$))
  if [ POSTGRES_BIN_DIR = '' ]; then
    echo "run sudo updatedb"
    exit -1
  fi
elif [ $SYSTEM = "Darwin" ]; then
  # homebrew install
  POSTGRES_BIN_DIR="/usr/local/bin"
fi

if [ ! -d "$POSTGRES_DATA_DIR" ]; then
  $POSTGRES_BIN_DIR/initdb "$POSTGRES_DATA_DIR"
fi

POSTGRES_PID=$(lsof -i :$POSTGRES_PORT -t)
if [ $POSTGRES_PID ]; then
  kill $POSTGRES_PID
  while kill -0 $POSTGRES_PID > /dev/null 2>&1; do
    sleep 1
  done
fi

$POSTGRES_BIN_DIR/postgres -D $POSTGRES_DATA_DIR -p $POSTGRES_PORT -k /tmp -h ::1,127.0.0.1 &
POSTGRES_PID=$!;

kill -0 $POSTGRES_PID || exit 255;

while ! $(nc -z $POSTGRES_HOST $POSTGRES_PORT > /dev/null); do
  sleep 1;
done

MITRO_PID=$(lsof -i :$MITRO_PORT -t)

if [ $MITRO_PID ]; then
  kill $MITRO_PID
fi


readlink $SERVER_DIRECTORY || { echo "you need to make a symlink from mitro-core/ to `pwd`/../../server" ; exit -1; }
echo "wiping the database"
$POSTGRES_BIN_DIR/dropdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro
$POSTGRES_BIN_DIR/createdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro || exit 255
cd $SERVER_DIRECTORY

ant jar
$MITRO_SERVER &
PID=$!;
echo $PID;

while ! $(nc -z $MITRO_HOST $MITRO_PORT > /dev/null); do
  sleep 1;
done

kill -0 $PID || exit 255;
cd -

node mitro_fe_regtest.js #&> /dev/null
RVAL=$?
sleep 1  # give ant/java time to log errors?
kill $PID
if [[ $RVAL != 0 ]]; then
  exit $RVAL
fi

echo "wiping the database"
$POSTGRES_BIN_DIR/dropdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro
$POSTGRES_BIN_DIR/createdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro || exit 255
cd $SERVER_DIRECTORY

# mitro_fe_regtest2 includes an email verification test
$MITRO_SERVER_VERIFICATION_TEST &

PID=$!;
echo $PID;

while ! $(nc -z $MITRO_HOST $MITRO_PORT > /dev/null); do
  sleep 1;
done

kill -0 $PID || exit 255;
cd -

node mitro_fe_regtest2.js
RVAL=$?
sleep 1  # give ant/java time to log errors?
kill $PID
if [[ $RVAL != 0 ]]; then
  exit $RVAL
fi
cd $SERVER_DIRECTORY
ant test || exit 255
cd -

if [ ! $FAST_MODE ]; then
  cd $LOGIN_DIRECTORY
  ./closurecheck.sh || exit 255
  cd -
fi

cd $LOGIN_DIRECTORY
./runtests.sh || exit 255
cd -

echo "wiping the database"
$POSTGRES_BIN_DIR/dropdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro
$POSTGRES_BIN_DIR/createdb --host=$POSTGRES_HOST --port=$POSTGRES_PORT mitro || exit 255
cd $SERVER_DIRECTORY
$MITRO_SERVER &
PID=$!;
echo $PID;

while ! $(nc -z $MITRO_HOST $MITRO_PORT > /dev/null); do
  sleep 1;
done

kill -0 $PID || exit 255;
cd -

# run a bunch of tests from python and then kill the server.
python runtests.py $FAST_MODE
RVAL=$?

sleep 1  # give ant/java time to log errors?
kill $PID
kill $POSTGRES_PID

while kill -0 $POSTGRES_PID > /dev/null 2>&1; do
  sleep 1
done

exit $RVAL