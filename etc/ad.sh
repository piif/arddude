#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

CLASSPATH="$(grep "lib" $ROOTDIR/.classpath |cut -d '"' -f 4|tr '\n' :)$ROOTDIR/bin"

if expr $(uname) : CYGWIN > /dev/null ; then
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
else
	JAVA=java
fi

JAR="$(/bin/ls -d1 $ROOTDIR/target/ArdDude-*.jar | tail -1)"

exec "$JAVA" -jar "$JAR" "$@"
