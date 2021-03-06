package bus;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class HistoricalDataBuilder {

	private static final String INCOMMING_JSON_FOLDER_PATH = "/media/tfc/ml693/data_json/";

	private static int lastDayProcessed = 0;

	private static File jsonFolder(LocalDateTime date) {
		String year = date.getYear() + "/";
		String month = String.format("%02d", date.getMonthValue()) + "/";
		String day = String.format("%02d", date.getDayOfMonth()) + "/";
		String jsonFolderPath = INCOMMING_JSON_FOLDER_PATH + year + month + day;
		return new File(jsonFolderPath);
	}

	public static void extractTripsFromDay(LocalDateTime date) {
		File jsonFolder = jsonFolder(date);
		File historiesFolder = new File("histories");
		BusTravelHistoryExtractor.extractHistory(jsonFolder, historiesFolder);
		File tripsFolder = new File("trips");
		HistoryIntoTripsSplitter.splitHistoryIntoTrips(historiesFolder,
				tripsFolder);
		System.out.println("Extracted all trips, deleting histories:");

		File[] histories = historiesFolder.listFiles();
		for (File history : histories) {
			history.delete();
		}
		System.out.println("Deleted all histories");
	}

	public static void main(String[] args) {
		while (true) {
			LocalDateTime lastDay = LocalDateTime.now().minusDays(1);

			if (lastDayProcessed != lastDay.getDayOfMonth()) {
				System.out.println("The new day: " + lastDay.getDayOfMonth());
				extractTripsFromDay(lastDay);
				int tripsConstructed = buildHistoricalData(args);
				lastDayProcessed = lastDay.getDayOfMonth();
				System.out.println("Finished with day " + lastDayProcessed
						+ ". Construced " + tripsConstructed + " from it.");
			} else {
				try {
					Thread.sleep(3600000);
				} catch (InterruptedException exception) {
					throw new RuntimeException(exception);
				}
			}
		}
	}

	public static int buildHistoricalData(String[] args) {
		try {
			Utils.checkCommandLineArguments(args, "folder", "folder", "folder");
		} catch (ProjectSpecificException exception) {
			throw new RuntimeException(exception);
		}

		ArrayList<File> tripFiles = Utils.filesInFolder(args[0]);
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[1]));
		String historicalDataFolder = args[2];

		int tripsConstructed = 0;

		for (int t = tripFiles.size() - 1; t >= 0; t--) {
			Trip trip = Trip.readFromFile(tripFiles.get(t));
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
					tripsConstructed++;
				} catch (ProjectSpecificException exception) {
				}
			}

			tripFiles.get(t).delete();
			tripFiles.remove(t);
			System.out.println(tripFiles.size() + " trips left");
		}

		return tripsConstructed;
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

		// Finding the first point that reached the last stop
		int toIndex = trip.gpsPoints.size() - 1;
		while (toIndex >= 0 && !route.atLastStop(trip.gpsPoints.get(toIndex))) {
			toIndex--;
		}
		while (toIndex >= 0 && route.atLastStop(trip.gpsPoints.get(toIndex))) {
			toIndex--;
		}
		toIndex++;

		if (fromIndex > toIndex) {
			throw new ProjectSpecificException(
					"Can not construct historical trip from " + trip.name
							+ " for " + route.name);
		}

		return trip.subTrip(fromIndex, toIndex + 1);
	}

}
