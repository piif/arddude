package pif.arduino.tools;

import pif.arduino.Console;

/**
 * have to extend Serial class to implement message() method
 */
public class MySerial extends processing.app.Serial {
	Console console;
	public MySerial(Console console) throws processing.app.SerialException {
		super();
		this.console = console;
	}

	@Override
	protected void message(char[] chars, int length) {
		byte[] data = new byte[length];
		for(int i = 0; i < length; i++) {
			data[i] = (byte)chars[i];
		}
		console.onIncomingData(data);
	}
}
