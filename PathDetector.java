package bus;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

class PathDetector {
	/*
	 * Program determines which trip the bus is following given a file showing
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
	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder");
		Trip tripInterval = new Trip(new File(args[0]));
		determineFuturePath(tripInterval, new File(args[1]));
	}

	static double SIMILARITY_THRESHOLD = 1f;

	private static boolean similarNumberOfPointsUsedToAlign(Trip trip,
			Trip path, int pointsUsedToAlign) {
		return pointsUsedToAlign
				/ trip.gpsPoints.size() <= SIMILARITY_THRESHOLD;
	}

	/*
	 * The smaller similarity measure, the more similar tripsInterval to some
	 * interval of fullTrip is.
	 * 
	 * The intuition behind this measure is that when a bus is following the
	 * route exactly, each point in its history will lie on one of the route's
	 * segments, hence the point's distance to the "best" segment's corners will
	 * equal the "best" segment's length. If the bus is not following the route
	 * exactly, then point's distance to the "best" segment's corners due to
	 * triangle inequality will be larger.
	 */
	static boolean tripFollowsPath(Trip trip, Trip path) {
		final int iMax = trip.gpsPoints.size();
		final int jMax = path.gpsPoints.size() - 1;

		double[][] alignmentCost = new double[iMax + 1][jMax + 1];
		for (int i = 1; i <= iMax; i++) {
			alignmentCost[i][0] = Double.MAX_VALUE;
		}
		for (int j = 0; j <= jMax; j++) {
			alignmentCost[0][j] = 0.0;
		}

		for (int i = 1; i <= iMax; i++) {
			for (int j = 1; j <= jMax; j++) {
				alignmentCost[i][j] = Math.min(alignmentCost[i][j - 1],
						trip.gpsPoints.get(i - 1 /* i-th point */)
								.ratioToSegmentCorners(
										path.gpsPoints.get(j - 1),
										path.gpsPoints.get(j))
								+ alignmentCost[i - 1][j]);
			}
		}

		int last = jMax;
		while (last > 0
				&& alignmentCost[iMax][last] == alignmentCost[iMax][last - 1]) {
			last--;
		}
		int first = last - 1;
		int i = iMax - 1;
		while (i > 0 && first > 0) {
			if (alignmentCost[i][first - 1] <= trip.gpsPoints.get(i)
					.ratioToSegmentCorners(path.gpsPoints.get(first - 1),
							path.gpsPoints.get(first))
					+ alignmentCost[i - 1][first]) {
				first--;
			} else {
				i--;
			}
		}
		return similarNumberOfPointsUsedToAlign(trip, path, last - first + 1)
				&& (alignmentCost[iMax][jMax] - iMax < SIMILARITY_THRESHOLD);
	}

	static void determineFuturePath(Trip tripsInterval, File allTripsFolder)
			throws IOException, ParseException, ProjectSpecificException {
		// TODO(ml693): finish implementing
		ArrayList<Trip> similarTrips = detectSimilarTrips(tripsInterval,
				allTripsFolder);
		if (similarTrips.size() == 0) {
			throw new ProjectSpecificException(
					"No similar trips found for " + tripsInterval.name);
		}

		ArrayList<Trip> futureRoutes = new ArrayList<Trip>();
		for (Trip similarTrip : similarTrips) {
			try {
				Trip futureRoute = ArrivalTimePredictor
						.generateFuturePrediction(tripsInterval, similarTrip);
				futureRoutes.add(futureRoute);
			} catch (ProjectSpecificException exception) {
			}
		}
		if (futureRoutes.size() == 0) {
			throw new ProjectSpecificException(
					"No future found for " + tripsInterval.name);
		}

		int largestPointsNumber = 100;
		double largestRatio = 0.0f;

		for (int ratioThreshold = 100; ratioThreshold <= 100; ratioThreshold++) {
			for (int futurePointsNumber = largestPointsNumber; futurePointsNumber >= Trip.MINIMUM_NUMBER_OF_GPS_POINTS
					- 1; futurePointsNumber--) {
				if (futurePointsNumber < Trip.MINIMUM_NUMBER_OF_GPS_POINTS) {
					return;
				}

				boolean enough = false;
				for (int r = 0; r < futureRoutes.size(); r++) {
					Trip futureRoute = futureRoutes.get(r);
					if (futureRoute.gpsPoints.size() < futurePointsNumber) {
						continue;
					}

					int tripsThatFollowCount = 0;
					int tripsThatNotFollowCount = 0;
					Trip potentialFuture = new Trip(futureRoute.name,
							new ArrayList<GpsPoint>(futureRoute.gpsPoints
									.subList(0, futurePointsNumber)));
					for (Trip similarTrip : similarTrips) {
						if (tripFollowsPath(potentialFuture, similarTrip)) {
							tripsThatFollowCount++;
						} else {
							tripsThatNotFollowCount++;
						}
					}

					if (tripsThatNotFollowCount == 0) {
						enough = true;
					}

					double ratio = (double) tripsThatFollowCount
							/ (double) (tripsThatFollowCount
									+ tripsThatNotFollowCount);
					if (ratio > largestRatio) {
						largestRatio = ratio;
						largestPointsNumber = futurePointsNumber;
					}
				}

				System.out.println("Future points " + futurePointsNumber
						+ " is enough: " + enough);
			}

		}
	}

	/* allTripsFolder should only contain CSV files */
	static ArrayList<Trip> detectSimilarTrips(Trip tripInterval,
			File allTripsFolder) throws IOException, ParseException,
					ProjectSpecificException {
		ArrayList<Trip> similarTrips = new ArrayList<Trip>();
		ArrayList<Trip> allTrips = Trip.extractTripsFromFolder(allTripsFolder);
		for (Trip trip : allTrips) {
			System.out.println("Processing " + trip.name);
			if (tripFollowsPath(tripInterval, trip)) {
				System.out.println(trip.name + " is similar!");
				similarTrips.add(trip);
			}
		}
		return similarTrips;
	}

}