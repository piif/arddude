package pif.arduino.tools;

public class hexTools {
	static public char hex(byte b) {
		if (b <= 9) {
			return (char)(b + '0');
		} else {
			return (char)(b - 10 + 'a');
		}
	}

	static public String toHex(byte b) {
		return "" + hex((byte)((b >> 4) & 0x0f)) + hex((byte)(b & 0x0f));
	}

	static protected String hexDumpTemplate =
			".. .. .. .. .. .. .. ..  .. .. .. .. .. .. .. .. :                 ";

	static public String toHexDump(byte data[]) {
		return toHexDump(data, data.length);
	}
	static public String toHexDump(byte data[], int length) {
		StringBuffer result = new StringBuffer(length * 4);
		StringBuffer line = null;
		// position into template for hex part / ascii part
		int i, h = 0, a = 0;
		for (i = 0; i < length; i++) {
			if (i % 16 == 0) {
				// ended a line -> append it to result, excepted at start of loop
				if (i != 0) {
					if (i > 16) {
						result.append('\n');
					}
					result.append(line);
				}
				line = new StringBuffer(hexDumpTemplate);
				h = 0;
				a = 51;
			} else if (i % 8 == 0) {
				// skip a space at line middle
				h++;
			}
			line.setCharAt(h, hex((byte)((data[i] >> 4) & 0x0f)));
			line.setCharAt(h + 1, hex((byte)(data[i] & 0x0f)));
			line.setCharAt(a, (char)((data[i] > 32 && data[i] < 128) ? data[i] : '.'));
			h+=3;
			a++;
		}
		// append remaining line if it's not an empty one
//		if (i % 16 != 0) {
			if (i > 16) {
				result.append('\n');
			}
			result.append(line);
//		}
		return result.toString();
	}
}
