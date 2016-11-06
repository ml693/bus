/* 
 * This program takes JSON files as an input,
 * extracts info about buses from input,
 * and for each bus X produces an output file busX,
 * containing the information for the bus X.
 * 
 * Example usage: will extract files from ./json directory and place output files to ./buses directory
 * java BusTravelHistoryExtractor ./json ./buses
 * 
 * // Example JSON input file:
 * {"received_timestamp":1476227188,"vehicle_id":"8","latitude":51.89,"longitude":0.453,"timestamp":1476227173}
 * TODO(ml693): remove current constrain expecting whole input file be stored in one line.
 * 
 * // Example output file ./buses/bus13 (contains latitude, longitude and timestamp)
 * 52.30 -0.086 1476251758
 * // TODO(ml693): add more data fields into the output file
 */
package bus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BusTravelHistoryExtractor {

	/*
	 * Internal map to store data. At the end this map will get printed as a
	 * list of files to the output directory.
	 */
	static HashMap<Integer, ArrayList<BusSnapshot>> buses = new HashMap<Integer, ArrayList<BusSnapshot>>();

	static void AddBusesToMap(String fileName) throws IOException {
		BufferedReader jsonInput = new BufferedReader(new FileReader(fileName));
		String line = jsonInput.readLine();
		/* Matches a new bus entry in the file */
		Pattern pattern = Pattern.compile("\\{\"received_timestamp" + "[^}]*");
		Matcher matcher = pattern.matcher(line);

		/* For each bus entry */
		while (matcher.find()) {
			/* We extract bus info */
			BusSnapshot busSnapshot = new BusSnapshot(matcher.group());
			if (!buses.containsKey(busSnapshot.vehicleId)) {
				buses.put(busSnapshot.vehicleId, new ArrayList<BusSnapshot>());
			}
			/* And store info into the map */
			buses.get(busSnapshot.vehicleId).add(busSnapshot);
		}

		jsonInput.close();
	}

	/*
	 * TODO(ml693): same method is present in BusStopsExtractor file. Think
	 * where to move the method to avoid code duplication.
	 */
	static void WriteLine(BufferedWriter file, String line) throws IOException {
		file.write(line);
		file.newLine();
	}

	static void PrintBusHistory(String busDirectory, int vehicleId) throws IOException {
		if (buses.containsKey(vehicleId)) {
			BufferedWriter outputFile = new BufferedWriter(
					new FileWriter(busDirectory + "/bus" + Integer.toString(vehicleId)));
			ArrayList<BusSnapshot> history = buses.get(vehicleId);
			for (BusSnapshot busSnapshot : history) {
				WriteLine(outputFile, busSnapshot.latitude + " " + busSnapshot.longitude + " " + busSnapshot.timestamp);
			}
			outputFile.close();
		}
	}

	static ArrayList<String> GetFileNames(String folderName) {
		File folder = new File(folderName);
		File[] listOfFiles = folder.listFiles();
		ArrayList<String> fileNames = new ArrayList<String>();
		for (File file : listOfFiles) {
			fileNames.add(folder.getName() + "/" + file.getName());
		}
		Collections.sort(fileNames);
		return fileNames;
	}

	public static void main(String args[]) throws IOException {
		/*
		 * args[0] is a directory containing JSON files. We first extract all
		 * file names from the directory.
		 */
		ArrayList<String> jsonFiles = GetFileNames(args[0]);
		/* Then we process each file in the directory */
		for (String jsonFile : jsonFiles) {
			AddBusesToMap(jsonFile);
		}

		/* Finally we output the processed data in args[1] directory */
		for (int busId : buses.keySet()) {
			PrintBusHistory(args[1], busId);
		}
	}
}
