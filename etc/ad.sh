#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

CLASSPATH="$(grep "lib" $ROOTDIR/.classpath |cut -d '"' -f 4|tr '\n' :)$ROOTDIR/bin"


options=
if expr $(uname) : Linux > /dev/null ; then
	for i in $(seq 0 20) ; do
		ttys="$ttys:/dev/ttyS$i:/dev/ttyUSB$i:/dev/ttyACM$i"
	done 
else
	for i in $(seq 1 30) ; do
		ttys="$ttys;com$i"
	done 
fi
options="-Dgnu.io.rxtx.SerialPorts=$ttys"

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*.jar | tail -1)"
if expr $(uname) : CYGWIN > /dev/null ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	JAR=$(cygpath -w "$JAR")
else
	JAVA=java
fi


exec "$JAVA" $options -jar "$JAR" "$@"
