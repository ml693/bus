package bus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class ArrivalTimePredictor {
	private static final long DURATION_DIFFERENCE_LIMIT = 80L;
	private static final long RECENT_INTERVAL = 3000L;

	/* Main method which predicts arrival time to the busStop */
	static Prediction makePrediction(BusStop busStop, Trip tripToPredict,
			ArrayList<Trip> historicalTrips) throws ProjectSpecificException {
		ArrayList<Prediction> predictions = generatePredictions(tripToPredict,
				historicalTrips, busStop);
		long predictedTimestamp = calculateAverageArrivalTime(
				tripToPredict.name, predictions);
		return new Prediction(predictedTimestamp,
				tripToPredict.lastPoint().timestamp, false, false, busStop,
				tripToPredict.name);
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

		Trip recentSubtrip = null;
		try {
			recentSubtrip = trip.subTrip(
					trip.gpsPoints.size() - Trip.MINIMUM_NUMBER_OF_GPS_POINTS,
					trip.gpsPoints.size());
		} catch (ProjectSpecificException exception) {
			throw new RuntimeException(exception);
		}

		int index = closestPointIndex;
		while (index > 0 && timestamp
				- historicalTrip.gpsPoints.get(index).timestamp < recentSubtrip
						.duration()) {
			index--;
		}

		long durationDifference = recentSubtrip.duration()
				- (timestamp - historicalTrip.gpsPoints.get(index).timestamp);
		return (durationDifference < DURATION_DIFFERENCE_LIMIT
				&& Utils.samePlace(trip.lastPoint(),
						historicalTrip.gpsPoints.get(closestPointIndex))
				&& Utils.samePlace(trip.firstPoint(),
						historicalTrip.gpsPoints.get(index)));
	}

	private static boolean historicalTripIsRecent(Trip trip,
			Trip historicalTrip) {
		// TODO(ml693): remove modulus when proper testing is done.
		return Math.abs(trip.lastPoint().timestamp
				- historicalTrip.lastPoint().timestamp) < RECENT_INTERVAL;
	}

	/* Finds point in trip that was closest to the mostRecentPoint */
	static int closestPointIndex(GpsPoint mostRecentPoint, Trip trip) {
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
			BusStop busStop) {
		int closestPointIndex = closestPointIndex(recentTrip.lastPoint(), trip);

		for (int p = 0; p < trip.gpsPoints.size(); p++) {
			if (busStop.atStop(trip.gpsPoints.get(p))) {
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
	 * the case where t has no further GPS points to be used for prediction).
	 */
	private static ArrayList<Prediction> generatePredictions(Trip trip,
			ArrayList<Trip> historicalTrips, BusStop busStop) {
		ArrayList<Prediction> predictions = new ArrayList<Prediction>();

		for (Trip historicalTrip : historicalTrips) {
			boolean recent = historicalTripIsRecent(trip, historicalTrip);
			boolean equallyCongested = equallyCongested(trip, historicalTrip);
			long predictedTimestamp = generateFuturePrediction(trip,
					historicalTrip, busStop);
			predictions.add(new Prediction(predictedTimestamp,
					trip.lastPoint().timestamp, recent, equallyCongested,
					busStop, historicalTrip.name));
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
				oldDifferentlyCongested.add(prediction.predictedTimestamp);
			}
			if (!prediction.recent && prediction.equallyCongested) {
				oldEquallyCongested.add(prediction.predictedTimestamp);
			}
			if (prediction.recent && !prediction.equallyCongested) {
				newDifferentlyCongested.add(prediction.predictedTimestamp);
			}
			if (prediction.recent && prediction.equallyCongested) {
				newEquallyCongested.add(prediction.predictedTimestamp);
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
		return averageArrivalTimestamp(oldDifferentlyCongested);
	}
}
