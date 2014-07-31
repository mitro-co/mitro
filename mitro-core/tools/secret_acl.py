#!/usr/bin/env python

'''Pretty print the ACL of a secret.'''

import sys

import psycopg2


def group_by_column0(rows):
    output = {}
    for row in rows:
        l = output.get(row[0], [])
        l.append(row)
        output[row[0]] = l
    return output


def pretty_print_secret(secret_id):
    connection = psycopg2.connect('dbname=mitro')
    cursor = connection.cursor()
    cursor.execute('SELECT hostname FROM secrets WHERE id = %s', (secret_id,))
    results = cursor.fetchall()
    if len(results) == 0:
        sys.stderr.write('Error: secret %d does not exist?\n' % secret_id)
        return 1
    hostname = results[0][0]

    cursor.execute('SELECT groups.id,groups.name FROM groups,group_secret WHERE ' +
        'group_secret."serverVisibleSecret_id" = %s AND group_secret.group_id = groups.id;',
        (secret_id,))

    # print 'Group id\tname:'
    groups = {}
    for row in cursor:
        groups[row[0]] = row[1]
    #     print '%8d\t%s' % row
    # print

    cursor.execute('SELECT group_id,level,member_identity,name FROM acl,identity WHERE ' +
        'group_id = ANY(%s) AND identity.id = member_identity ORDER BY group_id',
        (groups.keys(),))
    group_acls = group_by_column0(cursor)
    cursor.close()
    connection.close()

    print 'Groups for secret %d (%s):' % (secret_id, hostname)
    for group_id, name in groups.iteritems():
        print '%s (id %d):' % (name, group_id)
        for acl_row in group_acls[group_id]:
            print '  %s (identity %d; level %s)' % (acl_row[3], acl_row[2], acl_row[1])
        print


def main():
    if len(sys.argv) != 2:
        sys.stderr.write('secret_acl.py (secret id)\n')
        sys.exit(1)
    secret_id = int(sys.argv[1])

    code = pretty_print_secret(secret_id)
    if code is not None:
        sys.exit(code)


if __name__ == '__main__':
    main()
