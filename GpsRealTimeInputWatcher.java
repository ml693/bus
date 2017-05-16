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
import java.util.Collections;
import java.util.HashMap;

class GpsRealTimeInputWatcher {
	// These strings are likely to change
	private static final String INCOMMING_JSON_FOLDER_PATH = "/media/tfc/ml693/data_monitor/";
	private static final String LOCK_FILE_PATH = "/media/tfc/ml693/data_monitor_lock_file";

	private final File tripsFolder;
	private final File routesFolder;
	/* All predictions are recorded to this file */
	private final File predictionsFile;

	private final File debugFile;

	private final HashMap<String /* vehicleId */, Route> vehicleFollowsRoute = new HashMap<String, Route>();
	private final HashMap<String /* vehicleId */, Trip /* path */> vehicleFollowsPath = new HashMap<String, Trip>();
	private final HashMap<String /* vehicleId */, Prediction[]> lastStopPredictions = new HashMap<String, Prediction[]>();
	private final HashMap<String /* vehicleId */, Prediction> nextStopPrediction = new HashMap<String, Prediction>();

	private final ArrayList<Trip> paths;

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
				"folder");

		GpsRealTimeInputWatcher watcher = new GpsRealTimeInputWatcher(
				new File(args[0]), new File(args[1]), new File(args[2]),
				args[3]);
		watcher.waitForNewJsonInput();
	}

	GpsRealTimeInputWatcher(File tripsFolder, File routesFolder,
			File pathsFolder, String loggingFolderPath) {
		this.tripsFolder = tripsFolder;
		this.routesFolder = routesFolder;

		this.paths = Trip.readFromFolder(pathsFolder);
		Collections.sort(paths, (p1, p2) -> Utils
				.filesInFolder(tripsFolder.getName() + "/" + p2.name).size()
				- Utils.filesInFolder(tripsFolder.getName() + "/" + p1.name)
						.size());

		this.predictionsFile = new File(loggingFolderPath + "/predictions.txt");
		this.debugFile = new File(loggingFolderPath + "/debug.txt");
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
				// When the new file arrives
				WatchKey watchKey = watchService.take();
				watchKey.pollEvents();
				/*
				 * Sleeping because of some bug when locking the files. The
				 * String from file is null, although the file should not be
				 * empty. TODO(ml693): solve the bug, then remove the sleep.
				 */
				Thread.sleep(100);

				// First lock the real time input directory
				File lockFile = new File(LOCK_FILE_PATH);
				FileChannel channel = new RandomAccessFile(lockFile, "rw")
						.getChannel();
				FileLock lock = channel.lock();

				// Then process a new file
				if (watchKey.isValid()) {
					processNewGpsInput(new File(INCOMMING_JSON_FOLDER_PATH)
							.listFiles()[0]);
				}

				// Finally unlock the real time input directory
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
				exception.printStackTrace();
				throw new RuntimeException(exception);
			}
		}
	}

	Trip getTrip(String vehicleId, int numberOfPoints) {
		ArrayList<GpsPoint> points = BusTravelHistoryExtractor.allHistories
				.get(vehicleId);
		if (points.size() < Trip.MINIMUM_NUMBER_OF_GPS_POINTS) {
			return null;
		}

		try {
			return new Trip(vehicleId,
					new ArrayList<GpsPoint>(points.subList(
							Math.max(0, points.size() - numberOfPoints),
							points.size())));
		} catch (ProjectSpecificException exception) {
			throw new RuntimeException(exception);
		}
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

	private static final int MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION = 100;
	int searchesPerformed = 0;

	Route routeFollowedByTrip(Trip trip) {
		if (!vehicleFollowsRoute.containsKey(trip.name)
				&& searchesPerformed < MAX_NUMBER_OF_SEARCHES_IN_ONE_ITERATION
				&& Utils.randomBit()) {
			searchesPerformed++;
			for (Trip path : paths) {
				if (PathDetector.tripFollowsPath(trip, path)) {
					Route route = new Route(
							new File(routesFolder.getName() + "/" + path.name));
					if (!route.lastStop().atStop(trip.lastPoint())) {
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
		for (Trip path : paths) {
			if (path.name.equals(route.name)) {
				return !PathDetector.tripFollowsPath(trip, path);
			}
		}

		throw new RuntimeException(
				"I do not have " + route.name + " in my path set");
	}

	private boolean endOfRouteReached(Trip trip, Route route) {
		return route.lastStop().atStop(trip.lastPoint());
	}

	private void flushPredictions(Trip trip) {
		Prediction[] predictions = lastStopPredictions.get(trip.name);
		if (predictions != null) {
			for (Prediction prediction : predictions) {
				if (prediction != null) {
					prediction.appendToFile(predictionsFile,
							trip.lastPoint().timestamp);
					if (Math.abs(trip.lastPoint().timestamp
							- prediction.predictedTimestamp) > 500) {
						System.out.println(prediction.name
								+ " mispredicted for " + trip.name);
						Utils.appendLineToFile(debugFile,
								trip.name + " following a route "
										+ prediction.route.name
										+ " was equallyCongested="
										+ prediction.equallyCongested
										+ " and mispredicted because of the historical trip "
										+ prediction.name);
						getTrip(trip.name, 16).writeToFolder(
								new File("logging/mispredicted"));
					}
				}
			}
		}

		nextStopPrediction.remove(trip.name);
	}

	private void demoteRoute(String name) {
		int pathIndex = 0;
		while (!paths.get(pathIndex).name.equals(name)) {
			pathIndex++;
		}
		for (int i = 0; i < 3 && pathIndex < paths.size() - 1; i++) {
			/* if demote */
			if (Utils.randomBit()) {
				/* demote */
				Trip tempPath = paths.get(pathIndex);
				paths.set(pathIndex, paths.get(pathIndex + 1));
				paths.set(pathIndex + 1, tempPath);
				pathIndex++;
			}
		}
	}

	private void processNewGpsInput(File jsonFile)
			throws ProjectSpecificException {
		System.out.println("Dealing with file " + jsonFile.getName());
		BusTravelHistoryExtractor.updateBusesTravelHistoryWithFile(jsonFile);

		// For each bus we want to make a prediction
		for (String vehicleId : BusTravelHistoryExtractor.allHistories
				.keySet()) {
			Trip trip = getTrip(vehicleId, Trip.MINIMUM_NUMBER_OF_GPS_POINTS);
			if (trip == null) {
				continue;
			}

			Route route = routeFollowedByTrip(trip);
			if (route == null) {
				continue;
			}

			if (endOfRouteReached(trip, route)) {
				System.out.println(trip.name + " at the end of " + route.name);
				flushPredictions(trip);
				removeVehicle(vehicleId);
				continue;
			}

			if (tripDeviatedFromRoute(trip, route)) {
				System.out.println(trip.name + " deviated from " + route.name);
				Utils.appendLineToFile(debugFile,
						trip.name + " deviated from " + route.name);
				removeVehicle(vehicleId);
				demoteRoute(route.name);
				continue;
			}

			int recentStopIndex = nextStopIndex(trip) - 1;
			if (route.busStops.get(recentStopIndex).atStop(trip.lastPoint())) {
				System.out.println("Predicting for " + trip.name);
				ArrayList<Trip> historicalTrips = Trip.readFromFolder(
						new File(tripsFolder.getName() + "/" + route.name));

				Prediction prediction = ArrivalTimePredictor.makePrediction(
						route.lastStop(), trip, historicalTrips);
				prediction.predictionTimestamp = trip.lastPoint().timestamp;
				prediction.route = route;
				prediction.fromStopIndex = recentStopIndex;
				prediction.toStopIndex = route.busStops.size() - 1;

				if (!lastStopPredictions.containsKey(vehicleId)) {
					lastStopPredictions.put(vehicleId,
							new Prediction[route.busStops.size()]);
				}
				lastStopPredictions
						.get(vehicleId)[recentStopIndex] = prediction;
			}
		}

		System.out.println("Handled the new GPS point.");
		searchesPerformed = 0;
		System.out.println();
	}
}
