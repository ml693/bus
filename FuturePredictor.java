/*
 * Not finished class. It takes input file showing the most recent sub trip of a
 * bus. So far it finds all similar sub trips ever made by buses in the past and
 * generates files telling how those buses were travelling further.
 */
package bus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import bus.GpsPoint;

public class FuturePredictor {

	static final double CONGESTION_THRESHOLD = 0.15;

	/*
	 * Heuristic which checks whether roughly equal amount of distance was
	 * travelled in roughly equal amount of time.
	 */
	static boolean equallyCongested(Trip subtrip, Trip trip) {
		return TripDetector.similarityMeasure(subtrip.gpsPoints, trip.gpsPoints)
				+ TripDetector.similarityMeasure(trip.gpsPoints,
						subtrip.gpsPoints) < CONGESTION_THRESHOLD;
	}

	/* Finds point in trip that was closest to the last point of recentTrip */
	static int closestPointIndex(Trip recentTrip, Trip trip) {
		GpsPoint mostRecentPoint = recentTrip.lastPoint();
		int closestPointIndex = 0;
		double closestPointDistance = Utils.squaredDistance(mostRecentPoint,
				trip.gpsPoints.get(0));
		for (int i = 1; i < trip.gpsPoints.size(); i++) {
			double newDistance = Utils.squaredDistance(mostRecentPoint,
					trip.gpsPoints.get(i));
			if (newDistance < closestPointDistance) {
				closestPointIndex = i;
				closestPointDistance = newDistance;
			}
		}
		return closestPointIndex;
	}

	static Trip generateSubtrip(Trip recentTrip, Trip trip) {
		int closestPointIndex = closestPointIndex(recentTrip, trip);
		long timeFrame = recentTrip.duration();
		long tripTimestamp = trip.gpsPoints.get(closestPointIndex).timestamp;
		int index = closestPointIndex;

		while (index > 0 && tripTimestamp
				- trip.gpsPoints.get(index).timestamp < timeFrame) {
			index--;
		}

		return new Trip(trip.name, new ArrayList<GpsPoint>(
				trip.gpsPoints.subList(index, closestPointIndex + 1)));
	}

	static Trip generateFuturePrediction(Trip recentTrip, Trip trip) {
		int closestPointIndex = closestPointIndex(recentTrip, trip);
		long timeFrame = recentTrip.duration();
		long tripTimestamp = trip.gpsPoints.get(closestPointIndex).timestamp;
		int index = closestPointIndex + 1;

		while (index < trip.gpsPoints.size()
				&& trip.gpsPoints.get(index).timestamp
						- tripTimestamp < timeFrame) {
			index++;
		}

		return new Trip(trip.name,
				new ArrayList<GpsPoint>(
						trip.gpsPoints.subList(closestPointIndex + 1, index)))
								.shiftTimeTo(tripTimestamp);
	}

	static ArrayList<Trip> predictEquallyCongestedTrips(Trip recentTrip,
			ArrayList<Trip> trips) throws IOException {
		ArrayList<Trip> predictedTrips = new ArrayList<Trip>();
		for (Trip trip : trips) {
			Trip subTrip = generateSubtrip(recentTrip, trip);
			if (equallyCongested(subTrip, recentTrip)) {
				/* TODO(ml693): remove this line after debugging */
				subTrip.writeToFolder("equally_congested");

				predictedTrips.add(generateFuturePrediction(recentTrip, trip));
			}
		}
		return predictedTrips;
	}

	public static void main(String[] args) throws IOException {
		Utils.check2CommandLineArguments(args, "file", "folder");
		Trip recentTrip = new Trip(new File(args[0]));

		/* First find buses that were travelling along the same route. */
		ArrayList<Trip> similarTrips = TripDetector
				.detectSimilarTrips(recentTrip, new File(args[1]));

		/* Then filter those trips out that were much faster or slower */
		ArrayList<Trip> predictedTrips = predictEquallyCongestedTrips(
				recentTrip, similarTrips);

		for (Trip predictedTrip : predictedTrips) {
			predictedTrip.writeToFolder("future_predictions");
		}
	}
}
