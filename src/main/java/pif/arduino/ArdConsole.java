package pif.arduino;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import pif.arduino.tools.*;
import cc.arduino.packages.uploaders.SerialUploader;
import processing.app.PreferencesData;
import processing.app.Serial;
import processing.app.debug.TargetBoard;

public class ArdConsole implements Console.ConsolePeer, FileScanner.FileScanHandler {
	private static Logger logger = Logger.getLogger(ArdConsole.class);

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

		options.addOption("x", "exit", false, "exit after dump commands instead of launching console");
		options.addOption("r", "raw", false, "raw mode : dumps list only ids, console is raw, without history nor editing facilities");

	}

	protected static boolean rawMode = false;

	protected String boardName, portName;
	protected ArduinoConfig.PortBoard port;

	protected File uploadFile;
	String uploadFilePath, uploadFileName;
	protected FileScanner scanner;
	protected SerialUploader uploader;

	// "arduino IDE" serial object
	protected MySerial serial = null;

	protected Console console;

	public static void main(String[] args) {
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

		if (commandLine.hasOption('r')) {
			rawMode = true;
		}

		if (commandLine.hasOption('P')) {
			ArduinoConfig.listPorts(System.out, rawMode);
		}
		if (commandLine.hasOption('B')) {
			ArduinoConfig.listBoards(System.out, rawMode, false);
		}

		// set verbosity
		if (commandLine.hasOption('v')) {
			PreferencesData.setBoolean("upload.verbose", true);
		} else {
			PreferencesData.setBoolean("upload.verbose", false);
		}

		// ok, it will become a bit more complicated, pass to an instance...
		new ArdConsole(commandLine);
	}

	ArdConsole(CommandLine commandLine) {
		if (commandLine.hasOption('p')) {
			setPort(commandLine.getOptionValue('p'));
		}
		if (commandLine.hasOption('b')) {
			setBoard(commandLine.getOptionValue('b'));
		}

		if (commandLine.hasOption('f')) {
			uploadFile = new File(commandLine.getOptionValue('f'));
			// upload config waits for a file name specified as : {build.path}/{build.project_name}.hex or .bin
			// thus, have to split it
			uploadFilePath = uploadFile.getParent();
			uploadFileName = uploadFile.getName();
			if (!uploadFileName.endsWith(".hex") && !uploadFileName.endsWith(".bin")) {
				logger.error("file to upload must have .hex or .bin extension");
				System.exit(2);
			}
			uploadFileName = uploadFileName.substring(0, uploadFileName.length() - 4);
		}

		if (commandLine.hasOption('u')) {
			if (uploadFile == null) {
				logger.error("Can't upload : no file specified (-f option)");
				usage(2);
			} else {
				launchUpload();
			}
		}

		if (commandLine.hasOption('x')) {
			System.exit(0);
		}

		try {
			if (uploadFile != null) {
				scanner = new FileScanner(uploadFile, this);
			}
		} catch (FileNotFoundException e) {
			logger.error("File to upload doesn't exists");
			usage(2);
		}

		// establish console
		console = new Console(this, rawMode);

		if (portName != null) {
			connect();
		}
		console.start();
	}

	protected static void usage(int exitCode) {
		HelpFormatter fmt = new HelpFormatter();
		String footer =
				"\narduino ide path is looked for respectivly in command line option, java properties (-DARDUINO_IDE=...)," +
				" ARDUINO_IDE environment variable, location of BaseNoGui arduino class if already loaded" +
				" (if classpath was set accordingly for example)";
		fmt.printHelp(80, "java -jar main_jar_file.jar pif.arduino.ArdConsole options ...", "options :", options, footer);
		System.exit(exitCode);
	}

	protected void setPort(String portName) {
		ArduinoConfig.PortBoard newPort = ArduinoConfig.getPortByName(portName);
		if (newPort == null) {
			logger.error("Unknown port " + portName);
			return;
		}
		port = newPort;
		this.portName = portName;
		ArduinoConfig.setPort(port);

		// if no board selected and port identified one, set it
		if (boardName == null) {
			if (port.board != null) {
				ArduinoConfig.setBoard(port.board);
			}
		} else if (port.board != null && !boardName.equals(port.board.getId())) {
			logger.warn(String.format("port was detected has a different board (%s) than specifed one (%s)",
					port.board.getId(), boardName));
		}
	}

	protected void setBoard(String boardName) {
		TargetBoard board = ArduinoConfig.setBoard(boardName);
		if (board == null) {
			logger.error("Unknown board " + boardName);
			return;
		}
		if (port != null && port.board != null && port.board != board) {
			logger.warn(String.format("port was detected has a different board (%s) than specifed one (%s)",
					port.board.getId(), board.getId()));
		}
		this.boardName = boardName;
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
			command = command.substring(0, space);
		}

		if (command.equals("upload") || command.equals("u")) {
			launchUpload();
		} else if (command.equals("boards")) {
			ArduinoConfig.listBoards(System.out, rawMode, false);
		} else if (command.equals("board")) {
			setBoard(args);
		} else if (command.equals("ports")) {
			ArduinoConfig.listPorts(System.out, rawMode, args != null);
		} else if (command.equals("port")) {
			setPort(args);
		} else if (command.equals("connect")) {
			connect();
		} else if (command.equals("disconnect")) {
			disconnect();
		} else if (command.equals("reset")) {
			resetPort();
		} else if (command.equals("status")) {
			status(System.out);
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
		if (scanner != null) {
			scanner.stop();
		}
	}

	@Override
	public void onFileChange() {
		try {
			console.insertString("** Change detected in scanned file **");
		} catch (IOException e) {
			logger.info("** Change detected in scanned file **");
			
		}
		launchUpload();
	}

	protected void connect() {
		if (portName == null) {
			logger.error("port was not specified, can't connect");
			return;
		}

		// create this object lately because its constructor opens connection
		// and we want to be able to upload at startup, thus before connection
		if (state == STATE_NONE) {
			// serial must be null
			try {
				serial = new MySerial(console);
				state = STATE_CONNECTED;
			} catch (/*processing.app.Serial*/Exception e) {
				// just specify Exception or jvm crashes at startup
				// with java.lang.NoClassDefFoundError
				logger.error("Can't connect", e);
				try {
					// try to clean up ?
					serial.dispose();
				} catch (IOException e1) {}
				serial = null;
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
				serial.dispose();
				state = STATE_NONE;
			} catch (IOException e) {
				logger.error("Can't disconnect", e);
				state = STATE_FAIL;
			} finally {
				serial = null;
			}
		} else {
			logger.warn("not connected");			
		}
	}

	protected void resetPort() {
		if (portName == null) {
			logger.error("port was not specified, can't connect");
			return;
		}
		if (state == STATE_CONNECTED) {
			disconnect();
		}
		try {
			Serial.touchPort(port.address, 1200);
		} catch (Exception e) {
			logger.error("port reset failed", e);
		}
		connect();
	}

	protected void status(PrintStream output) {
		if (port != null) {
			output.println("Configured to connect on port " + port.address);
			if (boardName != null) {
				output.println(" as platform " + boardName);
			}
		}
		if (uploadFile != null) {
			output.println("Scanning file " + uploadFile);
		}
		switch(state) {
		case STATE_NONE:
		case STATE_FAIL:
			output.println("Not connected");
			break;
		case STATE_UPLOADING:
			output.println("Uploading code");
			break;
		case STATE_CONNECTED:
			output.println("Connected");
			break;
		}
	}

	protected void launchUpload() {
		if (portName == null) {
			logger.error("port was not specified, can't connect");
			return;
		}

		logger.info("** launching upload **");

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

		MessageRenderer warnings = new MessageRenderer(System.out, "[upload]");

		try {
			uploader.uploadUsingPreferences(uploadFile, uploadFilePath, uploadFileName, false, warnings);
		} catch (Exception e) {
			logger.error("Upload failed", e);
		}
//		for (String line: warnings) {
//			logger.warn(line);
//		}

		if (stateBefore == STATE_CONNECTED) {
			connect();
		}
	}
}
