package pif.arduino.tools;

import static processing.app.I18n._;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import processing.app.BaseNoGui;
import processing.app.Platform;
import processing.app.PreferencesData;
import processing.app.Serial;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;

public class ArduinoConfig extends BaseNoGui {
	private static Logger logger = Logger.getLogger(ArduinoConfig.class);

	static public void setIdePath(String arduinoIdePath) {
		System.setProperty("user.dir", arduinoIdePath);
	}

	// Arduino IDE path must be fixed before this class is accessed
	// It's explicitly passed to init method, even if it was not in command line parameters
	static public void initialize(String preferencesFile) {
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
		initPackages();
	}

	private static List<TargetBoard> boardList = null;

	/**
	 * return a list of all known target boards
	 */
	public static List<TargetBoard> listBoards() {
		if (boardList == null) {
			boardList = new ArrayList<TargetBoard>();
			for (Entry<String, TargetPackage> pkgEntry : ArduinoConfig.packages.entrySet()) {
				for (Entry<String, TargetPlatform> pfEntry : pkgEntry.getValue().getPlatforms().entrySet()) {
					for (Entry<String, TargetBoard> bdEntry : pfEntry.getValue().getBoards().entrySet()) {
						boardList.add(bdEntry.getValue());
					}
				}
			}
		}
		return boardList;
	}

	/**
	 * pretty print known target board list
	 * @param output
	 *            stream into which output is sent
	 */
	public static void listBoards(PrintStream output, boolean raw, boolean withPidVid) {
		if (!raw) {
			output.println("Board list (to be specified as a -b argument) :");
		}
		for (TargetBoard board: listBoards()) {
			TargetPlatform pf = board.getContainerPlatform();
			TargetPackage pkg = pf.getContainerPackage();
			if (raw) {
				output.println(String.format("%s:%s:%s",
						board.getId(), pf.getId(), pkg.getId()));
			} else {
				StringBuffer vidPids = new StringBuffer();
				if (withPidVid) {
					List<String> vids = new LinkedList<String>(board.getPreferences().subTree("vid").values());
					if (!vids.isEmpty()) {
						List<String> pids = new LinkedList<String>(board.getPreferences().subTree("pid").values());
						for (int i = 0; i < vids.size(); i++) {
							vidPids.append(" " + vids.get(i) + "/" + pids.get(i));
						}
					}
				}
				output.println(String.format(
						"  %1$s = %2$s (%3$s:%4$s:%1$s)%5$s",
						board.getId(), board.getName(), pf.getId(), pkg.getId(), vidPids));
			}
		}
	}

	protected static TargetBoard getBoardByName(String name) {
		for (TargetBoard b : listBoards()) {
			if (b.getName().equals(name)) {
				return b;
			}
		}
		return null;
	}

	private static List<PortBoard> portList = null;

	/**
	 * cc.arduino.packages.BoardPort class contains just a board name instead of
	 * an associated board structure this class keep the link with board class
	 */
	public static class PortBoard {
		public String address;
		public TargetBoard board = null;

		PortBoard(String address, String boardName) {
			this.address = address;
			if (boardName != null) {
				board = getBoardByName(boardName);
			}
		}
	}

	public static List<PortBoard> listPorts() {
		if (portList == null) {
			Platform os = BaseNoGui.getPlatform();
			String devicesListOutput = os.preListAllCandidateDevices();

			portList = new ArrayList<PortBoard>();

			List<String> ports = Serial.list();
			for (String port : ports) {
				String boardName = os.resolveDeviceAttachedTo(port, BaseNoGui.packages, devicesListOutput);
				portList.add(new PortBoard(port, boardName));
			}
		}
		return portList;
	}

	public static void listPorts(PrintStream output, boolean raw) {
		if (!raw) {
			output.println("Port list (to be specified as a -p argument) :");
		}
		for (PortBoard port: listPorts()) {
			if (raw) {
				output.println(port.address);
			} else {
				if (port.board == null) {
					output.println(String.format("  %s", port.address));
				} else {
					output.println(String.format("  %s (identified as %s)", port.address, port.board.getName()));
				}
			}
		}
	}

	public static PortBoard getPortByName(String name) {
		for (PortBoard port: listPorts()) {
			if (port.address.equalsIgnoreCase(name)) {
				return port;
			}
		}
		return null;
	}

	public static void setPort(PortBoard port) {
		ArduinoConfig.selectSerialPort(port.address);
	}


	private static final String USE = "use -B option to list existing ones";

	public static void setBoard(TargetBoard board) {
		ArduinoConfig.selectBoard(board);
	}

	public static TargetBoard setBoard(String boardName) {
		TargetBoard result = null;
		String options = null;

		String[] boardComponents = boardName.split(":");

		switch (boardComponents.length) {
		case 4:
			options = boardComponents[3];
			// NO BREAK
		case 3: {
			TargetPackage pkg = ArduinoConfig.packages.get(boardComponents[0]);
			if (pkg == null) {
				logger.error(String.format("package %s not found, " + USE,
						boardComponents[0]));
				return null;
			}
			TargetPlatform pf = pkg.get(boardComponents[1]);
			if (pf == null) {
				logger.error(String.format(
						"platform %2$s not found in package %1$s, " + USE,
						boardComponents[0], boardComponents[1]));
				return null;
			}
			result = pf.getBoard(boardComponents[2]);
			if (result == null || !result.getId().equals(boardComponents[2])) {
				logger.error(String.format(
						"board %3$s not found in platform %1$s:%2$s" + USE,
						boardComponents[0], boardComponents[1],
						boardComponents[2]));
				return null;
			}
		}
			break;

		case 2:
			options = boardComponents[1];
			// NO BREAK
		case 1:
			search: for (Entry<String, TargetPackage> pkgEntry : ArduinoConfig.packages
					.entrySet()) {
				String pkg = pkgEntry.getKey();
				for (Entry<String, TargetPlatform> pfEntry : pkgEntry
						.getValue().getPlatforms().entrySet()) {
					String platform = pfEntry.getKey();
					for (Entry<String, TargetBoard> bdEntry : pfEntry
							.getValue().getBoards().entrySet()) {
						if (boardName.equals(bdEntry.getKey())) {
							logger.info(String.format(
									"found board in package %s platform %s",
									pkg, platform));
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
				logger.error("Invalid option '" + option
						+ "', should be of the form \"name=value\"");
			}
			String key = keyValue[0].trim();
			String value = keyValue[1].trim();

			if (!board.hasMenu(key)) {
				logger.warn(String.format("Invalid option '%s' for board '%s'",
						key, board.getId()));
			}
			if (board.getMenuLabel(key, value) == null) {
				logger.warn(String.format(
						"Invalid '%s' option value for board '%s'", key,
						board.getId()));
			}

			PreferencesData.set("custom_" + key, board.getId() + "_" + value);
		}
	}

}
