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
import java.util.ArrayList;

import bus.GpsPoint;

class TripDetector {

	static double SIMILARITY_THRESHOLD = 0.1;

	/*
	 * Method finds a "best" segment and computes the Euclidean point's
	 * distance to both segment's corners. It returns the sum of 2 distances.
	 * Segment is "best" which minimises the returned value.
	 */
	static double DistanceSumToBestSegmentCorners(GpsPoint point,
			ArrayList<GpsPoint> trip) {
		double minDistance = Double.MAX_VALUE;
		for (int i = 1; i < trip.size(); i++) {
			minDistance = Math.min(minDistance,
					Utils.Distance(point, trip.get(i - 1))
							+ Utils.Distance(point, trip.get(i)));
		}
		return minDistance;
	}

	/*
	 * Method finds a "best" segment (as above), and instead of returning the
	 * distance, returns the index to the first segment's corner.
	 */
	static int BestSegment(GpsPoint point, ArrayList<GpsPoint> trip) {
		double minDistance = Double.MAX_VALUE;
		int bestIndex = 0;
		for (int i = 1; i < trip.size(); i++) {
			double newDistance = Utils.Distance(point, trip.get(i - 1))
					+ Utils.Distance(point, trip.get(i));
			if (newDistance < minDistance) {
				minDistance = newDistance;
				bestIndex = i - 1;
			}
		}
		return bestIndex;
	}

	/*
	 * The smaller similarity measure, the more similar travelHistory to some
	 * interval of trip is.
	 * 
	 * The intuition behind this measure is that when a bus is following the
	 * route exactly, each point in its history will lie on one of the route's
	 * segments, hence the point's distance to the "best" segment's corners will
	 * equal the "best" segment's length. If the bus is not following the route
	 * exactly, then point's distance to the "best" segment's corners due to
	 * triangle inequality will be larger.
	 *
	 * TODO(ml693): improve the SimilarityMeasure procedure for unusual cases.
	 */
	static double SimilarityMeasure(ArrayList<GpsPoint> tripPoints,
			ArrayList<GpsPoint> profilePoints) {
		double measure = 0.0;
		for (GpsPoint point : tripPoints) {
			measure += DistanceSumToBestSegmentCorners(point, profilePoints);
		}
		return measure;
	}

	/*
	 * TODO(ml693): replace this code by
	 * similarTrips = DetectSimilarTrips();
	 * for (trip : similarTrips) {
	 * if (SimilarityMeasure(trip) < bestMeasure) {
	 * bestTrip = trip;
	 * }
	 * }
	 */
	static Trip detectMostSimilarTrip(Trip travelHistory, File allTripsFolder)
			throws IOException {
		File[] tripFiles = allTripsFolder.listFiles();
		Trip bestTrip = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (File tripFile : tripFiles) {
			Trip currentTrip = new Trip(tripFile);
			double currentMeasure = SimilarityMeasure(travelHistory.gpsPoints,
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

	/* allTripsFolder will only contain CSV files */

	static ArrayList<Trip> detectSimilarTrips(Trip travelHistory,
			File allTripsFolder) throws IOException {
		ArrayList<Trip> similarTrips = new ArrayList<Trip>();
		File[] filesInFolder = allTripsFolder.listFiles();
		for (File file : filesInFolder) {
			Trip trip = new Trip(file);
			System.out.println("Detecting similarity for trip " + trip.name);
			if (SimilarityMeasure(travelHistory.gpsPoints,
					trip.gpsPoints) < SIMILARITY_THRESHOLD) {
				similarTrips.add(trip);
			}
		}
		return similarTrips;
	}

	public static void main(String args[]) throws IOException {
		Utils.CheckCommandLineArguments(args);
		Trip travelHistory = new Trip(new File(args[0]));
		detectMostSimilarTrip(travelHistory, new File(args[1]));
	}

}