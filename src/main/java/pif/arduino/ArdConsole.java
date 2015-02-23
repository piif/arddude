package pif.arduino;

import java.io.FileNotFoundException;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import cc.arduino.packages.BoardPort;
import cc.arduino.packages.discoverers.SerialDiscovery;
import pif.arduino.Console.ConsolePeer;
import pif.arduino.tools.*;
import processing.app.debug.TargetBoard;

public class ArdConsole implements ConsolePeer, FileScanner.FileScanHandler {
	// TODO: cmd line args
	// -B --boards -> list known boards 
	// -b --board -> select board
	// -P --ports -> list ports and try to detect attached boards
	// -p --port -> connect to this port
	// -f --file -> input file to scan and upload 
	// -u --upload -> force upload (if -f specified)
	// -v -verbose -> set uploader verbosity
	// associate commands !boards !board ...
	// command !reconnect !disconnect !upload
	private static Logger logger = Logger.getLogger(ArdConsole.class);

	static final short STATE_NONE = 0;
	static final short STATE_UPLOADING = 1;
	static final short STATE_CONNECTING = 2;
	static final short STATE_CONNECTED = 3;
	static final short STATE_FAIL = -1;

	static short state = STATE_NONE;

	static Options options;
	static {
		options = new Options();
		options.addOption("h", "help", false, "usage");
		options.addOption("I", "arduino-ide", true, "base installation directory of Arduino IDE");
		options.addOption(LoadConfig.PREFERENCES_OPTION, false, "alternate Arduino IDE preferences file");

		options.addOption("B", "boards", false, "list supported target boards");
		options.addOption("b", "board", true, "set target boards");

		options.addOption("P", "ports", false, "list available ports");
		options.addOption("p", "port", true, "set port to connect to");

		options.addOption("f", "file", true, "file to scan / upload");
		options.addOption("u", "upload", false, "launch upload at startup");
		options.addOption("v", "verbose", false, "set upload verbosity");
	}

	protected ArduinoConfig.PortBoard port;

	protected String uploadFileName;
	protected FileScanner scanner;

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

		if (commandLine.hasOption('P')) {
		    System.out.println("Port list (to be specified as a -p argument) :");

			SerialDiscovery sd = new SerialDiscovery();
			List<BoardPort> ports = sd.discovery();
			for (BoardPort entry: ports) {
				if (entry.getBoardName() != null) {
					System.out.println(String.format("  %s (identified as '%s')", entry.getAddress(), entry.getBoardName()));
				} else {
					System.out.println(String.format("  %s", entry.getAddress()));
				}
			}
		}
		if (commandLine.hasOption('B')) {
			ArduinoConfig.listBoards(System.out, false);
		}

		if (!commandLine.hasOption('p')) {
			if (commandLine.hasOption('B') || commandLine.hasOption('P')) {
				// user just asked to dump boards / ports -> stop here.
				System.exit(0);
			} else {
				// nothing to do -> send usage
				usage(0);
			}
		}

		// ok, it will become a bit more complicated, pass to a instance...
		new ArdConsole(commandLine);
	}

	ArdConsole(CommandLine commandLine) {
		// HERE, we have a p option => try to connect to this port.
		String portName = commandLine.getOptionValue('p');
		port = ArduinoConfig.getPortByName(portName);
		if (port == null) {
			logger.fatal("Unknown port " + portName);
			System.exit(4);
		}

		TargetBoard board = null;
		if (commandLine.hasOption('b')) {
			board = ArduinoConfig.setBoard(commandLine.getOptionValue('b'));
			if (board == null) {
				usage(2);
			}
			if (port.board != null && port.board != board) {
				logger.warn(String.format("port was detected has a different board (%s) than specifed one (%s)",
						port.board.getId(), board.getId()));
			}
		} else {
			// if board is not specified, try to detect it
			if (port.board == null) {
				logger.fatal("No board specified, and can't auto-detect it");
				usage(2);
			} else {
				board = port.board;
				ArduinoConfig.setBoard(board);
			}
		}
		// HERE portBoard contains a port address and a board structure, and Arduino preferences are set for this board.
		// OK, what do we have to do with this ...
		
		// may be a file to look for
		if (commandLine.hasOption('f')) {
			try {
				uploadFileName = commandLine.getOptionValue('f');
				scanner = new FileScanner(uploadFileName, this);
			} catch (FileNotFoundException e) {
				usage(2);
			}
			if (commandLine.hasOption('u')) {
				launchUpload();
			}
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

	@Override
	public void onOutgoingData(byte[] data) {
		// TODO if connected, send data
		// TODO else send message and abort
	}

	@Override
	public boolean onCommand(String command) {
		// TODO local commands ...
		return false;
	}

	@Override
	public void onDisconnect(int status) {
		// TODO close everything properly
	}

	@Override
	public void onFileChange() {
		launchUpload();
	}

	protected void launchUpload() {
		// TODO
	}
}
