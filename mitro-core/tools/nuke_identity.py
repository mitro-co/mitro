#!/usr/bin/env python

'''Pretty print the ACL of a secret.'''

import random
import sys
import time

import psycopg2


def group_by_column0(rows):
    output = {}
    for row in rows:
        l = output.get(row[0], [])
        l.append(row)
        output[row[0]] = l
    return output

_AUDIT_ACTION_DELETE = 'DELETE_IDENTITY'
_EXPECTED_PROCESSED_AUDIT_COLUMNS = set((
    'id',
    'actor',
    'actor_name',
    'affected_user',
    'affected_user_name',
    'affected_secret',
    'affected_group',
    'action',
    'timestamp_ms',
    'transaction_id',
    'source_ip',
    'device_id',
))


def is_processed_audit_unchanged(connection):
    cursor = connection.cursor()
    cursor.execute('SELECT * FROM processedaudit LIMIT 0')

    actual_columns = set()
    for column in cursor.description:
        actual_columns.add(column.name)

    cursor.close()
    connection.rollback()

    return actual_columns == _EXPECTED_PROCESSED_AUDIT_COLUMNS


def confirm_prompt_or_exit(description):
    '''Calls sys.exit if we don't confirm that we want to nuke.'''

    # add a random char to force the user to read:
    nuke_string = 'nuke %d' % random.randint(0, 9)
    print
    print 'Type "%s" to delete %s:' % (
        nuke_string, description)
    m = raw_input()
    if m != nuke_string:
        sys.stderr.write("Not deleting: input '%s' != '%s'\n" % (m, nuke_string))
        sys.exit(1)
    return True


def connect_to_mitro_db():
    connection = psycopg2.connect('dbname=mitro')

    # Set serializable isolation
    cursor = connection.cursor()
    cursor.execute('set transaction isolation level serializable')
    return connection



