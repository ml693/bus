package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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

	private final File tripsFolder;
	private final File routesFolder;
	/* Path is a trip following a route */
	private final File pathsFolder;
	private final HashMap<String, Route> tripFollowsRoute = new HashMap<String, Route>();
	private final HashMap<String, Trip> tripFollowsPath = new HashMap<String, Trip>();

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
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");

		GpsRealTimeInputWatcher watcher = new GpsRealTimeInputWatcher(
				new File(args[0]), new File(args[1]), new File(args[2]));
		watcher.waitForNewJsonInput();
	}

	GpsRealTimeInputWatcher(File tripsFolder, File routesFolder,
			File pathsFolder) {
		this.tripsFolder = tripsFolder;
		this.routesFolder = routesFolder;
		this.pathsFolder = pathsFolder;
	}

	WatchService realTimeJsonFolderWatcher() {
		try {
			FileSystem fileSystem = FileSystems.getDefault();
			Path directory = Paths.get(INCOMMING_JSON_FOLDER_PATH);
			WatchService watchService = fileSystem.newWatchService();
			WatchEvent.Kind<?>[] events = {
					StandardWatchEventKinds.ENTRY_CREATE };
			directory.register(watchService, events);
			return watchService;
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private void waitForNewJsonInput() {
		WatchService watchService = realTimeJsonFolderWatcher();

		while (true) {
			try {
				// When new file arrives
				WatchKey watchKey = watchService.take();
				watchKey.pollEvents();
				Thread.sleep(100);

				// First look real time input directory
				File lockFile = new File(LOCK_FILE_PATH);
				FileChannel channel = new RandomAccessFile(lockFile, "rw")
						.getChannel();
				FileLock lock = channel.lock();

				// Then process new file
				if (watchKey.isValid()) {
					processNewGpsInput(new File(INCOMMING_JSON_FOLDER_PATH)
							.listFiles()[0]);
				}

				// Finally unlock real time input directory
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

	Trip getTrip(String vehicleId) throws ProjectSpecificException {
		ArrayList<GpsPoint> points = BusTravelHistoryExtractor.allHistories
				.get(vehicleId);
		if (points.size() < Trip.MINIMUM_NUMBER_OF_GPS_POINTS * 2) {
			return null;
		}
		new Trip(vehicleId, points).writeToFolder(new File("logging"));

		return new Trip(vehicleId,
				new ArrayList<GpsPoint>(points.subList(
						points.size() - Trip.MINIMUM_NUMBER_OF_GPS_POINTS * 2,
						points.size())));
	}

	private BusStop getNextStop(Trip recentTrip) {
		Trip path = tripFollowsPath.get(recentTrip.name);
		Route route = tripFollowsRoute.get(recentTrip.name);

		int closestPointIndex = ArrivalTimePredictor
				.closestPointIndex(recentTrip.lastPoint(), path);
		for (int p = closestPointIndex + 1; p < path.gpsPoints.size(); p++) {
			for (BusStop stop : route.busStops) {
				if (stop.atStop(path.gpsPoints.get(p))) {
					return stop;
				}
			}
		}

		return null;
	}

	private static final int MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION = 10;
	int numberOfSearchesPerformed = 0;

	Route routeFollowedByTrip(Trip trip) {
		if (!tripFollowsRoute.containsKey(trip.name)
				&& numberOfSearchesPerformed < MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION
				&& Utils.randomBit()) {
			numberOfSearchesPerformed++;

			ArrayList<Trip> paths = Trip.extractTripsFromFolder(pathsFolder);
			for (Trip path : paths) {
				if (PathDetector.tripFollowsPath(trip, path)) {
					System.out.println(trip.name + " follows " + path.name);
					tripFollowsRoute.put(trip.name, new Route(new File(
							routesFolder.getName() + "/" + path.name)));
					tripFollowsPath.put(trip.name, path);
					break;
				}
			}
		}
		return tripFollowsRoute.get(trip.name);
	}

	private void processNewGpsInput(File jsonFile)
			throws ProjectSpecificException {
		System.out.println("Dealing with file " + jsonFile.getName());
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);
		numberOfSearchesPerformed = 0;

		// For each bus we want to make a prediction
		for (String vehicleId : BusTravelHistoryExtractor.allHistories
				.keySet()) {
			Trip vehicleTrip = getTrip(vehicleId);
			if (vehicleTrip == null) {
				continue;
			}

			Route routeFollowed = routeFollowedByTrip(vehicleTrip);
			if (routeFollowed == null) {
				continue;
			}

			BusStop nextStop = getNextStop(vehicleTrip);
			if (nextStop == null) {
				System.out.println("No next stop for " + vehicleId);
				tripFollowsRoute.remove(vehicleId);
			} else {
				ArrayList<Trip> historicalTrips = Trip
						.extractTripsFromFolder(new File(tripsFolder.getName()
								+ "/" + routeFollowed.name));

				System.out.println(
						"calculating prediction for stop " + nextStop.name);
				Long prediction = ArrivalTimePredictor
						.calculatePredictionTimestamp(p -> nextStop.atStop(p),
								vehicleTrip, historicalTrips);
				System.out.println("We predict that " + vehicleId
						+ " will arrive at " + nextStop.name + " at "
						+ Utils.convertTimestampToDate(prediction));

				Utils.appendLineToFile(new File("debug/prediction.txt"),
						"We predict that " + vehicleId + " will arrive at "
								+ nextStop.name + " at "
								+ Utils.convertTimestampToDate(prediction));
			}
		}

		System.out.println("Handled new GPS point.");
		System.out.println(
				"numberOfSearchesPerformed = " + numberOfSearchesPerformed);
	}

}
