/*
 * Program predicts which trip the bus is following given a file showing
 * the most recent bus GPS travel history, and a directory containing all
 * possible bus trips. For more details about the format of input files,
 * look at the TripsExtractor class documentation.
 * 
 * EXAMPLE USAGE
 * 
 * // The following will print "trip2",
 * // if "trips_folder" contains files {trip1, trip2, trip3} and
 * // "gps_history_file" is most similar to some sub-trip of trip2.
 * java TripDetector gps_history_file trips_folder
 * 
 * // This should always print answer "trip1",
 * // because the history of trip1 matches trip1 exactly.
 * java TripDetector trips_folder/trip1 trips_folder
 */
package bus;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

class TripDetector {

	static double SIMILARITY_THRESHOLD = 1.2;

	/*
	 * Method finds a "best" segment and computes the Euclidean point's
	 * distance to both segment's corners. It returns the sum of 2 distances.
	 * Segment is "best" which minimises the returned value.
	 */
	static double errorToBestSegmentCorners(GpsPoint point,
			ArrayList<GpsPoint> trip) {
		double minDistance = Double.MAX_VALUE;
		for (int i = 1; i < trip.size(); i++) {
			minDistance = Math.min(minDistance,
					(Utils.Distance(point, trip.get(i - 1))
							+ Utils.Distance(point, trip.get(i)))
							/ Utils.Distance(trip.get(i - 1), trip.get(i)));
		}
		return minDistance;
	}

	/*
	 * The smaller similarity measure, the more similar tripsInterval to some
	 * interval of fullTrip is.
	 * 
	 * The intuition behind this measure is that when a bus is following the
	 * route exactly, each point in its history will lie on one of the route's
	 * segments, hence the point's distance to the "best" segment's corners will
	 * equal the "best" segment's length. If the bus is not following the route
	 * exactly, then point's distance to the "best" segment's corners due to
	 * triangle inequality will be larger.
	 */
	static double similarityMeasure(ArrayList<GpsPoint> tripsInterval,
			ArrayList<GpsPoint> fullTrip) {
		double accumulatedError = 0.0;
		for (GpsPoint point : tripsInterval) {
			accumulatedError += errorToBestSegmentCorners(point, fullTrip);
		}
		return accumulatedError / tripsInterval.size();
	}

	/* Finds trip t which minimises similarityMeasure(tripsInterval, t). */
	static Trip detectMostSimilarTrip(Trip tripsInterval, File allTripsFolder)
			throws IOException, ParseException {
		ArrayList<Trip> similarTrips = detectSimilarTrips(tripsInterval,
				allTripsFolder);
		Trip bestTrip = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (Trip currentTrip : similarTrips) {
			double currentMeasure = similarityMeasure(tripsInterval.gpsPoints,
					currentTrip.gpsPoints);
			if (currentMeasure < smallestMeasure) {
				smallestMeasure = currentMeasure;
				bestTrip = currentTrip;
			}
		}

		System.out.println("Best trip detected with measure " + smallestMeasure
				+ ": " + bestTrip.name);
		return bestTrip;
	}

	/* allTripsFolder should only contain CSV files */
	static ArrayList<Trip> detectSimilarTrips(Trip tripInterval,
			File allTripsFolder) throws IOException, ParseException {
		ArrayList<Trip> similarTrips = new ArrayList<Trip>();

		ArrayList<Trip> allTrips = Trip.extractTripsFromFolder(allTripsFolder);
		for (Trip trip : allTrips) {
			if (similarityMeasure(tripInterval.gpsPoints,
					trip.gpsPoints) < SIMILARITY_THRESHOLD) {
				System.out.println("For trip " + trip.name
						+ " similarity measure equals to " + similarityMeasure(
								tripInterval.gpsPoints, trip.gpsPoints));
				similarTrips.add(trip);
			}
		}

		return similarTrips;
	}

	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder");
		Trip tripInterval = new Trip(new File(args[0]));
		detectMostSimilarTrip(tripInterval, new File(args[1]));
	}

}