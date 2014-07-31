Safari extension
================

Building the extension
----------------------
To build the extension, simply run one of the following commands:

    make safari-release
    make safari-debug
    make safari-test

Installing the extension
------------------------
#####Safari developer certificate
In order to be able to install the extension and/or build a packed extension file, you'll need to get the Safari developer certificate. 
Simply sign up to the [safari developer program](https://developer.apple.com/programs/safari/) and follow the further instructions.
After obtaining the certificate, you'll need to install the certificate into the system in a way corresponding to the OS you use.

#####Installing into the browser
All the manipulations with the safari extension should be done using the Extension builder from inside the safari browser. Please follow the steps below to get started:

1. Activate the develop menu by checking the appropriate checkbox on the "Advanced" tab of the safari preferences window.
2. Open the Extension builder from the develop menu.
3. Press the "+" sing and specify the directory containing the extension code by selecting the "Add extension" option.
4. Press the "Install" button.
5. Press the "Reload" button every time you need to refresh the extension.

Building the packed extension file
----------------------------------
Simply hit the "Build package..." button in the Extension builder window to build the .safariextz file. The command line building script is coming.