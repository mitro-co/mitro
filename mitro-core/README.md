Mitro Core Server
==========


Coding Style
------------

* 2 space indent spaces only (In Eclipse: Project Properties -> Java Code Style -> Formatter -> Indentation)
* Generally follow the [Java coding guidelines](http://google-styleguide.googlecode.com/svn/trunk/javaguide.html)


Requirements
----------------------

* Java 7
* Postgres

Mac OS X
--------

1. [Download and install the Java 7 SE JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
2. In Eclipse: Eclipse -> Preferences -> Java -> Installed JREs -> Search; Check JRE 7
3. Install Postgres (the built-in version doesn't have initdb?): `brew install postgresql`
4. Run: `sudo vi /etc/sysctl.conf` to add the following lines:

```
# To run multiple instances of postgres
kern.sysv.shmmax=1610612736
kern.sysv.shmall=393216
kern.sysv.shmmin=1
kern.sysv.shmmni=32
kern.sysv.shmseg=8
```

Note: these values are from: http://www.postgresql.org/docs/9.2/static/kernel-resources.html http://benscheirman.com/2011/04/increasing-shared-memory-for-postgres-on-os-x/

5. Reboot or run `sudo sysctl -w (line)` for each of the above lines
6. `ant test` runs all unit tests. This should pass.


Running/Building
----------------

* (ONCE): Run `./build.sh` to set up a local Postgres database in `build/postgres`
* `postgres -D build/postgres` starts Postgres using that local database
* (ONCE): `psql -c 'create database mitro;' postgres` creates the database named `mitro`
* `ant server` builds and starts the server.
* Connect to https://localhost:8443/mitro-core/


Regression Test
---------------

We have a set of tests that use NodeJS to verify the JS client library and server together:

* Run `./build.sh` to install node dependencies and set up an empty Postgres database.
* Start Postgres: `postgres -D build/postgres`
* Run the test: `cd js/cli && ./runtests.sh`
* When it completes, scroll up and look for the word `SUCCESS`, above the Java stack trace.
