package bus;

import java.io.File;
import java.util.ArrayList;

public class RoutesDetector {

	public static void main(String[] args) throws Exception {
		/*
		 * 1st argument is all trips folder.
		 * 2nd argument is all routes folder.
		 * 3rd argument is a folder to put good trips following routes.
		 */
		ArrayList<File> tripFiles = Utils.filesInFolder(args[0]);
		ArrayList<File> routeFiles = Utils.filesInFolder(args[1]);
		File pathsFolder = new File(args[2]);

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
}
