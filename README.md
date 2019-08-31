arddude third generation
=======

This rework of ArdDude is made to work with arduino-cli software (https://blog.arduino.cc/2018/08/24/announcing-the-arduino-command-line-interface-cli/)
and arduino-builder (https://github.com/arduino/arduino-builder)
It needs arduino-cli to be installed; arduino IDE is no more used at all.

This project contains 2 parts :

ArdConsole
-----

This tool is an enhanced serial console, with :
* meta command to connect or change serial port or baudrate
* ability to scan changes in a file and to upload it automatically (no need to disconnect, launch upload, reopen serial monitor)
* display incomming data as raw, ascii or hex dump
* send data from input in hex or raw

run etc/console.sh or etc\console.bat with --help option for more information
  TODO : update these files


Makefile
-----

This makefile is a generic one to compile, upload, launch console.
It can be used from any arduino project to compile it and collaborate with ArdDude

*** explain command line "-f ..." , variables , makefile.def

TODO
----
About serial console :

* auto-completion in ArdConsole ?
* deep testing
* test console from dos, cygwin, MacOS ... -> see https://github.com/mintty/mintty https://github.com/git-for-windows/git/blob/master/compat/winansi.c (isatty) and https://github.com/mintty/mintty/issues/56
* test with arduino variants (ATtiny, Yun) or other platforms (ESP)

Known issues
----
* inline help is out to date
* shell and bat helper are out to date
* blank lines are displayed after each serial input
