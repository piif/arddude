package pif.arduino.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Take a stream as input, and write it to output, with each line prefixed/suffixed.
 * 
 * User must call forward() method until and of input stream, and terminate by flush() method to send last bytes.
 * 
 * @author pif
 */
public class OutputRenderer {
	InputStream input;
	OutputStream output;
	byte[] prefix = null;
	byte[] suffix = null;

	protected byte[] buffer = new byte[1000];
	int offset = 0;
	int skip = 0;

	/**
	 * Initialize renderer
	 * @param input InputStream to read bytes from
	 * @param output OutputStream to send bytes to
	 * @param prefix byte[] to put before each line
	 * @param suffix byte[] to put after each line
	 */
	public OutputRenderer(
			InputStream input, OutputStream output,
			byte[] prefix, byte[] suffix) {
		this.input = input;
		this.output = output;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	/**
	 * Shortcut to pass prefix and suffix as String.
	 * String content must match stream charset 
	 * @param input InputStream to read bytes from
	 * @param output OutputStream to send bytes to
	 * @param prefix String to put before each line
	 * @param suffix String to put after each line
	 */
	public OutputRenderer(
			InputStream input, OutputStream output,
			String prefix, String suffix) {
		this(input, output, prefix.getBytes(), suffix.getBytes());
	}

	public void forward() throws IOException {
		int nb = input.read(buffer, offset, buffer.length - offset);
		if (nb <= 0) {
			return;
		}
		//System.err.println("forward from " + offset + " to " + nb);
		nb += offset;
		while(offset < nb) {
			if(buffer[offset] == '\r' || buffer[offset] == '\n') {
				// found end of line => must flush it
				nb = flush(nb);
				// skip eol chars
				if(buffer[0] == '\r' && buffer[1] == '\n') {
					skip = 2;
					offset = 2;
				} else {
					skip = 1;
					offset = 1;
				}
			} else {
				offset++;
			}
		}
		if (offset >= buffer.length) {
			// flush before buffer overflow
			flush(-1);
		}
	}
	
	/**
	 * Flush buffer from "skip" up to "offset" (excluded)
	 * then, if end >= offset, move bytes from "offset" to "end" onto beginning of buffer
	 * then reset offset to 0
	 * @return int new end position, or -1 if end was < offset
	 * @throws IOException 
	 */
	protected int flush(int end) throws IOException {
		//System.err.print("flush " + end);
		if (offset > skip) {
			output.write(prefix);
			output.write(buffer, skip, offset-skip);
			output.write(suffix);
		}
		if (end >= offset) {
			System.arraycopy(buffer, offset, buffer, 0, end-offset-1);
			end -= offset;
		} else {
			end = -1;
		}
		offset = 0;
		//System.err.print("=> end = " + end);
		return end;
	}
	
	/**
	 * flush buffer
	 * @throws IOException
	 */
	public void flush() throws IOException {
		flush(-1);
	}

}
