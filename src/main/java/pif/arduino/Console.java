package pif.arduino;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

import pif.arduino.tools.JlineConsole;
import pif.arduino.tools.hexTools;

/**
 * goal of this class is :
 * - to handle incoming data to display them in raw, ascii or hex format
 * - interpret command line to get data to send from raw or hex format
 * - handle specific commands
 * it must be associated to a peer which handle incoming and outgoing data
 * and specific commands
 * @author pif
 *
 */
public class Console extends Thread {
	Logger logger = Logger.getLogger(Console.class);

	protected ConsolePeer peer;

	static Options options;
	static {
		options = new Options();
		options.addOption("h", "help", false, "usage");
		options.addOption("l", "linemode", true, "line mode cr, lr, crlf or none (default)");
		options.addOption("r", "raw", false, "raw mode. Console output is raw, no history nor editing facilities");
		options.addOption("o", "output", true, "output mode : hex, ascii or raw");
	}
	static public Options getOptions() {
		return options;
	}

	static public final String PROMPT = "> ";

	/*
	 * console must be "attached" to a class which handle commands and data, thru
	 * this interface
	 */
	public interface ConsolePeer {
		/**
		 * called when console reads data to send to peer
		 * @param data raw bytes to send
		 */
		public void onOutgoingData(byte data[]);

		/**
		 * called when console receives command before trying to handle it itself
		 * @param command command line, without '!' prefix
		 * @return true if peer as handled the command
		 */
		public boolean onCommand(String command);

		/**
		 * called when console is closed (Ctrl-D or !exit command, or unrecoverable error)
		 * @param status 0 for normal exit, error code else
		 */
		public void onExit(int status);
	}

	protected boolean raw = false;

	public boolean isRaw() {
		return raw;
	}

	// current display mode
	public static final byte MODE_RAW   = 0;
	public static final byte MODE_ASCII = 1;
	public static final byte MODE_HEX   = 2;

	protected byte displayMode = MODE_ASCII;

	public String getDisplayMode() {
		switch (displayMode) {
		case MODE_RAW  : return "raw";
		case MODE_ASCII: return "ascii";
		case MODE_HEX  : return "hex";
		default: return "???";
		}
	}

	// current line mode
	public static final String LINE_CR   = "\r";
	public static final String LINE_LF   = "\n";
	public static final String LINE_CRLF = "\r\n";
	public static final String LINE_NONE = "";

	protected String lineMode = LINE_NONE;

	public String getLineMode() {
		switch (lineMode) {
		case LINE_CR  : return "CR";
		case LINE_LF  : return "LF";
		case LINE_CRLF: return "CRLF";
		case LINE_NONE: return "none";
		default: return "???";
		}
	}

	public Console(ConsolePeer peer) throws IllegalArgumentException {
		this(peer, null);
	}

	public Console(ConsolePeer peer, CommandLine commandLine) throws IllegalArgumentException {
		if (commandLine!=null) {
			if (commandLine.hasOption('l')) {
				switch(commandLine.getOptionValue('l')) {
				case "cr":
					lineMode = LINE_CR;
					break;
				case "lf":
					lineMode = LINE_LF;
					break;
				case "crlf":
					lineMode = LINE_CRLF;
					break;
				case "none":
					lineMode = LINE_NONE;
					break;
				default:
					throw new IllegalArgumentException("bad value for 'linemode' option");
				}
			}
			if (commandLine.hasOption('o')) {
				switch(commandLine.getOptionValue('o')) {
				case "hex":
					displayMode = MODE_HEX;
					break;
				case "ascii":
					displayMode = MODE_ASCII;
					break;
				case "raw":
					displayMode = MODE_RAW;
					break;
				default:
					throw new IllegalArgumentException("bad value for 'output' option");
				}
			}
			if (commandLine.hasOption('r')) {
				raw = true;
				displayMode = MODE_RAW;
			}
		}
		this.peer = peer;
	}

	/**
	 *  console where incoming data are displayed and command input comes from
	 *  wraps JLineConsole or System.in/out according to raw mode
	 */
	protected class MyConsole {
		// jline version if not raw
		JlineConsole jline;
		// input if raw mode
		BufferedReader input;

