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
	private final HashMap<String /* vehicleId */, Prediction[]> lastStopPredictions = new HashMap<String, Prediction[]>();

	private final HashMap<String /* vehicleId */, Prediction> nextStopPrediction = new HashMap<String, Prediction>();

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
				"route_name,from_stop_index,to_stop_index,prediction_timestamp,predicted_timestamp,actual_arrival_timestamp");
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
			for (int stopIndex = 1; stopIndex < route.busStops
					.size(); stopIndex++) {
				BusStop busStop = route.busStops.get(stopIndex);
				if (busStop.atStop(path.gpsPoints.get(p))
						&& !busStop.atStop(recentTrip.lastPoint())) {
					return stopIndex;
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
					Route route = new Route(
							new File(routesFolder.getName() + "/" + path.name));
					if (route.lastStop().atStop(trip.lastPoint())) {
						System.out.println(trip.name + " on " + route.name);
						vehicleFollowsRoute.put(trip.name, route);
						vehicleFollowsPath.put(trip.name, path);
						break;
					}
				}
			}
		}

		return vehicleFollowsRoute.get(trip.name);
	}

	private void removeVehicle(String vehicleId) {
		vehicleFollowsRoute.remove(vehicleId);
		vehicleFollowsPath.remove(vehicleId);
		lastStopPredictions.remove(vehicleId);
		nextStopPrediction.remove(vehicleId);
	}

	private boolean tripDeviatedFromRoute(Trip trip, Route route) {
		Prediction currentPrediction = nextStopPrediction.get(trip.name);

		/* If there is no prediction, I first make one. */
		if (currentPrediction == null) {
			int nextStopIndex = nextStopIndex(trip);

			ArrayList<Trip> historicalTrips = Trip.extractTripsFromFolder(
					new File(tripsFolder.getName() + "/" + route.name));
			try {
				Prediction newPrediction = ArrivalTimePredictor.makePrediction(
						route.busStops.get(nextStopIndex), trip,
						historicalTrips);
				newPrediction.route = route;
				newPrediction.fromStopIndex = -1;
				newPrediction.toStopIndex = nextStopIndex;
				newPrediction.predictionTimestamp = trip.lastPoint().timestamp;
				nextStopPrediction.put(trip.name, newPrediction);
			} catch (ProjectSpecificException exception) {
				throw new RuntimeException(exception);
			}

			return false;
		}

		if (currentPrediction.route.busStops.get(currentPrediction.toStopIndex)
				.atStop(trip.lastPoint())) {
			currentPrediction.appendToFile(loggingFile,
					trip.lastPoint().timestamp);
			nextStopPrediction.remove(trip.name);
			return false;
		}

		return Math.abs(trip.lastPoint().timestamp
				- currentPrediction.predictionTimestamp) < 4
						* Math.abs(currentPrediction.predictedTimestamp
								- currentPrediction.predictionTimestamp);
	}

	private boolean endOfRouteReached(Trip trip, Route route) {
		return route.lastStop().atStop(trip.lastPoint());
	}

	private void flushPredictions(Trip trip) {
		Prediction[] predictions = lastStopPredictions.get(trip.name);
		for (Prediction prediction : predictions) {
			if (prediction != null) {
				prediction.appendToFile(loggingFile,
						trip.lastPoint().timestamp);
			}
		}
	}

	private void processNewGpsInput(File jsonFile)
			throws ProjectSpecificException {
		System.out.println("Dealing with file " + jsonFile.getName());
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);

		int routeFoundFor = 0;
		// For each bus we want to make a prediction
		for (String vehicleId : BusTravelHistoryExtractor.allHistories
				.keySet()) {
			Trip trip = getTrip(vehicleId);
			if (trip == null) {
				continue;
			}

			Route route = routeFollowedByTrip(trip);
			if (route == null) {
				continue;
			}

			if (tripDeviatedFromRoute(trip, route)) {
				removeVehicle(vehicleId);
				continue;
			}

			if (endOfRouteReached(trip, route)) {
				flushPredictions(trip);
				removeVehicle(vehicleId);
				continue;
			}

			int recentStopIndex = nextStopIndex(trip) - 1;
			if (route.busStops.get(recentStopIndex).atStop(trip.lastPoint())) {
				System.out.println("Predicting for " + trip.name);
				ArrayList<Trip> historicalTrips = Trip.extractTripsFromFolder(
						new File(tripsFolder.getName() + "/" + route.name));

				Prediction prediction = ArrivalTimePredictor.makePrediction(
						route.lastStop(), trip, historicalTrips);
				prediction.predictionTimestamp = trip.lastPoint().timestamp;
				prediction.fromStopIndex = recentStopIndex;
				prediction.toStopIndex = route.busStops.size() - 1;

				lastStopPredictions
						.get(vehicleId)[recentStopIndex] = prediction;
			}
		}

		System.out.println("Handled new GPS point.");
		System.out.println("searchesPerformed = " + searchesPerformed);
		searchesPerformed = 0;
		System.out.println("routeFoundFor = " + routeFoundFor);
	}
}
