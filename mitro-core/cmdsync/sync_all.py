#!/usr/bin/python

'''Synchronizes all groups from a local server to Mitro.'''

import json
import sys
import urllib
import urllib2

import psycopg2

import setup


BASE_SYNC_URL = 'http://localhost:8081'
SYNC_ID = 'test@sync.mitro.co'
POSTGRES_CONNECTION = "dbname='mitro'"


def urllib_read(path, data=None):
    url = BASE_SYNC_URL + path
    request = urllib2.urlopen(url, data)
    data = request.read()
    request.close()
    return data


def request_json(path, arguments=None):
    post_data = None
    if arguments is not None:
        # TODO: This doesn't handle unicode types correctly
        post_data = urllib.urlencode(arguments)

    data = urllib_read(path, post_data)
    response = json.loads(data)
    return response


def get_private_group_id(db_connection, email):
    cursor = db_connection.cursor()

    # Find the user's hidden group id, in a single Postgres statement
    cursor.execute('''SELECT groups.id FROM groups,acl,identity WHERE '''
        '''groups.name='' AND groups."autoDelete"=false AND groups.id=acl.group_id AND '''
        '''acl.member_identity=identity.id AND identity.name=%s;''', [email])
    group_id = cursor.fetchone()[0]
    assert cursor.rowcount == 1

    # Verify that no one else has access to this group
    cursor.execute('''SELECT COUNT(*) FROM acl WHERE group_id=%s''', [group_id])
    count = cursor.fetchone()[0]
    assert count == 1
    cursor.close()

    return group_id


def main(sync_password):
    db_connection = psycopg2.connect(POSTGRES_CONNECTION)

    # Tell the server to sync all domains
    print 'synchronizing with google ...'
    response = request_json('/api/groups/sync', {})

    # For every domain, push the groups to mitro
    success = True
    for domain, admin_emails in response.iteritems():
        if len(admin_emails) == 0:
            print 'warning: domain %s has no administrators; skipping' % (domain)
            continue

        # Get the group ID for the first admin user, sorted alphabetically
        admin_emails.sort()
        admin = admin_emails[0]
        group_source_scope = 'gapps://' + urllib.quote(domain)
        print 'pushing data for domain %s with admin user %s' % (domain, admin)

        # Find this user's private group
        # TODO: This is a HORRIBLY UGLY HACK; make an API?
        admin_group_id = get_private_group_id(db_connection, admin)

        # Get the groups for this domain
        args = {'domain': domain}
        try:
            groups = request_json('/api/groups/dictionary?' + urllib.urlencode(args))
        except urllib2.HTTPError, e:
            if e.code == 404 and e.reason == 'no groups for domain':
                # No groups were selected for this domain; skip it
                print 'warning: domain %s has no groups; skipping' % (domain)
                continue
            # Any other exception: crash
            raise

        # Push to mitro
        print 'pushing %d pending groups to Mitro ...' % (len(groups))
        args = ('--gid', str(admin_group_id), 'upload_groups', group_source_scope)
        output, success = setup.mitro(SYNC_ID, sync_password, args, stdin_data=json.dumps(groups))
        if not success:
            sys.stderr.write('Failed to push groups for domain %s admin %s\n' % (domain, admin))
            print 'Command error output:', output
            print
            success = False

    return success


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.stderr.write('Usage: syncall.py (%s password)\n' % (SYNC_ID))
        sys.exit(1)
    password = sys.argv[1]

    success = main(password)
    print 'Sync completed; success =', success
    if not success:
        sys.exit(1)
