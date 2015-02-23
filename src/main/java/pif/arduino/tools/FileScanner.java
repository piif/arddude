package pif.arduino.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

/**
 * given a file name, calls onFileChange() method every time this file lastModified time changes
 */
public class FileScanner {
	private static Logger logger = Logger.getLogger(FileScanner.class);
	protected File source;
	protected FileScanHandler handler;

	protected long lastMod;

	public interface FileScanHandler {
		void onFileChange();
	}
	public FileScanner(String fileName, FileScanHandler handler) throws FileNotFoundException {
		source = new File(fileName);
		if (source.exists()) {
			logger.debug("Scanning file " + source);
		} else {
			logger.error("Can't find file " + source);
			throw new FileNotFoundException();
		}
		lastMod = source.lastModified();
		this.handler = handler;

		// scan file every 2 seconds
		Timer t = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				scanFile();
			}
		};
		t.schedule(task, 0, 2000);
	}

	protected void scanFile() {
		long t = source.lastModified();
		if (lastMod < t) {
			lastMod = t;
			handler.onFileChange();
		}
	}
}
