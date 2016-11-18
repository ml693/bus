/*
 * This program takes JSON files as an input, extracts info about
 * busesTravelHistory from
 * input, and for each bus X produces an output file busX, containing the
 * information about the bus X.
 * 
 * // Example how to extract files from ./json directory and place output files
 * // to ./busesTravelHistory directory
 * java BusTravelHistoryExtractor ./json ./busesTravelHistory
 * 
 * // Example JSON input file ./json/file1
 * |||||||||||||||||||||||||||||| NEW FILE ||||||||||||||||||||||||||||||||||||
 * || {"timestamp":1476227188,"latitude":51.89, "longitude":0.453}
 * |||||||||||||||||||||||||||||| END OF FILE |||||||||||||||||||||||||||||||||
 * 
 * TODO(ml693): eliminate constrain for whole input to be stored in one line.
 * TODO(ml693): add extra fields to the example to show it can contain more.
 * 
 * // Example csv output file ./busesTravelHistory/bus13
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
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bus.Utils.GpsPoint;
import bus.Utils.TravelHistory;

public class BusTravelHistoryExtractor {
	/*
	 * Internal map to store data. At the end this map will get printed as a
	 * list of files to the output directory.
	 */
	static HashMap<Integer, TravelHistory> busesTravelHistory = new HashMap<Integer, TravelHistory>();

	static int ExtractVehicleId(String snapshot) {
		Pattern pattern = Pattern.compile("vehicle_id" + "\":\"" + "[0-9]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Integer.parseInt(matcher.group().substring(13));
	}

	static void UpdateBusesTravelHistoryWithFile(File file) throws IOException {
		BufferedReader jsonInput = new BufferedReader(new FileReader(file));
		String line = jsonInput.readLine();
		/* Matches a new bus entry in the file */
		Pattern pattern = Pattern.compile("\\{\"received_timestamp" + "[^}]*");
		Matcher matcher = pattern.matcher(line);

		/* For each bus entry */
		while (matcher.find()) {
			/* We extract bus info */
			String busSnapshotTextEntry = matcher.group();
			int busId = ExtractVehicleId(busSnapshotTextEntry);
			GpsPoint gpsPoint = new GpsPoint(busSnapshotTextEntry);

			if (!busesTravelHistory.containsKey(busId)) {
				busesTravelHistory.put(busId, new TravelHistory());
			}
			/* And store info into the map */
			busesTravelHistory.get(busId).add(gpsPoint);
		}

		jsonInput.close();
	}

	static TravelHistory GetSortedHistoryFromMap(int busId) {
		TravelHistory travelHistory = busesTravelHistory.get(busId);
		Collections.sort(travelHistory,
				(gps1, gps2) -> Integer.compare(gps1.timestamp, gps2.timestamp));
		return travelHistory;
	}

	static void PrintBusHistory(String busDirectory, int busId)
			throws IOException {
		/*
		 * TODO(ml693): this "if" check is redundant so far, because
		 * busesTravelHistory map will always contain busId key.
		 * Either eliminate the check or comment about it.
		 */
		if (busesTravelHistory.containsKey(busId)) {

			BufferedWriter busOutput = new BufferedWriter(new FileWriter(
					busDirectory + "/bus" + Integer.toString(busId)));
			TravelHistory travelHistory = GetSortedHistoryFromMap(busId);
			Utils.WriteLine(busOutput, "timestamp,latitude,longitude");
			for (GpsPoint gpsPoint : travelHistory) {
				gpsPoint.Write(busOutput);
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
			UpdateBusesTravelHistoryWithFile(jsonFile);
		}
		/* Finally we output the processed data into args[1] directory */
		for (int busId : busesTravelHistory.keySet()) {
			PrintBusHistory(args[1], busId);
		}
	}
}
