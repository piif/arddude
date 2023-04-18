package pif.arduino.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * given a file name, calls onFileChange() method every time this file lastModified time changes
 */
public class FileScanner {
	private static Logger logger = LogManager.getLogger();
	protected File source;
	protected FileScanHandler handler;

	protected Timer timer;
	protected long lastMod;

	public interface FileScanHandler {
		void onFileChange();
	}
	public FileScanner(File file, FileScanHandler handler) throws FileNotFoundException {
		source = file;
		if (source.exists()) {
			logger.debug("Scanning file " + source);
		} else {
			logger.error("Can't find file " + source);
			throw new FileNotFoundException();
		}
		lastMod = source.lastModified();
		this.handler = handler;

		// scan file every 2 seconds
		timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				scanFile();
			}
		};
		timer.schedule(task, 0, 2000);
	}

	public void stop() {
		timer.cancel();
	}

	protected void scanFile() {
		long t = source.lastModified();
		if (lastMod < t) {
			lastMod = t;
			handler.onFileChange();
		}
	}
}
