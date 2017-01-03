/*
 * Program determines which trip the bus is following given a file showing
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

	static double SIMILARITY_THRESHOLD = 4f;

	static private double DISTANCE_TOO_SMALL_TO_CONSIDER = 0.0005f;
	static private double SIGNIFICANT_RATIO_THRESHOLD = 1.1f;

	/*
	 * Method finds a "best" segment and computes the Euclidean point's distance
	 * to both segment's corners. Returns the ratio (a + b) / c, where a and b
	 * are distances to segment's corners, c is the segment's length. Segment is
	 * "best" which minimises the returned value. For GPS point very close to
	 * the segment we return 1 instead of a ratio.
	 */
	private static double ratioToBestSegmentCorners(GpsPoint point, Trip trip) {
		double minError = Double.MAX_VALUE;
		for (int i = 1; i < trip.gpsPoints.size(); i++) {
			double distanceToCorners = Utils.Distance(point,
					trip.gpsPoints.get(i - 1))
					+ Utils.Distance(point, trip.gpsPoints.get(i));
			if (distanceToCorners <= DISTANCE_TOO_SMALL_TO_CONSIDER) {
				return 1.0;
			}

			double ratioError = distanceToCorners / Utils
					.Distance(trip.gpsPoints.get(i - 1), trip.gpsPoints.get(i));
			if (ratioError < SIGNIFICANT_RATIO_THRESHOLD) {
				return 1.0;
			}
			minError = Math.min(minError, ratioError);
		}
		return minError;
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
	static double similarityMeasure(Trip tripsInterval, Trip fullTrip) {
		double accumulatedError = 0.0;
		/*
		 * We ignore first and last points in case tripsInterval is exactly the
		 * start or exactly the end of the fullTrip. It's because there is no
		 * good segment to align first (or last) point with, and that will
		 * introduce unnecessary errors.
		 */
		for (int p = 1; p < tripsInterval.gpsPoints.size() - 1; p++) {
			accumulatedError += ratioToBestSegmentCorners(
					tripsInterval.gpsPoints.get(p), fullTrip);
		}
		return accumulatedError - (tripsInterval.gpsPoints.size() - 2);
	}

	/* Finds trip t which minimises similarityMeasure(tripsInterval, t). */
	static Trip detectMostSimilarTrip(Trip tripsInterval, File allTripsFolder)
			throws IOException, ParseException, ProjectSpecificException {
		ArrayList<Trip> similarTrips = detectSimilarTrips(tripsInterval,
				allTripsFolder);
		Trip bestTrip = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (Trip trip : similarTrips) {
			double currentMeasure = similarityMeasure(tripsInterval, trip);
			if (currentMeasure < smallestMeasure) {
				smallestMeasure = currentMeasure;
				bestTrip = trip;
			}
		}

		if (bestTrip == null) {
			throw new ProjectSpecificException(
					"No similar trips found for " + tripsInterval.name);
		} else {
			System.out.println("Best trip detected with measure "
					+ smallestMeasure + ": " + bestTrip.name);
			return bestTrip;
		}
	}

	/* allTripsFolder should only contain CSV files */
	static ArrayList<Trip> detectSimilarTrips(Trip tripInterval,
			File allTripsFolder) throws IOException, ParseException {
		ArrayList<Trip> similarTrips = new ArrayList<Trip>();
		ArrayList<Trip> allTrips = Trip.extractTripsFromFolder(allTripsFolder);
		for (Trip trip : allTrips) {
			if (similarityMeasure(tripInterval, trip) < SIMILARITY_THRESHOLD) {
				System.out.println(trip.name + " is similar!");
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