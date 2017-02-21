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
	 * Internal map to store data. At the end this map will get printed as a
	 * list of files to the output directory.
	 */
	static HashMap<String, ArrayList<GpsPoint>> allHistories = new HashMap<String, ArrayList<GpsPoint>>();

	/*
	 * This program takes JSON files as an input, extracts info about buses
	 * travel histories from input, and for each bus X produces a separate
	 * output file containing the information about the bus X.
	 * 
	 * To generate output files from json_folder into history_folder run
	 * java BusTravelHistoryExtractor json_folder travel_history_folder
	 * 
	 * Example JSON input file
	 * json_folder/file1:
	 * {vehicle_id="4","timestamp":1476227188, "bearing":162.0
	 * "latitude":51.8944,"longitude":0.4532, "route_id":"CBL-10"}
	 *
	 * Example CSV output file (timestamp is converted to a readable time field)
	 * buses_travel_history/bus4:
	 * time,latitude,longitude
	 * 2016-10-11 16:06:28,51.8944,0.4532
	 * ... // file will contain more entries generated from other files
	 * 
	 * TODO(ml693): eliminate constrain for whole input to be in one line.
	 * TODO(ml693): add extra fields to the example to show it can contain more.
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
		 * would put data into allHistories in order. That would
		 * allow us to avoid sorting the allHistories map.
		 */
		Arrays.sort(jsonFiles, (file1, file2) -> file1.compareTo(file2));
		for (File jsonFile : jsonFiles) {
			updateBusesTravelHistoryWithFile(jsonFile);
		}
		/* At the end we output the processed data into args[1] folder */
		File outputFolder = new File(args[1]);
		for (String key : allHistories.keySet()) {
			try {
				new Trip(key, allHistories.get(key))
						.writeToFolder(outputFolder);
			} catch (ProjectSpecificException exception) {
				System.out.println(key + " has too little GPS entries!");
			}
		}
		System.out.println("Done with " + args[0]);
	}

	static int extractVehicleId(String snapshot) {
		Pattern pattern = Pattern.compile("vehicle_id" + "\":\"" + "[0-9]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Integer.parseInt(matcher.group().substring(13));
	}

	static void updateBusesTravelHistoryWithFile(File file) {
		try {
			BufferedReader jsonInput = new BufferedReader(new FileReader(file));
			String line = jsonInput.readLine();
			/* Matches a new bus entry in the file */
			String openParenthesesRegex = "\\{";
			String untilClosedParenthesesRegex = "[^}]*";
			Pattern pattern = Pattern.compile(
					openParenthesesRegex + untilClosedParenthesesRegex);
			Matcher matcher = pattern.matcher(line);

			/* For each bus entry */
			while (matcher.find()) {
				/* We extract bus info */
				String busSnapshotTextEntry = matcher.group();
				String key = "day" + file.getParentFile().getName() + "_bus"
						+ extractVehicleId(busSnapshotTextEntry);
				GpsPoint gpsPoint = new GpsPoint(busSnapshotTextEntry);

				/* And store info into the map */
				if (!allHistories.containsKey(key)) {
					allHistories.put(key, new ArrayList<GpsPoint>());
				}
				if (gpsPoint.latitude != 0.0 || gpsPoint.longitude != 0.0) {
					allHistories.get(key).add(gpsPoint);
				}
			}

			jsonInput.close();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
}
