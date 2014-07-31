#!/usr/bin/env python

'''Clones the entire database N times.'''

import sys

import nuke_identity

TABLES = (
    'acl',
    'device_specific',
    'group_secret',
    'groups',
    'identity',
    'secrets',
    'username',
    )

OFFSET = 1000000
COPY_TIMES = 100


def clone_n_times():
    read_connection = nuke_identity.connect_to_mitro_db()
    read_cursor = read_connection.cursor()

    write_connection = nuke_identity.connect_to_mitro_db()
    write_connection.rollback()
    write_connection.autocommit = True
    write_cursor = write_connection.cursor()

    for table_name in TABLES:
        read_cursor.execute('SELECT * FROM ' + table_name + ' where id < %s', (OFFSET,))
        for row in read_cursor:
            for copy in xrange(COPY_TIMES):
                row = list(row)
                row[0] += OFFSET
                write_cursor.execute('INSERT INTO ' + table_name + ' VALUES %s', (tuple(row),))


def main():
    code = clone_n_times()
    if code is not None:
        sys.exit(code)


if __name__ == '__main__':
    main()
