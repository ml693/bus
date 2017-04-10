package bus;

/*
 * Path is a list of GPS points that follows a route from the first until the
 * last stop. This class contains code to find what path a trip follows.
 */
class PathDetector {
	static double SIMILARITY_THRESHOLD = 1.0f;

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

		/*
		 * After the for loop terminates, the alignmentCost[iMax][jMax] contains
		 * the desired cost value. I now compute what points are used to
		 * produce this cost.
		 */
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

}