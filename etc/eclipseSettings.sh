#!/bin/bash

includeList=$1
macrosList=$2

INCLUDES="$(sed "s%.*%			<includepath>&</includepath>%" $includeList)"
MACROS="$( \
  grep -v '_h *$' $macrosList \
  | sed \
	-e 's/&/\&amp;/g' \
	-e 's/>/\&gt;/g' \
	-e 's/</\&lt;/g' \
	-e 's%#define \([^	 ]*\)[	 ]*\(.*\)%			<macro><name>\1</name><value>\2</value></macro>%'\
)"

cat << EOF
<?xml version="1.0" encoding="UTF-8"?>
<cdtprojectproperties>
	<section name="org.eclipse.cdt.internal.ui.wizards.settingswizards.IncludePaths">
		<language name="holder for library settings"> </language>
		
		<language name="Assembly">
		</language>
		
		<language name="GNU C++">
$INCLUDES
		</language>
		
		<language name="GNU C">
		</language>
	</section>

	<section name="org.eclipse.cdt.internal.ui.wizards.settingswizards.Macros">
		<language name="holder for library settings"> </language>
		<language name="Assembly">
		</language>
	
		<language name="GNU C++">
$MACROS
		</language>
	
		<language name="GNU C">
		</language>
	</section>
</cdtprojectproperties>
EOF
