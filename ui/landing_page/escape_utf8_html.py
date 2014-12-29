import os
import sys
import tempfile

def escape_nonascii(filename):
    f = open(filename, 'r')
    contents = f.read().decode('utf-8')
    f.close()

    s = ''
    for char in contents:
        if ord(char) < 128:
            s += char
        else:
            s += '&#%d' % ord(char)
    return s

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print 'Usage: %s filename' % sys.argv[0]

    filename = sys.argv[1]
    tmpfile = tempfile.mktemp()

    f = open(tmpfile, 'w')
    f.write(escape_nonascii(filename))
    f.close()

    os.rename(tmpfile, filename)
