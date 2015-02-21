package pif.arduino.tests;

import java.util.Arrays;

import pif.arduino.Console;
import pif.arduino.tools.hexTools;

public class ReadHex {
	static Console console = new Console(null);

	static void test(String input, byte[] expected) {
		byte[] result = console.readHexData(input);
		if (!Arrays.equals(result, expected)) {
			System.out.println("Error :");
			System.out.println("  input  : " + input);
			System.out.print("  expected : " + hexTools.toHexDump(expected));
			System.out.print("  result   : " + hexTools.toHexDump(result));
		} else {
			System.out.println("OK for   : " + input);
		}
	}

	static public void main(String args[]) {
		test("12 34", new byte[] {
				0x12, 0x34
		});
		test("12 34 0 'abc' cafe", new byte[] {
				0x12, 0x34, 0, 0x61, 0x62, 0x63, (byte) 0xCA, (byte) 0xFE
		});
		test("123 0 f", new byte[] {
				0x12, 0x03, 0, 0x0f
		});
		test("'123'", new byte[] {
				0x31, 0x32, 0x33
		});
		test("'123", new byte[] {
				0x31, 0x32, 0x33
		});
		test("12 z 34", null);
	}
}
