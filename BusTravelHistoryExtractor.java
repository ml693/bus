package bus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BusTravelHistoryExtractor {
	/*
	 * This program takes JSON files as an input, extracts info about
	 * busesTravelHistory from input, and for each bus X produces an output file
	 * busX, containing the information about the bus X.
	 * 
	 * // Example how to extract files from json_folder and place output files to
	 * // travel_history_folder
	 * java BusTravelHistoryExtractor json_folder travel_history_folder
	 * 
	 * // Example JSON input file json_folder/file1
	 * |||||||||||||||||||||||||||||| NEW FILE ||||||||||||||||||||||||||||||||||||
	 * || {vehicle_id="4","timestamp":1476227188,"latitude":51.89,"longitude":0.453}
	 * |||||||||||||||||||||||||||||| END OF FILE |||||||||||||||||||||||||||||||||
	 * 
	 * TODO(ml693): eliminate constrain for whole input to be stored in one line.
	 * TODO(ml693): add extra fields to the example to show it can contain more.
	 * 
	 * // Example CSV output file buses_travel_history/bus4
	 * |||||||||||||||||||||||||||||| NEW FILE ||||||||||||||||||||||||||||||||||||
	 * || timestamp,latitude,longitude
	 * || 1476227188,51.89,0.453
	 * |||||||||||||||||||||||||||||| END OF FILE |||||||||||||||||||||||||||||||||
	 */
	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder");
		/* We first extract all JSON files from the args[0] folder. */
		File[] jsonFiles = new File(args[0]).listFiles();
		/*
		 * TODO(ml693): check whether the expectation below is correct.
		 * 
		 * We expect timestamps received from buses are delivered in order.
		 * 
		 * More formally, we expect that if name of file1 is alphabetically
		 * smaller than name of file2, then each x1 value in the file1 of the
		 * form "timestamp":"x1" will be smaller than each x2 value in the file2
		 * of the form "timestamp":"x2".
		 * 
		 * Therefore, we sort files here so that processing files in order
		 * would put data into allTrips in order. That would
		 * allow us to avoid sorting the busesTravelHistory map.
		 */
		Arrays.sort(jsonFiles, (file1, file2) -> file1.compareTo(file2));
		for (File jsonFile : jsonFiles) {
			System.out.println("Processing: " + jsonFile.getName());
			updateBusesTravelHistoryWithFile(jsonFile);
		}
		/* At the end we output the processed data into args[1] folder */
		File outputFolder = new File(args[1]);
		for (Trip trip : allTrips.values()) {
			trip.writeToFolder(outputFolder);
		}
	}

	/*
	 * Internal map to store data. At the end this map will get printed as a
	 * list of files to the output directory.
	 */
	static HashMap<Integer, Trip> allTrips = new HashMap<Integer, Trip>();

	static int extractVehicleId(String snapshot) {
		Pattern pattern = Pattern.compile("vehicle_id" + "\":\"" + "[0-9]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Integer.parseInt(matcher.group().substring(13));
	}

	static void updateBusesTravelHistoryWithFile(File file) throws IOException {
		BufferedReader jsonInput = new BufferedReader(new FileReader(file));
		String line = jsonInput.readLine();
		/* Matches a new bus entry in the file */
		String openParenthesesRegex = "\\{";
		String untilClosedParenthesesRegex = "[^}]*";
		Pattern pattern = Pattern
				.compile(openParenthesesRegex + untilClosedParenthesesRegex);
		Matcher matcher = pattern.matcher(line);

		/* For each bus entry */
		while (matcher.find()) {
			/* We extract bus info */
			String busSnapshotTextEntry = matcher.group();
			int busId = extractVehicleId(busSnapshotTextEntry);
			GpsPoint gpsPoint = new GpsPoint(busSnapshotTextEntry);

			/* And store info into the map */
			if (!allTrips.containsKey(busId)) {
				allTrips.put(busId, new Trip(
						"day" + file.getParentFile().getName() + "_bus" + busId,
						new ArrayList<GpsPoint>()));
			}
			if (gpsPoint.latitude != 0.0 || gpsPoint.longitude != 0.0) {
				allTrips.get(busId).gpsPoints.add(gpsPoint);
			}
		}

		jsonInput.close();
	}
}
