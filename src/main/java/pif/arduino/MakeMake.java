package pif.arduino;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;

import pif.arduino.tools.ArduinoConfig;
import pif.arduino.tools.ClassPathHacker;

import processing.app.BaseNoGui;
import processing.app.PreferencesData;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
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
		options.addOption("p", "preferences-file", false, "alternate Arduino IDE preferences file");
		OptionGroup choice =  new OptionGroup();
		choice.addOption(new Option("b", "board", true, "target board name"));
		choice.addOption(new Option("l", "list-boards", false, "list available boards"));
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

		String arduinoIdePath = findIdePathAndClasses(commandLine);
		if (arduinoIdePath == null) {
			// nothing worked
			logger.fatal("Can't find arduino IDE path");
			usage(1);
		}

		ArduinoConfig.initialize(arduinoIdePath, commandLine.getOptionValue('p'));

		if (commandLine.hasOption('l')) {
			listBoards(System.out);
		} else {
			generateBoard(
					commandLine.getOptionValue('b'), // board
					commandLine.getOptionValue('o'));     // without platform file
		}
	}

	public static final String ARDUINO_IDE_PROPERTY_NAME = "ARDUINO_IDE";

	// find Arduino IDE main directory
	// + load arduino-core jar if its classes are not already loaded
	// returns ide path, or null if not found
	private static String findIdePathAndClasses(CommandLine commandLine) {
		String result = null;

		if (commandLine.hasOption('I')) {
			result = commandLine.getOptionValue('I');
			logger.debug("found ide path in command line : " + result);
		} else if (System.getProperties().containsKey(ARDUINO_IDE_PROPERTY_NAME)) {
			// try in properties
			result = System.getProperty(ARDUINO_IDE_PROPERTY_NAME);
			logger.debug("found ide path in properties : " + result);
		} else {
			// try in env
			Map<String, String> env = System.getenv();
			if (env.containsKey(ARDUINO_IDE_PROPERTY_NAME)) {
				result = env.get(ARDUINO_IDE_PROPERTY_NAME);
				logger.debug("found ide path in environment : " + result);
			}
		}

		if (result != null) {
			File absolute = new File(result).getAbsoluteFile();
			if (!absolute.isDirectory()) {
				logger.fatal("Arduino IDE directory " + absolute + " not found");
			}
			result = absolute.getPath();
		}

		// look for Arduino IDE classes
		Class<?> baseClass = null;
		try {
			baseClass = processing.app.BaseNoGui.class;
		} catch (java.lang.NoClassDefFoundError e) {
			if (result == null) {
				logger.fatal("Arduino classes not loaded and IDE path not specified");
				return null;
			}
			File coreJar = new File(result, "lib" + File.separatorChar + "arduino-core.jar");
			if (!coreJar.isFile()) {
				logger.fatal("Arduino core jar not found in " + coreJar);
				return null;
			}
			try {
				logger.debug("adding in classpath core jar " + coreJar);
				ClassPathHacker.addFile(coreJar);
			} catch (IOException e1) {
				logger.fatal("Can't add core jar in classpath", e1);
				return null;
			}
			try {
				baseClass = ClassLoader.getSystemClassLoader().loadClass("processing.app.BaseNoGui");
//				baseClass = processing.app.BaseNoGui.class;
				logger.debug("class BaseNoGui found");
			} catch (/*java.lang.NoClassDefFoundError |*/ ClassNotFoundException e2) {
				logger.fatal("Can't load arduino core class", e2);
				return null;
			}
		}

		if (result == null) {
			// now that Arduino main jar is known, deduce IDE path from it
			URL jarPath = baseClass.getProtectionDomain().getCodeSource().getLocation();
			try {
				result = new File(jarPath.toURI().getPath()).getParentFile().getParent();
				logger.debug("found ide path via BaseNoGui class location : " + result);
			} catch (URISyntaxException e) {
				logger.fatal("Weird jar location '" + jarPath + "' ?", e);
				return null;
			}
		}

		return result;
	}

	protected static void listBoards(PrintStream output) {
		output.println("Board list (to be specified as a -b argument) :");
		for(Entry<String, TargetPackage> pkgEntry : ArduinoConfig.packages.entrySet()) {
			String pkg = pkgEntry.getKey();
			for(Entry<String, TargetPlatform> pfEntry : pkgEntry.getValue().getPlatforms().entrySet()) {
				String platform = pfEntry.getKey();
				for(Entry<String, TargetBoard> bdEntry : pfEntry.getValue().getBoards().entrySet()) {
					TargetBoard board = bdEntry.getValue();
					output.println(String.format("  %1$s = %2$s (%3$s:%4$s:%1$s)",
							board.getId(), board.getName(), pkg, platform));
				}	
			}	
		}
		output.println("Both syntax (short as in first column) or ':'-separated one" +
				" (as in parenthesis) are accepted as a -b argument)" +
				" but output file will use the short one");
	}

	private static final String USE = "use -l option to list existing ones";
	protected static TargetBoard setBoard(String boardName) {
		TargetBoard result = null;
		String options = null;

		String[] boardComponents = boardName.split(":");

		switch(boardComponents.length) {
		case 4:
			options = boardComponents[3];
			// NO BREAK
		case 3:
			{
				TargetPackage pkg = ArduinoConfig.packages.get(boardComponents[0]);
				if (pkg == null) {
					logger.error(String.format("package %s not found, " + USE, boardComponents[0]));
					return null;
				}
				TargetPlatform pf = pkg.get(boardComponents[1]);
				if (pf == null) {
					logger.error(String.format("platform %2$s not found in package %1$s, " + USE,
							boardComponents[0], boardComponents[1]));
					return null;
				}
				result = pf.getBoard(boardComponents[2]);
				if (result == null || !result.getId().equals(boardComponents[2])) {
					logger.error(String.format("board %3$s not found in platform %1$s:%2$s" + USE,
							boardComponents[0], boardComponents[1], boardComponents[2]));
					return null;
				}
			}
			break;

		case 2:
			options = boardComponents[1];
			// NO BREAK
		case 1:
			search: for(Entry<String, TargetPackage> pkgEntry : ArduinoConfig.packages.entrySet()) {
				String pkg = pkgEntry.getKey();
				for(Entry<String, TargetPlatform> pfEntry : pkgEntry.getValue().getPlatforms().entrySet()) {
					String platform = pfEntry.getKey();
					for(Entry<String, TargetBoard> bdEntry : pfEntry.getValue().getBoards().entrySet()) {
						if (boardName.equals(bdEntry.getKey())) {
							logger.info(String.format("found board in package %s platform %s", pkg, platform));
							result = bdEntry.getValue();
							break search;
						}
					}
				}	
			}
			if (result == null) {
				logger.error("board not found, " + USE);
				return null;
			}
			break;

		default:
			logger.error("bad board name format (must be [package:platform:]board[:options]");
			return null;
		}

		ArduinoConfig.selectBoard(result);

		if (options != null) {
			setOptions(result, options);
		}

		return result;
	}

	protected static void setOptions(TargetBoard board, String optionsString) {
		String[] options = optionsString.split(",");
		for (String option : options) {
			String[] keyValue = option.split("=", 2);

			if (keyValue.length != 2) {
				logger.error("Invalid option '" + option + "', should be of the form \"name=value\"");
			}
			String key = keyValue[0].trim();
			String value = keyValue[1].trim();

			if (!board.hasMenu(key)) {
				logger.warn(String.format("Invalid option '%s' for board '%s'", key, board.getId()));
			}
			if (board.getMenuLabel(key, value) == null) {
				logger.warn(String.format("Invalid '%s' option value for board '%s'", key, board.getId()));
			}

			PreferencesData.set("custom_" + key, board.getId() + "_" + value);
		}
	}

	public static String makefileName(String boardName) {
		return "Makefile.target." + boardName;
	}

	protected static void generateBoard(String boardName, String outdir) {
		TargetBoard board = setBoard(boardName);
		if (board == null) {
			usage(2);
		}
		TargetPlatform pf = board.getContainerPlatform();
		logger.info(String.format("found board %s:%s:%s", pf.getContainerPackage().getId(), pf.getId(), board.getId()));

		File outFile = new File(outdir);
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
				"compiler.c.extra_flags", "${CFLAGS} -x c++",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.ino");
		out.println("\t" + processRecipe(prefs, "recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"compiler.cpp.extra_flags", "${CXXFLAGS} -x c++",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.cpp");
		out.println("\t" + processRecipe(prefs, "recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"compiler.cpp.extra_flags", "${CXXFLAGS}",
				"source_file", "$<",
				"object_file", "$@"));
		out.println("%.o: %.S");
		out.println("\t" + processRecipe(prefs, "recipe.S.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"compiler.S.extra_flags", "${SFLAGS}",
				"source_file", "$<",
				"object_file", "$@"));

		out.println("\n## generate lib from .o files");
		out.println("%.a: ${OBJS}");
		out.println("\t" + processRecipe(prefs, "recipe.ar.pattern",
				"compiler.ar.extra_flags", "${ARFLAGS}",
				"object_file", "$<", // /!\ without 's'
				"archive_file", "$@"));

		out.println("\n## generate binary from .o files");
		out.println("%.elf: ${OBJS}");
		out.println("\t" + processRecipe(prefs, "recipe.ar.pattern",
				"compiler.c.elf.extra_flags", "${ELFFLAGS}",
				"object_files", "$<", // /!\ with 's'
				"archive_file", "$@"));

		// TODO
//		recipe.objcopy.eep.pattern = "{compiler.path}{compiler.objcopy.cmd}" {compiler.objcopy.eep.flags} {compiler.objcopy.eep.extra_flags} "{build.path}/{build.project_name}.elf" "{build.path}/{build.project_name}.eep"
//		recipe.objcopy.hex.pattern = "{compiler.path}{compiler.elf2hex.cmd}" {compiler.elf2hex.flags} {compiler.elf2hex.extra_flags} "{build.path}/{build.project_name}.elf" "{build.path}/{build.project_name}.hex"
//		recipe.size.pattern = "{compiler.path}{compiler.size.cmd}" -A "{build.path}/{build.project_name}.elf"

		out.println("\n## end of file");

		out.close();
	}

	static String processRecipe(PreferencesMap prefs, String recipeName, String... arguments) {
	    PreferencesMap dict = new PreferencesMap(prefs);
	    for(int i = 0; i < arguments.length; i += 2) {
	    	String key = arguments[i];
	    	String value = arguments[i + 1];
	    	if (dict.containsKey(key)) {
	    		value = dict.get(key) + " " + value;
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
