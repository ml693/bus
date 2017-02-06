/* TODO(ml693): make code cleaner. */
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

	static HashMap<String, BusStop> busStopsFromNaptan = new HashMap<String, BusStop>();

	static Set<String> duplicateRoute = new HashSet<String>();

	/*
	 * A program which is meant to be executed only once. It builds convenient
	 * route representation (as a list of bus stops) from the open source input
	 * files given.
	 * 
	 * Program takes stop_times.txt, stops.txt files and extracts all possible
	 * routes, each route being saved in a separate file.
	 */
	public static void main(String args[]) throws Exception {
		/*
		 * 1st argument is a file of stop locations.
		 * 2nd argument is a folder containing file per route.
		 * 3rd argument is a folder where to output routes.
		 */
		Utils.checkCommandLineArguments(args, "file", "folder", "folder");
		constructRoutesFromNaptanData(new File(args[0]), new File(args[1]),
				new File(args[2]));
	}

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

	static void readBusStopsFromNaptanFiles(File stopsFile) throws IOException {
		BufferedReader stopsInput = new BufferedReader(
				new FileReader(stopsFile));
		String line = stopsInput.readLine();

		while (line != null) {
			/*
			 * TODO(ml693): I forgot what 6, 10, 12, etc means. I suspect these
			 * are spacing numbers. Need to figure that out.
			 */
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

			busStopsFromNaptan.put(code,
					new BusStop(name, latitude, longitude));

			while (!line.contains("</StopPoint>")) {
				line = stopsInput.readLine();
			}
			if (!stopsInput.readLine().contains("<StopPoint")) {
				break;
			}
		}

		stopsInput.close();
	}

	static void extractRoutesFromNaptanFiles(File tripFile,
			File folderToSaveRoute) {
		final String stopPointRef = "<StopPointRef>";
		final String annotatedRefOpen = "<AnnotatedStopPointRef>";
		final String annotatedRefClose = "</AnnotatedStopPointRef>";

		try {
			BufferedReader stopsInput = new BufferedReader(
					new FileReader(tripFile));
			String line = stopsInput.readLine();
			String fileName = tripFile.getName();
			Route route = new Route(
					fileName.substring(0,
							fileName.length() - 4 /* to remove .xml part */),
					new ArrayList<BusStop>());

			while (line != null) {
				while (!line.contains(stopPointRef)) {
					line = stopsInput.readLine();
				}
				/* 6 and 15 is the lenght of spaces in that line */
				String code = line.substring(6 + stopPointRef.length(),
						line.length() - 15);
				route.busStops.add(busStopsFromNaptan.get(code));

				while (!line.contains(annotatedRefClose)) {
					line = stopsInput.readLine();
				}
				if (!stopsInput.readLine().contains(annotatedRefOpen)) {
					break;
				}
			}
			stopsInput.close();

			System.out.println("Route " + route.name + " consists of "
					+ route.busStops.size() + " stops.");

			String serializedRoute = route.serialize();
			if (!duplicateRoute.contains(serializedRoute)) {
				duplicateRoute.add(serializedRoute);
				route.writeToFolder(folderToSaveRoute);
			} else {
				System.out.println("Duplicate " + route.name + " found!");
			}

		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	static void constructRoutesFromNaptanData(File stopsFile, File routesFolder,
			File outputRoutesFolder) {
		try {
			readBusStopsFromNaptanFiles(stopsFile);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		File[] routesFiles = routesFolder.listFiles();
		for (File routeFile : routesFiles) {
			extractRoutesFromNaptanFiles(routeFile, outputRoutesFolder);
		}
	}

}
