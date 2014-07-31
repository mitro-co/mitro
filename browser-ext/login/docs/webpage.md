Webpage implementation
======================

Building the extension
----------------------
To build the extension, run one of the following commands:

    make webpage (same as 'make webpage-release')
    make webpage-debug
    make webpage-test

Running the extension
----------------------
In order to run the webpage implementation locally you need to use the static server
because the ajax functions will not work if open the page via file:// interface.

    cd [project-root]
    python server/server.py --static-root ../build/webpage/[release/debug/test] --port [port-number]

After this you'll be able to access the extension using http://localhost:[port-number]

Also you have to start the browser with the Same Origin Policy switched off. 
For Chrome browser you can achieve that by launching the browser from command line like:

    [path-to-chrome-executable] --disable-web-security &

