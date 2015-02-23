package pif.arduino.tests;

import static processing.app.I18n._;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import pif.arduino.tools.ClassPathHacker;
import processing.app.BaseNoGui;
import processing.app.PreferencesData;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;

public class ReadPrefs {
	private static Logger logger = Logger.getLogger(ReadPrefs.class);

	static Config config;

	static class MyBase extends BaseNoGui {
		static public void initialize(String[] args) {
			System.setProperty("user.dir", config.arduinoIdePath);

			initPlatform();
			initPortableFolder();
			// handle --preferences-file parameter, and --curdir, but useless here ?
			initParameters(args);
//			init(args);
		    getPlatform().init();
		    
		    String sketchbookPath = getSketchbookPath();
		    // If no path is set, get the default sketchbook folder for this platform
		    if (sketchbookPath == null) {
		      if (BaseNoGui.getPortableFolder() != null)
		        PreferencesData.set("sketchbook.path", getPortableSketchbookFolder());
		      else
		        showError(_("No sketchbook"), _("Sketchbook path not defined"), null);
		    }
		  
		    // now that sketchbook path is defined, specific hardware specified in it will be found
		    BaseNoGui.initPackages();
		}
	}

	static class Config {
		public String arduinoIdePath;

		public Config(String[] args) {
			// TODO parse command line
			boolean mustLoadIdeClass = findIdePath();
			File absolute = new File(arduinoIdePath);
			arduinoIdePath = absolute.getAbsolutePath();
			if (mustLoadIdeClass) {
				loadIdeClass();
			}
		}

		private void loadIdeClass() {
			File coreJar = new File(arduinoIdePath, "lib" + File.separatorChar + "arduino-core.jar");
			try {
				logger.debug("adding in classpath core jar " + coreJar);
				ClassPathHacker.addFile(coreJar);
			} catch (IOException e) {
				logger.fatal("Can't find arduino IDE path", e);
				System.exit(1);
			}
			try {
				@SuppressWarnings("unused")
				Class<?> b = BaseNoGui.class;
				logger.debug("class BaseNoGui found");
			} catch (java.lang.NoClassDefFoundError e) {
				logger.fatal("Can't load arduino core class");
				System.exit(1);
			}
		}

		// return true if path was found via environment data, thus have to load classes
		private boolean findIdePath() {
			if (arduinoIdePath != null) {
				logger.debug("found ide path in command line : " + arduinoIdePath);
				return true;
			}
			// try in properties
			arduinoIdePath = System.getProperty("ARDUINO_IDE");
			if (arduinoIdePath != null) {
				logger.debug("found ide path in properties : " + arduinoIdePath);
				return true;
			}
			// try in env
			Map<String, String> env = System.getenv();
			if (env.containsKey("ARDUINO_IDE")) {
				arduinoIdePath = env.get("ARDUINO_IDE");
				logger.debug("found ide path in environment : " + arduinoIdePath);
				return true;
			}
			// try if class already loaded
			try {
				Class<?> baseClass = BaseNoGui.class;
				// Here, Arduino main jar is already known -> deduce IDE path
				URL jarPath = baseClass.getProtectionDomain().getCodeSource().getLocation();
				try {
					arduinoIdePath = new File(jarPath.toURI().getPath()).getParentFile().getParent();
					logger.debug("found ide path via BaseNoGui class location : " + arduinoIdePath);
					return false;
				} catch (URISyntaxException e) {
					logger.fatal("Weird jar location '" + jarPath + "' ?", e);
					System.exit(1);
				}
			} catch (java.lang.NoClassDefFoundError e) {}
			// nothing worked
			logger.fatal("Can't find arduino IDE path");
			System.exit(1);
			return false;
		}
	}

	public static void main(String[] args) {
	
		config = new Config(args);
		logger.debug("init ...");
		MyBase.initialize(args);

		logger.info("arduinoIdePath " + config.arduinoIdePath);
		logger.info("getSettingsFolder " + MyBase.getSettingsFolder());
		logger.info("getBoardPreferences " + MyBase.getBoardPreferences());

		logger.info("getHardwarePath " + MyBase.getHardwarePath());
		logger.info("getHardwareFolder " + MyBase.getHardwareFolder());

		logger.info("getAvrBasePath " + MyBase.getAvrBasePath());
		logger.info("getLibrariesPath " + MyBase.getLibrariesPath());
		logger.info("getToolsFolder " + MyBase.getToolsFolder());
		logger.info("getTargetBoard " + MyBase.getTargetPackage("arduino"));
		logger.info("getTargetPlatform " + MyBase.getTargetPlatform());

		for(Entry<String, TargetPackage> pkgEntry : MyBase.packages.entrySet()) {
			logger.info("- Package " + pkgEntry.getKey());
			for(Entry<String, TargetPlatform> pfEntry : pkgEntry.getValue().getPlatforms().entrySet()) {
				logger.info("  - Platform " + pfEntry.getKey());
				for(Entry<String, TargetBoard> bdEntry : pfEntry.getValue().getBoards().entrySet()) {
					TargetBoard board = bdEntry.getValue();
					logger.info("    - Board " + board.getId() + " = " + board.getName());
				}	
			}	
		}

		System.exit(0);
	}
}
