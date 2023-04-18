#!/bin/bash

# uploads ArduinoISP example project to an Uno

HERE=$(cd $(dirname $0) ; /bin/pwd)
ARDDUDE=$(dirname $HERE)

make -f $ARDDUDE/etc/Makefile -C $ARDDUDE/src/arduino/ArduinoISP upload
