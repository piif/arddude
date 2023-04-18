package pif.arduino.tools;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jline.console.ConsoleReader;

/**
 * override default ConsoleReader class to add a method which inserts data
 * without breaking current command line
 * @author pif
 */
public class JlineConsole extends ConsoleReader {
	Logger logger = LogManager.getLogger();
	
	static {
		if (System.getenv("TERM") != null && System.getenv("APPDATA") != null) {
			// if cygwin, must force terminal mode
			System.setProperty("jline.terminal", "jline.UnixTerminal");
		}
	}

	public JlineConsole() throws IOException {
		super();
	}

	public void insertString(String str) throws IOException {
		int savedPos = getCursorBuffer().cursor;
		boolean hasToRestore = (getCursorBuffer().buffer.length() != 0);
		if (hasToRestore) {
			setCursorPosition(0);
			killLine();
		}
        print("" + RESET_LINE);
		println(str);

		if (hasToRestore) {
			yank();
		}
		restoreLine(getPrompt(), savedPos);
	}
}
