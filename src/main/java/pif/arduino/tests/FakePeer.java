package pif.arduino.tests;

import org.apache.log4j.Logger;

import pif.arduino.Console;

public class FakePeer implements Console.ConsolePeer {
	Logger logger = Logger.getLogger(FakePeer.class);

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
				// TODO Auto-generated catch block
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
	public void onCommand(String command) {
		// TODO : cr lf crlf none
		logger.info("received command '" + command + "'");
	}

	public void onDisconnect(int status) {
		System.exit(status);
	}

	static public void main(String args[]) {
		new FakePeer();
	}
}
