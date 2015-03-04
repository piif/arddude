Use Eclipse for Arduino programming
=====

The goal of this document is to explain how to configure Eclipse to use it for programming on arduino boards.
This method depends on ArdDude application and Arduino original IDE (to dispose of compilation/upload tools and board descriptors in a standard directory organisation)

This howto has been tried with Luna 4.4.1 and Arduino IDE 1.6.0, under Windows 7 and Linux Fedora 20

Requirements
-----

You need :
* Eclipse (at date, last stable version is Luna 4.4.1) available at http://www.eclipse.org/downloads/
* Arduino IDE  (at date, last stable version is 1.6.0) available at http://arduino.cc/en/Main/Software
* ArdDude package, available at https://github.com/piif/arddude

Install Arduino IDE
--
for details, look at arduino.cc)

Install Eclipse (for details, look at eclipse.org)
--

Install ArdDude
--

Once you got the ArdDude.zip file, you just have to unzip ot where you want
For the moment, there's no web site where to download this zip file, so you have to generate it.
* You need java, git and maven
* clone git repo
  git clone https://github.com/piif/arddude.git
* compile and package sources
  mvn clean package
* zip is ready in target directory

Configure Eclipse
--

* from Help menu, choose "Eclipse Marketplace", search "Eclipse CDT" and install it
* restart Eclipse
* Windows > Preferences >  C/C++ > Build > environment
	* add ARDUINO_IDE = where Arduino IDE is installed
	* add AMM_DIR = where ArdDude zip file was unzipped
	* if you're under Windows, add MAKE = ${ARDUINO_IDE}/hardware/arduino/sam/system/CMSIS/Examples/cmsis_example/gcc_arm/make.exe
	* if you're underlinux, install gnu make and simply define MAKE = make
* Windows > Preferences > General > Editors > File associations :
	* File types, add, *.ino
	* Associated Editors, add, C/C++ Editor
* Windows > Preferences > C/C++ > File types :
	* add *.ino =  C++ source file
* Windows > Preferences > C/C++ >  Build > Settings > folder Discovery , entry "CDT GCC Built-in compiler settings" :
	set "Command to get compiler specs" to :
	${MAKE} TARGET_BOARD=${ConfigName} CMD="${COMMAND} ${FLAGS} -E -P -v -dD '${INPUTS}'" discovery

Create a new project
--

* New project > C/C++ > "Makefile project from existing code"
* Project properties -> CDT
	* C/C++ build, folder "Builder settings", configuration "All configurations"
		* unset "use default build command"
		* Replace command by : ${MAKE} TARGET_BOARD=${ConfigName}
		* unset "generate Makefiles"
		* set "Build directory" to : ${workspace_loc}/
	* C/C++ build > Toolchain Editor, configuration "All configurations"
		* unset "Display compatible …."
			* Current Toolchain : Choose one of GCC chains, according to your environment
			  (will not be used, but alows eclipse to fill other prefenrences with target languages)
			* Current builder : Gnu make builder
	* C/C++ general -> Preprocessor Include... > folder Providers
		* unset "Managed Build Setting Entries" (really ?)
		* set "Built-in Compiler Settings", then select it to access to its details
		*unset "Use global provider ...", but let the same command line (or ${ConfigName} will be empty)
	* C/C++ build, button "Manage configurations"
		* Select "default" and click "Rename". Rename in your target platform name (ie "uno" lower case)
		* Create other entries with your other target platforms if needed
		  To obtain the exact name of each platform, from ArdDude directory, launch etc/mkmk.sh -B (or .bat)
	* Project properties > C/C++ general > indexer :
		* set "Enable specific ..." and "Enable indexer"
		* on bottom, set "Use active build configuration"

Create your Makefile
--
* Create a file names Makefile (with upper M) in project root directory.
* open it
* you must at least type in
  include ${AMM_DIR}/etc/Makefile.main
* before this line, you can fix some variables like default upload port, main program name, dependencies
  details to be continued ...

Write your code
--
* switch to C/C++ perspective (Window > Open perspective > Other -> C/C++
* Create your source fileswith .c, .cpp or .ino extension
* Right click on project > Build configuration > Set active
  select desired target platform 
  #define and specific variables should update according to this platform
  (ie PORTD register variable will be marked as error on Due platform, but not on Uno platform) 
* Compile them by clicking on "build" button
  a .hex file should be generated into target/xxx directory (xxx = target platform)
  On first build, core libraries should be compiled in to ArdDude target directory

The code is not pre-compiled by Arduino, thus you have to write real C code. Mainly :
- pathes in #include statements must be detailed relative to project root
- methods and variables must be declared before to be used
 
Upload it
--	
ArdDude project contains a etc/console.sh (or .bat) script to connect to yout arduino and upload code
from "cmd" or cygwin console (under windows), or terminal (under linux) launch :
	etc/console.sh -b your_board -p your_port -I arduino_ide_path -f path_to_your_target_file -u
"-u" forces upload at launch
Then, console will connect to serial port AND scan target file updates
If you launch again "build" from Eclipse, upload is relaunched automatically then console connects again

To remake indexes
--
Sometime, some (or all) platform specific references (registers, includes, arduino methods) are marked as error
Three solutions to solve this problem :
* rebuild index : right click on project > Index > Rebuild or Refresh or other entries
* Project properties > C/C++ general > indexer :
	unset indexer, apply, set back indexer, apply
* Indexer depends on files generated from "discovery" which itself depends on ArdDude makefile rules, but there's nothing to force discovery to relaunch
	in Project properties > C/C++ general > "Preprocessor includes ..."
	* Folder Providers, entry "Built in compiler settings"
		set  "use global ..", apply, unset, apply

