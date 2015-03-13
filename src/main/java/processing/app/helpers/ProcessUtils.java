package processing.app.helpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

// copy of Arduino IDE class, just to set PWD in ProcessBuilder

public class ProcessUtils {
	public static File pwd = null;

	public static Process exec(String[] command) throws IOException {
		// No problems on linux and mac
		if (!OSUtils.isWindows()) {
			return Runtime.getRuntime().exec(command);
		}

		String[] cmdLine = new String[command.length];
		for (int i = 0; i < command.length; i++)
			cmdLine[i] = command[i].replace("\"", "\\\"");

		ProcessBuilder pb = new ProcessBuilder(cmdLine);
		if (pwd != null) {
			pb.directory(pwd);
		}
		Map<String, String> env = pb.environment();
		env.put("CYGWIN", "nodosfilewarning");
		return pb.start();
	}
}
