/* TODO(ml693): make code cleaner. */

package bus;

import java.io.File;
import java.util.ArrayList;

public class RoutesDetector {

	static boolean tripFollowsRoute(Trip trip, Route route) {
		ArrayList<Integer> visitedIndices = new ArrayList<Integer>();
		for (BusStop stop : route.busStops) {
			for (int p = 0; p < trip.gpsPoints.size(); p++) {
				if (atStop(trip.gpsPoints.get(p), stop)) {
					visitedIndices.add(p);
					break;
				}
			}
		}
		if (visitedIndices.size() > 2 && Route.visitedInOrder(visitedIndices)) {
			System.out.println(visitedIndices.size());
		}
		return visitedIndices.size() > 2
				&& Route.visitedInOrder(visitedIndices);
	}

	public static void main(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder");

		int matchCount = 0;
		Trip trip = new Trip(new File(args[0]));

		File[] files = new File(args[1]).listFiles();
		for (File file : files) {
			Route route = new Route(file);
			if (tripFollowsRoute(trip, route)) {
				matchCount++;
				System.out.println(route.name);
			}
		}
		System.out.println("Matched " + matchCount + " routes");
	}

	static boolean atStop(GpsPoint point, BusStop stop) {
		return (stop.latitude - point.latitude)
				* (stop.latitude - point.latitude)
				+ (stop.longitude - point.longitude)
						* (stop.longitude - point.longitude) < 0.0000002;
	}

}
