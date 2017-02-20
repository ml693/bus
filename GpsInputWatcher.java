package bus;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

class GpsRealTimeInputWatcher {
	/*
	 * Real time GPS data is transmitted every 30s. This program sleeps, wakes
	 * up on every new incomming file, processes it, then goes back to sleep.
	 * 
	 * Usage:
	 * java GpsRealTimeInputWatcher folder_where_gps_file_arrives
	 */
	public static void main(String[] args) throws ProjectSpecificException {
		Utils.checkCommandLineArguments(args, "folder");

		while (true) {
			try {
				FileSystem fileSystem = FileSystems.getDefault();
				Path directory = Paths.get(args[0]);
				WatchService watchService = fileSystem.newWatchService();
				WatchEvent.Kind<?>[] events = {
						StandardWatchEventKinds.ENTRY_CREATE };
				directory.register(watchService, events);

				WatchKey watchKey = watchService.take();
				if (watchKey.isValid()) {
					File[] files = new File("/media/tfc/ml693/data_monitor")
							.listFiles();

					/* TODO(ml693): replace println with sensible code */
					for (File file : files) {
						System.out.println(file.getName());
					}
				}
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		}
	}
}
