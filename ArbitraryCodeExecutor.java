package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.function.Function;

/*
 * A class which contains main method to run anything we want. If one wants to
 * to check something, he can use this class, so that other classes would not
 * get 'polluted' with arbitrary code.
 */
class ArbitraryCodeExecutor {

	private static boolean atBusStop(GpsPoint point, BusStop stop) {
		GpsPoint stopPoint = new GpsPoint(0L, stop.latitude, stop.longitude);
		return (Utils.distance(point, stopPoint) < 0.00002);
	}

	static boolean atLastStop(Route route, GpsPoint point) {
		try {
			BusStop lastStop = route.busStops.get(route.busStops.size() - 1);
			return atBusStop(point, lastStop);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	static long lastStopTimestamp(Route route, Trip trip) {
		try {
			BusStop lastStop = route.busStops.get(route.busStops.size() - 1);
			for (GpsPoint point : trip.gpsPoints) {
				if (atBusStop(point, lastStop)) {
					return point.timestamp;
				}
			}
			throw new ProjectSpecificException(
					"No point passed through last stop for " + trip.name);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	static void compressPaths(String tripsPath, File routesFolder)
			throws Exception {
		File[] routesFiles = routesFolder.listFiles();

		for (File routeFile : routesFiles) {
			Route route = new Route(routeFile);
			BusStop firstStop = route.busStops.get(0);
			File[] tripFiles = new File(tripsPath + "/" + routeFile.getName())
					.listFiles();

			for (File tripFile : tripFiles) {
				Trip trip = new Trip(tripFile);
				for (int p = 0; p < trip.gpsPoints.size(); p++) {
					if (atBusStop(trip.gpsPoints.get(p), firstStop)) {
						for (int i = 0; i < p - 1; i++) {
							trip.gpsPoints.remove(0);
						}
						break;
					}
				}

				tripFile.delete();
				if (trip.gpsPoints
						.size() >= Trip.MINIMUM_NUMBER_OF_GPS_POINTS) {
					trip.writeToFolder(tripFile.getParentFile());
				}
			}
		}
	}

	public static void buildEvaluationData(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[0]));
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(new File(args[1]));

		for (Route route : routes) {
			System.out.println("Working with " + route.name);
			File routeFolder = new File(args[2] + "/" + route.name);
			routeFolder.mkdir();

			for (Trip trip : trips) {
				if (RoutesDetector.routeFollowedByTrip(route, trip)) {
					trip.writeToFolder(routeFolder);
				}
			}
		}
	}

	public static void delimitIntoRecentAndFuture(String args[])
			throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");

		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[0]));

