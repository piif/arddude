package pif.arduino;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jssc.SerialPort;
import jssc.SerialPortException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import pif.arduino.Console.ConsolePeer;
import pif.arduino.tools.*;

import cc.arduino.packages.BoardPort;
import cc.arduino.packages.discoverers.SerialDiscovery;
import cc.arduino.packages.uploaders.SerialUploader;
import processing.app.PreferencesData;
import processing.app.debug.TargetBoard;

public class ArdConsole implements ConsolePeer, FileScanner.FileScanHandler {
	private static Logger logger = Logger.getLogger(ArdConsole.class);

	static {
		logger.info("STATIC");
	}
	static final short STATE_NONE = 0;
	static final short STATE_UPLOADING = 1;
	static final short STATE_CONNECTED = 2;
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

	protected File uploadFile;
	protected FileScanner scanner;
	protected SerialUploader uploader;

	// "arduino IDE" serial object
	protected MySerial serial = null;
	// link to "real" port to force open/close
	protected SerialPort serialPort = null;

	protected Console console;

	public static void main(String[] args) {
		logger.info("MAIN");
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
		setPort(commandLine.getOptionValue('p'));

		if (commandLine.hasOption('b')) {
			setBoard(commandLine.getOptionValue('b'));
		} else {
			// if board is not specified, try to detect it
			if (port.board == null) {
				logger.fatal("No board specified, and can't auto-detect it");
				usage(2);
			} else {
				ArduinoConfig.setBoard(port.board);
			}
		}
		// HERE portBoard contains a port address and a board structure, and Arduino preferences are set accordingly.
		// OK, what do we have to do with this ...

		// set verbosity
		if (commandLine.hasOption('v')) {
			PreferencesData.setBoolean("upload.verbose", true);
		} else {
			PreferencesData.setBoolean("upload.verbose", false);
		}

		// establish console
		console = new Console(this);

		// may be a file to look for
		if (commandLine.hasOption('f')) {
			try {
				uploadFile = new File(commandLine.getOptionValue('f'));
				scanner = new FileScanner(uploadFile, this);
			} catch (FileNotFoundException e) {
				usage(2);
			}
			if (commandLine.hasOption('u')) {
				launchUpload();
			}
		}
		connect();
		console.start();
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

	protected void setPort(String portName) {
		port = ArduinoConfig.getPortByName(portName);
		if (port == null) {
			logger.fatal("Unknown port " + portName);
			System.exit(4);
		}
		ArduinoConfig.setPort(port);
	}

	protected void setBoard(String boardName) {
		TargetBoard board = ArduinoConfig.setBoard(boardName);
		if (board == null) {
			usage(2);
		}
		if (port.board != null && port.board != board) {
			logger.warn(String.format("port was detected has a different board (%s) than specifed one (%s)",
					port.board.getId(), board.getId()));
		}
	}

	@Override
	public void onOutgoingData(byte[] data) {
		if (state == STATE_CONNECTED) {
			serial.write(data);
		} else {
			logger.warn("Not connected : can't send data");
		}
	}

	@Override
	public boolean onCommand(String command) {
		String args = null;
		int space = command.indexOf(' ');
		if (space != -1) {
			args = command.substring(space + 1);
		}

		if (command.equals("upload") || command.equals("u")) {
			launchUpload();
		} else if (command.equals("boards")) {
			ArduinoConfig.listBoards(System.out, false);
		} else if (command.equals("board")) {
			setBoard(args);
		} else if (command.equals("ports")) {
			ArduinoConfig.listPorts(System.out);
		} else if (command.equals("port")) {
			setPort(args);
		} else if (command.equals("connect")) {
			connect();
		} else if (command.equals("disconnect")) {
			disconnect();
		} else {
			return false;
		}
		return true;
	}

	@Override
	public void onDisconnect(int status) {
		while (state == STATE_UPLOADING) {
			// if uploading, wait it to finish
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		if (state == STATE_CONNECTED) {
			disconnect();
		}
	}

	@Override
	public void onFileChange() {
		launchUpload();
	}

	protected void connect() {
		// create this object lately because its constructor opens connection
		// and we want to be able to upload at startup, thus before connection
		if (serial == null) {
			try {
				serial = new MySerial(console);
				serialPort = new SerialPort(port.address);
				state = STATE_CONNECTED;
			} catch (/*processing.app.Serial*/Exception e) {
				// just specify Exception or jvm crashes on java.lang.NoClassDefFoundError:
				// at startup.
				logger.error("Can't connect", e);
				serial = null;
				serialPort = null;
				state = STATE_FAIL;
			}
		} else if (state == STATE_NONE) {
			try {
				serialPort.openPort();
				state = STATE_CONNECTED;
			} catch (SerialPortException e) {
				logger.error("Can't connect", e);
				try {
					serial.dispose();
				} catch (IOException e1) {
					// already in fail state -> silently ignore
				}
				serial = null;
				serialPort = null;
				state = STATE_FAIL;
			}
		} else if (state == STATE_CONNECTED) {
			logger.warn("already connected");
		} else if (state == STATE_UPLOADING) {
			logger.warn("Can't connect during upload");
		}
	}

	protected void disconnect() {
		if (state == STATE_CONNECTED) {
			try {
				serialPort.closePort();
				state = STATE_NONE;
			} catch (SerialPortException e) {
				logger.error("Can't disconnect", e);
				try {
					serial.dispose();
				} catch (IOException e1) {
					// already in fail state -> silently ignore
				}
				serial = null;
				serialPort = null;
				state = STATE_FAIL;
			}
		} else {
			logger.warn("not connected");			
		}
	}

	protected void launchUpload() {
		if (uploader == null) {
			uploader = new SerialUploader();
		}
		short stateBefore = state;
		switch(state) {
		case STATE_UPLOADING: // possible ?
			logger.warn("Already uploading, abort");
			return;
		case STATE_CONNECTED:
			disconnect();
			break;
		case STATE_FAIL:
		case STATE_NONE:
			// noop
		}

		List<String> warnings = new ArrayList<String>();
		try {
			uploader.uploadUsingPreferences(uploadFile, null, "noname", false, warnings);
		} catch (Exception e) {
			logger.error("Upload failed", e);
		}
		for (String line: warnings) {
			logger.warn(line);
		}

		if (stateBefore == STATE_CONNECTED) {
			connect();
		}
	}
}
