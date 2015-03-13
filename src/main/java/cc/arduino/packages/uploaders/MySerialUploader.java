package cc.arduino.packages.uploaders;

import static processing.app.I18n._;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import pif.arduino.ArdConsole;
import processing.app.BaseNoGui;
import processing.app.I18n;
import processing.app.PreferencesData;
import processing.app.Serial;
import processing.app.SerialException;
import processing.app.debug.RunnerException;
import processing.app.debug.TargetPlatform;
import processing.app.helpers.OSUtils;
import processing.app.helpers.PreferencesMap;
import processing.app.helpers.StringReplacer;

public class MySerialUploader extends SerialUploader {
	private static Logger logger = Logger.getLogger(ArdConsole.class);

	public boolean uploadUsingPreferences(File sourcePath, String buildPath,
			String className, boolean usingProgrammer,
			List<String> warningsAccumulator) throws Exception {
		logger.debug("uploadUsingProgrammer " + verbose);
		TargetPlatform targetPlatform = BaseNoGui.getTargetPlatform();
		PreferencesMap prefs = PreferencesData.getMap();
		prefs.putAll(BaseNoGui.getBoardPreferences());
		String tool = prefs.getOrExcept("upload.tool");
		if (tool.contains(":")) {
			String[] split = tool.split(":", 2);
			targetPlatform = BaseNoGui.getCurrentTargetPlatformFromPackage(split[0]);
			tool = split[1];
		}
		prefs.putAll(targetPlatform.getTool(tool));

		// if no protocol is specified for this board, assume it lacks a
		// bootloader and upload using the selected programmer.
		if (usingProgrammer || prefs.get("upload.protocol") == null) {
			logger.debug("uploadUsingProgrammer");
			return uploadUsingProgrammer(buildPath, className);
		}

		if (noUploadPort) {
			logger.debug("noUploadPort");
			prefs.put("build.path", buildPath);
			prefs.put("build.project_name", className);
			if (verbose)
				prefs.put("upload.verbose", prefs.getOrExcept("upload.params.verbose"));
			else
				prefs.put("upload.verbose", prefs.getOrExcept("upload.params.quiet"));

			boolean uploadResult;
			try {
				String pattern = prefs.getOrExcept("upload.pattern");
				String[] cmd = StringReplacer.formatAndSplit(pattern, prefs, true);
				logger.debug("executeUploadCommand " + cmd);
				uploadResult = executeUploadCommand(cmd);
			} catch (Exception e) {
				throw new RunnerException(e);
			}
			return uploadResult;
		}

		// need to do a little dance for Leonardo and derivatives:
		// open then close the port at the magic baudrate (usually 1200 bps)
		// first
		// to signal to the sketch that it should reset into bootloader. after
		// doing
		// this wait a moment for the bootloader to enumerate. On Windows, also
		// must
		// deal with the fact that the COM port number changes from bootloader
		// to
		// sketch.
		String t = prefs.get("upload.use_1200bps_touch");
		boolean doTouch = t != null && t.equals("true");

		t = prefs.get("upload.wait_for_upload_port");
		boolean waitForUploadPort = (t != null) && t.equals("true");

		if (doTouch) {
			String uploadPort = prefs.getOrExcept("serial.port");
			try {
				// Toggle 1200 bps on selected serial port to force board reset.
				List<String> before = Serial.list();
				if (before.contains(uploadPort)) {
					if (verbose)
						System.out
								.println(I18n
										.format(_("Forcing reset using 1200bps open/close on port {0}"),
												uploadPort));
					Serial.touchPort(uploadPort, 1200);
				}
				Thread.sleep(400);
				if (waitForUploadPort) {
					// Scanning for available ports seems to open the port or
					// otherwise assert DTR, which would cancel the WDT reset if
					// it happened within 250 ms. So we wait until the reset
					// should
					// have already occured before we start scanning.
					uploadPort = waitForUploadPort(uploadPort, before);
				}
			} catch (SerialException e) {
				throw new RunnerException(e);
			} catch (InterruptedException e) {
				throw new RunnerException(e.getMessage());
			}
			prefs.put("serial.port", uploadPort);
			if (uploadPort.startsWith("/dev/"))
				prefs.put("serial.port.file", uploadPort.substring(5));
			else
				prefs.put("serial.port.file", uploadPort);
		}

		prefs.put("build.path", buildPath);
		prefs.put("build.project_name", className);
		if (verbose)
			prefs.put("upload.verbose", prefs.getOrExcept("upload.params.verbose"));
		else
			prefs.put("upload.verbose", prefs.getOrExcept("upload.params.quiet"));

		boolean uploadResult;
		try {
			// if (prefs.get("upload.disable_flushing") == null
			// ||
			// prefs.get("upload.disable_flushing").toLowerCase().equals("false"))
			// {
			// flushSerialBuffer();
			// }

			String pattern = prefs.getOrExcept("upload.pattern");
			String[] cmd = StringReplacer.formatAndSplit(pattern, prefs, true);
			logger.debug("executeUploadCommand " + cmd);
			uploadResult = executeUploadCommand(cmd);
		} catch (RunnerException e) {
			throw e;
		} catch (Exception e) {
			throw new RunnerException(e);
		}

		try {
			if (uploadResult && doTouch) {
				String uploadPort = PreferencesData.get("serial.port");
				if (waitForUploadPort) {
					// For Due/Leonardo wait until the bootloader serial port
					// disconnects and the
					// sketch serial port reconnects (or timeout after a few
					// seconds if the
					// sketch port never comes back). Doing this saves users
					// from accidentally
					// opening Serial Monitor on the soon-to-be-orphaned
					// bootloader port.
					Thread.sleep(1000);
					long started = System.currentTimeMillis();
					while (System.currentTimeMillis() - started < 2000) {
						List<String> portList = Serial.list();
						if (portList.contains(uploadPort))
							break;
						Thread.sleep(250);
					}
				}
			}
		} catch (InterruptedException ex) {
			// noop
		}
		return uploadResult;
	}

	private String waitForUploadPort(String uploadPort, List<String> before)
			throws InterruptedException, RunnerException {
		// Wait for a port to appear on the list
		int elapsed = 0;
		while (elapsed < 10000) {
			List<String> now = Serial.list();
			List<String> diff = new ArrayList<String>(now);
			diff.removeAll(before);
			if (verbose) {
				System.out.print("PORTS {");
				for (String p : before) System.out.print(p + ", ");
				System.out.print("} / {");
				for (String p : now) System.out.print(p + ", ");
				System.out.print("} => {");
				for (String p : diff) System.out.print(p + ", ");
				System.out.println("}");
			}
			if (diff.size() > 0) {
				String newPort = diff.get(0);
				if (verbose) System.out.println("Found upload port: " + newPort);
				return newPort;
			}

			// Keep track of port that disappears
			before = now;
			Thread.sleep(250);
			elapsed += 250;

			// On Windows, it can take a long time for the port to disappear and
			// come back, so use a longer time out before assuming that the
			// selected
			// port is the bootloader (not the sketch).
			if (((!OSUtils.isWindows() && elapsed >= 500) || elapsed >= 5000)
					&& now.contains(uploadPort)) {
				if (verbose) System.out.println("Uploading using selected port: " + uploadPort);
				return uploadPort;
			}
		}

		// Something happened while detecting port
		throw new RunnerException(
				_("Couldn't find a Board on the selected port. Check that you have the correct port selected.  If it is correct, try pressing the board's reset button after initiating the upload."));
	}
}
