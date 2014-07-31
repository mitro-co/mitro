#!/usr/bin/python

import json
import subprocess
import sys
import urllib


def print_validation(type_string, arg_string):
    v = json.loads(arg_string)

    if type_string == 'new_device_login':
        print v[0], v[1], v[2]
        print 'https://localhost:8443/mitro-core/user/VerifyDevice?' + urllib.urlencode({
            'user': v[0],
            'token': v[1],
            'token_signature': v[2],
        })
    elif type_string == 'address_verification':
        print 'https://localhost:8443/mitro-core/user/VerifyAccount?' + urllib.urlencode({
            'user': v[0],
            'code': v[1],
        })
    elif type_string == 'new_user_invitation':
        print 'http://www.mitro.co/install.html#' + urllib.urlencode({
            'u': v[1],
            'p': v[2],
        })
    else:
        print 'unknown type', type_string

def main():
    if len(sys.argv) == 3:
        type_string = sys.argv[1]
        arg_string = sys.argv[2]
        print_validation(type_string, arg_string)
        sys.exit(0)

    command = ('psql', 'mitro', '--tuples-only', '--no-align', '-c', 'select type_string,arg_string from email_queue where attempted_time is null order by id;')
    p = subprocess.Popen(command, stdout=subprocess.PIPE)
    lines = p.stdout.read()
    code = p.wait()
    assert code == 0

    for line in lines.split('\n'):
        if line == '':
            continue

        type_string, arg_string = line.split('|')
        print_validation(type_string, arg_string)
        print

if __name__ == '__main__':
    main()
