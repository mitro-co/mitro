Firefox extension
=================

Installing the Firefox Addon SDK
--------------------------------
You have to install the Firefox Addon SDK before you can build the Firefox extension. 
Please follow the link below for the instructions.

https://addons.mozilla.org/en-US/developers/docs/sdk/latest/dev-guide/tutorials/installation.html

*Please, activate the environment* before performing the further actions

Building the extension
----------------------
To build the extension, simply run one of the following commands:

    make firefox-release
    make firefox-debug
    make firefox-test

Installing the extension
------------------------
After building the extension, you'll find the packed extension file (.xpi) in the build directory. You can simply 
install that package on the 'Addons' screen in Firefox browser. 

The other way of running extension is to use an SDK command line sintax 
as following:

    cfx run #from within the extension build directory

This will open the browser window where the extension is already installed. This command uses the unpacked 
extension sources, not the packed .xpi file.