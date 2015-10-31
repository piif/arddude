arddude next generation
=======

This new version of ArdDude is the base for a complete Arduino tool chain.
It needs Arduino IDE to be installed (tested on version 1.6) to find compilers and boards descriptors and tools to read them.

This project contains 2 parts :

ArdConsole
-----

This is a new version of old ArdDude utility.
It's goal is to connect to Arduino serial monitor, and be able to upload new sketch and reconnect just after.
It needs Arduino IDE installation path (under windows, it looks for default one) and is able to :
* dump available serial ports (and find Arduino type connected on it when possible)
* dump available boards (native ones, or user supplied ones in sketchbook/hardware directory)
* connect to port
* display incomming data as raw, ascii or hex dump
* send data from input in hex or raw

run etc/console.sh or etc\console.bat with --help option for more information


MakeMake
-----

This utility is made to be called from makefiles available in etc directory.
It generates Makefile rules from arduino descriptor files, to run compilation tools included in Arduino IDE
Those makefiles are ready to launch from Eclipse environment, or from command line.

Documentation about this tool chain is still uncomplete, but Eclipse.Howto.md file give indication on how to use it from Eclipse IDE.

TODO
----
* auto-completion in ArdConsole
* deep testing
* test console from dos, cygwin ... -> see https://github.com/mintty/mintty https://github.com/git-for-windows/git/blob/master/compat/winansi.c (isatty) and https://github.com/mintty/mintty/issues/56
 
Known issues
----
* have to reset line at ArdConsole launch from linux (at least with uno)
