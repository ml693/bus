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

	private final File tripsFolder;
	private final File routesFolder;
	/* Path is a trip following a route */
	private final File pathsFolder;
	/* All predictions are recorded to this file */
	private final File loggingFile;

	private final HashMap<String /* vehicleId */, Route> vehicleFollowsRoute = new HashMap<String, Route>();
	private final HashMap<String /* vehicleId */, Trip /* path */> vehicleFollowsPath = new HashMap<String, Trip>();
	private final HashMap<String /* vehicleId */, Prediction[]> currentPredictions = new HashMap<String, Prediction[]>();

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
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder",
				"file");

		GpsRealTimeInputWatcher watcher = new GpsRealTimeInputWatcher(
				new File(args[0]), new File(args[1]), new File(args[2]),
				new File(args[3]));
		watcher.waitForNewJsonInput();
	}

	GpsRealTimeInputWatcher(File tripsFolder, File routesFolder,
			File pathsFolder, File loggingFile) {
		this.tripsFolder = tripsFolder;
		this.routesFolder = routesFolder;
		this.pathsFolder = pathsFolder;
		this.loggingFile = loggingFile;
		Utils.appendLineToFile(loggingFile,
				"trip_name,from_stop_name,prediction_timestamp,predicted_timestamp,actual_arrival_timestamp");
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
				/*
				 * Sleeping because of some bug when locking files. The
				 * String from file is null, although the file should not be
				 * empty. TODO(ml693): solve the bug, then remove the sleep.
				 */
				Thread.sleep(100);

				// First look the real time input directory
				File lockFile = new File(LOCK_FILE_PATH);
				FileChannel channel = new RandomAccessFile(lockFile, "rw")
						.getChannel();
				FileLock lock = channel.lock();

				// Then process a new file
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

	private int nextStopIndex(Trip recentTrip) {
		Trip path = vehicleFollowsPath.get(recentTrip.name);
		Route route = vehicleFollowsRoute.get(recentTrip.name);

		int closestPointIndex = ArrivalTimePredictor
				.closestPointIndex(recentTrip.lastPoint(), path);
		for (int p = closestPointIndex + 1; p < path.gpsPoints.size(); p++) {
			for (int stop = 0; stop < route.busStops.size(); stop++) {
				if (route.busStops.get(stop).atStop(path.gpsPoints.get(p))) {
					return stop;
				}
			}
		}

		return route.busStops.size();
	}

	private static final int MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION = 30;
	int searchesPerformed = 0;

	Route routeFollowedByTrip(Trip trip) {
		if (!vehicleFollowsRoute.containsKey(trip.name)
				&& searchesPerformed < MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION
				&& Utils.randomBit()) {
			searchesPerformed++;
			ArrayList<Trip> paths = Trip.extractTripsFromFolder(pathsFolder);
			for (Trip path : paths) {
				if (PathDetector.tripFollowsPath(trip, path)) {
					System.out.println(trip.name + " follows " + path.name);
					vehicleFollowsRoute.put(trip.name, new Route(new File(
							routesFolder.getName() + "/" + path.name)));
					vehicleFollowsPath.put(trip.name, path);
					break;
				}
			}
		}

		return vehicleFollowsRoute.get(trip.name);
	}

	private boolean needNewPrediction(Trip trip, Route route, int stopIndex) {
		if (currentPredictions.containsKey(trip.name)) {
			Prediction[] predictions = currentPredictions.get(trip.name);
			if (predictions[stopIndex] == null) {
				return true;
			}

			if (predictions[stopIndex].busStop.atStop(trip.lastPoint())) {
				predictions[stopIndex].appendToFile(loggingFile,
						trip.lastPoint().timestamp);
				predictions[stopIndex] = null;
				return true;
			}

			long allowedErrorDifference = trip.lastPoint().timestamp
					- predictions[stopIndex].predictionTimestamp;
			if ((trip.lastPoint().timestamp
					- predictions[stopIndex].predictedTimestamp) > allowedErrorDifference) {
				predictions[stopIndex].appendToFile(loggingFile, -1);
				predictions[stopIndex] = null;
				return true;
			}

			return false;
		}
		currentPredictions.put(trip.name,
				new Prediction[route.busStops.size()]);
		return true;
	}

	private void processNewGpsInput(File jsonFile)
			throws ProjectSpecificException {
		System.out.println("Dealing with file " + jsonFile.getName());
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);

		int routeFoundFor = 0;
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

			int nextStopIndex = nextStopIndex(vehicleTrip);
			if (nextStopIndex == routeFollowed.busStops.size()) {
				System.out.println(vehicleId + " arrived at the last stop.");
				vehicleFollowsRoute.remove(vehicleId);
				vehicleFollowsPath.remove(vehicleId);
				currentPredictions.remove(vehicleId);
				continue;
			}
			routeFoundFor++;

			System.out.println("Predicting for " + vehicleTrip.name);
			ArrayList<Trip> historicalTrips = Trip.extractTripsFromFolder(
					new File(tripsFolder.getName() + "/" + routeFollowed.name));
			for (int stopIndex = nextStopIndex; stopIndex < routeFollowed.busStops
					.size(); stopIndex++) {
				if (needNewPrediction(vehicleTrip, routeFollowed, stopIndex)) {
					Prediction prediction = ArrivalTimePredictor.makePrediction(
							routeFollowed.busStops.get(stopIndex), vehicleTrip,
							historicalTrips);
					currentPredictions.get(vehicleId)[stopIndex] = prediction;
				}
			}
		}

		System.out.println("Handled new GPS point.");
		System.out.println("searchesPerformed = " + searchesPerformed);
		searchesPerformed = 0;
		System.out.println("routeFoundFor = " + routeFoundFor);
	}

}
