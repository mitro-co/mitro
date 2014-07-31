#!/usr/bin/env python

# *****************************************************************************
# Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
# Authors:
# Vijay Pandurangan (vijayp@mitro.co)
# Evan Jones (ej@mitro.co)
# Adam Hilss (ahilss@mitro.co)
#
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#     You can contact the authors at inbound@mitro.co.
# *****************************************************************************


import sys
import subprocess
import os
COMMON_CLI_ARGS="--password=password --test_server --key_cache"
# COMMON_CLI_ARGS="--password=password --test_server --test_use_crappy_crypto"
FAST = 'FAST'

COMMANDS = [
  FAST, ('node crappycrypto_test.js ', True),
  ('node crypto_test.js ', True),
  ('node keycache_test.js', True),
  FAST, ('node logging_test.js', True),
  FAST, ('node mitro_fe_test.js', True),
  FAST, ('node mitro_lib_test.js', True),
  ('node parallel_test.js', True),
  (' ./mitro addiden ' + COMMON_CLI_ARGS + ' --uid=u1@example.com User1 ', True),
  (' ./mitro addiden ' + COMMON_CLI_ARGS + ' --uid=u2@example.com User2 ', True),
  (' ./mitro addiden ' + COMMON_CLI_ARGS + ' --uid=u3@example.com User3 ', True),
  (' ./mitro ls ' + COMMON_CLI_ARGS + ' --uid=u1@example.com ', False),
  (' ./mitro addgroup ' + COMMON_CLI_ARGS + ' --uid=u1@example.com group1 ', True),
  (' ./mitro addgroup ' + COMMON_CLI_ARGS + ' --uid=u1@example.com group2 ', True),
  (' ./mitro addgroup ' + COMMON_CLI_ARGS + ' --uid=u3@example.com group3 ', True),
  (' ./mitro ls ' + COMMON_CLI_ARGS + ' --uid=u1@example.com  ', True),
  (' ./mitro addmember ' + COMMON_CLI_ARGS + ' --uid u1@example.com --target_uid u3@example.com --gid 1  ', True),
  (' ./mitro addmember ' + COMMON_CLI_ARGS + ' --uid u1@example.com --target_uid u2@example.com --gid 2  ', True),
  (' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u1@example.com --gid 1 ', True),
  (' ./mitro add ' + COMMON_CLI_ARGS + ' --uid u1@example.com --gid 1 test.mitro.co2 mysecret2 mycritical2 ', True),
  (' ./mitro add ' + COMMON_CLI_ARGS + ' --uid u3@example.com --gid 1 test.mitro.co mysecret mycritical ', True),
  (' ./mitro ls ' + COMMON_CLI_ARGS + ' --uid=u1@example.com  ', True),
  (' ./mitro ls ' + COMMON_CLI_ARGS + ' --uid=u3@example.com  ', True),
  (' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u1@example.com --gid 1 ', True),
  (' ./mitro add --secretId 1 ' + COMMON_CLI_ARGS + ' --uid=u1@example.com --gid=2 ', True),
  (' ./mitro rmmember ' + COMMON_CLI_ARGS + ' --uid u1@example.com --target_uid u3@example.com --gid 1 ', True),
  (' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u1@example.com --gid 1 ', True),

  # secret id 2 is owned by group 1 {u1}
  # add u3 to g1
  ('./mitro addmember ' + COMMON_CLI_ARGS + ' --uid=u1@example.com --target_uid u3@example.com --gid 1 ', True),
  # add sid 2 to group 3 {owned by group 3} via u3's membership
  ('./mitro add --secretId 2 ' + COMMON_CLI_ARGS + ' --uid=u3@example.com --gid 3 ', True),
  ('./mitro cat ' + COMMON_CLI_ARGS + ' --uid=u1@example.com 1', True),
  ('./mitro cat ' + COMMON_CLI_ARGS + ' --uid=u2@example.com 1', True),
  ('./mitro cat ' + COMMON_CLI_ARGS + ' --uid=u1@example.com 2', True),

  # this secret does not exist, so this should fail.
  ('./mitro cat ' + COMMON_CLI_ARGS + ' --uid=u3@example.com 20', False),

  # we are permitted to remove u3 from group 1: The admins of group 3 can edit it
  ('./mitro rmmember ' + COMMON_CLI_ARGS + ' --uid u1@example.com --target_uid u3@example.com --gid 1', True),
  
  (' ./mitro addgroup ' + COMMON_CLI_ARGS + ' --uid=u1@example.com group4 ', True),
  (' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u1@example.com --gid 4 ', True),

  # try this with a bad password
  (' ./mitro getgroup ' + COMMON_CLI_ARGS.replace('=password', '=badpwd') + ' --uid u1@example.com --gid 4 ', False),

  # only users in a group may view them:
  #(' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u2@example.com --gid 4 ', False),
  #(' ./mitro getgroup ' + COMMON_CLI_ARGS + ' --uid u3@example.com --gid 4 ', False),
]

def log(s):
  print >> sys.stderr, s

def main(argv):
  commands = []
  only_fast_commands = (len(argv) == 2) and (argv[1] == FAST)
  is_fast = False
  for item in COMMANDS:
    if item == FAST:
      is_fast = True
    elif not only_fast_commands or is_fast:
      commands.append(item)
      is_fast = False

  for cmd, success in commands:
    log('running <<%s>>' % cmd)
    #TODO: this is a bit dangerous
    rval = subprocess.call(cmd, shell=True)
    if rval:
      assert not success
    else:
      assert success
    log('completed OK')
  else:
    log('SUCCESS!')
    if (only_fast_commands):
      log('***** WARNING: ONLY RAN FAST TESTS. THIS IS NOT A FULL REGRESSION TEST *****')
    return 0
if __name__ == '__main__':
  sys.exit(main(sys.argv))