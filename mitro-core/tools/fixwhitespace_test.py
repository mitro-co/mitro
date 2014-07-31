#!/usr/bin/env python

import os
import tempfile
import unittest
import stat

import fixwhitespace


class TestWhitespaceInLine(unittest.TestCase):
    def testBadEquals(self):
        self.assertTrue(fixwhitespace.lineHasBadSpacing('  if (foo  == 42) {'))
        self.assertFalse(fixwhitespace.lineHasBadSpacing('  if (foo == 42) {'))

    def testMessageInString(self):
        self.assertFalse(fixwhitespace.lineHasBadSpacing(
            '  print "some string", "  this string has leading space"'))


class ConvertWhitespace(unittest.TestCase):
    def test_warn_bad_spacing(self):
        DATA = '  two spaces\n'
        self.temp = tempfile.NamedTemporaryFile()
        self.temp.write(DATA)
        self.temp.flush()

        fixwhitespace.convert_indentation(self.temp.name, 4, 2)
        # the file should not have been changed
        self.temp.seek(0)
        self.assertEquals(DATA, self.temp.read())


class KeepPermissions(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.NamedTemporaryFile()
        self.temp.write('  trailing whitespace  \n')
        self.temp.flush()

        self.assertEquals(0600, stat.S_IMODE(os.stat(self.temp.name).st_mode))
        os.chmod(self.temp.name, 0700)

    def tearDown(self):
        self.temp.close()
        if os.path.exists(self.temp.name):
            os.remove(self.temp.name)

    def test_clean_permissions(self):
        self.assertEquals(0700, stat.S_IMODE(os.stat(self.temp.name).st_mode))
        changed = fixwhitespace.cleanWhitespace(self.temp.name, True)
        self.assertTrue(changed)
        self.assertEquals(0700, stat.S_IMODE(os.stat(self.temp.name).st_mode))

    def test_atomic_write_permissions(self):
        self.assertEquals(0700, stat.S_IMODE(stat.S_IMODE(os.stat(self.temp.name).st_mode)))
        fixwhitespace.atomic_write(self.temp.name, 'some contents')
        self.assertEquals(0700, stat.S_IMODE(os.stat(self.temp.name).st_mode))

    def testRecursiveSuffixesSingleFile(self):
        # Passing a single file to recursiveSuffixes should always return this file
        files = list(fixwhitespace.recursiveSuffixes(
            self.temp.name, fixwhitespace.SOURCE_SUFFIXES))
        self.assertEquals([self.temp.name], files)


if __name__ == '__main__':
    unittest.main()
