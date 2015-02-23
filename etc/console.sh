#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*.jar | tail -1)"
MAIN=pif.arduino.ArdConsole
options="-cp $JAR"

if [ "$OSTYPE" = "cygwin" ] ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	JAR=$(cygpath -w "$JAR")
	// force terminal config and type
	options="$options -Djline.terminal=jline.UnixTerminal"
	stty -icanon min 1 -echo
	"$JAVA" $options $MAIN "$@"
	stty icanon echo
else
	exec java $options $MAIN "$@"
fi
