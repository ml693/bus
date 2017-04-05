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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Scanner;

public class TraffiHistoryExtractor {

	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder");

		ArrayList<File> csvDateFiles = Utils.filesInFolder(args[0]);
		for (File file : csvDateFiles) {
			System.out.println("Dealing with file " + file.getName());
			extractDaysHistory(file, args[1]);
		}
	}

	public static void extractDaysHistory(File inputFile,
			String topOutputDirectoryPath) throws IOException, ParseException {
		HashMap<String, HashMap<String, ArrayList<GpsPoint>>> trackTrips = new HashMap<String, HashMap<String, ArrayList<GpsPoint>>>();

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
			Calendar calendar = GregorianCalendar.getInstance();
			calendar.setTime(time);
			if (calendar.get(Calendar.HOUR_OF_DAY) == 0) {
				timestamp += 12 * 3600;
			}

			String vehicleId = scanner.next().substring(4);
			/* Skipping schedule_id */
			String trackId = scanner.next();
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
			if (!trackTrips.containsKey(trackId)) {
				trackTrips.put(trackId,
						new HashMap<String, ArrayList<GpsPoint>>());
			}
			HashMap<String, ArrayList<GpsPoint>> tripsFollowingTrack = trackTrips
					.get(trackId);
			if (!tripsFollowingTrack.containsKey(vehicleId)) {
				tripsFollowingTrack.put(vehicleId, new ArrayList<GpsPoint>());
			}
			GpsPoint gpsPoint = new GpsPoint(timestamp, latitude, longitude);
			tripsFollowingTrack.get(vehicleId).add(gpsPoint);

		}
		scanner.close();

		for (String trackId : trackTrips.keySet()) {
			HashMap<String, ArrayList<GpsPoint>> tripsFollowingTrack = trackTrips
					.get(trackId);
			for (String vehicleId : tripsFollowingTrack.keySet()) {
				try {
					File trackFolder = new File(
							topOutputDirectoryPath + "/" + trackId);
					trackFolder.mkdir();
					new Trip(
							"day" + inputFile.getName().substring(23, 25) + "_"
									+ vehicleId,
							tripsFollowingTrack.get(vehicleId))
									.writeToFolder(trackFolder);
				} catch (ProjectSpecificException exception) {
				}
			}
		}
	}
}