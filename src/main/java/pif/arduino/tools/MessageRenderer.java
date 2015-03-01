package pif.arduino.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

@SuppressWarnings("serial")
public class MessageRenderer extends ArrayList<String> {
	protected PrintStream output;
	protected String prefix;

	public MessageRenderer(PrintStream output, String prefix) {
		this.output = output;
		this.prefix = prefix + " ";
	}

	@Override
	public boolean add(String s) {
		output.println(s);
		return super.add(prefix + s);
	}

	@Override
	public boolean addAll(Collection<? extends String> c) {
		for (String s: c) {
			output.println(prefix + s);
		}
		return super.addAll(c);
	}
}
