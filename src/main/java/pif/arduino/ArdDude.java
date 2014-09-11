package pif.arduino;

import gnu.io.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class ArdDude implements CommPortOwnershipListener {
	static final short STATE_NONE = 0;
	static final short STATE_UPLOADING = 1;
	static final short STATE_CONNECTING = 2;
	static final short STATE_CONNECTED = 3;
	static final short STATE_FAIL = -1;

	static short state = STATE_NONE;

	String portName;
	int  portNameParam;
	CommPortIdentifier portId;
	SerialPort connection;
	int baudRate = 115200;

	boolean forceUpload = false;
	boolean forceReset = false;
	String fileName;
	File source;
	long lastMod;

	List<String> commandArgs;

	boolean scanFile() {
		long t = source.lastModified();
		if (lastMod < t) {
			lastMod = t;
			launchUpload();
			return true;
		}
		return false;
	}

	void launchUpload() {
		if (state == STATE_CONNECTED || state == STATE_CONNECTING) {
			// change state before to avoid thread to reconnect just after
			state = STATE_UPLOADING;
			disconnect();
		}

		state = STATE_UPLOADING;

		if (forceReset) {
			String newPortName = reset(portName);
			commandArgs.set(portNameParam, "-P" + newPortName);
		}

		System.out.println("** Launching " + commandArgs + " ...");

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
			try {
				System.out.println("wait a little before reconnection ..");
				Thread.sleep(1000);
				reset(null);
			} catch (InterruptedException e) {
				// don't matter
			}
		}

		connect();
	}

	InputStream inStream;
	OutputStream outStream;

	SerialPortEventListener serialReader = new SerialPortEventListener() {
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

	void connect() {
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

	static List<String> list() {
		List<String> list = new ArrayList<String>();
		try {
			// System.err.println("trying");
			@SuppressWarnings("unchecked")
			Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();
			// System.err.println("got port list");
			while (portList.hasMoreElements()) {
				CommPortIdentifier portId = portList.nextElement();
				// System.out.println(portId);
				if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					String name = portId.getName();
					list.add(name);
				}
			}
		} catch (UnsatisfiedLinkError e) {
			throw new RuntimeException("ports", e);
		} catch (Exception e) {
			throw new RuntimeException("ports", e);
		}
		return list;
	}

	String reset(String portName) {
		String newPortName = null;
		try {

			// Toggle 1200 bps on selected serial port to force board reset.
			List<String> before = list();
			if (portName != null && before.contains(portName)) {
				System.out.println("Forcing reset using 1200bps open/close on port " + portName);
				SerialPort connection = (SerialPort) portId.open("ArdDude", 5000);
				connection.setSerialPortParams(1200, SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				connection.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
	
				Thread.sleep(500);
				connection.close();
				Thread.sleep(500);
			}

			// Wait for a port to appear on the list
			int elapsed = 0;
			while (elapsed < 10000) {
				List<String> now = list();
				List<String> diff = new ArrayList<String>(now);
				diff.removeAll(before);
				System.out.print("PORTS {");
				for (String p : before) {
					System.out.print(p + ", ");
				}
				System.out.print("} / {");
				for (String p : now) {
					System.out.print(p + ", ");
				}
				System.out.print("} => {");
				for (String p : diff) {
					System.out.print(p + ", ");
				}
				System.out.println("}");
				if (diff.size() > 0) {
					newPortName = diff.get(0);
					System.out.println("Found upload port: " + newPortName);
					break;
				}
				before = now;
				Thread.sleep(250);
				elapsed += 250;
			}
		} catch(Exception e) {
			System.err.println("Can't reset port : " + e);
			System.exit(1);
		}
		return newPortName;
	}

	void disconnect() {
		if (connection != null) {
			connection.close();
		}
		inStream = null;
		outStream = null;
		connection = null;
		state = STATE_NONE;
	}

	@Override
	public void ownershipChange(int arg0) {
		// TODO Auto-generated method stub
		System.err.println("ownershipChange !!" + arg0);
		disconnect();
	}

	private static final Pattern PORT_PATTERN = Pattern.compile("-P(.*)");
	private static final Pattern FILE_PATTERN = Pattern.compile("-U\\w+:\\w:([^:]*)(:.*)?");

	public static void usage() {
		System.out.println("adrdude [-f] [-r] avrdude command line ...");
		System.out.println(" launch a console to serial and relaunch upload everytime image file is modified");
		System.out.println(" -f force upload at startup");
		System.out.println(" -r force serial reset before upload");
		System.out.println(" command line must contain serial device and image filename to look at");
		System.out.println(" in console, !exit stop connection and !upload force new upload");
		System.exit(0);
	}

	void run(String[] args) {
		commandArgs = new ArrayList<String>(args.length);

		if (args.length < 1 || "-h".equals(args[0]) || "--help".equals(args[0])) {
			usage();
		}

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
				portNameParam = commandArgs.size();
			} else if ((m = FILE_PATTERN.matcher(args[i])).matches()) {
				fileName = m.group(1);
				if (fileName.startsWith("/cygdrive/")) {
					fileName = fileName.charAt(10) + ":" + fileName.substring(11);
				}
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

		source = new File(fileName);
		if (source.exists()) {
			System.out.println("Scanning file " + source);
		} else {
			System.err.println("Can't find file " + source);
			System.exit(1);
		}

		try {
			// Obtain a CommPortIdentifier object for the port you want to open
			portId = CommPortIdentifier.getPortIdentifier(portName);
		} catch(Exception e) {
			System.err.println("Can't open port " + portName + " : " + e);
			System.exit(1);
		}
		portId.addPortOwnershipListener(this);

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

	public static void main(String[] args) {
		ArdDude myDude = new ArdDude();
		myDude.run(args);
	}
}
