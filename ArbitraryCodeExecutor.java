package bus;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Function;

/*
 * A class which contains main method to run anything we want. If one wants to
 * to check something, he can use this class, so that other classes would not
 * get 'polluted' with arbitrary code.
 */
class ArbitraryCodeExecutor {

	public static void main(String args[]) throws ProjectSpecificException {
		File[] tripsFolder = new File(args[0]).listFiles();
		File pathsFolder = new File(args[1]);

		for (File tripFile : tripsFolder) {
			File[] tripFiles = tripFile.listFiles();
			Trip path = Trip.readFromFile(tripFiles[0]);
			path.makeCopyWithNewName(tripFile.getName())
					.writeToFolder(pathsFolder);
		}
	}

	public static void evaluateOneRoute(String args[])
			throws ProjectSpecificException {
		Route route = new Route(new File(args[0]));
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(new File(args[1]));

		ArrayList<Trip> shortTrips = new ArrayList<Trip>();
		for (Trip trip : trips) {
			shortTrips.add(trip.subTrip(0, 8));
		}

		long difference = 0;

		for (int t = 0; t < trips.size(); t++) {
			Trip trip = trips.get(t);
			Trip shortTrip = shortTrips.get(t);

			long predictedTimestamp = ArrivalTimePredictor
					.calculatePredictionTimestamp(p -> route.atLastStop(p),
							shortTrip, trips);
			long actualTimestamp = PredictionEvaluator.lastStopTimestamp(route,
					trip);

			difference += Math.abs(predictedTimestamp - actualTimestamp);
		}

		System.out.println("MAE = " + difference / trips.size());
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