		public MyConsole(boolean raw) throws IOException {
			if (raw) {
				input = new BufferedReader(new InputStreamReader(System.in));
			} else {
				jline = new JlineConsole();
				jline.setHistoryEnabled(true);
				jline.setExpandEvents(false);
				jline.setPrompt(PROMPT);
			}
		}
		public String readLine() throws IOException {
			if (raw) {
				return input.readLine();
			} else {
				return jline.readLine();
			}
		}
		public void insertString(String str) throws IOException {
			if (raw) {
				System.out.print(str);
			} else {
				jline.insertString(str);
			}
		}
	}
	protected MyConsole console;

	/*
	 * peer sends data it receives thru this method
	 */
	public void onIncomingData(byte data[], int length) {
		String toDisplay;
		switch(displayMode) {
		case MODE_ASCII:
			StringBuffer sb = new StringBuffer(length);
			for (int i = 0; i < length; i++) {
				if (data[i] == '\r') {
					sb.append("\\r");
				} else if(data[i] == '\n' || (data[i] >= 32 && data[i] < 128)) {
					sb.append((char)data[i]);
				} else {
					sb.append("\033[7m[" + hexTools.toHex(data[i]) + "]\033[0m");
				}
			}
			toDisplay = sb.toString();
			break;
		case MODE_HEX:
			toDisplay = hexTools.toHexDump(data, length);
			break;
		default:
			toDisplay = new String(data, 0, length);
		}
		try {
			console.insertString(toDisplay);
		} catch (NullPointerException e) {
			logger.error("console is null (start up race condition ?)");
		} catch (IOException e) {
			logger.error("Couldn't display incoming data", e);
		}
	}

	public void onIncomingData(byte data[]) {
		onIncomingData(data, data.length);
	}

	public void insertString(String str) throws IOException {
		console.insertString(str);
	}

	byte outgoingBuffer[] = null;

	void setBuffer(String line) {
		outgoingBuffer = (line + lineMode).getBytes();
	}

	void send() {
		peer.onOutgoingData(outgoingBuffer);
	}

	void handleCommand(String line) {
		// delegate to peer at first
		if (peer.onCommand(line)) {
			return;
		}

		switch(line) {
		case "cr":
			lineMode = LINE_CR;
			break;
		case "lf":
			lineMode = LINE_LF;
			break;
		case "crlf":
			lineMode = LINE_CRLF;
			break;
		case "none":
			lineMode = LINE_NONE;
			break;

		case "hex":
			displayMode = MODE_HEX;
			break;
		case "ascii":
			displayMode = MODE_ASCII;
			break;
		case "raw":
			displayMode = MODE_RAW;
			break;

		case "help":
		case "?":
			help();
			break;

		case "exit":
			// calling method will handle this
			break;

		default:
			if (line.startsWith("x ")) {
				byte[] data = readHexData(line.substring(2));
				logger.debug(hexTools.toHexDump(data));
				if (data != null) {
					peer.onOutgoingData(data);
				}
			} else {
				logger.warn("Unknown command " + line + ". Type !help or !? for help");
			}
		}
	}

