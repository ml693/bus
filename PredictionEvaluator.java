// TODO(ml693): make code cleaner.
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

/*
 * A class which contains methods to evaluate (i.e. calculate statistics)
 * how well the prediction algorithm performs.
 */
class PredictionEvaluator {

	public static void main(String[] args) throws ProjectSpecificException {
		evaluateRoute(args);
		produceCsvForPlotting();
	}

	public static void produceCsvForPlotting() throws ProjectSpecificException {
		Scanner scanner = Utils.csvScanner(new File("counts.txt"));
		scanner.nextLine();

		int[][] errorCount = new int[7][2000];
		while (scanner.hasNext()) {
			errorCount[scanner.nextInt()][scanner.nextInt()]++;
		}

		BufferedWriter writer = Utils.writer("plot.txt");
		Utils.writeLine(writer, "stop_number,error,count");
		for (int i = 0; i < 7; i++) {
			for (int error = 0; error < 2000; error++) {
				if (errorCount[i][error] > 0) {
					Utils.writeLine(writer,
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
		BufferedWriter writer = Utils.writer(args[2]);

		for (int stop = 0; stop < route.busStops.size() - 1; stop++) {
			System.out.println("For stop number " + stop + " results are:");
			evaluateRoute(route, tripsFolder, stop, writer);
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
			int fromStopNumber, BufferedWriter writer)
					throws ProjectSpecificException {
		Utils.writeLine(writer,
				"Predicting when trips will reach " + route.lastStop().name
						+ " (nr. " + route.busStops.size() + ") from "
						+ route.busStops.get(fromStopNumber).name + " (nr. "
						+ (fromStopNumber + 1) + ")");

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

			Utils.writeLine(writer,
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

		Utils.writeLine(writer, "MAE = " + difference / trips.size());
		Utils.writeLine(writer, "");

		System.out.println("MAE = " + difference / trips.size());
		System.out.println("Delay = " + delaysSum / trips.size());
		System.out.println("Prediction count = " + trips.size());
		System.out.println();
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

}