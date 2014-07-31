#!/usr/bin/env python

'''Pretty print the ACL of a secret.'''

import random
import sys

import psycopg2

import nuke_identity


def get_group_individual_members(cursor, group_id):
    result = set()
    cursor.execute('SELECT identity.name FROM acl, identity WHERE ' +
        'acl.group_id = %s AND acl.member_identity = identity.id', (group_id,))
    for row in cursor:
        result.add(row[0])
    return result


def is_private_group(group_type, group_name, autodelete):
    if group_type == 'PRIVATE':
        return True
    elif group_type is None and group_name == '' and autodelete:
        return True
    return False


def nuke_org(org_id):
    assert isinstance(org_id, int) or isinstance(org_id, long)
    connection = nuke_identity.connect_to_mitro_db()
    cursor = connection.cursor()

    cursor.execute('SELECT name, type from groups where id = %s', (org_id,))
    results = cursor.fetchall()
    if len(results) == 0:
        sys.stderr.write('Error: group_id %d not found\n' % org_id)
        return 1
    org_name = results[0][0]
    group_type = results[0][1]
    if group_type != 'TOP_LEVEL_ORGANIZATION':
        raise Exception('ERROR: group type is not TOP_LEVEL_ORGANIZATION (is %s)' % (group_type))
    print 'Org id: %d name: %s' % (org_id, org_name)

    # Load administrators on the org group (administrators)
    cursor.execute('SELECT identity.name FROM acl, identity WHERE ' +
        'acl.group_id = %s AND acl.member_identity = identity.id', (org_id,))
    print 'Administrators:'
    for admin in get_group_individual_members(cursor, org_id):
        print '  %s' % admin
    print

    # Locate all groups that belong to the organization
    cursor.execute('SELECT acl.group_id, groups.type, groups.name, groups."autoDelete" FROM ' +
        'acl, groups WHERE group_identity = %s AND groups.id = acl.group_id', (org_id,))
    org_group_types = {}
    for group_id, group_type, group_name, autodelete in cursor:
        org_group_types[group_id] = (group_type, group_name, autodelete)

    members = {}
    for group_id, (group_type, group_name, autodelete) in org_group_types.iteritems():
        if is_private_group(group_type, group_name, autodelete):
            users = get_group_individual_members(cursor, group_id)
            if len(users) == 0:
                print 'WARNING: Empty private group id %d' % group_id
            else:
                assert len(users) == 1
                username = iter(users).next()
                members[username] = group_id

    print 'Members (%d):' % (len(members))
    for member, private_group_id in members.iteritems():
        print '  %s (private group %d)' % (member, private_group_id)
    print

    print 'Groups:'
    for group_id, (group_type, group_name, autodelete) in org_group_types.iteritems():
        if is_private_group(group_type, group_name, autodelete):
            continue
        assert group_type is None
        print '  %d: %s' % (group_id, group_name)

        for group_member in get_group_individual_members(cursor, group_id):
            print '    %s' % (group_member)

    # Get secret ids and hostnames for all secrets accessible by any of the organization groups
    cursor.execute('SELECT secrets.id, secrets.hostname FROM secrets, group_secret WHERE ' +
        'group_secret.group_id = ANY(%s) AND group_secret."serverVisibleSecret_id" = secrets.id',
        (org_group_types.keys(),))
    org_secrets = list(cursor)

    print 'Secrets (%d):' % (len(org_secrets))
    for secret_id, hostname in org_secrets:
        print '  %d: %s' % (secret_id, hostname)
    print

    prompt_description = 'organization %s; %d groups' % (
            org_name, len(org_group_types))
    nuke_identity.confirm_prompt_or_exit(prompt_description)

    group_ids = set(org_group_types.iterkeys())
    group_ids.add(org_id)

    # Verify, in two ways, that we won't delete groups that contain secrets (yet)
    if len(org_secrets) != 0:
        raise Exception('TODO: Support removing organizations with secrets')
    cursor.execute('SELECT COUNT(*) FROM group_secret WHERE group_id IN %s', (tuple(group_ids),))
    count = cursor.next()[0]
    assert count == 0

    # Delete ACLs for all the groups
    cursor.execute('DELETE FROM acl WHERE group_id IN %s', (tuple(group_ids),))
    # This should never delete anything, since we collect all the group ids above
    cursor.execute('DELETE FROM acl WHERE group_identity IN %s', (tuple(group_ids),))
    assert cursor.rowcount == 0

    cursor.execute('DELETE FROM groups WHERE id IN %s', (tuple(group_ids),))
    assert cursor.rowcount == len(group_ids)

    cursor.close()
    connection.commit()
    print 'SUCCESS'


def main():
    if len(sys.argv) != 2:
        sys.stderr.write('nuke_org.py (org group id)\n')
        sys.exit(1)
    org_id = int(sys.argv[1])

    code = nuke_org(org_id)
    if code is not None:
        sys.exit(code)


if __name__ == '__main__':
    main()
