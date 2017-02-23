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
import java.util.ArrayList;
import java.util.HashMap;

class GpsRealTimeInputWatcher {
	// These strings are likely to change
	private static final String INCOMMING_JSON_FOLDER_PATH = "/media/tfc/ml693/data_monitor/";
	private static final String LOCK_FILE_PATH = "/media/tfc/ml693/data_monitor_lock_file";

	private final String tripsFolderPath;
	private final ArrayList<Trip> paths;
	private final String routesFolderPath;
	private final HashMap<String, Route> tripFollowsRoute = new HashMap<String, Route>();

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
		Utils.checkCommandLineArguments(args, "folder", "folder");

		GpsRealTimeInputWatcher watcher = new GpsRealTimeInputWatcher(args[0],
				args[1], new File(args[2]));
		watcher.waitForNewJsonInput();
	}

	GpsRealTimeInputWatcher(String tripsFolderPath, String routesFolderPath,
			File pathsFolder) {
		this.tripsFolderPath = tripsFolderPath;
		this.routesFolderPath = routesFolderPath;
		paths = Trip.extractTripsFromFolder(pathsFolder);
	}

	private void waitForNewJsonInput() {
		WatchService watchService = null;
		try {
			// Preparing to listen for new file
			FileSystem fileSystem = FileSystems.getDefault();
			Path directory = Paths.get(INCOMMING_JSON_FOLDER_PATH);
			watchService = fileSystem.newWatchService();
			WatchEvent.Kind<?>[] events = {
					StandardWatchEventKinds.ENTRY_CREATE };
			directory.register(watchService, events);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}

		while (true) {
			try {
				// When new file arrives
				WatchKey watchKey = watchService.take();
				watchKey.pollEvents();
				File lockFile = new File(LOCK_FILE_PATH);
				FileChannel channel = new RandomAccessFile(lockFile, "rw")
						.getChannel();
				FileLock lock = channel.lock();

				if (watchKey.isValid()) {
					// We process it
					processNewGpsInput(new File(INCOMMING_JSON_FOLDER_PATH)
							.listFiles()[0]);
				}

				if (lock != null) {
					lock.release();
				}
				channel.close();
				if (!watchKey.reset()) {
					throw new ProjectSpecificException(
							"Something happened with "
									+ INCOMMING_JSON_FOLDER_PATH
									+ " being watched");
				}

			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		}
	}

	BusStop getNextStop(Trip recentTrip, Route route) {
		int p = 0;
		for (BusStop busStop : route.busStops) {
			while (p < recentTrip.gpsPoints.size()
					&& !busStop.atStop(recentTrip.gpsPoints.get(p))) {
				p++;
			}
			if (p == recentTrip.gpsPoints.size()) {
				return busStop;
			}
		}
		return null;
	}

	private void processNewGpsInput(File jsonFile)
			throws ProjectSpecificException {
		System.out.println("Dealing with file " + jsonFile.getName());
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);

		for (String key : BusTravelHistoryExtractor.allHistories.keySet()) {
			ArrayList<GpsPoint> points = BusTravelHistoryExtractor.allHistories
					.get(key);
			// TODO(ml693): figure out best amount of points;
			if (points.size() > 20) {
				ArrayList<GpsPoint> latest20Points = new ArrayList<GpsPoint>(
						points.subList(points.size() - 20, points.size()));

				Trip recentTrip = new Trip(key, latest20Points);
				if (!tripFollowsRoute.containsKey(key)) {
					for (Trip path : paths) {
						if (PathDetector.tripFollowsPath(recentTrip, path)) {
							System.out.println(key + " follows " + path.name);
							tripFollowsRoute.put(key, new Route(new File(
									routesFolderPath + "/" + path.name)));
							break;
						}
					}
				}

				BusStop nextStop = getNextStop(recentTrip,
						tripFollowsRoute.get(key));
				if (nextStop == null) {
					System.out.println("No next stop for " + key);
				} else {
					ArrayList<Trip> historicalTrips = Trip
							.extractTripsFromFolder(
									new File(tripsFolderPath + "/" + key));

					Long prediction = ArrivalTimePredictor
							.calculatePredictionToBusStop(
									p -> nextStop.atStop(p), recentTrip,
									historicalTrips);

					System.out.println("We predict that " + recentTrip
							+ " will arrive at " + nextStop.name + " at "
							+ Utils.convertTimestampToDate(prediction));
				}
			}

		}
	}

}