	/**
	 * convert a string to a byte array.
	 * string must contains a list of hex values, with optional spaces and ascii sections
	 * example : 0 123456 7 8 9ab 'ab c' 0123
	 * is converted in hex values [ 00 12 34 56 07 08 9a 0b 61 62 20 63 01 23 ]
	 * @param data
	 * @return
	 */
	public byte[] readHexData(String data) {
		byte[] result = new byte[data.length()];
		int len = 0;
		// -1 = reading ascii
		//  1 = waiting for high part of a byte value
		//  2 = waiting for low part of a byte value
		// TODO 0d123 => input decimal value
		// TODO 0w123 or 0w-123 or => word big endian value
		// TODO 0l123 or 0l-123 => long big endian value
		// for these options, detect d, w or l when state==1 => state = 3 / 4 / 5
		// + flag (-3 / -4 / -5 ?) to handle negative value
		// + must stop on space or quote
		short state = 1;
		byte currentByte = 0;
		for(int i = 0; i < data.length(); i++) {
			char c = data.charAt(i);

			if (c == '\'') {
				// end of ascii mode
				if (state == -1) {
					state = 1;
					currentByte = 0;
				} else {
					if (state == 2) {
						// already read beginning of a byte => consider it as a 1 digit value
						result[len++] = currentByte;
					}
					state = -1;
				}
				continue;
			}

			// ascii mode => take char as a byte.
			if (state == -1) {
				result[len++] = (byte)c;
				continue;
			}

			if (c == ' ') {
				// explicit separation between values
				// event if they have 1 digit
				if (state == 2) {
					result[len++] = currentByte;
					state = 1;
				}
				continue;
			}

			// else, must be a hex digit
			byte h;
			if (c >= '0' && c <= '9') {
				h = (byte)(c - '0');
			} else if (c >= 'a' && c <= 'f') {
				h = (byte)(c - 'a' + 10);
			} else if (c >= 'A' && c <= 'F') {
				h = (byte)(c - 'A' + 10);
			} else {
				logger.error(String.format("unexpected character '%c' at position %d", c, i));
				return null;
			}
			if (state == 1) {
				currentByte = h;
				state = 2;
			} else {
				currentByte = (byte)(currentByte << 4 | h);
				result[len++] = currentByte;
				state = 1;
			}
		}
		if (state == 2) {
			// already read beginning of a byte => consider it as a 1 digit value
			result[len++] = currentByte;
		}
		return Arrays.copyOf(result, len);
	}

	public void run() {
		try {
			console = new MyConsole(raw);
		} catch (IOException e) {
			logger.error("Can't initialize console", e);
			peer.onExit(1);
			return;
		} 

		String inputLine;

		try {
			while((inputLine = console.readLine()) != null) {
				try {
					if (inputLine.length() > 0 && inputLine.charAt(0) == '!') {
						if (inputLine.length() == 1) {
							// resend last buffer
							if (outgoingBuffer != null) {
								peer.onOutgoingData(outgoingBuffer);
							}
						} else if (inputLine.charAt(1) == '!') {
							// specific case when user wants to send raw data beginning by '!',
							// he must double it.
							setBuffer(inputLine.substring(1));
							send();
						} else {
							if ("!exit".equals(inputLine)) {
								logger.debug("Exit console");
								peer.onExit(0);
								return;
							}
							handleCommand(inputLine.substring(1));
						}
					} else {
						setBuffer(inputLine);
						send();
					}
				} catch (Exception e) {
					logger.info("Exception in console loop", e);
//					e.printStackTrace();
				}
			}
			logger.debug("EOF");
			peer.onExit(0);
		} catch (IOException e) {
			logger.error("Exception with input, cancelling console", e);
			peer.onExit(1);
//			e.printStackTrace();
		}
	}

	void help() {
		// TODO
		String help = "Any byte incomming from peer is displayed in a format according to display mode (see hex, rax and ascii command below)"
				+ "Any character entered in console are filled in a sending buffer, excepted if line begins with '!'.\n"
				+ "  When hitting enter, end of line characters are appended, and buffer is sent to peer.\n"
				+ "  End of line character depends on current line mode, which is 'none' by default (thus no character).\n"
				+ "  To change this mode see cr, lf, .. commands below.\n"
				+ "  To send explicitly a line beginning by '!', double it ('!!')\n"
				+ "Lines begining with a '!' start a command :"
				+ "  !exit (or Ctrl-D) : exit console program\n"
				+ "  ! : resend last sending buffer, including its end of line characters (even if line mode has been modified)\n"
				+ "  !hex : incoming bytes are displayed in hex, in same format than hexdump -C\n"
				+ "  !ascii : printable characters are displayed raw, other ones are displayed in [hh] format (default mode)\n"
				+ "  !raw : all incomming bytes are displayed raw (default mode if -raw command line option was set)\n"
				+ "  !cr, !lf, !crlf, !none : set 'end of line' mode, respectivly to '\\r', '\\n', '\\r\\n', nothing\n"
				+ "  !x : rest of input line is interpreted in a intuitive (?) way mixing hex values and raw text\n"
				+ "    Example : 0 123456 7 8 9ab 'ab c' 0123  becames hex bytes [ 00 12 34 56 07 08 9a 0b 61 62 20 63 01 23 ]\n"
				+ "    Caution : this not does NOT append end of line characters, whatever current line mode";
		System.out.println(help);
	}
}
