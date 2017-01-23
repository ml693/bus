/* TODO(ml693): make code cleaner. */

package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.stream.Collectors;

class Route {
	final String name;
	final ArrayList<BusStop> busStops;

	Route(String name, ArrayList<BusStop> busStops) {
		this.name = name;
		this.busStops = busStops;
	}

	Route(File file) throws IOException {
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

	static ArrayList<Route> extractRoutesFromFolder(File folder)
			throws IOException {
		ArrayList<Route> routes = new ArrayList<Route>();
		File[] files = folder.listFiles();
		for (File file : files) {
			routes.add(new Route(file));
		}
		return routes;
	}

	void writeToFolder(File routesFolder) throws IOException {
		BufferedWriter writer = new BufferedWriter(
				new FileWriter(routesFolder.getAbsolutePath() + "/" + name));
		Utils.writeLine(writer, "name,latitude,longitude");
		for (BusStop busStop : busStops) {
			busStop.write(writer);
		}
		writer.close();
	}

	static boolean visitedInOrder(ArrayList<Integer> visitNumbers) {
		for (int i = 1; i < visitNumbers.size(); i++) {
			if (visitNumbers.get(i - 1).intValue() >= visitNumbers.get(i)
					.intValue()) {
				return false;
			}
		}
		return true;
	}

	GpsPoint getGpsPoint(int i) {
		return new GpsPoint(0L, busStops.get(i).latitude,
				busStops.get(i).longitude);
	}

	String serialize() {
		return busStops.stream().map(stop -> stop.serialize())
				.collect(Collectors.joining(", "));

	}
}