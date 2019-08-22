package pif.arduino.tests;

import java.io.IOException;
import java.io.InputStream;

public class LaunchCommand {

	// TODO ProcessBuilder tests
	// TODO test with mixed arguments "mycommand", "several arguments in one string" 
	// TODO stdout and stderr prefixed by [%s INFO] [%s  ERR]
	// TODO use colors ?
	public static void main(String[] args) throws IOException {

		String[] cmdLine = {
			"/bin/ls",
			"-l",
			"-a /"
		};

		ProcessBuilder pb = new ProcessBuilder(cmdLine);
		Process p = pb.start();
		InputStream out = p.getInputStream();
		InputStream err = p.getErrorStream();
		// TODO : adapt MessageRenderer to append chars and split at \n
		while(true) {
			read out => append
					if \n, flush prefixed
			read err => append
					if \n, flush prefixed
			if out and err EOF? flush and break
			if status not exception, flush and break
		}
	}

}