		for (Route route : routes) {
			System.out.println("Dealing with " + route.name + ", which has "
					+ route.busStops.size() + " stops.");

			new File(args[1] + "/" + route.name).mkdir();
			File recentFolder = new File(
					args[1] + "/" + route.name + "/recent");
			File futureFolder = new File(
					args[1] + "/" + route.name + "/future");
			recentFolder.mkdir();
			futureFolder.mkdir();

			File[] tripFiles = new File(args[2] + "/" + route.name).listFiles();
			for (File tripFile : tripFiles) {
				Trip trip = new Trip(tripFile);
				for (int p = 0; p < trip.gpsPoints.size(); p++) {
					if (atBusStop(trip.gpsPoints.get(p),
							route.busStops.get(route.busStops.size() / 2))) {
						try {
							Trip recent = new Trip(trip.name,
									new ArrayList<GpsPoint>(
											trip.gpsPoints.subList(0, p)));

							Trip future = new Trip(trip.name,
									new ArrayList<GpsPoint>(
											trip.gpsPoints.subList(p,
													trip.gpsPoints.size())));

							recent.writeToFolder(recentFolder);
							future.writeToFolder(futureFolder);
						} catch (ProjectSpecificException exception) {
						}
					}
				}
			}
		}
	}

	public static void evaluatePrediction(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[0]));

		for (Route route : routes) {
			ArrayList<Trip> recentTrips = Trip.extractTripsFromFolder(
					new File(args[1] + "/" + route.name + "/recent"));
			if (recentTrips.size() < 30) {
				continue;
			}

			BufferedWriter resultsWriter = new BufferedWriter(
					new FileWriter(args[1] + "/" + route.name + "/results"));
			Utils.writeLine(resultsWriter,
					"Predicted when the bus will reach last stop of "
							+ route.name);
			Utils.writeLine(resultsWriter, "");

			long accumulatedAbsoluteError = 0L;
			for (Trip recentTrip : recentTrips) {
				ArrayList<Trip> historicalTrips = Trip.extractTripsFromFolder(
						new File(args[2] + "/" + route.name),
						recentTrip.lastPoint().timestamp);

				for (int h = 0; h < historicalTrips.size(); h++) {
					if (historicalTrips.get(h).name.equals(recentTrip.name)) {
						historicalTrips.remove(h);
					}
				}
				if (historicalTrips.size() < 10) {
					continue;
				}

				long predictedTimestamp = ArrivalTimePredictor
						.calculatePredictionToBusStop(route::atLastStop,
								recentTrip, historicalTrips);

				Trip futureTrip = new Trip(new File(args[1] + "/" + route.name
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
		}
	}

	public static void main(String args[]) throws Exception {
		evaluatePrediction(args);
	}

	public static boolean inCambridge(Trip path) {
		for (GpsPoint point : path.gpsPoints) {
			if (point.latitude >= 52.19 && point.latitude <= 52.22
					&& point.longitude >= 0.08 && point.longitude <= 0.12) {
				return true;
			}
		}
		return false;
	}

	static boolean atMadingleyPark(GpsPoint point) {
		return (point.latitude >= 52.2138 && point.latitude <= 52.2150
				&& point.longitude >= 0.0825 && point.longitude <= 0.0843);
	}

	static boolean stoppedAtMadingleyPark(Trip trip) {
		for (GpsPoint point : trip.gpsPoints) {
			if (atMadingleyPark(point)) {
				return true;
			}
		}
		return false;
	}

	static boolean atVictoria(GpsPoint point) {
		return (point.latitude >= 51.4940 && point.latitude <= 51.4955
				&& point.longitude >= -0.147 && point.longitude <= -0.145);
	}

	static long madingleyStopTimestamp(Trip trip) {
		for (GpsPoint point : trip.gpsPoints) {
			if (atMadingleyPark(point)) {
				return point.timestamp;
			}
		}
		return -1L;
	}

	static long victoriaStopTimestamp(Trip trip) {
		for (GpsPoint point : trip.gpsPoints) {
			if (atVictoria(point)) {
				return point.timestamp;
			}
		}
		return -1L;
	}

	static boolean atLuton(GpsPoint point) {
		return (point.latitude >= 51.87 && point.latitude <= 51.88
				&& point.longitude >= -0.40 && point.longitude <= -0.36);
	}

	static void extractThroughMadingley(String[] args) throws Exception {
		extractThroughRegion(args, ArbitraryCodeExecutor::atMadingleyPark);
	}

	static void extractThroughLuton(String[] args) throws Exception {
		extractThroughRegion(args, ArbitraryCodeExecutor::atLuton);
	}

	static void extractThroughVictoria(String[] args) throws Exception {
		extractThroughRegion(args, ArbitraryCodeExecutor::atVictoria);
	}

	static void extractThroughRegion(String[] args,
			Function<GpsPoint, Boolean> passedThrough) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder");
		File inputFolder = new File(args[0]);
		File[] tripFiles = inputFolder.listFiles();
		File outputFolder = new File(args[1]);
		for (File tripFile : tripFiles) {
			Trip trip = new Trip(tripFile);
			System.out.println("Processing trip " + trip.name);
			for (GpsPoint point : trip.gpsPoints) {
				if (passedThrough.apply(point)) {
					System.out
							.println("Trip " + trip.name + " passed through!");
					trip.writeToFolder(outputFolder);
					break;
				}
			}
		}
	}

	static void extractGoodTripsThroughMadingley(String[] args)
			throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder", "folder");

		Trip profileTripThroughMadingley = new Trip(new File(args[0]));
		File allTripsFolder = new File(args[1]);
		ArrayList<Trip> goodTrips = PathDetector.detectSimilarTrips(
				profileTripThroughMadingley, allTripsFolder);

		File outputFolder = new File(args[2]);
		for (Trip goodTrip : goodTrips) {
			goodTrip.writeToFolder(outputFolder);
		}
	}

	static boolean closeEnough(GpsPoint p1, GpsPoint p2) {
		return (Utils.distance(p1, p2) < 0.000001);
	}

	static boolean delimitingPoint(GpsPoint point) {
		GpsPoint point1 = new GpsPoint(0L, 52.2143, 0.1258);
		GpsPoint point2 = new GpsPoint(0L, 52.2139, 0.1233);
		GpsPoint point3 = new GpsPoint(0L, 52.2129, 0.1257);
		return closeEnough(point1, point) || closeEnough(point2, point)
				|| closeEnough(point3, point);
	}

	/* Specific for Madingley Park evaluation */
	static int indexToDelimit(Trip trip) {
		int index = 0;
		int delimitingIndex = 0;
		while (index < trip.gpsPoints.size()) {
			if (delimitingPoint(trip.gpsPoints.get(index))) {
				delimitingIndex = index;
				long delimitArrivalTime = trip.gpsPoints.get(index).timestamp;
				while (index < trip.gpsPoints.size()
						&& !atMadingleyPark(trip.gpsPoints.get(index))
						&& (trip.gpsPoints.get(index).timestamp
								- delimitArrivalTime) < 1500) {

					if (trip.gpsPoints.get(index).timestamp
							- delimitArrivalTime > 200
							&& delimitingPoint(trip.gpsPoints.get(index))) {
						break;
					}
					index++;
				}
				if (index < trip.gpsPoints.size()
						&& atMadingleyPark(trip.gpsPoints.get(index))) {
					long timeDifference = (trip.gpsPoints.get(index).timestamp
							- delimitArrivalTime);
					if (timeDifference < 1200) {
						return delimitingIndex + 3;
					}
				}
			}
			index++;
		}
		return -1;
	}

	static void delimitTrips(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(new File(args[0]));
		File recentPartsFolder = new File(args[1]);
		File futurePartsFolder = new File(args[2]);

		for (Trip trip : trips) {
			int indexToDelimit = indexToDelimit(trip);
			if (indexToDelimit >= 7) {
				int minIndex = Math.max(0, indexToDelimit - 10);
				Trip recentPart = new Trip(trip.name, new ArrayList<GpsPoint>(
						trip.gpsPoints.subList(minIndex, indexToDelimit)));
				recentPart.writeToFolder(recentPartsFolder);

				Trip futurePart = new Trip(trip.name,
						new ArrayList<GpsPoint>(trip.gpsPoints.subList(
								indexToDelimit, trip.gpsPoints.size())));
				futurePart.writeToFolder(futurePartsFolder);
			} else {
				System.out.println(
						"For trip " + trip.name + " things didn't work out.");
			}
		}
	}

	static void extractTripsFollowingPath(Trip path, File tripsFolder)
			throws Exception {
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(tripsFolder);
		for (Trip trip : trips) {
			if (PathDetector.tripFollowsPath(trip, path)) {
				System.out.println(trip.name + " follows");
				trip.writeToFolder(new File("uk/debug"));
			}
		}
	}

}