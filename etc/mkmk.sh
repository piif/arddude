#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*.jar | tail -1)"
MAIN=pif.arduino.MakeMake

if [ "$OSTYPE" = "cygwin" ] ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
	JAR=$(cygpath -w "$JAR")
	options="-cp $JAR"
	"$JAVA" $options $MAIN "$@"
else
	options="-cp $JAR"
	exec java $options $MAIN "$@"
fi
