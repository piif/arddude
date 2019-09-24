Use Eclipse for Arduino programming
=====

The goal of this document is to explain how to configure Eclipse and CDT to use it for programming on arduino boards.
This method depends on ArdDude application and Arduino-cli tool (https://github.com/arduino/arduino-cli)

This howto has been tried with :
- Eclipse 2019-06 (4.12) and Arduino-cli 0.3.7-alpha.preview, under Linux Fedora 29

Requirements
-----

You need :
* Eclipse available at http://www.eclipse.org/downloads/
* This ArdDude package, available at https://github.com/piif/arddude

Install Arduino Cli
--
for details, look at https://github.com/arduino/arduino-cli

Install Eclipse with "CDT C/C++" plugin, or Eclipse-CDT version
--
For details, look at eclipse.org
CDT version shouldn't matter since we will only use "make" command, and no included toolchains

Install ArdDude
--

For the moment, there's no packaged version, so you have to generate it.
* You need java, git and maven
* clone git repo
  git clone https://github.com/piif/arddude.git
* compile and package sources
  mvn clean package
* zip is ready in target directory : target/ArdDude-(version).zip
  unzip it anywhere you want

Create a project
--

* Select menu "File -> New -> Project" then "C/C++ Project" in "C/C++" section
* Then choose type "Managed C/C++ project" (not "Makefile project")
* In the next widow, choose a name, select a project folder (in ~/Arduino if you want to stay compatible
  with Arduino IDE), choose the type "Empty project" in "Makefile project" section and leave empty
  the toolchain choice.
* Click "Finish" and your project should appear in the left panel (if not, you certainly have to edit your current working set).

Here, you have a project directory with two files : .project and .cproject
You may start to write your main .ino file, but all arduino functions and types (Serial, byte, Arduino.h inclusion ...)
will be marked as errors in the text editor.

Configure Arduino paths and symbols
--

To solve this problem, from command line, "cd" into this directory and :
* if you didn't create your main source file, create it empty :
  touch your_project_name.ino

* launch following command :
  make -f /path/to/arddude/etc/Makefile eclipse 

  This command will create a file named eclipse.settings.xml in your project directory

* Go back into eclipse, right click on your project and select Properties -> "C/C++ General" -> "Paths & symbols"
* Click "import settings", select the eclipse.settings.xml file path , select all and import. 

In your main source, declare Arduino header file
  #include <Arduino.h>

It's not necessary from Arduino IDE, but if you include it, Arduino IDE won't complain thus it's
compatible with twice IDEs.
Now, all arduino symbols should be ok, and auto-completion should work.

Add libraries
--

If your code relies on external libraries, you have to include their header files.
In order to enable syntax check and auto-completion on it, you need to run again previous step
(make .. eclipse + import settings)

Compile
--
In project properties windows, select "C/C++ build" and in "build command" type in
  make -C ${ProjDirPath} -f ${workspace_loc:ArdDude/etc/Makefile}

Select your source code in left panel then click on "build 'default'" (hammer icon)

Console
--
TODO ...
