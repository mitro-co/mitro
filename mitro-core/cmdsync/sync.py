#!/usr/bin/python

import sys

import setup


def main():
    identity, password, admin_group_id = setup.load_identity()
    group_source_id = setup.GROUP_SOURCE_ID + identity

    print 'Synchronizing groups from Google ...'
    groups_json = setup.sync()

    print 'Pushing groups to Mitro ...'
    args = ('--gid', str(admin_group_id), 'upload_groups', group_source_id)
    output, success = setup.mitro(identity, password, args, stdin_data=groups_json)
    if not success:
        sys.stderr.write('Failed to push groups\n')
        return 1

    print 'Updating groups to reflect changes ...'
    args = ('--gid', str(admin_group_id), 'sync_groups', group_source_id)
    output, success = setup.mitro(identity, password, args)
    if not success:
        sys.stderr.write('Failed to synchronize groups\n')
        return 1
    print 'SUCCESS'
    return 0


if __name__ == '__main__':
    code = main()
    if code != 0:
        sys.exit(code)
