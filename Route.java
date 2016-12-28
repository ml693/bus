/* TODO(ml693): make code cleaner. */

package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

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
}