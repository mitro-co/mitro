#!/usr/bin/env python

'''Shows differences between a SQLAlchemy metadata definitions. Can be used with SQLAlchemy
reflection to compute differences with an existing database.

I started writing this the "right" way, which compares the Python objects. Then I realized
I just need this the hacky way:

1. Create new temporary database.
2. Get SQLAlchemy to create the schema there.
3. Collect the output of "show create tables".
4. Use the command line diff tool.'''

import argparse
import re
import subprocess
import tempfile


CREATE_TABLE_RE = re.compile('^CREATE TABLE (.+) \(')
TABLE_END = ');'

def dump_schema(db_name):
    command = ('pg_dump', '--schema-only', '--no-owner', db_name)
    output = run_with_output(command)

    in_table = False
    columns = None

    # Canonicalize the schema so it diffs the same
    out = []
    for line in output.split('\n'):
        if in_table:
            if line == TABLE_END:
                # Sort the columns and output them
                columns.sort()
                out.extend(columns)
                out.append(line + '\n')
                in_table = False
            else:
                # Column of the table
                if not line.endswith(','):
                    line += ','
                columns.append(line + '\n')
        else:
            if CREATE_TABLE_RE.match(line):
                in_table = True
                columns = []

            out.append(line + '\n')
    return ''.join(out)


def read_wait_output(proc):
    output = proc.stdout.read()
    code = proc.wait()
    assert code == 0
    return output


def run_with_output(command):
    proc = subprocess.Popen(command, stdout=subprocess.PIPE)
    return read_wait_output(proc)


def hack_diff(jar_path):
    # Create a new temporary database
    # TODO: Append a random id
    temp_database_name = 'difftemp'
    command = ('psql', 'postgres', '--command=create database ' + temp_database_name)
    run_with_output(command)

    # create the tables
    command = ('java', '-ea', '-cp', jar_path, 'co.mitro.core.server.CreateTables',
        temp_database_name)
    run_with_output(command)

    # Dump the schemas
    production = dump_schema('mitro')
    development = dump_schema(temp_database_name)

    # Drop the test db
    command = ('psql', 'postgres', '--command=drop database ' + temp_database_name)
    run_with_output(command)

    temp1 = tempfile.NamedTemporaryFile()
    temp1.write(production)
    temp1.flush()

    temp2 = tempfile.NamedTemporaryFile()
    temp2.write(development)
    temp2.flush()

    diff = subprocess.Popen(('diff', '-u', temp1.name, temp2.name), stdout=subprocess.PIPE)
    output = diff.stdout.read()
    code = diff.wait()
    # 0: no diff; 1: diff; >1: error
    assert code == 0 or code == 1
    return output


def main():
    parser = argparse.ArgumentParser(description='Compare production and development schemas')
    parser.add_argument('--jar', default='build/mitrocore.jar', help='Path to mitrocore Jar')
    args = parser.parse_args()

    output = hack_diff(args.jar)
    print output,


if __name__ == '__main__':
    main()
