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

class GpsInputWatcher {

	public static void main(String[] args) throws Exception {

		while (true) {
			FileSystem fileSystem = FileSystems.getDefault();
			Path directory = Paths.get("/media/tfc/ml693/data_monitor");
			WatchService watchService = fileSystem.newWatchService();
			WatchEvent.Kind<?>[] events = {
					StandardWatchEventKinds.ENTRY_CREATE };
			directory.register(watchService, events);

			WatchKey watchKey = watchService.take();
			if (watchKey.isValid()) {
				File[] files = new File("/media/tfc/ml693/data_monitor")
						.listFiles();
				for (File file : files) {
					System.out.println(file.getName());
				}
			} else {
				throw new ProjectSpecificException(
						"Problem monitoring the directory");
			}
		}
	}

}
