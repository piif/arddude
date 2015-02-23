package pif.arduino.tools;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.log4j.Logger;

/**
 * This class has to find Arduino IDE environment to force load its core jar file
 * After this, ArduinoConfig class can be used 
 * @author FR20311
 */
public class LoadConfig {
	private static Logger logger = Logger.getLogger(LoadConfig.class);

	public static final String ARDUINO_IDE_PROPERTY_NAME = "ARDUINO_IDE";
	public static final String PREFERENCES_OPTION = "preferences";

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
			} else {
				// TODO look for default places, according to environment :
				// System.getProperty("os.name").indexOf("Windows" / Linux / Mac) != -1;
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

	public static boolean load(CommandLine commandLine) {
		String arduinoIdePath = findIdePathAndClasses(commandLine);
		if (arduinoIdePath == null) {
			// nothing worked
			logger.fatal("Can't find arduino IDE path");
			return false;
		}

		ArduinoConfig.setIdePath(arduinoIdePath);
		ArduinoConfig.initialize(commandLine.getOptionValue("preferences-file"));
	
		return true;
	}
}