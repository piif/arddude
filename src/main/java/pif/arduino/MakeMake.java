package pif.arduino;

import java.io.*;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;

import pif.arduino.tools.ArduinoConfig;
import pif.arduino.tools.LoadConfig;
import processing.app.BaseNoGui;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPlatform;
import processing.app.helpers.PreferencesMap;
import processing.app.helpers.StringReplacer;

public class MakeMake {
	private static Logger logger = Logger.getLogger(MakeMake.class);

	static Options options;
	static {
		options = new Options();
		options.addOption("h", "help", false, "usage");
		options.addOption("I", "arduino-ide", true, "base installation directory of Arduino IDE");
		options.addOption(LoadConfig.PREFERENCES_OPTION, false, "alternate Arduino IDE preferences file");
		OptionGroup choice =  new OptionGroup();
		choice.addOption(new Option("b", "board", true, "target board name"));
		choice.addOption(new Option("B", "boards", false, "list available boards"));
		choice.setRequired(true);
		options.addOptionGroup(choice);
		options.addOption("o", "output", true, "output directory for generated files");
	}

	public static void main(String[] args) {
		// -- mandatory option(s)
		CommandLine commandLine = null;
		try {
			commandLine = new BasicParser().parse(options, args);
		} catch (ParseException e) {
			logger.error(e);
			usage(1);
		}
		if (commandLine.hasOption('h')) {
			usage(0);
		}

		if (!LoadConfig.load(commandLine)) {
			usage(2);
		}

		if (commandLine.hasOption('B')) {
			ArduinoConfig.listBoards(System.out, false, false);
		} else {
			generateBoard(
					commandLine.getOptionValue('b'), // board
					commandLine.getOptionValue('o'));// output directory
		}
	}

	public static String makefileName(String boardName) {
		return "Makefile.target." + boardName;
	}

