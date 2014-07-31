#!/bin/bash
# Create standalone tar for Google Apps syncing
# This is really, really gross, but it gets the job done for the moment

set -e
set -x

mkdir mitro-sync

# Build and copy the jar
(cd ../tools/java && ant dist-jar)
cp ../tools/java/build/mitro-sync-start-master-[0-9a-f]*.jar mitro-sync/mitro-sync-start-1.0-SNAPSHOT.jar

# Copy the JS stuff
cp -r ../../browser-ext/api/js/cli mitro-sync

# Copy the node modules
cp -r ../../browser-ext/api/build/node/lib/node_modules mitro-sync/cli
# Don't need webworkers (binary dependency)
rm -rf mitro-sync/cli/node_modules/webworker-threads


# Copy README
cp README mitro-sync
echo '''<!DOCTYPE html><html><head></head><body>''' > mitro-sync/README.html
./markdown2.py < README >> mitro-sync/README.html
echo '</body></html>' >> mitro-sync/README.html

# Copy python scripts
cp sync.py setup.py mitro-sync

# Tar!
tar cvjf mitro-sync.tar.bz2 --exclude */.git/* mitro-sync
