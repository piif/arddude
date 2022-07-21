#!/bin/bash

HERE="$(cd $(dirname $(realpath $0)) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*-shaded.jar $ROOTDIR/lib/arddude.jar 2> /dev/null | tail -1)"
MAIN=pif.arduino.ArdConsole

if [ "$OSTYPE" = "cygwin" ] ; then
	if [ -z "$JAVA" ] ; then
		JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	fi
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
	if [ -z "$JAVA" ] ; then
		if which java > /dev/null 2>&1 ; then
			JAVA=$(which java)
		elif [ -e $ARDUINO_IDE/bin/java ] ; then
			JAVA=$ARDUINO_IDE/bin/java
		else
			echo "Can't find java path, set JAVA variable"
			exit 1
		fi
	fi
	options="-cp $JAR:$(ls $ARDUINO_IDE/lib/*jar 2> /dev/null| tr '\n' ':')"
	exec $JAVA $options $MAIN "$@"
fi
