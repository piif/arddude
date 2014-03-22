arddude
=======

Mix between arvdude and serial console, for arduino
Need java (to execute) and maven (to compile) + avrdude binary (can be found in
arduino sdk)

This code is made to run avrdude and swap to a serial console just after.

Call it with a avrdude full command line as arguments

It looks to avrdude command arguments to find serial port name and file name beeing uploaded.
Then, when this file changes (because of a build), it relaunch upload after closing serial console
and open it again after.

Thus, you just have to launch it once to update your arduino automatically, and stay connected to
its console.

Depends on rxtx-rebundled (http://dev.root1.de/projects/rxtx-rebundled) to embed native serial libs.

* to build it
  mvn clean install

* to launch it
  java -jar generated_jar [-f] /path/to/avrdude avrdude arguments ...
  use -f option to force an upload at startup
