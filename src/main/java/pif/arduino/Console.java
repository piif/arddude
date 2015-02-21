package pif.arduino;

import java.io.IOException;
import java.util.Arrays;

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
		 * called when console receives command it can't handle itself
		 * @param command command line, without '!' prefix
		 */
		public boolean onCommand(String command);

		/**
		 * called when console is closed (Ctrl-D or !exit command, or unrecoverable error)
		 * @param status 0 for normal exit, error code else
		 */
		public void onDisconnect(int status);
	}

	public Console(ConsolePeer peer) {
		this.peer = peer;
	}

	// jline console where incoming data are displayed and command input comes from
	JlineConsole console;

	// current display mode
	static final byte MODE_RAW   = 0;
	static final byte MODE_ASCII = 1;
	static final byte MODE_HEX   = 2;

	protected byte displayMode = MODE_RAW;

	/*
	 * peer sends data it receives thru this method
	 */
	public void onIncomingData(byte data[]) {
//		logger.debug("Receiving data '" + new String(data) + "'");
		String toDisplay;
		switch(displayMode) {
		case MODE_ASCII:
			StringBuffer sb = new StringBuffer(data.length);
			for (int i = 0; i < data.length; i++) {
				if (data[i] == '\r' || data[i] == '\n' || (data[i] > 32 && data[i] < 128)) {
					sb.append((char)data[i]);
				} else {
					sb.append("\\x");
					sb.append(hexTools.toHex(data[i]));
				}
			}
			toDisplay = sb.toString();
			break;
		case MODE_HEX:
			toDisplay = hexTools.toHexDump(data);
			break;
		default:
			toDisplay = new String(data);
		}
		try {
			console.insertString(toDisplay);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	byte outgoingBuffer[] = null;

	void setBuffer(String line) {
		// TODO hex => translate
		outgoingBuffer = line.getBytes();
	}

	void send() {
		peer.onOutgoingData(outgoingBuffer);
	}

	void handleCommand(String line) {
		if (line.equals("hex")) {
			displayMode = MODE_HEX;
		} else if (line.equals("ascii")) {
			displayMode = MODE_ASCII;
		} else if (line.equals("raw")) {
			displayMode = MODE_RAW;
		} else if (line.startsWith("x ")) {
			byte[] data = readHexData(line.substring(2));
			logger.debug(hexTools.toHexDump(data));
			if (data != null) {
				peer.onOutgoingData(data);
			}
		} else {
			if (!peer.onCommand(line)) {
				logger.warn("Unknown command " + line);
				
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
			console = new JlineConsole();
			console.setHistoryEnabled(true);
			console.setExpandEvents(false);
			console.setPrompt(PROMPT);
		} catch (IOException e) {
			logger.error("Can't initialize console", e);
			peer.onDisconnect(1);
			return;
		} 

		String inputLine;

		try {
			while((inputLine = console.readLine()) != null) {
				try {
					if ("!exit".equals(inputLine)) {
						logger.info("Exit console");
						peer.onDisconnect(0);
						return;
					} else if (inputLine.length() > 0 && inputLine.charAt(0) == '!') {
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
			logger.info("EOF");
			peer.onDisconnect(0);
		} catch (IOException e) {
			logger.error("Exception with input, cancelling console", e);
			peer.onDisconnect(1);
//			e.printStackTrace();
		}
	}
}
