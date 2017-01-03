/*
 * Not finished class. It takes input file showing the most recent sub trip of a
 * bus. So far it finds all similar sub trips ever made by buses in the past and
 * generates files telling how those buses were travelling further.
 */
package bus;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;

public class FuturePredictor {
	private static final double DISTANCE_BOUND = 0.00004;
	private static final long DURATION_DIFFERENCE_LIMIT = 80L;
	private static final long RECENT_INTERVAL = 2400L;

	private static boolean endPointsMatch(Trip trip, Trip subTrip) {
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
	private static boolean equallyCongested(Trip trip, Trip subTrip) {
		long durationDifference = trip.duration() - subTrip.duration();
		if (Math.abs(durationDifference) > DURATION_DIFFERENCE_LIMIT) {
			return false;
		}

		return (TripDetector.similarityMeasure(subTrip,
				trip) < TripDetector.SIMILARITY_THRESHOLD
				|| TripDetector.similarityMeasure(trip,
						subTrip) < TripDetector.SIMILARITY_THRESHOLD)
				&& endPointsMatch(trip, subTrip);
	}

	/* Finds point in trip that was closest to the mostRecentPoint */
	private static int closestPointIndex(GpsPoint mostRecentPoint, Trip trip) {
		int closestPointIndex = -1;
		double closestPointDistance = Double.MAX_VALUE;
		for (int i = 0; i < trip.gpsPoints.size(); i++) {
			double newDistance = Utils.squaredDistance(mostRecentPoint,
					trip.gpsPoints.get(i));
			if (newDistance < closestPointDistance) {
				closestPointIndex = i;
				closestPointDistance = newDistance;
			}
		}
		return closestPointIndex;
	}

	private static Trip generateSubtrip(Trip recentTrip, Trip trip) {
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

	private static Trip generateFuturePrediction(Trip recentTrip, Trip trip) {
		int closestPointIndex = closestPointIndex(recentTrip.lastPoint(), trip);
		return (closestPointIndex + 1 == trip.gpsPoints.size()) ? null
				: new Trip(trip.name, new ArrayList<GpsPoint>(trip.gpsPoints
						.subList(closestPointIndex + 1, trip.gpsPoints.size())))
								.shiftTimeTo(recentTrip.lastPoint().timestamp
										+ trip.gpsPoints.get(
												closestPointIndex + 1).timestamp
										- trip.gpsPoints.get(
												closestPointIndex).timestamp);
	}

	/*
	 * Finds all equally congested trips with the recentTrip, and for each
	 * equally congested trip t, uses its interval as prediction (except for the
	 * case where t has no further GPS points to be used for prediction).
	 */
	private static ArrayList<Trip> generatePredictionsFromEquallyCongestedTrips(
			Trip recentTrip, ArrayList<Trip> trips) throws IOException,
					ParseException, ProjectSpecificException {
		ArrayList<Trip> predictions = new ArrayList<Trip>();
		for (Trip trip : trips) {
			Trip subTrip = generateSubtrip(recentTrip, trip);
			if (equallyCongested(recentTrip, subTrip)) {
				Trip predictedTrip = generateFuturePrediction(recentTrip, trip);
				if (predictedTrip != null) {
					String newName = recentTrip.lastPoint().timestamp
							- subTrip.lastPoint().timestamp < RECENT_INTERVAL
									? predictedTrip.name + "_recent"
									: predictedTrip.name + "_old";
					predictions.add(predictedTrip.rename(newName));
				}
			}
		}

		if (predictions.size() == 0) {
			throw (new ProjectSpecificException(
					"Failed to generate predictions for " + recentTrip.name));
		} else {
			return predictions;
		}
	}

	static long calculatePredictionToBusStop(
			Function<GpsPoint, Boolean> atBusStop, Trip tripToPredict,
			ArrayList<Trip> historicalTrips) throws IOException, ParseException,
					ProjectSpecificException {
		ArrayList<Trip> predictions = FuturePredictor
				.generatePredictionsFromEquallyCongestedTrips(tripToPredict,
						historicalTrips);
		return FuturePredictor.calculateAverageArrivalTime(tripToPredict.name,
				predictions, atBusStop);
	}

	private static long averageArrivalTimestamp(
			ArrayList<Long> arrivalTimestamps) throws ParseException {
		Collections.sort(arrivalTimestamps);
		return arrivalTimestamps.get(arrivalTimestamps.size() / 2);
	}

	private static long calculateAverageArrivalTime(String tripName,
			ArrayList<Trip> predictions, Function<GpsPoint, Boolean> atBusStop)
					throws IOException, ParseException,
					ProjectSpecificException {
		ArrayList<Long> recentStoppingTimes = new ArrayList<Long>();
		ArrayList<Long> oldStoppingTimes = new ArrayList<Long>();
		for (Trip prediction : predictions) {
			for (GpsPoint point : prediction.gpsPoints) {
				if (atBusStop.apply(point)) {
					if (prediction.name.contains("recent")) {
						recentStoppingTimes.add(point.timestamp);
					} else {
						oldStoppingTimes.add(point.timestamp);
					}
					break;
				}
			}
		}

		if (recentStoppingTimes.size() + oldStoppingTimes.size() == 0) {
			throw new ProjectSpecificException("Failed to predict for trip "
					+ tripName + " despite having " + predictions.size()
					+ " predictions.");
		}

		return recentStoppingTimes.size() > 0
				? averageArrivalTimestamp(recentStoppingTimes)
				: averageArrivalTimestamp(oldStoppingTimes);
	}
}