	protected static void generateBoard(String boardName, String outdir) {
		TargetBoard board = ArduinoConfig.setBoard(boardName);
		if (board == null) {
			usage(3);
		}
		TargetPlatform pf = board.getContainerPlatform();
		logger.info(String.format("found board %s:%s:%s", pf.getContainerPackage().getId(), pf.getId(), board.getId()));

		File outFile = new File(outdir == null ? "." : outdir);
		if (outFile.isDirectory()) {
			outFile = new File(outFile, makefileName(boardName));
		} else if (!outFile.getParentFile().isDirectory()) {
			logger.fatal("outdir must be an existing directory or a file name in existing directory");
			System.exit(4);
		}
		PrintWriter out = null;
		try {
			out = new PrintWriter(outFile);
		} catch (FileNotFoundException e) {
			logger.fatal("Can't open output file", e);
			System.exit(4);
		}

//		SketchData sketch = new SketchData(new File("toto.cpp"));
		PreferencesMap prefs;
		try {
			// arguments are unused, we must just instantiate Compiler class to access to preferences
			processing.app.debug.Compiler compiler = new processing.app.debug.Compiler(null, "${TARGET_DIR}", "${PROJECT_NAME}");
			prefs = compiler.getBuildPreferences();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		prefs.put("ide_version", "" + BaseNoGui.REVISION);
		logger.debug("prefs = " + prefs);

		logger.debug("generating board file");
		out.println("## autogenerated makefile rules for package %s, platform %s, board %s");

		out.println("\n## generate code from c, cpp, ino or S files");
		out.println("%.o: %.c");
		out.println("\t" + processRecipe(prefs, "recipe.c.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.c.extra_flags", "${CFLAGS} -x c++",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.ino");
		out.println("\t" + processRecipe(prefs, "recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.cpp.extra_flags", "${CXXFLAGS} -x c++",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.cpp");
		out.println("\t" + processRecipe(prefs, "recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.cpp.extra_flags", "${CXXFLAGS}",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.S");
		String recipe = processRecipe(prefs, "recipe.S.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.S.extra_flags", "${SFLAGS}",
				"source_file", "$<",
				"object_file", "$@");
		if (recipe == null) {
			out.println("\t$(error No rule to compile this kind of file for this target platform)");
		} else {
			out.println("\t" + recipe);
		}

		out.println("\n## generate lib from .o files");
		out.println("%.a: ${OBJS}");
		out.println("\t" + processRecipe(prefs, "recipe.ar.pattern",
				"+compiler.ar.extra_flags", "${ARFLAGS}",
				"object_file", "$<", // /!\ without 's'
				"archive_file", "$@"));

		out.println("\n## generate binary from .o files");
		out.println("%.elf: ${OBJS}");
		out.println("\t" + processRecipe(prefs, "recipe.c.combine.pattern",
				"+compiler.c.elf.extra_flags", "${ELFFLAGS}",
				"object_files", "$<", // /!\ with 's'
				"archive_file", "libEmpty.a") + " ${LDFLAGS}");
		out.println("\n## with a dependency to a fake libEmpty.a since recipe needs it");
		out.println("${TARGET_DIR}/libEmpty.a:");
		out.println("\t> ${TARGET_DIR}/libEmpty.a:");

		out.println("\n## convert binary from .elf");
		out.println("%.eep:%.elf");
		out.println("\t" + processRecipe(prefs, "recipe.objcopy.eep.pattern",
				"+compiler.objcopy.eep.extra_flags", "${EEPFLAGS}",
				"build.project_name", "$(*F)"));

		out.println("%.hex:%.elf");
		out.println("\t" + processRecipe(prefs, "recipe.objcopy.hex.pattern",
				"+compiler.elf2hex.extra_flags", "${HEXFLAGS}",
				"build.project_name", "$$(*F)"));

		// specific case : generate into a file then "cat" it, to keep a %-like rule
		out.println("%.size:%.elf");
		out.println("\t" + processRecipe(prefs, "recipe.size.pattern",
				"+compiler.size.cmd", " ${SIZEFLAGS}",
				"build.project_name", "$(*F)") + " > $@");
		out.println("\tcat $@");

		out.println("\n## entry point for core compilation");
		out.println("CORE_DIR:=" + prefs.get("build.core.path"));
		out.println("VARIANT_DIR:=" + prefs.get("build.variant.path"));

		out.println("\n## end of file");

		out.close();
	}

	/**
	 * translate template string according to preferences and optional arguments 
	 * @param prefs preferences map containing strings templates to replace with
	 * @param recipeName name of template to get from preferences
	 * @param arguments list of successive key / value pairs specifying specific entry to add in map
	 * if a key starts with character '+', value is appended to existing map entry if exists, else it replaces its value
	 * @return translated string
	 * @throws Exception 
	 */
	static String processRecipe(PreferencesMap prefs, String recipeName, String... arguments) {
	    PreferencesMap dict = new PreferencesMap(prefs);
	    for(int i = 0; i < arguments.length; i += 2) {
	    	String key = arguments[i];
	    	String value = arguments[i + 1];
	    	if (key.charAt(0) == '+') {
	    		key = key.substring(1);
		    	if (dict.containsKey(key)) {
		    		value = dict.get(key) + " " + value;
		    	}
	    	}
	    	dict.put(key, value);
	    }

	    try {
	    	String recipe = prefs.getOrExcept(recipeName);
	    	String result, source = recipe;
	    	int retries = 10;
	    	result = StringReplacer.replaceFromMapping(recipe, dict);
	    	while (retries != 0 && !source.equals(result)) {
	    		source = result;
	    		result = StringReplacer.replaceFromMapping(source, dict);
	    		retries--;
	    	}
	    	return result;
		} catch (Exception e) {
//			throw new Exception(e);
			logger.error("Couldn't process recipe " + recipeName, e);
		}
	    return null;
	}

	protected static void usage(int exitCode) {
		HelpFormatter fmt = new HelpFormatter();
		String footer =
				"\narduino ide path is looked for respectivly in command line option, java properties (-DARDUINO_IDE=...)," +
				" ARDUINO_IDE environment variable, location of BaseNoGui arduino class if already loaded" +
				" (if classpath was set accordingly for example)";
		fmt.printHelp(80, "java -jar ArduinoMakeMake.jar options ...", "options :", options, footer);
		System.exit(exitCode);
	}
}
