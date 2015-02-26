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
		ArduinoConfig.changePathSeparators(prefs);
//		logger.debug("prefs = " + prefs);

		preferencesHelper helper = new preferencesHelper(prefs, out);

		logger.debug("generating board file");
		helper.format("## autogenerated makefile rules for package %s, platform %s, board %s\n",
				pf.getContainerPackage().getId(), pf.getId(), board.getId());

		// modify some pathes to make them relatives to other variables
		String idePath = helper.get("runtime.ide.path");
		helper.changeRoot("runtime.hardware.path", idePath, "${ARDUINO_IDE}");
		helper.changeRoot("build.core.path", idePath, "${ARDUINO_IDE}");
		helper.changeRoot("build.variant.path", idePath, "${ARDUINO_IDE}");

		helper.pref2varAndSet("ARDUINO_IDE", "runtime.ide.path");
		helper.pref2varAndSet("TOOLCHAIN_DIR", "compiler.path");

		helper.println("\n## entry point for core compilation");
		helper.pref2varAndSet("HARDWARE_DIR", "runtime.hardware.path");
		helper.pref2varAndSet("CORE_DIR", "build.core.path");
		helper.pref2varAndSet("VARIANT_DIR", "build.variant.path");
		helper.raw2var("INCLUDE_FLAGS", "-I${CORE_DIR} -I${VARIANT_DIR}");

		out.println("\n## C compiler");
		helper.recipe2var("CC", "{compiler.path}{compiler.c.cmd}");
		helper.pref2varAndSet("CFLAGS", "compiler.c.flags");
		helper.recipe2var("CXX", "{compiler.path}{compiler.cpp.cmd}");
		helper.pref2varAndSet("CXXFLAGS", "compiler.cpp.flags");
		helper.recipe2var("AS", "{compiler.path}{compiler.c.cmd}");
		helper.pref2varAndSet("ASFLAGS", "compiler.S.flags");

		String targetFlags = helper.recipe("recipe.cpp.o.pattern",
				"compiler.cpp.flags", "<<<BEGIN>>>",
				"compiler.cpp.extra_flags", "<<<END>>>");
		targetFlags = targetFlags.replaceFirst("^.*<<<BEGIN>>>(.*)<<<END>>>.*$", "$1");
		helper.raw2var("DISCOVERY_FLAGS", targetFlags);

		// target specific flags are into recipe itself and not in cflags definition
		// => have to generate a full rule
		out.println("\n## generate code from c, cpp, ino or S files");
		out.println("${TARGET_DIR}/%.o: %.c");
		out.println("\t@${MKDIR} ${TARGET_DIR}/${*D}");
		out.println("\t" + helper.recipe("recipe.c.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.c.extra_flags", "${CFLAGS_EXTRA}",
				"source_file", "$<",
				"object_file", "${TARGET_DIR}/$*.o"));
		out.println("${TARGET_DIR}/%.o: %.ino");
		out.println("\t@${MKDIR} ${TARGET_DIR}/${*D}");
		out.println("\t" + helper.recipe("recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.cpp.extra_flags", "${CXXFLAGS_EXTRA} -x c++",
				"source_file", "$<",
				"object_file", "${TARGET_DIR}/$*.o"));
		out.println("${TARGET_DIR}/%.o: %.cpp");
		out.println("\t@${MKDIR} ${TARGET_DIR}/${*D}");
		out.println("\t" + helper.recipe("recipe.cpp.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.cpp.extra_flags", "${CXXFLAGS_EXTRA}",
				"source_file", "$<",
				"object_file", "${TARGET_DIR}/$*.o"));
		out.println("${TARGET_DIR}/%.o: %.S");
		out.println("\t@${MKDIR} ${TARGET_DIR}/${*D}");
		String recipe = helper.recipe("recipe.S.o.pattern",
				"includes", "${INCLUDE_FLAGS}",
				"+compiler.S.extra_flags", "${ASFLAGS_EXTRA}",
				"source_file", "$<",
				"object_file", "${TARGET_DIR}/$*.o");
		if (recipe == null) {
			out.println("\t$(error No rule to compile this kind of file for this target platform)");
		} else {
			out.println("\t" + recipe);
		}

		out.println("\n## generate library");
		helper.recipe2var("AR", "{compiler.path}{compiler.ar.cmd}");
		helper.pref2varAndSet("ARFLAGS", "compiler.ar.flags");
        out.println("${TARGET_DIR}/lib%.a: ${OBJS}");
        out.println("\t${AR} ${ARFLAGS} ${ARFLAGS_EXTRA} $@ $^");

		// command line is a bit crazy
		// => have to generate a full rule
		out.println("\n## generate binary from .o files");
		out.println("${TARGET_DIR}/%.elf: ${OBJS}");
		// recipes contain a "{build.path}/{archive_file}" to include core lib, but it doesn't match our path constraints
		// => fake it by putting last object in it, and other ones in {object_files}
		// + have to remove target_dir from this last entry
		out.println("\t" + helper.recipe("recipe.c.combine.pattern",
				"+compiler.c.elf.extra_flags", "${ELFFLAGS}",
				"object_files", "$(filter-out $(lastword $^),$^)", // /!\ with 's'
				"archive_file", "$(subst ${TARGET_DIR}/,,$(lastword $^))") + " ${LDFLAGS}");

		out.println("\n## convert elf file");
		helper.recipe2var("EEP", "{compiler.path}{compiler.objcopy.cmd}");
		helper.pref2varAndSet("EEPFLAGS", "compiler.objcopy.eep.flags");
        out.println("%.eep:%.elf");
        out.println("\t${EEP} ${EEPFLAGS} ${EEPFLAGS_EXTRA} $< $@");

		helper.recipe2var("HEX", "{compiler.path}{compiler.elf2hex.cmd}");
		helper.pref2varAndSet("HEXFLAGS", "compiler.elf2hex.flags");
        out.println("%.hex:%.elf");
        out.println("\t${HEX} ${HEXFLAGS} ${HEXFLAGS_EXTRA} $< $@");

		helper.recipe2var("SIZE", "{compiler.path}{compiler.size.cmd}");
		// this command has no flags preference
		helper.raw2var("SIZEFLAGS", "-A");
        out.println("size:%.elf");
        out.println("\t${SIZE} ${SIZEFLAGS} ${SIZEFLAGS_EXTRA} $<");

		out.println("\n## end of file");

		out.close();
	}

	/**
	 * helper class to generate rules from preferences map
	 */
	static class preferencesHelper {
		protected PreferencesMap prefs;
		protected PrintWriter out;

		/**
		 * create a new processor for given PreferencesMap
		 * @param prefs map into which recipes and variables can be found
		 * @param out PrintWriter where outputs will be printed
		 */
		public preferencesHelper(PreferencesMap prefs, PrintWriter out) {
			this.prefs = prefs;
			this.out = out;
		}

		/**
		 * have to wrap source preferences get() method, since it may be modified by other methods
		 */
		public String get(String pref) {
			return prefs.get(pref);
		}
		/**
		 * replace preferences entry
		 */
		public void set(String pref, String value) {
			prefs.put(pref, value);
		}
		/**
		 * append text to preferences entry, or set it if it wasn't defined already
		 */
		public void append(String pref, String value) {
			String old = prefs.get(pref);
			if (old == null) {
				set(pref,  value);
			} else {
				set(pref, old + value);
			}
		}

		/**
		 * recursively replace references into a string
		 * @param pref name of template to get from preferences
		 * @param arguments list of successive key / value pairs specifying specific entry to add in map
		 * if a key starts with character '+', value is appended to existing map entry if exists, else it replaces its value
		 * @return translated string
		 * @throws Exception 
		 */
		public String interpret(String source, String... arguments) {
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

		    String result;
	    	int retries = 10;
	    	result = StringReplacer.replaceFromMapping(source, dict);
	    	while (retries != 0 && !source.equals(result)) {
	    		source = result;
	    		result = StringReplacer.replaceFromMapping(source, dict);
	    		retries--;
	    	}
	    	if (retries == 0) {
	    		logger.warn("Catched max retries : infinite loop ?");
	    	}
	    	return result;
		}

		/**
		 * dump a "var := value" rule into current Writer
		 * @param var out variable name
		 * @param value variable value
		 */
		public void raw2var(String var, String value) {
			out.println(String.format("%s = %s", var, value));
		}

		/**
		 * dump a "var := value" rule into current Writer
		 * @param var out variable name
		 * @param pref preference name to get as value
		 */
		public void pref2var(String var, String pref, String... arguments) {
			String recipe = prefs.get(pref);
			if (recipe == null) {
				logger.warn("preference '" + pref + "' not found");
				return;
			}
			String value = interpret(recipe, arguments);
			raw2var(var, value);
		}

		/**
		 * dump a "var := value" rule into current Writer THEN replace preference value by reference to this variable
		 * @param var out variable name
		 * @param pref preference name to get as value
		 */
		public void pref2varAndSet(String var, String pref, String... arguments) {
			String recipe = prefs.get(pref);
			if (recipe == null) {
				logger.warn("preference '" + pref + "' not found");
				return;
			}
			String value = interpret(recipe, arguments);
			raw2var(var, value);
			set(pref, String.format("${%s}", var));
		}

		/**
		 * dump a "var := value" rule into current Writer
		 * @param var out variable name
		 * @param recipe string to interpret as value
		 */
		public void recipe2var(String var, String recipe, String... arguments) {
			String value = interpret(recipe, arguments);
			raw2var(var, value);
		}

		/**
		 * change start of given path in another value (ie variable value)
		 * @param pref preference to modify
		 * @param oldPrefix prefix path to replace
		 * @param newPrefix new value for this prefix
		 */
		public void changeRoot(String pref, String oldPrefix, String newPrefix) {
			String path = prefs.get(pref);
			if (path == null) {
				logger.warn("preference '" + pref + "' not found");
				return;
			}
			prefs.put(pref, path.replace(oldPrefix, newPrefix));
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
		public String recipe(String recipeName, String... arguments) {
	    	String recipe = prefs.get(recipeName);
	    	if (recipe == null) {
	    		return null;
	    	}
	    	return interpret(recipe, arguments);
		}

		public void println(String str) {
			out.println(str);
		}
		public void format(String format,  Object... args) {
			out.println(String.format(format, args));
		}
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
