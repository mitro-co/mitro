#!/bin/sh
# Backs up the database to a file with today's date suffixed

NAME=pgdump-`date +%Y%m%d`.sql.gz
echo dumping to $NAME
pg_dump mitro | gzip > $NAME
