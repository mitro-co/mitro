browser-ext
===========

Setup
=====

1. Install [nodeJS](http://nodejs.org/) and its package manager [npm](https://www.npmjs.org/). 

2. Get node dependencies and crypto dependencies (run this once). 

        git clone git@github.com:mitro-co/browser-ext.git
        cd browser-ext/api; ./build.sh

   (Sometimes you can incur in [this error](http://plaidzooks.tumblr.com/post/36894116867/using-node-js-on-debian-ubuntu-systems))

3. Build

        cd browser-ext/login && make

4. Go to [chrome://extensions](chrome://extensions). Check the developer mode box.

5. Click Load unpacked extension -> `browser-ext/login/build/chrome/release`



If you want to run regression tests:
====================================

This requires server code.

1. Checkout dependencies in the directory above `browser-ext`:

        git clone git@github.com:mitro-co/mitro-core.git

2. Symlink mitro-core to `browser-ext/api/server`:

        ln -s ../../mitro-core/ browser-ext/api/server

3. Run regression tests:

        cd api/js/cli && ./runtests.sh


Notes
=====

We can't use symlinks to edit files in place because Chrome does not load symlinked resources:

http://code.google.com/p/chromium/issues/detail?id=27185
