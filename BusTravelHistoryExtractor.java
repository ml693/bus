/*
 * This program takes JSON files as an input, extracts info about buses from
 * input, and for each bus X produces an output file busX, containing the
 * information about the bus X.
 * 
 * // Example how to extract files from ./json directory and place output files
 * // to ./buses directory
 * java BusTravelHistoryExtractor ./json ./buses
 * 
 * // Example JSON input file ./json/file1
 * |||||||||||||||||||||||||||||| NEW FILE ||||||||||||||||||||||||||||||||||||
 * || {"timestamp":1476227188,"latitude":51.89, "longitude":0.453}
 * |||||||||||||||||||||||||||||| END OF FILE |||||||||||||||||||||||||||||||||
 * 
 * TODO(ml693): eliminate constrain for whole input to be stored in one line.
 * TODO(ml693): add extra fields to the example to show it can contain more.
 * 
 * // Example csv output file ./buses/bus13
 * |||||||||||||||||||||||||||||| NEW FILE ||||||||||||||||||||||||||||||||||||
 * || timestamp,latitude,longitude
 * || 51.89,0.453,1476227188
 * |||||||||||||||||||||||||||||| END OF FILE |||||||||||||||||||||||||||||||||
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

	static void AddBusesToMap(File file) throws IOException {
		BufferedReader jsonInput = new BufferedReader(new FileReader(file));
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
			/* And store info in the map */
			buses.get(busSnapshot.vehicleId).add(busSnapshot);
		}

		jsonInput.close();
	}

	static void PrintBusHistory(String busDirectory, int busId)
			throws IOException {
		/*
		 * TODO(ml693): this "if" check is redundant so far, because buses map
		 * will always contain vehicleId key. Either eliminate the check or
		 * comment about it.
		 */
		if (buses.containsKey(busId)) {

			BufferedWriter busOutput = new BufferedWriter(new FileWriter(
					busDirectory + "/bus" + Integer.toString(busId)));
			ArrayList<BusSnapshot> history = buses.get(busId);
			Utils.WriteLine(busOutput, "timestamp,latitude,longitude");
			for (BusSnapshot busSnapshot : history) {
				busSnapshot.gpsPoint.Write(busOutput);
			}
			busOutput.close();

		}
	}

	public static void main(String args[]) throws IOException {
		/*
		 * args[0] is a directory name containing JSON files. We first extract
		 * all files from the directory.
		 */
		File[] jsonFiles = new File(args[0]).listFiles();
		/* Then we process each file in the directory */
		for (File jsonFile : jsonFiles) {
			AddBusesToMap(jsonFile);
		}
		/* Finally we output the processed data into args[1] directory */
		for (int busId : buses.keySet()) {
			PrintBusHistory(args[1], busId);
		}
	}
}
