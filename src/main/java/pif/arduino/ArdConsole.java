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

import cc.arduino.packages.uploaders.MySerialUploader;
import pif.arduino.tools.*;

/**
 * Main class for serial console
 * @author pif
 */
public class ArdConsole implements Console.ConsolePeer, FileScanner.FileScanHandler {
	private static Logger logger = Logger.getLogger(ArdConsole.class);

	static final short STATE_NONE = 0;
	static final short STATE_UPLOADING = 1;
	static final short STATE_CONNECTED = 2;
	static final short STATE_FAIL = -1;

	static short state = STATE_NONE;

	static Options options;
	static {
		options = Console.getOptions();
		options.addOption("a", "arduino-cli", true, "arduino-cli command and global options");

		options.addOption("p", "port", true, "set port to connect to");
		options.addOption("s", "baudrate", true, "set port baudrate");

		options.addOption("f", "file", true, "file to scan / upload");
		options.addOption("u", "upload", false, "launch upload at startup");
		options.addOption("d", "debug", false, "set debug level");

		options.addOption("x", "exit", false, "exit after dump or upload commands, instead of launching console");
		options.addOption("c", "command", true, "comma separated list of commands to send after connection");
	}

	protected static boolean rawMode = false;

	protected String portName;
	protected int baudrate;

	protected File uploadFile;
	String uploadFilePath;
	protected FileScanner scanner;

	public static final String DEFAULT_UPLOAD_COMMAND = "arduino-cli upload";
	String uploadCommand = DEFAULT_UPLOAD_COMMAND;

	// "arduino IDE" serial object
	protected MySerial serial = null;

	protected Console console = null;

	/**
	 * parse command line, load arduino config and instantiate a ArdConsole
	 * @param args
	 */
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

		if (commandLine.hasOption('d')) {
			Logger.getRootLogger().setLevel(org.apache.log4j.Level.DEBUG);
		}

		// ok, it will become a bit more complicated, pass to an instance...
		new ArdConsole(commandLine);
	}

	/**
	 * initialize port ..., launch file scanner if file specified,
	 * launch console and connect
	 * @param commandLine
	 */
	ArdConsole(CommandLine commandLine) {
		if (commandLine.hasOption('p')) {
			setPort(commandLine.getOptionValue('p'));
		}
		if (commandLine.hasOption('s')) {
			baudrate = Integer.parseInt(commandLine.getOptionValue('s'));
		}

		if (commandLine.hasOption('a')) {
			uploadCommand = commandLine.getOptionValue('a');
		}

		if (commandLine.hasOption('f')) {
			uploadFile = new File(commandLine.getOptionValue('f'));
			// upload config waits for a file name specified as : {build.path}/{build.project_name}.hex or .bin
			// thus, have to split it
			uploadFilePath = uploadFile.getParent();
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
		try {
			console = new Console(this, commandLine);
		} catch(IllegalArgumentException e) {
			usage(3);
		}

		if (portName != null) {
			connect();
		}
		console.start();
		if (commandLine.hasOption('c')) {
			for (String cmd : commandLine.getOptionValue('c').split(",")) {
				console.handleCommand(cmd);
			}
		}
	}

	protected static void usage(int exitCode) {
		HelpFormatter fmt = new HelpFormatter();
		fmt.printHelp(80, "java -jar main_jar_file.jar pif.arduino.ArdConsole options ...", "options :", options, "");
		System.exit(exitCode);
	}


	void commandsHelp() {
		String help = "  !port xxx : set current serial port (like -p option)\n"
				+ "  !connect and !disconnect : as the name suggests ...\n"
				+ "  !baudrate nn : set baudrate\n"
				+ "  !reset : try to reset serial port, then reconnect if was connected (useful after upload in some cases)\n"
				+ "  !upload : launch upload then reconnect if was connected\n"
				+ "  !file filename : set file path to scan for modification"
				+ "  !status : display current connexion status";
		System.out.println(help);
	}

	protected void setPort(String portName) {
		this.portName = portName;
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

		switch(command) {
		case "upload":
		case "u":
			launchUpload();
			break;
		case "port":
			setPort(args);
			break;
		case "baudrate":
			baudrate = Integer.parseInt(args);
			break;
		case "connect":
			connect();
			break;
		case "disconnect":
			disconnect();
			break;
		case "file":
		case "scan":
			if (args == null) {
				logger.error("missing file path to scan");
			} else {
				if (scanner != null) {
					scanner.stop();
				}
				try {
					scanner = new FileScanner(new File(args), this);
				} catch (FileNotFoundException e) {
					logger.error("Bad file path to scan");
				}
			}
			break;
		case "reset":
			resetPort();
			break;
		case "status":
			status(System.out);
			break;
		case "help":
		case "?":
			// trap command to output our own help
			commandsHelp();
			// but return false to let caller handle its own
			return false;
		default:
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
				serial = new BufferedSerial(console, portName, baudrate);
				state = STATE_CONNECTED;
			} catch (MySerialException e) {
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
			MySerial.touchForCDCReset(portName);
		} catch (Exception e) {
			logger.error("port reset failed", e);
		}
		connect();
	}

	protected void status(PrintStream output) {
		if (portName != null) {
			output.println("Configured to connect on port " + portName);
		}
		output.println("Baudrate is " + baudrate);

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

		output.println("Line mode " + console.getLineMode());
		output.println("Display mode " + console.getDisplayMode());

		if (uploadFile != null) {
			output.println("Scanning file " + uploadFile);
		}
	}

	protected void launchUpload() {
		if (portName == null) {
			logger.error("port was not specified, can't connect");
			return;
		}

		logger.info("** launching upload **");

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

		// TODO : use ProcessBuilder instead , or a variant if ProcessBuilder wants separate arguments
		ProgramLauncher cmd = new ProgramLauncher(uploadCommand);
		if (!console.isRaw()) {
			cmd.setOutMask("\033[32m", "\033[0m\n");
			cmd.setErrMask("\033[32m", "\033[0m\n");
		}
		int status = -1;
		try {
			status = cmd.run(System.out);
		} catch (Exception e) {
			logger.error("Upload execution failed", e);
		}
		if (status != 0) {
			logger.error("Upload failed");
		}

		if (stateBefore == STATE_CONNECTED) {
			connect();
		}
	}
}
