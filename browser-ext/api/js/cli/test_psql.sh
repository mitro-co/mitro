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
SYSTEM=`uname -s`

POSTGRES_HOST=::1
POSTGRES_PORT=5433
POSTGRES_DATA_DIR="$ABSOLUTE_BUILD/postgres"
DATABASE_URL="jdbc:postgresql://[$POSTGRES_HOST]:$POSTGRES_PORT/mitro"

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

$POSTGRES_BIN_DIR/postgres -D $POSTGRES_DATA_DIR -p $POSTGRES_PORT -k /tmp -h ::1,127.0.0.1 &
POSTGRES_PID=$!;

sleep 3;
psql -p 5433 -h::1 mitro

kill $POSTGRES_PID;