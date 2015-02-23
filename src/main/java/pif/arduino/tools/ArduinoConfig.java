package pif.arduino.tools;

import static processing.app.I18n._;

import processing.app.BaseNoGui;
import processing.app.PreferencesData;

public class ArduinoConfig extends BaseNoGui {
	// Arduino IDE path must be fixed before this class is accessed
	// It's explicitly passed to init method, even if it was not in command line parameters
	static public void initialize(String arduinoIdePath, String preferencesFile) {
		System.setProperty("user.dir", arduinoIdePath);

		// caller platform specific initializations
		initPlatform();
	    getPlatform().init();

		initPortableFolder();

		// if preferencesFile is null, this method uses default location
		PreferencesData.init(absoluteFile(preferencesFile));

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
