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
	
	public static void main(String[] args) throws Exception {
		/*
		 * 1st argument is a folder containing all trips.
		 * 2nd argument is a folder containing all routes.
		 * 3rd argument is a folder where to put good routes
		 */
		Utils.checkCommandLineArguments(args, "folder", "folder", "folder");

		ArrayList<Trip> trips = Trip.extractTripsFromFolder(new File(args[0]));
		ArrayList<Route> routes = Route
				.extractRoutesFromFolder(new File(args[1]));

		for (Trip trip : trips) {
			System.out.println("Processing " + trip.name);
			for (int r = 0; r < routes.size(); r++) {
				if (routes.get(r).allStopsVisitedInOrder(trip)) {
					trip.makeCopyWithNewName(routes.get(r).name)
							.writeToFolder(new File(args[2]));
					routes.remove(r);
					System.out.println(routes.size() + " routes left");
				}
			}
		}
	}
	
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

	boolean allStopsVisitedInOrder(Trip trip) {
		int point = 0;
		for (BusStop busStop : busStops) {
			while (point < trip.gpsPoints.size()) {
				if (busStop.atBusStop(trip.gpsPoints.get(point))) {
					break;
				}
				point++;
			}
		}
		return point < trip.gpsPoints.size();
	}

	/*
	 * This predicate checks whether a trip visited
	 * all routes stops in order.
	 */
	boolean followedByTrip(Trip trip) {
		final int iMax = busStops.size();
		final int jMax = trip.gpsPoints.size() - 1;

		double[][] cost = new double[iMax + 1][jMax + 1];
		for (int i = 1; i <= iMax; i++) {
			cost[i][0] = Double.MAX_VALUE;
		}
		for (int j = 0; j <= jMax; j++) {
			cost[0][j] = 0.0;
		}

		for (int i = 1; i <= iMax; i++) {
			for (int j = 1; j <= jMax; j++) {
				cost[i][j] = Math.min(cost[i][j - 1],
						getGpsPoint(i - 1).ratioToSegmentCorners(
								trip.gpsPoints.get(j - 1),
								trip.gpsPoints.get(j)) + cost[i - 1][j]);
			}
		}

		return (cost[iMax][jMax] - iMax < PathDetector.SIMILARITY_THRESHOLD)
				&& allStopsVisitedInOrder(trip);
	}

	boolean atLastStop(GpsPoint point) {
		try {
			BusStop lastStop = busStops.get(busStops.size() - 1);
			GpsPoint stopPoint = new GpsPoint(0L, lastStop.latitude,
					lastStop.longitude);
			return (Utils.distance(point, stopPoint) < 0.00002);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	GpsPoint getGpsPoint(int i) {
		return new GpsPoint(0L, busStops.get(i).latitude,
				busStops.get(i).longitude);
	}

	String serialize() {
		return busStops.stream().map(stop -> stop.serializeToString())
				.collect(Collectors.joining(", "));

	}
}