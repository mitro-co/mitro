#!/usr/bin/env python

"""Combines a bunch of directories and Java jars into a single jar.
Sometimes called an uberjar.

Better approaches:

* Maven Shade http://maven.apache.org/plugins/maven-shade-plugin/
* Maven Assembly http://maven.apache.org/plugins/maven-assembly-plugin/

"""


import argparse
import tempfile
import shutil
import sys
import subprocess
import os


def unpack_jar(jarpath, tempdir):
    # extract quietly, no overwriting
    args = ['unzip', '-q', '-n']
    args.append(jarpath)
    args.extend(('-d', tempdir))
    process = subprocess.Popen(args)
    code = process.wait()
    assert code == 0


def copy_dir(subdir, tempdir):
    assert subdir[-1] != '/'
    subdir += '/.'
    assert tempdir[-1] != '/'
    tempdir += '/'
    # -a: archive mode; keeps permissions etc
    args = ['cp', '-a', subdir, tempdir]
    process = subprocess.Popen(args)
    code = process.wait()
    assert code == 0


def make_jar(outjar, jardir, main_class):
    # create a temporary manifest
    manifest = tempfile.NamedTemporaryFile()
    manifest.write('Main-Class: %s\n' % (main_class))
    manifest.flush()

    args = ['jar', 'cfm', outjar, manifest.name, '-C', jardir, '.']
    jar = subprocess.Popen(args)
    code = jar.wait()
    assert code == 0

    manifest.close()


def main():
    parser = argparse.ArgumentParser(description='Packages jars together into an uberjar.')
    parser.add_argument('path', nargs='*', help='Directory or jars to combine')
    parser.add_argument('--outjar', help='Output jar path', required=True)
    parser.add_argument('--main', help='Main class', required=True)
    parser.add_argument('--classpath', help='Java-style classpath to include')
    parser.add_argument('--verbose', default=False, type=bool, help='Enable logging output')

    args = parser.parse_args()

    if os.path.exists(args.outjar):
        sys.stderr.write('Error: output jar exists: %s\n' % (args.outjar))
        sys.exit(1)

    paths = list(args.path)
    if args.classpath:
        paths.extend(args.classpath.split(':'))
    if len(paths) == 0:
        sys.stderr.write('Error: no input paths specified\n')
        sys.exit(1)

    tempdir = tempfile.mkdtemp()
    try:
        # Unpack all jars or copy directories into the temporary directory
        for path in paths:
            if os.path.isdir(path):
                # copy the path into the jar
                if args.verbose:
                    print 'copy directory %s -> %s' % (path, tempdir)
                copy_dir(path, tempdir)
            else:
                if args.verbose:
                    print 'unpack jar %s' % (path)
                unpack_jar(path, tempdir)

        # Remove the META-INF directory; we will replace it
        metadata_path = tempdir + '/META-INF'
        if os.path.exists(metadata_path):
            shutil.rmtree(metadata_path)

        # Jar it up!
        make_jar(args.outjar, tempdir, args.main)
    finally:
        shutil.rmtree(tempdir)

    if args.verbose:
        print 'successfully created', args.outjar


if __name__ == '__main__':
    main()
