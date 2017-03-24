package bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;

public class ArrivalTimePredictor {
	private static final double DISTANCE_BOUND = 0.00004;
	private static final long DURATION_DIFFERENCE_LIMIT = 80L;
	private static final long RECENT_INTERVAL = 3000L;

	/* Main method which predicts arrival time to bus stop */
	static long calculatePredictionToBusStop(
			Function<GpsPoint, Boolean> atBusStop, Trip tripToPredict,
			ArrayList<Trip> historicalTrips) throws ProjectSpecificException {
		ArrayList<Trip> predictions = generatePredictions(tripToPredict,
				historicalTrips);
		return calculateAverageArrivalTime(tripToPredict.name, predictions,
				atBusStop);
	}

	private static boolean endPointsMatch(Trip trip, Trip subTrip) {
		GpsPoint subTripFirstPoint = subTrip.gpsPoints.get(0);
		GpsPoint subTripLastPoint = subTrip.lastPoint();
		GpsPoint tripFirstPoint = trip.gpsPoints.get(0);
		GpsPoint tripLastPoint = trip.lastPoint();

		return (Utils.distance(subTripFirstPoint,
				tripFirstPoint) < DISTANCE_BOUND)
				&& (Utils.distance(subTripLastPoint,
						tripLastPoint) < DISTANCE_BOUND);
	}

	/*
	 * Heuristic which checks whether roughly equal amount of distance was
	 * travelled in roughly equal amount of time.
	 */
	private static boolean equallyCongested(Trip trip, Trip subTrip) {
		long durationDifference = trip.duration() - subTrip.duration();
		return (Math.abs(durationDifference) > DURATION_DIFFERENCE_LIMIT)
				&& endPointsMatch(trip, subTrip);
	}

	/* Finds point in trip that was closest to the mostRecentPoint */
	private static int closestPointIndex(GpsPoint mostRecentPoint, Trip trip) {
		int closestPointIndex = -1;
		double closestPointDistance = Double.MAX_VALUE;
		for (int i = 0; i < trip.gpsPoints.size(); i++) {
			double newDistance = Utils.distance(mostRecentPoint,
					trip.gpsPoints.get(i));
			if (newDistance < closestPointDistance) {
				closestPointIndex = i;
				closestPointDistance = newDistance;
			}
		}
		return closestPointIndex;
	}

	private static Trip generateSubtrip(Trip recentTrip, Trip trip)
			throws ProjectSpecificException {
		int closestPointIndex = closestPointIndex(recentTrip.lastPoint(), trip);
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

	static Trip generateFuturePrediction(Trip recentTrip, Trip trip)
			throws ProjectSpecificException {
		int closestPointIndex = closestPointIndex(recentTrip.lastPoint(), trip);
		if (closestPointIndex + 1 == trip.gpsPoints.size()) {
			throw new ProjectSpecificException(
					"No future points for trip " + trip.name);
		}

		ArrayList<GpsPoint> predictionPoints = new ArrayList<GpsPoint>(
				trip.gpsPoints.subList(closestPointIndex + 1,
						trip.gpsPoints.size()));
		long adjustedTime = recentTrip.lastPoint().timestamp
				+ trip.gpsPoints.get(closestPointIndex + 1).timestamp
				- trip.gpsPoints.get(closestPointIndex).timestamp;

		return new Trip(trip.name, predictionPoints).shiftTimeTo(adjustedTime);
	}

	/*
	 * Finds all equally congested trips with the recentTrip, and for each
	 * equally congested trip t, uses its interval as prediction (except for the
	 * case where t has no further GPS points to be used for prediction).
	 */
	private static ArrayList<Trip> generatePredictions(Trip recentTrip,
			ArrayList<Trip> trips) {
		System.out.println("For " + recentTrip.name + " we have " + trips.size()
				+ " historical trips");
		
		ArrayList<Trip> predictions = new ArrayList<Trip>();

		for (Trip trip : trips) {
			try {
				Trip subTrip = generateSubtrip(recentTrip, trip);
				Trip predictedTrip = generateFuturePrediction(recentTrip, trip);

				String recent = recentTrip.lastPoint().timestamp
						- subTrip.lastPoint().timestamp < RECENT_INTERVAL
								? "_recent_" : "_old_";
				String congested = equallyCongested(recentTrip, subTrip)
						? "equally_congested" : "differently_congested";

				predictions.add(predictedTrip.makeCopyWithNewName(
						predictedTrip.name + recent + congested));
			} catch (ProjectSpecificException exception) {
			}
		}

		return predictions;
	}

	private static long averageArrivalTimestamp(
			ArrayList<Long> arrivalTimestamps) {
		Collections.sort(arrivalTimestamps);
		return arrivalTimestamps.get(arrivalTimestamps.size() / 2);
	}

	private static long calculateAverageArrivalTime(String tripName,
			ArrayList<Trip> predictions, Function<GpsPoint, Boolean> atBusStop)
					throws ProjectSpecificException {
		ArrayList<Long> oldDifferentlyCongested = new ArrayList<Long>();
		ArrayList<Long> oldEquallyCongested = new ArrayList<Long>();
		ArrayList<Long> newDifferentlyCongested = new ArrayList<Long>();
		ArrayList<Long> newEquallyCongested = new ArrayList<Long>();

		for (Trip prediction : predictions) {
			for (GpsPoint point : prediction.gpsPoints) {
				if (atBusStop.apply(point)) {
					if (prediction.name.contains("old")
							&& prediction.name.contains("differently")) {
						oldDifferentlyCongested.add(point.timestamp);
					}
					if (prediction.name.contains("old")
							&& prediction.name.contains("equally")) {
						oldEquallyCongested.add(point.timestamp);
					}
					if (prediction.name.contains("recent")
							&& prediction.name.contains("differently")) {
						newDifferentlyCongested.add(point.timestamp);
					}
					if (prediction.name.contains("recent")
							&& prediction.name.contains("equally")) {
						newEquallyCongested.add(point.timestamp);
					}
					break;
				}
			}
		}

		if (newEquallyCongested.size() > 0) {
			return averageArrivalTimestamp(newEquallyCongested);
		}
		if (oldEquallyCongested.size() > 0) {
			return averageArrivalTimestamp(oldEquallyCongested);
		}
		if (newDifferentlyCongested.size() > 0) {
			return averageArrivalTimestamp(newDifferentlyCongested);
		}
		if (oldDifferentlyCongested.size() == 0) {
			throw new ProjectSpecificException(
					"Failed to generate prediction for " + tripName);
		}
		return averageArrivalTimestamp(oldDifferentlyCongested);
	}
}
