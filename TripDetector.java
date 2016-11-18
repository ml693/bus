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
import java.util.Scanner;

import bus.Utils.GpsPoint;

class TripDetector {

	/*
	 * This should be used to extract either a travel history or a trip.
	 * WARNING: trip and travel history in this project are viewed as different
	 * things, although they both are ArrayList<GpsPoint>.
	 * TODO(ml693): figure out how to merge trip and history into 1 thing.
	 * 
	 * Travel history is bus GPS history for some time interval.
	 * Suppose a bus started to travel from point A and after 2 hours it has
	 * travelled through points B and C. Then all of {ABC, AB, BC, A, B, C}
	 * would be valid histories:
	 *
	 * Trip is either a past path that a bus followed
	 * WITHOUT PAUSING (short break at the bus stop does not count as pause) or
	 * a predetermined future route that a bus SHOULD FOLLOW. For example,
	 * Cambridge - London - back to Cambridge would be a trip.
	 * 
	 * It's often the case that the bus does NOT FOLLOW any trip exactly, hence
	 * the travel history tells the exact path a bus followed for
	 * some interval of time.
	 */
	static ArrayList<GpsPoint> GpsPointsFromFile(File file) throws IOException {
		ArrayList<GpsPoint> points = new ArrayList<GpsPoint>();
		Scanner scanner = new Scanner(file).useDelimiter(",|\\n");
		/* To skip "timestamp,latitude,longitude" line */
		scanner.nextLine();
		while (scanner.hasNext()) {
			points.add(new GpsPoint(scanner.nextInt(), scanner.nextDouble(),
					scanner.nextDouble()));
		}
		scanner.close();
		return points;
	}

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
	static double SimilarityMeasure(ArrayList<GpsPoint> travelHistory,
			ArrayList<GpsPoint> trip) {
		double measure = 0.0;
		for (GpsPoint point : travelHistory) {
			measure += DistanceSumToBestSegmentCorners(point, trip);
		}
		return measure;
	}

	public static void main(String args[]) throws IOException {
		ArrayList<GpsPoint> travelHistory = GpsPointsFromFile(
				new File(args[0]));
		File[] tripFiles = new File(args[1]).listFiles();
		File bestTripFile = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (File tripFile : tripFiles) {
			ArrayList<GpsPoint> trip = GpsPointsFromFile(tripFile);
			double currentMeasure = SimilarityMeasure(travelHistory, trip);
			if (currentMeasure < smallestMeasure) {
				smallestMeasure = currentMeasure;
				bestTripFile = tripFile;
			}
		}

		System.out.println(bestTripFile.getName());
	}

}