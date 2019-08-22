/* copy/paste from https://github.com/arduino/Arduino/blob/master/arduino-core/src/processing/app/SerialException.java */

package pif.arduino.tools;

import java.io.IOException;

@SuppressWarnings("serial")
public class MySerialException extends IOException {
	public MySerialException() {
		super();
	}

	public MySerialException(String message) {
		super(message);
	}

	public MySerialException(String message, Throwable cause) {
		super(message, cause);
	}

	public MySerialException(Throwable cause) {
		super(cause);
	}
}