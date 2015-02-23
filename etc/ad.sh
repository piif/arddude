#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

## force a list of serial names to make rxtx find them
## see https://bugs.launchpad.net/ubuntu/+source/rxtx/+bug/367833/comments/6
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
if [ "$OSTYPE" = "cygwin" ] ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	JAR=$(cygpath -w "$JAR")
	// force terminal config and type
	$options="$options -Djline.terminal=jline.UnixTerminal"
	stty -icanon min 1 -echo
	"$JAVA" $options -jar "$JAR" "$@"
	stty icanon echo
else
	exec java $options -jar "$JAR" "$@"
fi
