#!/usr/bin/python
import re
import sys
from collections import defaultdict
import argparse
import json


parser = argparse.ArgumentParser(description='Extract info from ldap dump, read from stdin.')
parser.add_argument('--all_groups', type=bool, default=False, help='extract all groups')
parser.add_argument('--groups', help='which groups?')
MATCH_COMMENT = re.compile('^#')
MATCH_UNIX_GROUP = re.compile('^memberOf: CN=([^,]+)')
MATCH_USERNAME = re.compile('^userPrincipalName: (.+)@(.+)')

# COMMAND LINE:
# ldapsearch -v -H ldaps://sm-nyc-rodc01.secondmarket.com,ldaps://sm-nyc-dc03.secondmarket.com -b 'dc=secondmarket,dc=com' -D 'cn=srv-mitro,ou=Service Accounts,ou=New York,dc=secondmarket,dc=com'  -w 'PASSWORD' 'objectClass=user' > /tmp/OBJECTCLASS_USER
#

def main(all_groups, group_list):
  users_to_groups = defaultdict(list)
  groups_to_users = defaultdict(list)
  fd = sys.stdin#open(args[0], 'r')

  groups = []
  uid = None
  for line in fd:
    comment = MATCH_COMMENT.search(line)
    if comment:
      if uid:
        # found a comment. push out the previous accumulated groups for this user
        users_to_groups[uid] += groups
        for g in groups:
          groups_to_users[g].append(uid)
      uid = None
      groups = []

    group = MATCH_UNIX_GROUP.search(line)
    if group:
      assert uid is None
      if all_groups or (group.groups()[0] in group_list):
        groups.append(group.groups()[0])


    username = MATCH_USERNAME.search(line)
    if username:
      assert uid is None
      uid = '%s@%s' % username.groups()

  #pp = pprint.PrettyPrinter(indent=4)
  #pp.pprint(dict(users_to_groups))
  #pp.pprint(dict(groups_to_users))
  print json.dumps(groups_to_users, indent=2)


if __name__ == '__main__':
  args = parser.parse_args()
  assert args.all_groups or args.groups, 'need groups or --all_groups'
  sys.exit(main(args.all_groups, args.groups.split(',')))
