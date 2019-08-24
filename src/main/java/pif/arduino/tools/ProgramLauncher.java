package pif.arduino.tools;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Simple class to execute an external command and output its stdout/stderr in formatted way
 * @author pif
 */
public class ProgramLauncher {
	protected String command;
	protected String outPrefix = "OUT ", outSuffix = "\n";
	protected String errPrefix = "OUT ", errSuffix = "\n";

	protected Process process = null;

	/**
	 * prepare command to launch
	 * @param command
	 */
	public ProgramLauncher(String command) {
		this.command = command;
	}

	/**
	 * set prefix/suffix for stdout lines
	 * @param prefix
	 * @param suffix
	 */
	public void setOutMask(String prefix, String suffix) {
		outPrefix = prefix;
		outSuffix = suffix;
	}
	/**
	 * set prefix/suffix for stderr lines
	 * @param prefix
	 * @param suffix
	 */
	public void setErrMask(String prefix, String suffix) {
		errPrefix = prefix;
		errSuffix = suffix;
	}

	/**
	 * launch command and wait its end
	 * @param out stream to send stdout and stderr to
	 * @return exit status
	 * @throws IOException
	 */
	public int run(OutputStream out) throws IOException {
		Process process = Runtime.getRuntime().exec(command);
		OutputRenderer pout = new OutputRenderer(process.getInputStream(), out, "\033[32m", "\033[0m\n");
		OutputRenderer perr = new OutputRenderer(process.getErrorStream(), out, "\033[91m", "\033[0m\n");

		while(process.isAlive()) {
			pout.forward();
			perr.forward();
		}
		pout.forward();
		pout.flush();
		perr.forward();
		perr.flush();

		int result =  process.exitValue();
		process = null;
		return result;
	}

	/**
	 * Wait for the process end
	 */
	public void waitFor() {
		if (process != null) {
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				// ignored
			}
		}
	}
}
