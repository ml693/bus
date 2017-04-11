package bus;

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
		return medianPrediction(predictions);
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
				&& Utils.samePlace(recentSubtrip.lastPoint(),
						historicalTrip.gpsPoints.get(closestPointIndex))
				&& Utils.samePlace(recentSubtrip.firstPoint(),
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
			/*
			 * TODO(ml693): unfortunatelly at real time no historicalTrip will
			 * be considered as recent. Find a way to fix this.
			 */
			boolean recent = historicalTripIsRecent(trip, historicalTrip);
			boolean equallyCongested = equallyCongested(trip, historicalTrip);
			long predictedTimestamp = generateFuturePrediction(trip,
					historicalTrip, busStop);
			Prediction prediction = new Prediction(predictedTimestamp);
			prediction.name = historicalTrip.name;
			prediction.equallyCongested = equallyCongested;
			prediction.recent = recent;
			predictions.add(prediction);
		}

		return predictions;
	}

	private static Prediction median(ArrayList<Prediction> predictions) {
		Collections.sort(predictions, (p1,
				p2) -> p1.predictedTimestamp < p2.predictedTimestamp ? -1 : 1);
		return predictions.get(predictions.size() / 2);
	}

	private static Prediction medianPrediction(
			ArrayList<Prediction> predictions) throws ProjectSpecificException {
		ArrayList<Prediction> oldDifferentlyCongested = new ArrayList<Prediction>();
		ArrayList<Prediction> oldEquallyCongested = new ArrayList<Prediction>();
		ArrayList<Prediction> newDifferentlyCongested = new ArrayList<Prediction>();
		ArrayList<Prediction> newEquallyCongested = new ArrayList<Prediction>();

		for (Prediction prediction : predictions) {
			if (!prediction.recent && !prediction.equallyCongested) {
				oldDifferentlyCongested.add(prediction);
			}
			if (!prediction.recent && prediction.equallyCongested) {
				oldEquallyCongested.add(prediction);
			}
			if (prediction.recent && !prediction.equallyCongested) {
				newDifferentlyCongested.add(prediction);
			}
			if (prediction.recent && prediction.equallyCongested) {
				newEquallyCongested.add(prediction);
			}
		}

		if (newEquallyCongested.size() > 0) {
			return median(newEquallyCongested);
		}
		if (oldEquallyCongested.size() > 0) {
			return median(oldEquallyCongested);
		}
		if (newDifferentlyCongested.size() > 0) {
			return median(newDifferentlyCongested);
		}
		return median(oldDifferentlyCongested);
	}
}
