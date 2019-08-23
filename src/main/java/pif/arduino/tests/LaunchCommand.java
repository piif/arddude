package pif.arduino.tests;

import java.io.IOException;

import pif.arduino.tools.OutputRenderer;

public class LaunchCommand {

	// TODO handle pwd
	public static void main(String[] args) throws IOException {

		String[] cmdLine = {
			"/bin/ls",
			"-l",
			"-a", "/DATA", "/root"
		};

		ProcessBuilder pb = new ProcessBuilder(cmdLine);
		Process p = pb.start();
		OutputRenderer out = new OutputRenderer(p.getInputStream(), System.out, "\033[32m", "\033[0m\n");
		OutputRenderer err = new OutputRenderer(p.getErrorStream(), System.out, "\033[91m", "\033[0m\n");

		while(p.isAlive() /*|| !outClosed || !errClosed*/) {
			out.forward();
			err.forward();
		}
		out.forward();
		out.flush();
		err.forward();
		err.flush();
		System.out.println("Exit status : " + p.exitValue());
	}

}
