arddude
=======

Mix between arvdude and serial console, for arduino

This code is made to run avrdude and swap to a serial console just after.

Call it with a avrdude full command line as arguments

It looks to avrdude command arguments to find serial port name and file name beeing uploaded.
Then, when this file changes (because of a build), it relaunch upload after closing serial console
and open it again after.

Thus, you just have to launch it once to update your arduino automatically, and stay connected to
its console.

TODO : update shell and maven build to generate a full standalone jar and run it from command line.
