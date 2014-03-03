#!/bin/bash

HERE="$(cd $(dirname $0) ; /bin/pwd)"
ROOTDIR="$(dirname "$HERE")"

# TODO : que faire de ce chemin, s'il faut reprendre une ligne de commande de toutes façons ?
AVRTOOLS=/SRC/Arduino/hardware/tools/avr

COM="$1"
SRC="$2"
shift 2

CLASSPATH="$(grep "lib" $ROOTDIR/.classpath |cut -d '"' -f 4|tr '\n' :)$ROOTDIR/bin"

if expr $(uname) : CYGWIN > /dev/null ; then
	convPath() {
		echo "$1" | tr ":" "\n" | while read l ; do
			cygpath -w "$l"
		done | tr "\n" ";"
	}
	CLASSPATH="$(convPath "$CLASSPATH")"
	
	JAVA="$(/bin/ls -d1 /cygdrive/c/Program*/Java/*/bin/java | tail -1)"
else
	JAVA=java
fi

JAR="$(/bin/ls -d1 target/ArdDude-*.jar | tail -1)"

"$JAVA" -classpath "$CLASSPATH" -jar "$JAR" -scan "$SRC" "-P$COM" "$AVRTOOLS/bin/avrdude.exe" "-Uflash:w:$SRC:a" -C$AVRTOOLS/etc/avrdude.conf "$@"
