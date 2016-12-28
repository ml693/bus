package bus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/*
 * A class which contains main method to run anything we want. If one wants to
 * to check something, he can use this class, so that other classes would not
 * get 'polluted' with arbitrary code.
 */
class ArbitraryCodeExecutor {

	static boolean atMadingleyPark(GpsPoint point) {
		return (point.latitude >= 52.2144 && point.latitude <= 52.2147
				&& point.longitude >= 0.0830 && point.longitude <= 0.0840);
	}

	static boolean stoppedAtMadingleyPark(Trip trip) {
		for (GpsPoint point : trip.gpsPoints) {
			if (atMadingleyPark(point)) {
				return true;
			}
		}
		return false;
	}

	static Long madingleyStopTimestamp(Trip trip) {
		for (GpsPoint point : trip.gpsPoints) {
			if (atMadingleyPark(point)) {
				return point.timestamp;
			}
		}
		return -1L;
	}

	static void extractThroughMadingley(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder");
		File inputFolder = new File(args[0]);
		File[] tripFiles = inputFolder.listFiles();
		File outputFolder = new File(args[1]);
		for (File tripFile : tripFiles) {
			Trip trip = new Trip(tripFile);
			System.out.println("Processing trip " + trip.name);
			if (stoppedAtMadingleyPark(trip)) {
				System.out.println("This trip stopped at Madingley Park");
				trip.writeToFolder(outputFolder);
			}
		}
	}

	static void extractGoodTripsThroughMadingley(String[] args)
			throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder", "folder");

		Trip profileTripThroughMadingley = new Trip(new File(args[0]));
		File allTripsFolder = new File(args[1]);
		ArrayList<Trip> goodTrips = TripDetector.detectSimilarTrips(
				profileTripThroughMadingley, allTripsFolder);

		File outputFolder = new File(args[2]);
		for (Trip goodTrip : goodTrips) {
			goodTrip.writeToFolder(outputFolder);
		}
	}

	static Long calculateAverageArrivalTime(ArrayList<Trip> predictions)
			throws Exception {
		ArrayList<Long> stoppingTimes = new ArrayList<Long>();
		for (Trip prediction : predictions) {
			for (GpsPoint point : prediction.gpsPoints) {
				if (atMadingleyPark(point)) {
					stoppingTimes.add(point.timestamp);
					break;
				}
			}
		}
		if (stoppingTimes.size() == 0) {
			throw new Exception("Failed to predict");
		}
		
		Collections.sort(stoppingTimes);
		return stoppingTimes.get(stoppingTimes.size() / 2);
	}

	static Long predictForMadingley(Trip tripToPredict,
			ArrayList<Trip> historicalTrips) throws Exception {
		ArrayList<Trip> predictions = FuturePredictor
				.generatePredictionsFromEquallyCongestedTrips(tripToPredict,
						historicalTrips);
		return calculateAverageArrivalTime(predictions);
	}

	static boolean closeEnough(GpsPoint p1, GpsPoint p2) {
		return (Utils.squaredDistance(p1, p2) < 0.000001);
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
				Long delimitArrivalTime = trip.gpsPoints.get(index).timestamp;
				while (index < trip.gpsPoints.size()
						&& !atMadingleyPark(trip.gpsPoints.get(index))
						&& (trip.gpsPoints.get(index).timestamp
								- delimitArrivalTime) < 1800) {
					index++;
				}
				if (index < trip.gpsPoints.size()) {
					Long timeDifference = (trip.gpsPoints.get(index).timestamp
							- delimitArrivalTime);
					if (timeDifference < 1800) {
						return delimitingIndex + 1;
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
			}
		}
	}

	public static void evaluatePredictionToMadingley(String[] args)
			throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<Trip> historicalTrips = Trip
				.extractTripsFromFolder(new File(args[0]));
		ArrayList<Trip> recentTrips = Trip
				.extractTripsFromFolder(new File(args[1]));

		Long accumulatedAbsoluteError = 0L;
		Long accumulatedError = 0L;

		for (Trip recentTrip : recentTrips) {
			Long predictedTimestamp = predictForMadingley(recentTrip,
					historicalTrips);
			Trip futureTrip = new Trip(
					new File(args[2] + "/" + recentTrip.name));
			Long actualTimestamp = madingleyStopTimestamp(futureTrip);
			Long error = actualTimestamp - predictedTimestamp;
			accumulatedError += error;
			accumulatedAbsoluteError += Math.abs(error);

			System.out.println(recentTrip.name + " was predicted for "
					+ Utils.convertTimestampToDate(predictedTimestamp)
					+ ", actually arrived at "
					+ Utils.convertTimestampToDate(actualTimestamp)
					+ ", prediction error is " + error);
		}
		System.out.println(
				"MAE = " + accumulatedAbsoluteError / recentTrips.size());
		System.out.println(
				"Average error = " + accumulatedError / recentTrips.size());
	}

	public static void main(String[] args) throws Exception {
		evaluatePredictionToMadingley(args);
	}
}