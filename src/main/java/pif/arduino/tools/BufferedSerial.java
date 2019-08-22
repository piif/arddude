package pif.arduino.tools;

import pif.arduino.Console;
import java.util.Timer;
import java.util.TimerTask;

/**
 * have to extend Serial class to implement message() method
 */
public class BufferedSerial extends MySerial {
	Console console;

	final static int MAX_BUFFER = 1024;

	// bufferize incoming data
	byte[] buffer;
	int bufIndex;

	// and send them to console after a short delay
	// to avoid to split packets
	final static int FLUSH_DELAY = 100; // in milliseconds 
	Timer flushTimer;

	class FlushTask extends TimerTask {
		public void run() {
			flush();
			flushTimer.purge();
		}
	}
	FlushTask pendingFlush;

	protected void prepareFlush() {
		pendingFlush = new FlushTask();
		flushTimer.schedule(pendingFlush, FLUSH_DELAY);
	}

	protected void flush() {
		if (bufIndex != 0) {
			console.onIncomingData(buffer, bufIndex);
			bufIndex = 0;
		}
	}

	public BufferedSerial(Console console, String portName, int baudrate) throws MySerialException {
		super(portName, baudrate);
		this.console = console;
		buffer = new byte[MAX_BUFFER];
		bufIndex = 0;

		flushTimer = new Timer("flush timer", true);
		pendingFlush = null;
	}

	@Override
	protected void message(char[] chars, int length) {
		// if flush is pending, cancel it
		if (pendingFlush != null) {
			pendingFlush.cancel();
			pendingFlush = null;
		}
		if (bufIndex + length >= MAX_BUFFER) {
			// will overflow => send buffered data before
			flush();
		}
		for(int i = 0; i < length; i++) {
			buffer[bufIndex++] = (byte)chars[i];
			if (bufIndex >= MAX_BUFFER) {
				flush();
			}
		}
		prepareFlush();
	}
}
