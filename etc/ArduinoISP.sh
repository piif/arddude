#!/bin/bash

ICI=$(dirname $0)

$ARDUINO_IDE/hardware/tools/avr/bin/avrdude -C$ARDUINO_IDE/hardware/tools/avr/etc/avrdude.conf -v -patmega328p -carduino -P/dev/ttyACM0 -b115200 -D -Uflash:w:$ICI/ArduinoISP.cpp.hex:i 

