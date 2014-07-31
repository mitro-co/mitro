#!/usr/bin/python

"""Reformat source according to our rules."""

import argparse
import os
import re
import stat
import sys

# Configuration parameters
TAB_INDENT = 2
LINE_LENGTH_LIMIT = 100
SOURCE_SUFFIXES = (".java", ".cc", ".cpp", ".py", ".js")

_TAB_EXPANSION = " " * TAB_INDENT


def matchesSuffixes(path, suffixes):
    """Returns True if path ends with one of the suffixes in suffixes."""

    for suffix in suffixes:
        if path.endswith(suffix):
            return True
    return False


def recursiveSuffixes(start_path, suffixes):
    if not os.path.isdir(start_path):
        # A single file passed as an argument always matches, ignoring suffix
        yield start_path
    else:
        stack = [start_path]

        while len(stack) > 0:
            dirpath = stack[-1]
            del stack[-1]
            for path in os.listdir(dirpath):
                path = os.path.join(dirpath, path)
                if os.path.isdir(path):
                    stack.append(path)
                elif matchesSuffixes(path, suffixes):
                    yield path

JAVADOC_CONTINUATION_RE = re.compile("^(\s*) \*")
def countLeadingSpaces(s):
    # lstrip strips a solo newline, which isn't what we want
    if s == "\n": return 0

    # Javadoc continuations with " *" are permitted
    # TODO: This is too permissive; check we are in Javadoc?
    match = JAVADOC_CONTINUATION_RE.match(s)
    if match:
        # print "wtf?", repr(s), repr(match.group(1)), len(match.group(1))
        # Return the number of spaces before the last " *"
        return len(match.group(1))

    return len(s) - len(s.lstrip())


HTTP_RE = re.compile("(http://[^ ]+)")
JDBC_RE = re.compile("(jdbc:[^ ]+)")
def isLineTooLong(line):
    if len(line) <= LINE_LENGTH_LIMIT:
        return False

    # Permit length violations for long URLs
    match = HTTP_RE.search(line)
    if not match:
        match = JDBC_RE.search(line)
    # TODO: verify that the URL is the part that is too long!
    # if match and len(match.group(1)) > LINE_LENGTH_LIMIT:
    if match:
        return False
    return True


BAD_SPACING_RE = re.compile('[\S]  ')
# yuck: we shouldn't parse strings with regular expressions
STRING_RE = re.compile('"[^"]+"')

def lineHasBadSpacing(line):
    '''Returns true if line has spaces in the middle.'''

    match = BAD_SPACING_RE.search(line)
    if match:
        # Attempt to ignore spaces in strings: remove strings
        # This is broken for quoted strings eg: "quote: \" bad space:  "
        line = STRING_RE.sub('""', line)
        if BAD_SPACING_RE.search(line):
            return True

        # After removing strings we didn't find bad spacing? Okay then
        return False
    return False


def atomic_write(path, contents):
    # Atomically replace the file, preserving permissions
    # TODO: preserve ownership?
    original_permissions = stat.S_IMODE(os.stat(path).st_mode)

    temp = path + ".tmp"
    f = open(temp, "w")
    f.write(contents)
    f.close()

    os.chmod(temp, original_permissions)
    os.rename(temp, path)


def cleanWhitespace(path, should_fix):
    f = open(path)
    lines = f.readlines()
    f.close()

    warned = False
    changed = False
    for i, line in enumerate(lines):
        # Expand tabs
        if "\t" in line:
            changed = True
            line = line.replace("\t", _TAB_EXPANSION)

        # Strip trailing whitespace
        trailing = line.rstrip() + "\n"
        if trailing != line:
            print "%s:%d has trailing whitespace" % (path, i+1)
            changed = True
            line = trailing

        # Warn on funny indent
        if countLeadingSpaces(line) % TAB_INDENT != 0:
            print "%s:%d indent is %d; not a multiple of %d" % (
                    path, i+1, countLeadingSpaces(line), TAB_INDENT)

        # Warn on bad spacing in the middle of the line
        if lineHasBadSpacing(line):
            print "%s:%d two spaces in the middle of the line" % (
                    path, i+1)

        # Warn on long lines
        if not warned and isLineTooLong(line):
            warned = True
            print "%s:%d line length exceeds limit (%d > %d)" % (
                    path, i+1, len(line), LINE_LENGTH_LIMIT)

        lines[i] = line
        line = line

    if changed and should_fix:
        atomic_write(path, "".join(lines))
    return changed


def convert_indentation(path, source_indent, destination_indent):
    assert source_indent != destination_indent

    f = open(path)
    lines = f.readlines()
    f.close()

    destination_spaces = " " * destination_indent

    # Check that the file is indented with source_ident
    error = False
    changed = False
    for i, line in enumerate(lines):
        spaces = countLeadingSpaces(line)

        if countLeadingSpaces(line) % source_indent != 0:
            print "%s:%d indent is not a multiple of %d" % (
                path, i+1, source_indent)
            error = True
            break

        if "\t" in line:
            print "%s:%d contains raw tabs; not converting" % (path, i+1)
            error = True
            break

        assert spaces % source_indent == 0
        assert "\t" not in line
        indent = spaces / source_indent
        if indent > 0:
            changed = True
            lines[i] = (destination_spaces * indent) + line[spaces:]

    if error:
        print "%s does not appear to be indented by %d spaces; not changing" % (
            path, source_indent)
        return

    if changed:
        atomic_write(path, "".join(lines))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Fix/check whitespace in files.')
    parser.add_argument('--fix', action='store_true', help='attempt to fix the errors')
    parser.add_argument('--num_spaces', default=2, type=int, help='number of spaces to indent')
    parser.add_argument('path', nargs='+', help='file/directory to search for source code')
    parser.add_argument('--orig_spaces', default=None, type=int, help='re-indent from orig_spaces to num_spaces')
    args = parser.parse_args()

    # TODO: Create an object?
    TAB_INDENT = args.num_spaces
    _TAB_EXPANSION = " " * TAB_INDENT

    error_count = 0
    for target in args.path:
        for source_file in recursiveSuffixes(target, SOURCE_SUFFIXES):
            if cleanWhitespace(source_file, args.fix):
                error_count += 1
            if args.orig_spaces:
                convert_indentation(source_file, args.orig_spaces, args.num_spaces)
    sys.exit(error_count)
