#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*.jar | tail -1)"
MAIN=pif.arduino.ArdConsole

if [ "$OSTYPE" = "cygwin" ] ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	JAR=$(cygpath -w "$JAR")
	options="-cp $JAR"

	## force terminal config and type since jline can't do it itself under cygwin
	options="$options -Djline.terminal=jline.UnixTerminal"
	TTY_STATE=$(stty -g)
	## but only if jline will be used ...
	IS_RAW=0
	for a in "$@" ; do
		if [ "$a" = "-r" -o "$a" = "--raw" ] ; then
			IS_RAW=1
			break;
		fi
	done
	if [ $IS_RAW -eq 0 ] ; then
		stty -icanon min 1 -echo
	fi
	"$JAVA" $options $MAIN "$@"
	stty $TTY_STATE
else
	options="-cp $JAR"
	exec java $options $MAIN "$@"
fi
