package pif.arduino;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import pif.arduino.Console.ConsolePeer;

public class ArdConsole implements ConsolePeer {
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

	static Options options;
	static {
		options = new Options();
		options.addOption("h", "help", false, "usage");
		options.addOption("I", "arduino-ide", true, "base installation directory of Arduino IDE");
		options.addOption("B", "boards", false, "list supported target boards");
		options.addOption("b", "board", true, "set target boards");

		options.addOption("P", "ports", false, "list available ports");
		options.addOption("p", "port", true, "set port to connect to");

		options.addOption("f", "file", true, "file to scan / upload");
		options.addOption("u", "upload", false, "launch upload at startup");
		options.addOption("v", "verbose", false, "set upload verbosity");
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
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onCommand(String command) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onDisconnect(int status) {
		// TODO Auto-generated method stub

	}

}
