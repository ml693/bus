package bus;

import java.io.File;
import java.util.ArrayList;

public class RoutesDetector {

	public static void main(String[] args) throws Exception {
		findTripsFollowing(new File("trips"),
				new Route(new File("routes/1055852-20150607-20150830")),
				new File("trips_following_route"));
	}

	static void findPathForRoute(String tripsFolderName,
			String routesFolderName, File pathsFolder)
					throws ProjectSpecificException {
		ArrayList<File> tripFiles = Utils.filesInFolder(tripsFolderName);
		ArrayList<File> routeFiles = Utils.filesInFolder(routesFolderName);

		for (int t = 0; t < tripFiles.size(); t++) {
			Trip trip = new Trip(tripFiles.get(t));
			System.out.println("Processing " + trip.name);

			for (int r = 0; r < routeFiles.size(); r++) {
				Route route = new Route(routeFiles.get(r));
				if (route.allStopsVisitedInOrder(trip)) {
					trip.makeCopyWithNewName(route.name)
							.writeToFolder(pathsFolder);
					routeFiles.get(r).delete();
					routeFiles.remove(r);
					System.out.println(routeFiles.size() + " routes left");
				}
				trip.writeToFolder(pathsFolder);
			}
			tripFiles.get(t).delete();
			tripFiles.remove(t);
		}
	}

	static void findTripsFollowing(File tripsFolder, Route route,
			File outputFolder) {
		ArrayList<Trip> trips = Trip.extractTripsFromFolder(tripsFolder);
		for (Trip trip : trips) {
			if (route.allStopsVisitedInOrder(trip)) {
				trip.writeToFolder(outputFolder);
			}
		}
	}
}
