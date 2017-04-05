// TODO(ml693): make code cleaner.
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.function.Function;

/*
 * A class which contains methods to evaluate (i.e. calculate statistics)
 * how well the prediction algorithm performs.
 */
class PredictionEvaluator {

	public static void main(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[0]));

		for (Route route : routes) {
			File tripsFolder = new File(args[1] + "/" + route.name);
			if (!tripsFolder.exists()) {
				continue;
			}

			BufferedWriter writer = Utils.writer(args[2] + "/" + route.name);

			Utils.writeLine(writer, "The morning rush hour");
			evaluateRouteForCertainTime(t -> correctMorningTime(t), route,
					tripsFolder, writer);
			Utils.writeLine(writer, "");
			Utils.writeLine(writer, "The working hours");
			evaluateRouteForCertainTime(t -> correctDayTime(t), route,
					tripsFolder, writer);
			Utils.writeLine(writer, "");
			Utils.writeLine(writer, "The evening rush hour");
			evaluateRouteForCertainTime(t -> correctEveningTime(t), route,
					tripsFolder, writer);
			Utils.writeLine(writer, "");
			Utils.writeLine(writer, "The night time");
			evaluateRouteForCertainTime(t -> correctNightTime(t), route,
					tripsFolder, writer);

			writer.close();
		}
	}

	static boolean correctMorningTime(long timestamp) {
		return correctTime("08", timestamp) || correctTime("09", timestamp);
	}

	static boolean correctDayTime(long timestamp) {
		return correctTime("10", timestamp) || correctTime("11", timestamp)
				|| correctTime("12", timestamp) || correctTime("13", timestamp)
				|| correctTime("14", timestamp) || correctTime("15", timestamp)
				|| correctTime("16", timestamp);
	}

	static boolean correctEveningTime(long timestamp) {
		return correctTime("17", timestamp) || correctTime("18", timestamp)
				|| correctTime("19", timestamp);
	}

	static boolean correctNightTime(long timestamp) {
		return !correctMorningTime(timestamp) && !correctDayTime(timestamp)
				&& !correctEveningTime(timestamp);
	}

	private static boolean correctTime(String time, long timestamp) {
		return Utils.convertTimestampToDate(timestamp).substring(11, 13)
				.equals(time);
	}

	public static ArrayList<Trip> getTripsOfCertainTime(
			Function<Long, Boolean> correctTime, File tripsFolder) {
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(tripsFolder);
		ArrayList<Trip> tripsOfCertainTime = new ArrayList<Trip>();

		for (Trip trip : trips) {
			if (correctTime.apply(trip.firstPoint().timestamp)) {
				tripsOfCertainTime.add(trip);
			}
		}

		return tripsOfCertainTime;
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

	static void removeTrip(ArrayList<Trip> trips, int removeIndex) {
		trips.set(removeIndex, trips.get(trips.size() - 1));
		trips.remove(trips.size() - 1);
	}

	static void addTripBack(ArrayList<Trip> trips, Trip trip, int setIndex) {
		if (setIndex == trips.size()) {
			trips.add(trip);
		} else {
			trips.add(trips.get(setIndex));
			trips.set(setIndex, trip);
		}
	}

	static long lastStopTimestamp(Route route, Trip trip)
			throws ProjectSpecificException {
		for (GpsPoint point : trip.gpsPoints) {
			if (route.atLastStop(point)) {
				return point.timestamp;
			}
		}
		throw new ProjectSpecificException(
				"No point passed through last stop for " + trip.name);
	}

	public static void evaluateRouteForCertainTime(
			Function<Long, Boolean> correctTime, Route route, File tripsFolder,
			BufferedWriter writer) throws ProjectSpecificException {
		ArrayList<Trip> trips = getTripsOfCertainTime(correctTime, tripsFolder);
		System.out.println("Will evaluate for route " + route.name + " using "
				+ trips.size() + " trips.");

		for (int stop = 0; stop < route.busStops.size() - 1; stop++) {
			System.out.println("Evaluating for stop nr. " + stop);
			Utils.writeLine(writer, "From stop nr. " + stop);
			Utils.writeLine(writer, route.busStops.get(stop).name);
			if (trips.size() < 30) {
				Utils.writeLine(writer, "not enough trips");
				Utils.writeLine(writer, "");
			} else {
				evaluateRoute(route, trips, stop, writer);
			}
		}
	}

	/* Computes various statistics for the arrival time to the last stop */
	public static void evaluateRoute(Route route, ArrayList<Trip> trips,
			int stop, BufferedWriter writer) throws ProjectSpecificException {
		ArrayList<Trip> shortTrips = new ArrayList<Trip>();
		for (Trip trip : trips) {
			shortTrips.add(upToStop(stop, trip, route));
		}

		long difference = 0;
		long delaysSum = 0;
		for (int t = trips.size() - 1; t >= 0; t--) {
			Trip trip = trips.get(t);
			removeTrip(trips, t);

			long predictedTimestamp = ArrivalTimePredictor.makePrediction(
					route.lastStop(), shortTrips.get(t),
					trips).predictedTimestamp;
			long actualTimestamp = PredictionEvaluator.lastStopTimestamp(route,
					trip);
			long predictionError = Math
					.abs(actualTimestamp - predictedTimestamp);
			difference += predictionError;
			delaysSum += actualTimestamp
					- shortTrips.get(t).lastPoint().timestamp;

			addTripBack(trips, trip, t);
		}

		Utils.writeLine(writer, "MAE = " + difference / trips.size());
		Utils.writeLine(writer, "ATT = " + delaysSum / trips.size());
		Utils.writeLine(writer, "");
	}

	public static void produceCsvForPlotting() throws ProjectSpecificException {
		Scanner scanner = Utils.csvScanner(new File("uk/counts.csv"));
		scanner.nextLine();

		int[][] errorCount = new int[7][2000];
		while (scanner.hasNext()) {
			errorCount[scanner.nextInt()][scanner.nextInt()]++;
		}

		BufferedWriter writer = Utils.writer("uk/plot.csv");
		Utils.writeLine(writer, "stop_number,error,count");
		for (int i = 0; i < 7; i++) {
			for (int error = 0; error < 2000; error++) {
				if (errorCount[i][error] > 0) {
					Utils.writeLine(writer,
							i + "," + error + "," + errorCount[i][error]);
				}
			}
		}

		try {
			writer.close();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

}