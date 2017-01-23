/* TODO(ml693): make code cleaner. */

package bus;

import java.io.File;
import java.util.ArrayList;

public class RoutesDetector {

	static boolean routeFollowedByTrip(Route route, Trip trip) {
		final int iMax = route.busStops.size();
		final int jMax = trip.gpsPoints.size() - 1;

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
						route.getGpsPoint(i - 1).ratioToSegmentCorners(
								trip.gpsPoints.get(j - 1),
								trip.gpsPoints.get(j))
						+ alignmentCost[i - 1][j]);
			}
		}

		return alignmentCost[iMax][jMax]
				- iMax < PathDetector.SIMILARITY_THRESHOLD;
	}

	public static void main(String[] args) throws Exception {
		/*
		 * 1st argument is a folder containing all trips.
		 * 2nd argument is a folder containing all routes.
		 * 3rd argument is a folder where to put good routes
		 */
		// TODO(ml693): replace 2nd argument by static_data_folder
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");

		ArrayList<Trip> trips = Trip.extractTripsFromFolder(new File(args[0]));
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[1]));

		for (Trip trip : trips) {
			System.out.println("Processing " + trip.name);
			for (int r = 0; r < routes.size(); r++) {
				Route route = routes.get(r);
				if (routeFollowedByTrip(route, trip)) {
					trip.makeCopyWithNewName(route.name)
							.writeToFolder(new File(args[2]));
					routes.remove(r);
					System.out.println(routes.size() + " routes left");
				}
			}
		}
	}

}
