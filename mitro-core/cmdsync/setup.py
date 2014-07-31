#!/usr/bin/python

import errno
import getpass
import json
import os
import signal
import subprocess
import sys


MITRO_HOST = 'www.mitro.co'
MITRO_PORT = 443
TEST_SERVER = tuple()
IDENTITY_FILE = 'identity.json'
JAVA_COMMAND = ('java', '-ea', '-cp', 'mitro-sync-start-1.0-SNAPSHOT.jar')
COMMAND_TIMEOUT_S = 300

# Test config
# MITRO_HOST = 'localhost'
# MITRO_PORT = 8443
# TEST_SERVER = ('--test_server', 'true')


# TODO: This should be the domain not the mitro user?
GROUP_SOURCE_ID = 'gapps://example.com/'


def communicate_with_timeout(process, stdin_data):
    stdout_data = None
    stderr_data = None

    # Set a timer to kill the subprocess after a timeout
    # On SIGALRM, system calls *usually* raise IOError with EINTR, but not reliably. Maybe Python's
    # libraries retry? So we explicitly kill the process
    alarm_triggered = []  # dict to workaround Python's fun scoping rules
    def _wakeup(signal, stack):
        process.kill()
        alarm_triggered.append(True)
    previous_handler = signal.signal(signal.SIGALRM, _wakeup)
    try:
        signal.alarm(COMMAND_TIMEOUT_S)

        # Write/read data
        stdout_data, stderr_data = process.communicate(stdin_data)

    except IOError, e:
        # Ignore EINTR caused by the alarm signal
        if not (len(alarm_triggered) > 0 and e.errno == errno.EINTR):
            raise
    finally:
        # Restore the original SIGALRM handler
        signal.alarm(0)
        signal.signal(signal.SIGALRM,  previous_handler)

    if len(alarm_triggered) > 0:
        # interrupted: timeout was triggered
        sys.stderr.write('Error: Timeout waiting for process (killed)\n')

    return stdout_data, stderr_data


def mitro(identity, password, args, stdin_data=None):
    command = ('node', 'cli/mitro.js', '--server_host', MITRO_HOST,
            '--server_port', str(MITRO_PORT), '--uid', identity, '--password', password)
    command += TEST_SERVER
    command += args
    # print ' '.join(command)

    # The node scripts depend on webworkers, but we don't generate keys so it should be fine
    os.environ['DISABLE_WEBWORKERS'] = 'true'

    stdin = None
    if stdin_data is not None:
        stdin = subprocess.PIPE
    m = subprocess.Popen(command, stdout=subprocess.PIPE, stdin=stdin)

    output, stderr_data = communicate_with_timeout(m, stdin_data)

    code = m.wait()
    return output, code == 0


def oauth():
    command = JAVA_COMMAND + ('co.mitro.core.sync.google.OAuth',)
    p = subprocess.Popen(command)
    stdout, stderr = communicate_with_timeout(p, None)
    code = p.wait()
    return code == 0


def sync():
    command = JAVA_COMMAND + ('co.mitro.core.sync.google.Sync',)
    p = subprocess.Popen(command, stdout=subprocess.PIPE)
    output, stderr = communicate_with_timeout(p, None)
    code = p.wait()
    assert code == 0

    return output


def save_identity(identity, password, private_group_id):
    f = open(IDENTITY_FILE, 'w')
    data = {'identity': identity, 'password': password, 'private_group_id': private_group_id}
    json.dump(data, f)
    f.close()


def load_identity():
    f = open(IDENTITY_FILE, 'r')
    data = json.load(f)
    f.close()

    return data['identity'], data['password'], data['private_group_id']


def main():
    print 'Enter Mitro identity used to synchronize groups:',
    identity = raw_input()
    password = getpass.getpass('Password for %s: ' % (identity))

    # Verify credentials using the command line tool
    success = False
    while not success:
        print 'Verifying identity ...'
        output, success = mitro(identity, password, ('ls',))
        if not success:
            if '"DoEmailVerificationException"' in output:
                # User must verify by clicking the link
                print 'Please click the verification link in your email, then press enter.'
                out = raw_input()
            else:
                sys.stderr.write('Error: Incorrect username/password?\n')
                return 1

    # Parse the output to find the user's private group
    json_string = output[output.index('{'):]
    data = json.loads(json_string)
    private_group_id = None
    for group_id, group in data['groups'].iteritems():
        if group['name'] == '':
            assert not group['autoDelete']
            private_group_id = group['groupId']
            break
    if private_group_id is None:
        sys.stderr.write('Error: could not find private group\n')
        return 1

    print 'Verified! writing to %s' % (IDENTITY_FILE)
    save_identity(identity, password, private_group_id)

    success = oauth()
    if not success:
        sys.stderr.write('Error: Failed to get credentials from Google\n')
        return 1

    return 0


if __name__ == '__main__':
    # Set up 
    code = main()
    if code != 0:
        sys.exit(code)
