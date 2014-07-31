#!/bin/bash

set -e

ABSOLUTE_BUILD=`pwd`/build
POSTGRES_DATA="$ABSOLUTE_BUILD/postgres"
NODE_DATA="$ABSOLUTE_BUILD/node"


# Create Postgres instance
mkdir -p $POSTGRES_DATA
# TODO: Handle non-empty directories correctly?
TZ=UTC initdb --encoding="UTF8" --locale="C" $POSTGRES_DATA || true
# TODO: Start postgres and create the DB
# createdb mitro

