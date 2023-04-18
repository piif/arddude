#!/bin/bash

# uploads ArduinoISP example project to an Uno

HERE=$(cd $(dirname $0) ; /bin/pwd)
ARDDUDE=$(dirname $HERE)

if [ "X$1" = "X-u" ] ; then
    UPLOAD="upload"
else
    UPLOAD=""
fi

make -f $ARDDUDE/etc/Makefile -C $ARDDUDE/src/arduino/HV_Rescue_Simple $UPLOAD console
