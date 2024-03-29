package pif.arduino.tests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pif.arduino.Console;

public class FakePeer implements Console.ConsolePeer {
	Logger logger = LogManager.getLogger();

	Console console;

	public FakePeer() {
		console = new Console(this);
		console.start();
		for (;;) {
			// send fake data randomly
			try {
				Thread.sleep((long) (Math.random()*2000));
				console.onIncomingData("bla bla bla".getBytes());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void onOutgoingData(byte data[]) {
		logger.info("data to send '" + new String(data) + "'");
		// echo everything
		if (data != null && data.length != 0) {
			console.onIncomingData(data);
		}
	}
	public boolean onCommand(String command) {
		if (command.equals("test")) {
			logger.info("received command '" + command + "'");
			return true;
		}
		return false;
	}

	public void onExit(int status) {
		System.exit(status);
	}

	static public void main(String args[]) {
		new FakePeer();
	}
}
