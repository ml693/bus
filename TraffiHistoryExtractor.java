/*
 * TODO(ml693): make code cleaner
 * This class is not important. I wanted to use it on a different source on
 * data, but that's unlikely to happen in the nearest future.
 *
 */
package bus;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

public class TraffiHistoryExtractor {

	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "file", "folder");
		extractDaysHistory(new File(args[0]), new File(args[1]));
	}

	public static void extractDaysHistory(File inputFile, File outputFolder)
			throws IOException, ParseException {
		HashMap<String, ArrayList<GpsPoint>> dayTrips = new HashMap<String, ArrayList<GpsPoint>>();

		Scanner scanner = Utils.csvScanner(inputFile);
		/* Skip timestamp,latitude,etc meta line */
		scanner.nextLine();

		while (scanner.hasNext()) {
			/* Extracting timestamp */
			String timeString = scanner.next();
			DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
			Date time = dfm
					.parse(timeString.substring(0, timeString.length() - 4));
			long timestamp = time.getTime() / 1000;

			String vehicleId = scanner.next().substring(4);
			/* Skipping schedule_id */
			scanner.next();
			/* Skipping track_id */
			scanner.next();

			/* Extracting GPS coordinates */
			double latitude = scanner.nextDouble();
			double longitude = scanner.nextDouble();

			/* Skipping stop_from_id */
			scanner.next();
			/* Skipping stop_to_id */
			scanner.next();
			/* Skipping stop_index_fraction */
			scanner.next();
			/* Skipping distance_along_track */
			scanner.next();
			/* Skipping delay_seconds */
			scanner.next();

			/*
			 * We processed whole line corresponding to one GPS point. It is now
			 * time to put this point into busTravelHistories map.
			 */
			GpsPoint gpsPoint = new GpsPoint(timestamp, latitude, longitude);
			String key = inputFile.getName() + "_id" + vehicleId;
			if (!dayTrips.containsKey(key)) {
				dayTrips.put(key, new ArrayList<GpsPoint>());
			}
			dayTrips.get(key).add(gpsPoint);
		}
		scanner.close();

		for (String key : dayTrips.keySet()) {
			try {
				new Trip(key, dayTrips.get(key)).writeToFolder(outputFolder);
			} catch (ProjectSpecificException exception) {
				System.out.println(key + " has too little GPS entries!");
			}
		}

	}
}