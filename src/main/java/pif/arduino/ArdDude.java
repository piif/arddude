package pif.arduino;

import gnu.io.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class ArdDude {
	static final short STATE_NONE = 0;
	static final short STATE_UPLOADING = 1;
	static final short STATE_CONNECTING = 2;
	static final short STATE_CONNECTED = 3;
	static final short STATE_FAIL = -1;

	static short state = STATE_NONE;

	static String portName;
	static CommPortIdentifier portId;
	static SerialPort connection;
	static int baudRate = 115200;

	static boolean forceUpload = false;
	static boolean forceReset = false;
	static String fileName;
	static File source;
	static long lastMod;

	static List<String> commandArgs;

	static boolean scanFile() {
		long t = source.lastModified();
		if (lastMod < t) {
			lastMod = t;
			launchUpload();
			return true;
		}
		return false;
	}

	static void launchUpload() {
		if (state == STATE_CONNECTED || state == STATE_CONNECTING) {
			// change state before to avoid thread to reconnect just after
			state = STATE_UPLOADING;
			disconnect();
		}

		state = STATE_UPLOADING;

		if (forceReset) {
			reset();
		}

		System.out.println("** Launching upload ..." + commandArgs);

		try {
			ProcessBuilder pb = new ProcessBuilder(commandArgs);
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
			Process p = pb.start();
			p.waitFor();
			int exitCode = p.exitValue();
			if (exitCode != 0) {
				System.err.println("Exit code " + exitCode);
				System.exit(1);
			}
		} catch (IOException | InterruptedException e) {
			state = STATE_FAIL;
			e.printStackTrace();
			System.exit(1);
		}

		if (forceReset) {
			reset();
		}

		connect();
	}

	static InputStream inStream;
	static OutputStream outStream;

	static SerialPortEventListener serialReader = new SerialPortEventListener() {
		public void serialEvent(SerialPortEvent event) {
			if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
				try {
					while (inStream.available() > 0) {
						int data = inStream.read();
						System.out.print((char)data);
					}
				} catch ( IOException e ) {
					e.printStackTrace();
				}
			}
		}
	};

	static void connect() {
		state = STATE_CONNECTING;

		try {
			// Get the port's ownership
			connection = (SerialPort) portId.open("ArdDude", 5000);

			connection.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			connection.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

			outStream = connection.getOutputStream();
			inStream = connection.getInputStream();

			connection.addEventListener(serialReader);
		} catch(Exception e) {
			state = STATE_FAIL;
			// or ignore ?
			e.printStackTrace();
			connection.close();
			System.exit(1);
		}

		System.out.println("** Connected");
		state = STATE_CONNECTED;
		connection.notifyOnDataAvailable(true);
	}

	static void reset() {
		try {
			System.out.println("** force serial reset");
			// Get the port's ownership
			connection = (SerialPort) portId.open("ArdDude", 5000);
			connection.setSerialPortParams(1200, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			connection.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

			Thread.sleep(1500);

			connection.close();

			Thread.sleep(1500);

		} catch(Exception e) {
			state = STATE_FAIL;
			// or ignore ?
			e.printStackTrace();
			connection.close();
			System.exit(1);
		}
	}

	static void disconnect() {
		if (connection != null) {
			connection.close();
		}
		inStream = null;
		outStream = null;
		connection = null;
		state = STATE_NONE;
	}

	private static final Pattern PORT_PATTERN = Pattern.compile("-P(.*)");
	private static final Pattern FILE_PATTERN = Pattern.compile("-U\\w+:\\w:([^:]*)(:.*)?");

	public static void main(String[] args) {
		commandArgs = new ArrayList<String>(args.length);

		int i = 0;
		while(true) {
			if ("-f".equals(args[i])) {
				forceUpload = true;
				i++;
			} else if ("-r".equals(args[i])) {
				forceReset = true;
				i++;
			} else {
				break;
			}
		}
		for(; i < args.length; i++) {
			Matcher m;
			if ((m = PORT_PATTERN.matcher(args[i])).matches()) {
				portName = m.group(1);
			} else if ((m = FILE_PATTERN.matcher(args[i])).matches()) {
				fileName = m.group(1);
			}
			commandArgs.add(args[i]);
		}

		if (portName == null) {
			System.err.println("Can't find port name in command line");
			System.exit(1);
		}
		if (fileName == null) {
			System.err.println("Can't find file to scan in command line");
			System.exit(1);
		}

		// ugly hack found here :
		// https://bugs.launchpad.net/ubuntu/+source/rxtx/+bug/367833/comments/6
		System.setProperty("gnu.io.rxtx.SerialPorts", portName);

		try {
			// Obtain a CommPortIdentifier object for the port you want to open
			portId = CommPortIdentifier.getPortIdentifier(portName);
		} catch(Exception e) {
			System.err.println("Can't open port " + portName + " : " + e);
			System.exit(1);
		}

		source = new File(fileName);
		if (forceUpload) {
			lastMod = -1;
		} else {
			lastMod = source.lastModified();
		}

		if (!scanFile()) {
			connect();
		}

		Timer t = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				scanFile();
			}
		};
		t.schedule(task, 0, 2000);

		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String inputLine;
		try {
			while((inputLine = input.readLine()) != null) {
				if ("!exit".equals(inputLine)) {
					break;
				} else if ("!upload".equals(inputLine)) {
					launchUpload();
					continue;
				}
				while (state != STATE_CONNECTED) {
					// comment facilement bloquer tant qu'on n'est pas connecté ?
					Thread.sleep(500);
				}
				outStream.write((inputLine + "\n").getBytes());
			}
		} catch (Exception e) {
			// ignore ?
			e.printStackTrace();
		}

		disconnect();
		System.exit(0);
	}
}
