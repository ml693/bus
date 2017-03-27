package bus;

import java.io.File;
import java.util.ArrayList;

public class HistoricalDataBuilder {

	public static void main(String[] args) {
		buildHistoricalData(args);
	}

	public static void buildHistoricalData(String[] args) {
		try {
			Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}

		ArrayList<File> tripFiles = Utils.filesInFolder(args[0]);
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[1]));
		String historicalDataFolder = args[2];

		for (int t = tripFiles.size() - 1; t >= 0; t--) {
			Trip trip = null;
			try {
				trip = new Trip(tripFiles.get(t));
			} catch (ProjectSpecificException exception) {
				throw new RuntimeException(exception);
			}
			System.out.println("Processing " + trip.name);

			for (Route route : routes) {
				try {
					Trip historicalTrip = buildHistoricalTrip(route, trip);
					System.out.println(
							historicalTrip.name + " follows " + route.name);
					File pathFolder = new File(
							historicalDataFolder + "/" + route.name);
					pathFolder.mkdir();
					historicalTrip.writeToFolder(pathFolder);
				} catch (ProjectSpecificException exception) {
				}
			}

			tripFiles.get(t).delete();
			tripFiles.remove(t);
			System.out.println(tripFiles.size() + " trips left");
		}
	}

	public static Trip buildHistoricalTrip(Route route, Trip trip)
			throws ProjectSpecificException {
		if (!route.allStopsVisitedInOrder(trip)) {
			throw new ProjectSpecificException(
					trip.name + " does not follow " + route.name);
		}

		BusStop firstStop = route.busStops.get(0);
		int fromIndex = 0;
		while (fromIndex < trip.gpsPoints.size()
				&& !firstStop.atStop(trip.gpsPoints.get(fromIndex))) {
			fromIndex++;
		}

		int toIndex = trip.gpsPoints.size() - 1;
		while (toIndex >= 0 && !route.atLastStop(trip.gpsPoints.get(toIndex))) {
			toIndex--;
		}

		if (fromIndex > toIndex) {
			throw new ProjectSpecificException(
					"Can not construct historical trip from " + trip.name
							+ " for " + route.name);
		}
		
		System.out.println(fromIndex + " " + toIndex);

		return trip.subTrip(fromIndex, toIndex + 1);
	}

}
