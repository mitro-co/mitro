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
import hashlib
import shutil
import subprocess
import errno
import os

TMP_TEMPLATE = '/tmp/compilecache.%s'
try:
    from os.path import expanduser
    home = expanduser("~")
    newdir = os.path.join(home, '.ccache')
    try:
      os.mkdir(newdir)
    except:
      pass
    os.stat(newdir)
    TMP_TEMPLATE = os.path.join(newdir,'%s')
except Exception, e:
    print >> sys.stderr, 'create ~/.ccache directory for a longer-lived cache. Using /tmp instead...'

ARG_TYPE_UNKNOWN = 1
ARG_TYPE_IGNORE = 2
ARG_TYPE_INPUT = 3
ARG_TYPE_OUTPUT = 4

INPUT_ARGS = set(('--js', '--externs'))
OUTPUT_ARGS = set(('--js_output_file',))


def parse_inputs_outputs(args):
    inputs = []
    outputs = []

    next_arg_type = ARG_TYPE_UNKNOWN
    for value in args:
        current_arg_type = next_arg_type
        next_arg_type = ARG_TYPE_UNKNOWN
        if current_arg_type == ARG_TYPE_IGNORE:
            # Do nothing with this argument
            pass
        elif current_arg_type == ARG_TYPE_INPUT:
            inputs.append(value)
        elif current_arg_type == ARG_TYPE_OUTPUT:
            outputs.append(value)
        else:
            assert current_arg_type == ARG_TYPE_UNKNOWN
            if value.startswith('--'):
                # Looks like an argument
                # TODO: Support = in values
                assert '=' not in value
                if value in INPUT_ARGS:
                    next_arg_type = ARG_TYPE_INPUT
                elif value in OUTPUT_ARGS:
                    next_arg_type = ARG_TYPE_OUTPUT
                else:
                    next_arg_type = ARG_TYPE_IGNORE
            else:
                # Plain value: must be an input file
                inputs.append(value)

    return inputs, outputs


def main(args):
    # java -jar compiler.jar --js IN --js_output_file OUT
    use_cache = True
    use_cache = use_cache and (args[0] == 'java')
    use_cache = use_cache and (args[1] == '-jar')
    use_cache = use_cache and (args[2].endswith('/compiler.jar'))
    if not use_cache:
        raise Exception('Unknown command: ' + ' '.join(args))
        # print 'UNRECOGNIZED: %s' % ' '.join(args)
        # return subprocess.call(args)

    inputs, outputs = parse_inputs_outputs(args[3:])
    assert len(inputs) >= 1
    assert len(outputs) == 1
    js_output_file = outputs[0]

    # hash all arguments: any command line changes will invalidate the cache
    sha1 = hashlib.sha1()
    sha1.update(' '.join(args))

    # hash contents of all inputs
    for i in inputs:
        f = open(i, 'rb')
        sha1.update(f.read())
        f.close()

    sha_code = sha1.hexdigest()
    tmp_fn = TMP_TEMPLATE % sha_code
    try:
        if js_output_file == '/dev/null' and os.path.isfile(tmp_fn):
            # success, the file exists
            print 'CACHE:     compiled successfully %s' % (inputs)
            return
        shutil.copy(tmp_fn, js_output_file)
        print 'CACHE:     compiled %s->%s' % (inputs, js_output_file)
    except IOError, e:
        # ignore "not found" errors (cache miss), but raise any other errors
        if e.errno != errno.ENOENT:
            raise

        # no file found.
        subprocess.check_call(args)
        shutil.copy(js_output_file, tmp_fn)
        print 'UNCACHED:  compiled %s->%s (CACHED TO %s) ' % (inputs, js_output_file, tmp_fn)

if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))