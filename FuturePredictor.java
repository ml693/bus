/*
 * Not finished class. It takes input file showing the most recent sub trip of a
 * bus. So far it finds all similar sub trips ever made by buses in the past and
 * generates files telling how those buses were travelling further.
 */
package bus;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

public class FuturePredictor {

	private static final double CONGESTION_THRESHOLD = 2.2;
	private static final double DISTANCE_BOUND = 0.000003;
	private static final long DURATION_DIFFERENCE_LIMIT = 80;

	private static boolean endPointsMatch(Trip subTrip, Trip trip) {
		GpsPoint subTripFirstPoint = subTrip.gpsPoints.get(0);
		GpsPoint subTripLastPoint = subTrip.lastPoint();
		GpsPoint tripFirstPoint = trip.gpsPoints.get(0);
		GpsPoint tripLastPoint = trip.lastPoint();

		return (Utils.squaredDistance(subTripFirstPoint,
				tripFirstPoint) < DISTANCE_BOUND)
				&& (Utils.squaredDistance(subTripLastPoint,
						tripLastPoint) < DISTANCE_BOUND);
	}

	/*
	 * Heuristic which checks whether roughly equal amount of distance was
	 * travelled in roughly equal amount of time.
	 */
	static boolean equallyCongested(Trip subtrip, Trip trip) {
		Long durationDifference = trip.duration() - subtrip.duration();
		if (Math.abs(durationDifference) < DURATION_DIFFERENCE_LIMIT) {
			return false;
		}
		/* TODO(ml693): replace sum of 2 similarity measures by one value. */
		return TripDetector.similarityMeasure(subtrip.gpsPoints, trip.gpsPoints)
				+ TripDetector.similarityMeasure(trip.gpsPoints,
						subtrip.gpsPoints) < CONGESTION_THRESHOLD
				&& endPointsMatch(subtrip, trip);
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

		return (closestPointIndex + 1 == trip.gpsPoints.size()) ? null :

		new Trip(trip.name,
				new ArrayList<GpsPoint>(trip.gpsPoints
						.subList(closestPointIndex + 1, trip.gpsPoints.size())))
								.shiftTimeTo(recentTrip.lastPoint().timestamp
										+ trip.gpsPoints.get(
												closestPointIndex + 1).timestamp
										- trip.gpsPoints.get(
												closestPointIndex).timestamp);
	}

	static ArrayList<Trip> generatePredictionsFromEquallyCongestedTrips(
			Trip recentTrip, ArrayList<Trip> trips)
					throws IOException, ParseException {
		ArrayList<Trip> predictedTrips = new ArrayList<Trip>();
		for (Trip trip : trips) {
			Trip subTrip = generateSubtrip(recentTrip, trip);
			if (equallyCongested(subTrip, recentTrip)) {
				/* TODO(ml693): remove this line after debugging */
				subTrip.shiftTimeTo(recentTrip.gpsPoints.get(0).timestamp)
						.writeToFolder("equally_congested");
				Trip predictedTrip = generateFuturePrediction(recentTrip, trip);
				if (predictedTrip != null) {
					predictedTrips.add(predictedTrip);
				}
			}
		}
		return predictedTrips;
	}
}
