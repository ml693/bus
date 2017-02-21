package bus;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

class GpsRealTimeInputWatcher {
	// This is likely to change
	private static final String INCOMMING_JSON_FOLDER_PATH = "/media/tfc/ml693/data_monitor/";

	/*
	 * Real time GPS data is transmitted every 30s. This program sleeps, wakes
	 * up on every new incomming file, processes it, then goes back to sleep.
	 * The incomming file is JSON, and has the format specified at
	 * BusTravelHistoryExtractor class.
	 * 
	 * Usage:
	 * java GpsRealTimeInputWatcher folder_where_gps_file_arrives
	 */
	public static void main(String[] args) throws ProjectSpecificException {
		waitForNewJsonInput();
	}

	public static void waitForNewJsonInput() throws ProjectSpecificException {
		int loopCount = 0;
		while (true) {
			loopCount++;
			try {
				// Preparing to listen for new file
				FileSystem fileSystem = FileSystems.getDefault();
				Path directory = Paths.get(INCOMMING_JSON_FOLDER_PATH);
				WatchService watchService = fileSystem.newWatchService();
				WatchEvent.Kind<?>[] events = {
						StandardWatchEventKinds.ENTRY_CREATE };
				directory.register(watchService, events);

				// When new file arrives
				WatchKey watchKey = watchService.take();

				File lockFile = new File(
						INCOMMING_JSON_FOLDER_PATH + "/lock_file");
				FileChannel channel = new RandomAccessFile(lockFile, "rw")
						.getChannel();
				FileLock lock = channel.lock();

				if (watchKey.isValid()) {
					File newGpsInputFile = new File(INCOMMING_JSON_FOLDER_PATH)
							.listFiles()[0];

					// We process it
					processNewGpsInput(newGpsInputFile, loopCount);
				}

				if (lock != null) {
					lock.release();
				}
				channel.close();

			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		}

	}

	public static void processNewGpsInput(File jsonFile, int loopCount)
			throws ProjectSpecificException {
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);

		int max = 0;
		/*
		for (String key : BusTravelHistoryExtractor.allHistories.keySet()) {
			ArrayList<GpsPoint> points = BusTravelHistoryExtractor.allHistories
					.get(key);
			max = Math.max(max, points.size());
		}
		*/
		System.out.println("max = " + max + ", loopCount = " + loopCount + ", jsonFile = " + jsonFile.getName());
	}
}
