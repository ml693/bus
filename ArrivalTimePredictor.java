package bus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;

public class ArrivalTimePredictor {
	private static final long DURATION_DIFFERENCE_LIMIT = 80L;
	private static final long RECENT_INTERVAL = 3000L;

	static private class Prediction {
		final Long timestamp;
		final boolean recent;
		final boolean equallyCongested;
		final String name;

		Prediction(Long arrivalTimestamp, boolean recent,
				boolean equallyCongested, String name) {
			this.timestamp = arrivalTimestamp;
			this.recent = recent;
			this.equallyCongested = equallyCongested;
			this.name = name;
		}
	}

	/* Main method which predicts arrival time to bus stop */
	static long calculatePredictionTimestamp(
			Function<GpsPoint, Boolean> atBusStop, Trip tripToPredict,
			ArrayList<Trip> historicalTrips) throws ProjectSpecificException {
		ArrayList<Prediction> predictions = generatePredictions(tripToPredict,
				historicalTrips, atBusStop);
		return calculateAverageArrivalTime(tripToPredict.name, predictions);
	}

	/*
	 * Heuristic which checks whether roughly equal amount of distance was
	 * travelled in roughly equal amount of time.
	 */
	private static boolean equallyCongested(Trip trip, Trip historicalTrip) {
		int closestPointIndex = closestPointIndex(trip.lastPoint(),
				historicalTrip);
		long timestamp = historicalTrip.gpsPoints
				.get(closestPointIndex).timestamp;
		int index = closestPointIndex;
		while (index > 0 && timestamp
				- historicalTrip.gpsPoints.get(index).timestamp < trip
						.duration()) {
			index--;
		}

		long durationDifference = trip.duration()
				- (timestamp - historicalTrip.gpsPoints.get(index).timestamp);

		return (durationDifference < DURATION_DIFFERENCE_LIMIT
				&& Utils.samePlace(trip.lastPoint(),
						historicalTrip.gpsPoints.get(closestPointIndex))
				&& Utils.samePlace(trip.firstPoint(),
						historicalTrip.gpsPoints.get(index)));
	}

	private static boolean historicalTripIsRecent(Trip trip,
			Trip historicalTrip) {
		return trip.lastPoint().timestamp
				- historicalTrip.lastPoint().timestamp < RECENT_INTERVAL;
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

	static long generateFuturePrediction(Trip recentTrip, Trip trip,
			Function<GpsPoint, Boolean> atBusStop) {
		int closestPointIndex = closestPointIndex(recentTrip.lastPoint(), trip);

		for (int p = closestPointIndex + 1; p < trip.gpsPoints.size(); p++) {
			if (atBusStop.apply(trip.gpsPoints.get(p))) {
				return recentTrip.lastPoint().timestamp
						+ (trip.gpsPoints.get(p).timestamp - trip.gpsPoints
								.get(closestPointIndex).timestamp);
			}
		}

		System.out.println("Debugging: closestPoint = " + closestPointIndex);
		System.out.println("recentTrip = " + recentTrip.name);
		System.out.println("trip = " + trip.name);

		recentTrip.writeToFolder(new File("debug"));
		trip.writeToFolder(new File("debug"));

		throw new RuntimeException(ProjectSpecificException
				.historicalTripMissingImportantPoints(trip.name));
	}

	/*
	 * Finds all equally congested trips with the recentTrip, and for each
	 * equally congested trip t, uses its interval as prediction (except for
	 * the
	 * case where t has no further GPS points to be used for prediction).
	 */
	private static ArrayList<Prediction> generatePredictions(Trip trip,
			ArrayList<Trip> historicalTrips,
			Function<GpsPoint, Boolean> atBusStop) {
		System.out.println("For " + trip.name + " we have "
				+ historicalTrips.size() + " historical trips");

		ArrayList<Prediction> predictions = new ArrayList<Prediction>();

		for (Trip historicalTrip : historicalTrips) {
			boolean recent = historicalTripIsRecent(trip, historicalTrip);
			boolean equallyCongested = equallyCongested(trip, historicalTrip);
			long predictedTimestamp = generateFuturePrediction(trip,
					historicalTrip, atBusStop);
			predictions.add(new Prediction(predictedTimestamp, recent,
					equallyCongested, historicalTrip.name));
		}

		return predictions;
	}

	private static long averageArrivalTimestamp(
			ArrayList<Long> arrivalTimestamps) {
		Collections.sort(arrivalTimestamps);
		return arrivalTimestamps.get(arrivalTimestamps.size() / 2);
	}

	private static long calculateAverageArrivalTime(String tripName,
			ArrayList<Prediction> predictions) throws ProjectSpecificException {
		ArrayList<Long> oldDifferentlyCongested = new ArrayList<Long>();
		ArrayList<Long> oldEquallyCongested = new ArrayList<Long>();
		ArrayList<Long> newDifferentlyCongested = new ArrayList<Long>();
		ArrayList<Long> newEquallyCongested = new ArrayList<Long>();

		for (Prediction prediction : predictions) {
			if (!prediction.recent && !prediction.equallyCongested) {
				oldDifferentlyCongested.add(prediction.timestamp);
			}
			if (!prediction.recent && prediction.equallyCongested) {
				oldEquallyCongested.add(prediction.timestamp);
			}
			if (prediction.recent && !prediction.equallyCongested) {
				newDifferentlyCongested.add(prediction.timestamp);
			}
			if (prediction.recent && prediction.equallyCongested) {
				newEquallyCongested.add(prediction.timestamp);
			}
			break;
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
		return averageArrivalTimestamp(oldDifferentlyCongested);
	}
}
