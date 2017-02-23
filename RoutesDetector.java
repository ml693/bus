package bus;

import java.io.File;
import java.util.ArrayList;

public class RoutesDetector {

	public static void main(String[] args) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		ArrayList<File> tripFiles = Utils.filesInFolder(args[0]);
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[1]));
		String pathsFolder = args[2];

		for (int t = 0; t < tripFiles.size(); t++) {
			Trip trip = new Trip(tripFiles.get(t));
			System.out.println("Processing " + trip.name);

			for (Route route : routes) {
				if (route.allStopsVisitedInOrder(trip)) {
					System.out.println(trip.name + " follows " + route.name);
					File pathFolder = new File(pathsFolder + "/" + route.name);
					pathFolder.mkdir();
					trip.writeToFolder(pathFolder);
				}
			}

			tripFiles.get(t).delete();
			tripFiles.remove(t);
			System.out.println(tripFiles.size() + " trips left");
		}
	}
}