def nuke_identity(identity_name):
    connection = connect_to_mitro_db()
    cursor = connection.cursor()

    # Check that processed_audit table is unchanged
    if not is_processed_audit_unchanged(connection):
        sys.stderr.write('Error: processedaudit table has changed!\n')
        return 1

    cursor.execute('SELECT id from identity where name = %s', (identity_name,))
    results = cursor.fetchall()
    if len(results) == 0:
        sys.stderr.write('Error: identity %s not found\n' % identity_name)
        return 1
    identity_id = results[0][0]

    cursor.execute('SELECT email from username where identity = %s', (identity_id,))
    results = cursor.fetchall()
    if len(results) > 1:
        print 'Aliases:'
        for row in results:
            email = row[0]
            print ' ', email
        print

    print 'ACLs for identity %s (id %d):' % (identity_name, identity_id)

    # Locate all acls for the user
    groups = {}
    delete_acls = set()
    cursor.execute('SELECT acl.id,group_id,groups.name,level FROM acl,groups WHERE member_identity = %s AND groups.id = group_id', (identity_id,))
    for acl_id, group_id, group_name, level in cursor:
        print '  group "%s" (%d): %s' % (group_name, group_id, level)
        delete_acls.add(acl_id)
        groups[group_id] = group_name
    print

    # Find groups that must be deleted (this user is the only member)
    delete_groups = set()
    cursor.execute('SELECT group_id, groups.type, count(*) FROM acl, groups WHERE group_id = ANY(%s) AND groups.id = group_id GROUP BY group_id, groups.type',
        (groups.keys(),))
    for group_id, group_type, count in cursor:
        if count == 1:
            if group_type != 'PRIVATE' and group_type is not None:
                raise Exception('ERROR: Only admin for an organization? Cannot delete!')
            delete_groups.add(group_id)

    print 'Groups and secrets that will be deleted:'
    print

    # Verify that none of our "to delete" groups are parent groups in an ACL
    # This should be caught by the group_type check above
    cursor.execute('SELECT count(*) FROM acl WHERE group_identity = ANY(%s)', (list(delete_groups),))
    count = cursor.next()[0]
    assert count == 0

    delete_group_secrets = set()
    maybe_delete_secrets = set()
    not_printed_groups = list(delete_groups)
    cursor.execute('SELECT group_id, group_secret.id, "serverVisibleSecret_id", hostname FROM ' + 
        'group_secret,secrets WHERE group_id = ANY(%s) AND secrets.id="serverVisibleSecret_id"', (list(delete_groups),))
    for group_id, rows in group_by_column0(cursor).iteritems():
        not_printed_groups.remove(group_id)
        print 'Group "%s" (id %d):' % (groups[group_id], group_id)
        for group_id, group_secret_id, svs_id, hostname in rows:
            delete_group_secrets.add(group_secret_id)
            maybe_delete_secrets.add(svs_id)
            print '  %s (secret %d; group_secret %d)' % (hostname, svs_id, group_secret_id)
    for group_id in not_printed_groups:
        print 'Group "%s" (id %d): (no secrets)' % (groups[group_id], group_id)

    # find secrets shared outside groups we are going to delete; do not delete these
    # if len() > 0 required because IN of empty tuple is an error
    delete_secrets = set()
    if len(maybe_delete_secrets) > 0 and len(delete_groups) > 0:
        cursor.execute('SELECT DISTINCT "serverVisibleSecret_id" FROM group_secret ' +
                'WHERE "serverVisibleSecret_id" IN %s AND "group_id" NOT IN %s',
                (tuple(maybe_delete_secrets), tuple(delete_groups)))
        shared_secrets = set()
        for row in cursor:
            secret_id = row[0]
            shared_secrets.add(secret_id)
        delete_secrets = maybe_delete_secrets - shared_secrets

    prompt_description = 'identity %s; %d groups; %d secrets' % (
        identity_name, len(delete_groups), len(delete_secrets))
    confirm_prompt_or_exit(prompt_description)

    cursor.execute('DELETE FROM secrets WHERE id = ANY(%s)', (list(delete_secrets),))
    print 'Deleted %d secrets' % cursor.rowcount
    assert cursor.rowcount == len(delete_secrets)

    # Remove dangling king references on shared secrets TODO: Set to another user?
    cursor.execute('UPDATE secrets SET king = NULL WHERE king = %s', (identity_id,))
    print 'Removed king from %d secrets' % cursor.rowcount

    cursor.execute('DELETE FROM group_secret WHERE id = ANY(%s)', (list(delete_group_secrets),))
    print 'Deleted %d group_secrets' % cursor.rowcount
    assert cursor.rowcount == len(delete_group_secrets)
    cursor.execute('DELETE FROM acl WHERE id = ANY(%s)', (list(delete_acls),))
    print 'Deleted %d acls' % cursor.rowcount
    assert cursor.rowcount == len(delete_acls)

    cursor.execute('SELECT COUNT(*) FROM acl WHERE member_identity = %s', (identity_id,))
    results = cursor.fetchall()
    assert results[0][0] == 0

    cursor.execute('DELETE FROM groups WHERE id = ANY(%s)', (list(delete_groups),))
    print 'Deleted %d groups' % cursor.rowcount
    assert cursor.rowcount == len(delete_groups)
    cursor.execute('DELETE FROM device_specific WHERE "user" = %s', (identity_id,))
    print 'Deleted %d device_specific' % cursor.rowcount
    cursor.execute('DELETE FROM username WHERE identity = %s', (identity_id,))
    print 'Deleted %d aliases' % cursor.rowcount
    cursor.execute('DELETE FROM identity WHERE id = %s', (identity_id,))
    print 'Deleted %d identity' % cursor.rowcount
    assert cursor.rowcount == 1

    now_ms = long(time.time() * 1000 + 0.5)
    cursor.execute('INSERT INTO processedaudit (actor, actor_name, action, timestamp_ms) VALUES ' +
        '(%s, %s, %s, %s)', (identity_id, identity_name, _AUDIT_ACTION_DELETE, now_ms))

    cursor.close()
    connection.commit()
    print 'Committed'
    connection.close()


def main():
    if len(sys.argv) != 2:
        sys.stderr.write('nuke_identity.py (identity name)\n')
        sys.exit(1)
    identity_name = sys.argv[1]

    code = nuke_identity(identity_name)
    if code is not None:
        sys.exit(code)


if __name__ == '__main__':
    main()
