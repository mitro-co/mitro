Running the helpers test
========================

Please use one of the following commands to run the test:

    python test.py --browser chrome # for chrome
    python test.py --browser firefox # for firefox
    python test.py --browser safari # for safari *see notes below

* Running safari browser on a separate machine
---------------------------------------------

If you're about to run the test on a linux host, you have to use a separate computer running Windows or Mac OS
to launch the Safari browser. This can be a virtual machine as well. The selenium remoteDriver will be used to connect
your host machine with the one running the browser.

####Preparing *Windows* machine:

1. Install the Safari browser (http://support.apple.com/kb/dl1531)
2. Install the java runtime environment (https://java.com/en/download/index.jsp)

Note that you don't need to install the software manually if using Mac OS computer.
Both Safari browser and Java runtime are preinstalled on your system.

####Run the selenium server

Please use the following command to start the selenium server:

    java -jar path/to/the/selenium.server.jar
    
You'll find the selenium server jar in the /third_party/ directory

####Now you can pass the remote host's ip or domain name to the test script:

    python test.py --browser safari --selenium-server-host {selenium machine host or domain}

