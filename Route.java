package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.stream.Collectors;

/* Route is a consecutive list of bus stops. */
class Route {

	final String name;
	final ArrayList<BusStop> busStops;

	Route(String name, ArrayList<BusStop> busStops) {
		this.name = name;
		this.busStops = busStops;
	}

	/*
	 * Example file:
	 * 
	 * name,latitude,longitude
	 * Kits Close,52.14931,-0.84826
	 * Ashton Road,52.14613,-0.85365
	 * Blacksmiths Way,52.14360,-0.85611
	 * The Globe,52.12421,-0.84077
	 * Hanslope School,52.11917,-0.83303
	 * Gold Street,52.11561,-0.82855
	 */
	Route(File file) {
		name = file.getName();
		busStops = new ArrayList<BusStop>();

		Scanner scanner = Utils.csvScanner(file);
		/* Skipping name,latitude,longitude */
		scanner.nextLine();

		while (scanner.hasNext()) {
			busStops.add(new BusStop(scanner.next(), scanner.nextDouble(),
					scanner.nextDouble()));
		}
	}

	static ArrayList<Route> extractRoutesFromFolder(File folder) {
		ArrayList<Route> routes = new ArrayList<Route>();
		File[] files = folder.listFiles();
		for (File file : files) {
			routes.add(new Route(file));
		}
		return routes;
	}

	void writeToFolder(File routesFolder) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(
					routesFolder.getAbsolutePath() + "/" + name));
			Utils.writeLine(writer, "name,latitude,longitude");
			for (BusStop busStop : busStops) {
				busStop.write(writer);
			}
			writer.close();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	String serialize() {
		return busStops.stream().map(stop -> stop.serializeToString())
				.collect(Collectors.joining(", "));
	}

	BusStop lastStop() {
		return busStops.get(busStops.size() - 1);
	}

	boolean atLastStop(GpsPoint point) {
		return lastStop().atStop(point);
	}

	boolean allStopsVisitedInOrder(Trip trip) {
		int point = 0;
		for (BusStop busStop : busStops) {
			while (point < trip.gpsPoints.size()) {
				if (busStop.atStop(trip.gpsPoints.get(point))) {
					break;
				}
				point++;
			}
		}
		return point < trip.gpsPoints.size();
	}
}