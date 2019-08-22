/**
 * copy/paste from https://github.com/arduino/Arduino/blob/master/arduino-core/src/processing/app/Serial.java
 * with adaptations to remove references to processing
 * + code to send back data to console
 */

package pif.arduino.tools;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

abstract public class MySerial implements SerialPortEventListener {
	private SerialPort port;

	private static final int IN_BUFFER_CAPACITY = 128;
	private static final int OUT_BUFFER_CAPACITY = 128;

	private CharsetDecoder bytesToStrings;
	private ByteBuffer inFromSerial = ByteBuffer.allocate(IN_BUFFER_CAPACITY);
	private CharBuffer outToMessage = CharBuffer.allocate(OUT_BUFFER_CAPACITY);

	/**
	 * This method is intented to be extended to receive messages coming from serial
	 * port.
	 */
	abstract protected void message(char[] chars, int length);

	public MySerial(String iname, int irate) throws MySerialException {
		this(iname, irate, 'N', 8, 1, true, true);
	}

	public static boolean touchForCDCReset(String iname) throws MySerialException {
		SerialPort serialPort = new SerialPort(iname);
		try {
			serialPort.openPort();
			serialPort.setParams(1200, 8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setDTR(false);
			serialPort.closePort();
			return true;
		} catch (SerialPortException e) {
			throw new MySerialException(MessageFormat.format("Error touching serial port ''{0}''.", iname), e);
		} finally {
			if (serialPort.isOpened()) {
				try {
					serialPort.closePort();
				} catch (SerialPortException e) {
					// noop
				}
			}
		}
	}

	private MySerial(String iname, int irate, char iparity, int idatabits, float istopbits, boolean setRTS,
			boolean setDTR) throws MySerialException {
		// if (port != null) port.close();
		// this.parent = parent;
		// parent.attach(this);

		resetDecoding(StandardCharsets.UTF_8);

		int parity = SerialPort.PARITY_NONE;
		if (iparity == 'E')
			parity = SerialPort.PARITY_EVEN;
		if (iparity == 'O')
			parity = SerialPort.PARITY_ODD;

		int stopbits = SerialPort.STOPBITS_1;
		if (istopbits == 1.5f)
			stopbits = SerialPort.STOPBITS_1_5;
		if (istopbits == 2)
			stopbits = SerialPort.STOPBITS_2;

		try {
			port = new SerialPort(iname);
			port.openPort();
			boolean res = port.setParams(irate, idatabits, stopbits, parity, setRTS, setDTR);
			if (!res) {
				System.err.println(MessageFormat.format("Error while setting serial port parameters: {0} {1} {2} {3}.",
						irate, iparity, idatabits, istopbits));
			}
			port.addEventListener(this);
		} catch (SerialPortException e) {
			if (e.getPortName().startsWith("/dev")
					&& SerialPortException.TYPE_PERMISSION_DENIED.equals(e.getExceptionType())) {
				throw new MySerialException(
						MessageFormat.format("Permission denied opening serial port ''{0}''.", iname));
			}
			throw new MySerialException(MessageFormat.format("Error opening serial port ''{0}''.", iname), e);
		}

		if (port == null) {
			throw new MySerialException(MessageFormat.format("Serial port ''{0}'' not found.", iname));
		}
	}

	public void setup() {
		// parent.registerCall(this, DISPOSE);
	}

	public void dispose() throws IOException {
		if (port != null) {
			try {
				if (port.isOpened()) {
					port.closePort(); // close the port
				}
			} catch (SerialPortException e) {
				throw new IOException(e);
			} finally {
				port = null;
			}
		}
	}

	@Override
	public synchronized void serialEvent(SerialPortEvent serialEvent) {
		if (serialEvent.isRXCHAR()) {
			try {
				byte[] buf = port.readBytes(serialEvent.getEventValue());
				int next = 0;
				while (next < buf.length) {
					while (next < buf.length && outToMessage.hasRemaining()) {
						int spaceInIn = inFromSerial.remaining();
						int copyNow = buf.length - next < spaceInIn ? buf.length - next : spaceInIn;
						inFromSerial.put(buf, next, copyNow);
						next += copyNow;
						inFromSerial.flip();
						bytesToStrings.decode(inFromSerial, outToMessage, false);
						inFromSerial.compact();
					}
					outToMessage.flip();
					if (outToMessage.hasRemaining()) {
						char[] chars = new char[outToMessage.remaining()];
						outToMessage.get(chars);
						message(chars, chars.length);
					}
					outToMessage.clear();
				}
			} catch (SerialPortException e) {
				errorMessage("serialEvent", e);
			}
		}
	}

	/**
	 * This will handle both ints, bytes and chars transparently.
	 */
	public void write(int what) { // will also cover char
		try {
			port.writeInt(what & 0xff);
		} catch (SerialPortException e) {
			errorMessage("write", e);
		}
	}

	public void write(byte bytes[]) {
		try {
			port.writeBytes(bytes);
		} catch (SerialPortException e) {
			errorMessage("write", e);
		}
	}

	/**
	 * Write a String to the output. Note that this doesn't account for Unicode (two
	 * bytes per char), nor will it send UTF8 characters.. It assumes that you mean
	 * to send a byte buffer (most often the case for networking and serial i/o) and
	 * will only use the bottom 8 bits of each char in the string. (Meaning that
	 * internally it uses String.getBytes)
	 * <p>
	 * If you want to move Unicode data, you can first convert the String to a byte
	 * stream in the representation of your choice (i.e. UTF8 or two-byte Unicode
	 * data), and send it as a byte array.
	 */
	public void write(String what) {
		write(what.getBytes());
	}

	public void setDTR(boolean state) {
		try {
			port.setDTR(state);
		} catch (SerialPortException e) {
			errorMessage("setDTR", e);
		}
	}

	public void setRTS(boolean state) {
		try {
			port.setRTS(state);
		} catch (SerialPortException e) {
			errorMessage("setRTS", e);
		}
	}

	/**
	 * Reset the encoding used to convert the bytes coming in before they are handed
	 * as Strings to {@Link #message(char[], int)}.
	 */
	public synchronized void resetDecoding(Charset charset) {
		bytesToStrings = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE).replaceWith("\u2e2e");
	}

	/**
	 * General error reporting, all corraled here just in case I think of something
	 * slightly more intelligent to do.
	 */
	private static void errorMessage(String where, Throwable e) {
		System.err.println(MessageFormat.format("Error inside Serial.{0}().", where));
		e.printStackTrace();
	}
}
