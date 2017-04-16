package bus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Function;

/*
 * A class which contains main method to run anything we want. If one wants to
 * to check something, he can use this class, so that other classes would not
 * get 'polluted' with arbitrary code.
 */
class ArbitraryCodeExecutor {

	public static void main(String args[]) throws ProjectSpecificException {
		int[] att = new int[12];
		int[] mae = new int[12];
		int[] count = new int[12];

		Scanner scanner = Utils.csvScanner(new File(args[0]));
		scanner.nextLine();

		HashMap<String, Integer> routeCounts = new HashMap<String, Integer>();

		while (scanner.hasNext()) {
			/* Skipping trip name */
			scanner.next();

			String routeName = scanner.next();
			if (!routeCounts.containsKey(routeName)) {
				routeCounts.put(routeName, 1);
			} else {
				routeCounts.put(routeName, routeCounts.get(routeName) + 1);
			}

			int fromStop = scanner.nextInt();
			scanner.nextInt();

			long predictionTime = Utils.convertDateToTimestamp(scanner.next());
			long predictedTime = Utils.convertDateToTimestamp(scanner.next());
			long actualTime = Utils.convertDateToTimestamp(scanner.next());
			scanner.nextInt();

			if (routeName.equals("1051308-20150531-20150830")) {
				att[fromStop] += (actualTime - predictionTime);
				mae[fromStop] += Math.abs(actualTime - predictedTime);
				count[fromStop]++;
			}
		}

		for (String key : routeCounts.keySet()) {
			Route route = new Route(new File("uk/routes/" + key));
			System.out.println(key + " " + routeCounts.get(key) + " "
					+ route.busStops.size());
		}

		for (int i = 1; i < 7; i++) {
			System.out.println(i);
			System.out.println("ATT = " + att[i] / count[i]);
			System.out.println("MAE = " + mae[i] / count[i]);
			System.out.println();
		}

	}

	public static void produceCsvForPlotting(String args[])
			throws ProjectSpecificException {
		Scanner scanner = Utils.csvScanner(new File("results.csv"));
		scanner.nextLine();

		int[][] errorCount = new int[7][2000];
		while (scanner.hasNext()) {
			errorCount[scanner.nextInt()][scanner.nextInt()]++;
		}

		File outputFile = new File("counts.txt");
		Utils.appendLineToFile(outputFile, "stop_number,error,count");
		for (int i = 0; i < 7; i++) {
			for (int error = 0; error < 2000; error++) {
				if (errorCount[i][error] > 0) {
					Utils.appendLineToFile(outputFile,
							i + "," + error + "," + errorCount[i][error]);
				}
			}
		}
	}

	public static void evaluateRoute(String[] args)
			throws ProjectSpecificException {
		Utils.checkCommandLineArguments(args, "file", "folder", "file");
		Route route = new Route(new File(args[0]));
		File tripsFolder = new File(args[1]);
		File evaluationFile = new File(args[2]);

		for (int stop = 1; stop < route.busStops.size() - 1; stop++) {
			System.out.println("For stop number " + stop + " results are:");
			evaluateRoute(route, tripsFolder, stop, evaluationFile);
		}
	}

	public static Trip upToStop(int upToNumber, Trip trip, Route route) {
		for (int p = 0; p < trip.gpsPoints.size(); p++) {
			if (route.busStops.get(upToNumber).atStop(trip.gpsPoints.get(p))) {
				ArrayList<GpsPoint> points = new ArrayList<GpsPoint>(
						trip.gpsPoints.subList(0, p));
				for (int i = 0; i < Trip.MINIMUM_NUMBER_OF_GPS_POINTS
						- p; i++) {
					points.add(0, trip.gpsPoints.get(0));
				}
				try {
					return new Trip(trip.name, points);
				} catch (ProjectSpecificException exception) {
					throw new RuntimeException(exception);
				}
			}
		}
		throw new RuntimeException("No GPS point passes through "
				+ route.busStops.get(upToNumber).name + " for " + trip.name);
	}

	/* Computes various statistics for the arrival time to last stop */
	public static void evaluateRoute(Route route, File tripsFolder,
			int fromStopNumber, File evaluationFile)
					throws ProjectSpecificException {
		Utils.appendLineToFile(evaluationFile,
				"Predicting when trips will reach " + route.lastStop().name
						+ " (nr. " + route.busStops.size() + ") from "
						+ route.busStops.get(fromStopNumber).name + " (nr. "
						+ fromStopNumber + ")");

		ArrayList<Trip> trips = Trip.extractTripsFromFolder(tripsFolder);
		ArrayList<Trip> shortTrips = new ArrayList<Trip>();
		for (Trip trip : trips) {
			shortTrips.add(upToStop(fromStopNumber, trip, route));
		}

		long difference = 0;
		long delaysSum = 0;
		for (int t = trips.size() - 1; t >= 0; t--) {
			Trip historicalTrip = trips.get(t);
			trips.remove(t);

			long predictedTimestamp = ArrivalTimePredictor.makePrediction(
					route.lastStop(), shortTrips.get(t),
					trips).predictedTimestamp;
			long actualTimestamp = PredictionEvaluator.lastStopTimestamp(route,
					historicalTrip);
			long predictionError = Math
					.abs(actualTimestamp - predictedTimestamp);

			Utils.appendLineToFile(evaluationFile,
					historicalTrip.name + " started at "
							+ Utils.convertTimestampToDate(
									shortTrips.get(t).lastPoint().timestamp)
					+ ", was predicted for "
					+ Utils.convertTimestampToDate(predictedTimestamp)
					+ ", actually arrived at "
					+ Utils.convertTimestampToDate(actualTimestamp)
					+ ", prediction error is "
					+ (actualTimestamp - predictedTimestamp));

			difference += predictionError;
			delaysSum += actualTimestamp
					- shortTrips.get(t).lastPoint().timestamp;
			trips.add(historicalTrip);
		}

		System.out.println("MAE = " + difference / trips.size());
		System.out.println("Delay = " + delaysSum / trips.size());
		System.out.println("Prediction count = " + trips.size());
		System.out.println();
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
			Trip trip = Trip.readFromFile(tripFile);
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