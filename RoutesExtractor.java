/*
 * Program takes stop_times.txt, stops.txt files and extracts all possible
 * routes, each route being saved in a separate file.
 * 
 * TODO(ml693): make code cleaner.
 */
package bus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class RoutesExtractor {

	static HashMap<Long, BusStop> busStops = new HashMap<Long, BusStop>();

	static HashMap<String, BusStop> busStopsNew = new HashMap<String, BusStop>();

	static Set<String> duplicateRoute = new HashSet<String>();

	static void readBusStopsOld(File stopsFile) throws IOException {
		Scanner scanner = Utils.csvScanner(stopsFile);
		/* Skipping header line */
		scanner.nextLine();

		/*
		 * Assumption is that stops.txt file contains lines of the form
		 * stop_id,stop_code,stop_name,stop_lat,stop_lon
		 */
		while (scanner.hasNext()) {
			long stopId = scanner.nextLong();
			/* Skipping stop_code */
			scanner.next();

			BusStop busStop = new BusStop(scanner.next(), scanner.nextDouble(),
					scanner.nextDouble());
			busStops.put(stopId, busStop);
		}
		scanner.close();
	}

	static void readBusStopsNew(File stopsFile) throws IOException {
		BufferedReader stopsInput = new BufferedReader(
				new FileReader(stopsFile));
		String line = stopsInput.readLine();

		while (line != null) {
			while (!line.contains("<AtcoCode>")) {
				line = stopsInput.readLine();
			}
			String code = line.substring(6 + 10, line.length() - 11);

			while (!line.contains("<CommonName>")) {
				line = stopsInput.readLine();
			}
			String name = line.substring(8 + 12, line.length() - 13);

			while (!line.contains("<Longitude>")) {
				line = stopsInput.readLine();
			}
			double longitude = Double
					.parseDouble(line.substring(12 + 11, line.length() - 12));

			while (!line.contains("<Latitude>")) {
				line = stopsInput.readLine();
			}
			double latitude = Double
					.parseDouble(line.substring(12 + 10, line.length() - 11));

			busStopsNew.put(code, new BusStop(name, latitude, longitude));

			while (!line.contains("</StopPoint>")) {
				line = stopsInput.readLine();
			}
			if (!stopsInput.readLine().contains("<StopPoint")) {
				break;
			}
		}

		stopsInput.close();
	}

	/* WARNING: tripsFile has to be well formatted! */
	static void constructRoutesOld(File tripsFile, File routesFolder)
			throws IOException {
		Scanner scanner = Utils.csvScanner(tripsFile);
		/* Skip 'trip_id,arrival_time,departure_time,id,sequence_nr,name' */
		scanner.nextLine();

		String tripName = scanner.next();
		while (!(tripName.equals("end_of_file"))) {
			Route route = new Route(tripName, new ArrayList<BusStop>());
			while (tripName.equals(route.name)) {
				/* Skipping arrival and departure times */
				scanner.next();
				scanner.next();

				route.busStops.add(busStops.get(scanner.nextLong()));

				/*
				 * Skipping stop_sequence number, as we're assuming bus stops
				 * are listed in order.
				 */
				scanner.nextInt();
				/* Skipping stops name, as we have it from other file */
				scanner.next();

				tripName = scanner.next();
			}

			String serializedRoute = route.serialize();
			if (!duplicateRoute.contains(serializedRoute)) {
				duplicateRoute.add(serializedRoute);
				route.writeToFolder(routesFolder);
			} else {
				System.out.println("Duplicate " + route.name + " found!");
			}
		}
	}

	static void constructRoutesNew(File tripFile, File routesFolder)
			throws IOException {
		BufferedReader stopsInput = new BufferedReader(
				new FileReader(tripFile));
		String line = stopsInput.readLine();

		String fileName = tripFile.getName();
		Route route = new Route(fileName.substring(0, fileName.length() - 4),
				new ArrayList<BusStop>());
		while (line != null) {
			while (!line.contains("<StopPointRef>")) {
				line = stopsInput.readLine();
			}
			String code = line.substring(6 + 14, line.length() - 15);
			route.busStops.add(busStopsNew.get(code));

			while (!line.contains("</AnnotatedStopPointRef>")) {
				line = stopsInput.readLine();
			}
			if (!stopsInput.readLine().contains("<AnnotatedStopPointRef>")) {
				break;
			}
		}
		stopsInput.close();

		System.out.println("Route " + route.name + " consists of "
				+ route.busStops.size() + " stops.");

		String serializedRoute = route.serialize();
		if (!duplicateRoute.contains(serializedRoute)) {
			duplicateRoute.add(serializedRoute);
			route.writeToFolder(routesFolder);
		} else {
			System.out.println("Duplicate " + route.name + " found!");
		}

	}

	public static void main(String args[]) throws Exception {
		/*
		 * 1st argument is a folder containing stops.txt, stop_times.txt files.
		 * 2nd argument is a folder where to put routes.
		 */
		Utils.checkCommandLineArguments(args, "file", "folder", "folder");

		readBusStopsNew(new File(args[0]));
		System.out.println("Extracted " + busStopsNew.size() + " stops.");

		File[] routesFiles = new File(args[1]).listFiles();
		File outputRoutesFolder = new File(args[2]);
		for (File routeFile : routesFiles) {
			constructRoutesNew(routeFile, outputRoutesFolder);
		}
	}

}
