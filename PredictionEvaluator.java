package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/*
 * A class which contains methods to evaluate (i.e. calculate statistics)
 * how well the prediction algorithm performs.
 */
class PredictionEvaluator {

	private static long lastStopTimestamp(Route route, Trip trip)
			throws ProjectSpecificException {
		BusStop lastStop = route.busStops.get(route.busStops.size() - 1);
		for (GpsPoint point : trip.gpsPoints) {
			if (lastStop.atStop(point)) {
				return point.timestamp;
			}
		}
		throw new ProjectSpecificException(
				"No point passed through last stop for " + trip.name);
	}

	private static void extractData(Route route, File inputTripsFolder,
			String outputPath, String dataPurpose) {
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(inputTripsFolder);

		System.out.println(
				"Extracting " + dataPurpose + " trips for route " + route.name);
		new File(outputPath + "/" + route.name).mkdir();
		File outputFolder = new File(
				outputPath + "/" + route.name + "/" + dataPurpose);
		outputFolder.mkdir();

		for (Trip trip : trips) {
			if (route.followedByTrip(trip)) {
				trip.writeToFolder(outputFolder);
			}
		}

		System.out.println("Found " + outputFolder.listFiles().length
				+ " trips following " + route.name);
	}

	private static void extractTrainingData(Route route, File inputTripsFolder,
			String outputPath) throws ProjectSpecificException {
		extractData(route, inputTripsFolder, outputPath, "training");
	}

	private static void extractTestData(Route route, File inputTripsFolder,
			String outputPath) {
		extractData(route, inputTripsFolder, outputPath, "test");
	}

	private static void compressPaths(Route route, String outputPath) {
		BusStop firstStop = route.busStops.get(0);
		File[] tripFiles = new File(outputPath + "/" + route.name + "/test")
				.listFiles();
		File compressedTripsFolder = new File(
				outputPath + "/" + route.name + "/compressed");
		compressedTripsFolder.mkdir();

		for (File tripFile : tripFiles) {
			try {
				Trip trip = new Trip(tripFile);
				for (int p = 0; p < trip.gpsPoints.size(); p++) {
					if (firstStop.atStop(trip.gpsPoints.get(p))) {
						for (int i = 0; i < p; i++) {
							trip.gpsPoints.remove(0);
						}
						break;
					}
				}

				if (trip.gpsPoints
						.size() >= Trip.MINIMUM_NUMBER_OF_GPS_POINTS) {
					trip.writeToFolder(compressedTripsFolder);
				}
			} catch (ProjectSpecificException exception) {
				throw new RuntimeException(exception);
			}
		}
	}

	private static void delimitIntoRecentAndFutureSubtrips(Route route,
			String outputPath) {
		System.out.println("Dealing with " + route.name + ", which has "
				+ route.busStops.size() + " stops.");

		File recentFolder = new File(outputPath + "/" + route.name + "/recent");
		File futureFolder = new File(outputPath + "/" + route.name + "/future");
		recentFolder.mkdir();
		futureFolder.mkdir();

		ArrayList<Trip> trips = Trip.extractTripsFromFolder(
				new File(outputPath + "/" + route.name + "/compressed"));
		for (Trip trip : trips) {
			for (int p = 0; p < trip.gpsPoints.size(); p++) {
				if (route.busStops.get(route.busStops.size() / 2)
						.atStop(trip.gpsPoints.get(p))) {
					try {
						Trip recent = new Trip(trip.name,
								new ArrayList<GpsPoint>(
										trip.gpsPoints.subList(0, p)));
						Trip future = new Trip(trip.name,
								new ArrayList<GpsPoint>(trip.gpsPoints
										.subList(p, trip.gpsPoints.size())));

						recent.writeToFolder(recentFolder);
						future.writeToFolder(futureFolder);
					} catch (ProjectSpecificException exception) {
					}
				}
			}
		}
	}

	private static boolean statisticsGenerated(Route route, String outputPath)
			throws IOException, ProjectSpecificException {
		ArrayList<Trip> recentTrips = Trip.extractTripsFromFolder(
				new File(outputPath + "/" + route.name + "/recent"));
		if (recentTrips.size() < 30) {
			return false;
		}
		System.out.println("Will evaluate for " + route.name + " with "
				+ recentTrips.size() + " passing through it");

		BufferedWriter resultsWriter = new BufferedWriter(
				new FileWriter(outputPath + "/" + route.name + "/results"));
		Utils.writeLine(resultsWriter,
				"Predicted when the bus will reach last stop of " + route.name);
		Utils.writeLine(resultsWriter, "");

		long accumulatedAbsoluteError = 0L;
		for (Trip recentTrip : recentTrips) {
			ArrayList<Trip> historicalTrips = Trip.extractTripsFromFolder(
					new File(outputPath + "/" + route.name + "/training"),
					recentTrip.lastPoint().timestamp);
			if (historicalTrips.size() < 10) {
				continue;
			}

			long predictedTimestamp = ArrivalTimePredictor
					.calculatePredictionToBusStop(route::atLastStop, recentTrip,
							historicalTrips);

			Trip futureTrip = new Trip(new File(outputPath + "/" + route.name
					+ "/future/" + recentTrip.name));

			long actualTimestamp = lastStopTimestamp(route, futureTrip);
			long error = actualTimestamp - predictedTimestamp;
			accumulatedAbsoluteError += Math.abs(error);

			Utils.writeLine(resultsWriter,
					recentTrip.name + " started at "
							+ Utils.convertTimestampToDate(
									recentTrip.lastPoint().timestamp)
					+ ", was predicted for "
					+ Utils.convertTimestampToDate(predictedTimestamp)
					+ ", actually arrived at "
					+ Utils.convertTimestampToDate(actualTimestamp)
					+ ", prediction error is " + error);
		}

		Utils.writeLine(resultsWriter,
				"Mean absolute prediction error is MAE = "
						+ accumulatedAbsoluteError / recentTrips.size()
						+ " seconds.");

		resultsWriter.close();

		return true;
	}

	private static void evaluatePrediction(String[] args)
			throws IOException, ProjectSpecificException {
		/* 1st argument is a folder routes */
		/* 2nd argument is a folder containing historical trips */
		/* 3rd argument is a top directory where to output results */
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder",
				"folder");

		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[0]));
		File trainingTripsFolder = new File(args[1]);
		File testTripsFolder = new File(args[2]);
		String outputPath = args[3];

		for (Route route : routes) {
			extractTrainingData(route, trainingTripsFolder, outputPath);
			extractTestData(route, testTripsFolder, outputPath);
			compressPaths(route, outputPath);
			delimitIntoRecentAndFutureSubtrips(route, outputPath);
			if (statisticsGenerated(route, outputPath)) {
				System.out.println("Generated statistics for " + route.name);
			}
		}
	}

	public static void main(String args[]) throws Exception {
		evaluatePrediction(args);
	}

}