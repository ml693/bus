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
import bus.Utils.Trip;
import bus.Utils.TravelHistory;

class TripDetector {
	/*
	 * When down-casted, the method can return either an instance of Trip or
	 * TravelHistory class, depending on what we want.
	 */
	static ArrayList<GpsPoint> PointsFromFile(File file) throws IOException {
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
	 * The smaller similarity measure, the more similar travelHistory to some
	 * interval of trip is.
	 * 
	 * The similarity measure works as follows. For each point p in the travel
	 * history, we find the "best" segment in the route (two consecutive route's
	 * points) and sum p distance to both segment's corners. "Best" segment
	 * is the one which has the minimal sum. We then return sum of sums ranged
	 * over all points in the travel history.
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
	static double SimilarityMeasure(TravelHistory travelHistory, Trip trip) {
		double measure = 0.0;
		for (GpsPoint point : travelHistory) {
			measure += DistanceSumToBestSegmentCorners(point, trip);
		}
		return measure;
	}

	static double DistanceSumToBestSegmentCorners(GpsPoint point, Trip trip) {
		double minDistance = Double.MAX_VALUE;
		for (int i = 1; i < trip.size(); i++) {
			minDistance = Math.min(minDistance,
					Utils.Distance(point, trip.get(i - 1))
							+ Utils.Distance(point, trip.get(i)));
		}
		return minDistance;
	}

	public static void main(String args[]) throws IOException {
		TravelHistory recentHistory = (TravelHistory) PointsFromFile(
				new File(args[0]));
		File[] tripFiles = new File(args[1]).listFiles();
		File bestTripFile = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (File tripFile : tripFiles) {
			Trip trip = (Trip) PointsFromFile(tripFile);
			double currentMeasure = SimilarityMeasure(recentHistory, trip);
			if (currentMeasure < smallestMeasure) {
				smallestMeasure = currentMeasure;
				bestTripFile = tripFile;
			}
		}

		System.out.println(bestTripFile.getName());
	}

}