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
	/*
	 * A program which is meant to be executed only once. It builds convenient
	 * route representation (as a list of bus stops) from the open source input
	 * files given.
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

	static HashMap<Long, BusStop> busStopsFromOldData = new HashMap<Long, BusStop>();
	static HashMap<String, BusStop> busStopsFromNaptanData = new HashMap<String, BusStop>();
	static Set<String> duplicateRoute = new HashSet<String>();

	static void constructRoutesFromNaptanData(File stopsFile, File routesFolder,
			File outputRoutesFolder) throws IOException {
		readBusStopsFromNaptanFile(stopsFile);
		File[] routesFiles = routesFolder.listFiles();
		for (File routeFile : routesFiles) {
			readRouteFromNaptanFile(routeFile, outputRoutesFolder);
		}
	}

	static void constructRoutesFromOldData(File stopsFile, File routesFile,
			File outputRoutesFolder) throws IOException {
		readBusStopsFromOldFile(stopsFile);
		readRouteFromOldFile(routesFile, outputRoutesFolder);
	}

	static void readBusStopsFromNaptanFile(File stopsFile) throws IOException {
		BufferedReader stopsInput = new BufferedReader(
				new FileReader(stopsFile));
		String line = stopsInput.readLine();

		while (line != null) {
			final String codeTag = "<AtcoCode>";
			final String nameTag = "<CommonName>";
			final String longitudeTag = "<Longitude>";
			final String latitudeTag = "<Latitude>";
			final String stopPointCloseTag = "</StopPoint>";
			final String stopPointOpenTag = "<StopPoint";

			/*
			 * Numbers inside the while loop (6, 8, 11, 12, 13) correspond to
			 * number of spaces in various lines.
			 */
			while (!line.contains(codeTag)) {
				line = stopsInput.readLine();
			}
			String code = line.substring(6 + codeTag.length(),
					line.length() - 11);

			while (!line.contains(nameTag)) {
				line = stopsInput.readLine();
			}
			String name = line.substring(8 + nameTag.length(),
					line.length() - 13);

			while (!line.contains(longitudeTag)) {
				line = stopsInput.readLine();
			}
			double longitude = Double.parseDouble(line
					.substring(12 + longitudeTag.length(), line.length() - 12));

			while (!line.contains(latitudeTag)) {
				line = stopsInput.readLine();
			}
			double latitude = Double.parseDouble(line
					.substring(12 + latitudeTag.length(), line.length() - 11));

			busStopsFromNaptanData.put(code,
					new BusStop(name, latitude, longitude));

			while (!line.contains(stopPointCloseTag)) {
				line = stopsInput.readLine();
			}
			if (!stopsInput.readLine().contains(stopPointOpenTag)) {
				break;
			}
		}

		stopsInput.close();
	}

	static void readRouteFromNaptanFile(File routeFile, File folderToSaveRoute)
			throws IOException {
		final String stopPointRef = "<StopPointRef>";
		final String annotatedRefOpen = "<AnnotatedStopPointRef>";
		final String annotatedRefClose = "</AnnotatedStopPointRef>";

		BufferedReader stopsInput = new BufferedReader(
				new FileReader(routeFile));
		String line = stopsInput.readLine();
		String fileName = routeFile.getName();
		Route route = new Route(
				fileName.substring(0,
						fileName.length() - 4 /* to remove .xml part */),
				new ArrayList<BusStop>());

		while (line != null) {
			/* Searching for next stop */
			while (!line.contains(stopPointRef)) {
				line = stopsInput.readLine();
			}
			/* 6 and 15 is the length of spaces in that line */
			String code = line.substring(6 + stopPointRef.length(),
					line.length() - 15);
			route.busStops.add(busStopsFromNaptanData.get(code));

			/* Checking whether route has a new stop */
			while (!line.contains(annotatedRefClose)) {
				line = stopsInput.readLine();
			}
			if (!stopsInput.readLine().contains(annotatedRefOpen)) {
				/* Case when route has no new stops */
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

	}

	static void readBusStopsFromOldFile(File stopsFile) throws IOException {
		Scanner scanner = Utils.csvScanner(stopsFile);
		/* Skipping header line */
		scanner.nextLine();

		/*
		 * Assumption is that stopsFile contains lines of the form
		 * stop_id,stop_code,stop_name,stop_lat,stop_lon
		 */
		while (scanner.hasNext()) {
			long stopId = scanner.nextLong();
			/* Skipping stop_code */
			scanner.next();

			BusStop busStop = new BusStop(scanner.next(), scanner.nextDouble(),
					scanner.nextDouble());
			busStopsFromOldData.put(stopId, busStop);
		}
		scanner.close();
	}

	/* WARNING: tripsFile has to be well formatted! */
	static void readRouteFromOldFile(File routesFile, File outputRoutesFolder)
			throws IOException {
		Scanner scanner = Utils.csvScanner(routesFile);
		/* Skip 'trip_id,arrival_time,departure_time,id,sequence_nr,name' */
		scanner.nextLine();

		String tripName = scanner.next();
		while (!(tripName.equals("end_of_file"))) {
			Route route = new Route(tripName, new ArrayList<BusStop>());
			while (tripName.equals(route.name)) {
				/* Skipping arrival and departure times */
				scanner.next();
				scanner.next();
				route.busStops.add(busStopsFromOldData.get(scanner.nextLong()));

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
				route.writeToFolder(outputRoutesFolder);
			} else {
				System.out.println("Duplicate " + route.name + " found!");
			}
		}
	}

}
