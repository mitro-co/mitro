browser-ext
===========

Setup
=====

1. Mac: [Install Homebrew](http://mxcl.github.io/homebrew/). Run `brew install node` or download node from the web. (apt-get is too old).

2. For Firefox: Install [Firefox Addon-sdk 1.6+](https://github.com/mozilla/addon-sdk/tree/05dab6aeb50918d4c788df9c5da39007b4fca335) and [toolbarwidget](https://github.com/Rob--W/toolbarwidget-jplib)

3. Get node dependencies, crypto and browser extensions build dependencies (run this once)

        git clone git@github.com:mitro-co/browser-ext.git
        sh browser-ext/api/build.sh
	sh browser-ext/login/build.sh

4. Build

        cd browser-ext/login && make

5. Go to [chrome://extensions](chrome://extensions). Check the developer mode box.

6. Click Load unpacked extension -> `browser-ext/login/build/chrome/release`



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
