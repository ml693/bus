/*
 * Program takes stop_times.txt, stops.txt files and extracts all possible
 * routes, each route being saved in a separate file.
 * 
 * TODO(ml693): make code cleaner.
 */
package bus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class RoutesExtractor {

	static HashMap<Long, BusStop> busStops = new HashMap<Long, BusStop>();

	static void readBusStops(File stopsFile) throws IOException {
		Scanner scanner = Utils.csvScanner(stopsFile);

		/* Skipping header line */
		scanner.nextLine();

		/*
		 * Assumption is that stops.txt file contains line, each of the form
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
	static void constructRoutes(File tripsFile, File routesFolder)
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

			route.writeToFolder(routesFolder);
		}
	}

	public static void main(String args[]) throws Exception {
		/*
		 * 1st argument is a folder containing stops.txt, stop_times.txt files.
		 * 2nd argument is a folder where to put routes.
		 */
		Utils.checkCommandLineArguments(args, "folder", "folder");

		readBusStops(new File(args[0] + "/stops.txt"));

		constructRoutes(new File(args[0] + "/stop_times.txt"),
				new File("routes"));
	}

}
