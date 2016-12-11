/*
 * Program takes CSV file showing a GPS history of a single bus. It
 * (roughly) splits the GPS history into multiple time intervals and stores each
 * time interval as an output CSV file. The expectation is that each time
 * interval represents a single trip a bus has made. This behaviour is repeated
 * for each file in the input folder.
 * 
 * WARNING: How exactly the content of output files should be generated is not
 * specified precisely. Output content generation procedure might change if we
 * think it's better to extract trips in another way. Some lines from the input
 * file might be missing (e.g. if a bus was on and standing for 30min, we will
 * remove all lines showing that it was standing at the same place).
 * 
 * // Create files output_folder/trip_subtrip0, output_folder/trip_subtrip1, ...
 * java bus.RouteExtractor trip output_folder
 */
package bus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

class TripsExtractor {
	/*
	 * The main method which decides whether the bus started a new trip or not.
	 * We check that based on how long the bus was standing in the same place.
	 * Such heuristic works for now, but can be freely changed if we want.
	 */
	public static boolean newSubtripAccordingToTimestamp(long currentTimestamp,
			long newTimestamp) {
		return (newTimestamp - currentTimestamp) > 600L;
	}

	/*
	 * Writes current sub trip to file if it's long enough.
	 * Does nothing otherwise.
	 */
	public static boolean currentSubTripFlushed(Trip subTrip,
			File folderToFlush) throws IOException {
		if (subTrip.gpsPoints.size() > 30) {
			subTrip.writeToFolder(folderToFlush);
			return true;
		}
		return false;
	}

	public static String generateName(File travelHistoryFile,
			int extractedTripsCount) {
		return travelHistoryFile.getName() + "_subtrip" + extractedTripsCount;
	}

	/*
	 * EXPLANATION OF travelHistoryFile ARGUMENT
	 * 
	 * Input is a CSV file containing a TIMESTAMP-ORDERED list of GPS locations
	 * for a SINGLE bus. Timestamp is the POSIX time. Requirement is that 2
	 * consecutive lines i and (i+1) should have timestamp_i <= timestamp_i+1.
	 * There is no restriction placed on how large or small the difference
	 * (timestamp_i+1 - timestamp_i) should be. In practise the difference will
	 * be ~30s, but might become larger in such example cases:
	 * 
	 * (i) The bus stopped at line_i, turned itself and GPS transmitter off,
	 * after 1h turned back on, hence the timestamp difference will be ~1h =
	 * 3600. Note that latitude and longitude values in line_i will match values
	 * in line_i+1.
	 * 
	 * (ii) The GPS transmitter failed to transmit data while travelling for ~20
	 * minutes, then started to function again. The timestamp difference will be
	 * ~20min = 1200. Note that in this case there might be a fairly big change
	 * in latitude/longitude values between line_i and line_i+1, if the bus
	 * moved much.
	 * 
	 * Latitude and longitude coordinates need only to show the approximate
	 * location where the bus is. That is, if a bus has travelled through the
	 * same location twice, the latitude/longitude coordinates should be roughly
	 * equal, but do not need to match precisely.
	 * 
	 * EXAMPLE CONTENTS OF travelHistoryFile ARGUMENT
	 * 
	 * // New file
	 * timestamp,latitude,longitude // 1st line of file
	 * 1000000000,52.0000,0.2000 // starting in A
	 * 1000000030,52.0001,0.2001 // leaving A
	 * ...
	 * ... // travelling to B
	 * ...
	 * 1000020000,52.1000,0.3000 // arrived at B
	 * 1000020050,52.1000,0.3000 // staying in B
	 * ...
	 * ... // staying in B
	 * ...
	 * 1000020700,52.1001,0.3001 // leaving B
	 * ...
	 * ... // travelling back to A
	 * ...
	 * 1000040008,52.0002,0.2002 // back at A
	 * 1000040070,52.0002,0.2002 // staying in A
	 * ...
	 * ... // staying in A
	 * ...
	 * 1000050030,52.0003,0.2003 // leaving A
	 * ...
	 * ... // travelling to C
	 * ...
	 * 1000080050,51.9000,0.1000 // arrived at C.
	 * // End of file
	 * 
	 * OUTPUT FILES EXPLANATION
	 * 
	 * Output will be multiple CSV files, each containing a TIMESTAMP-ORDERED
	 * list of GPS locations for a SINGLE bus. Each output file viewed as a list
	 * of lines is a sublist of the input file. Each output file should contain
	 * a single bus sub trip. For example, if input file contains a history of a
	 * bus having iterated through one route 5 times, then this program will
	 * create 5 output files, the i-th output file containing the history of the
	 * i-th iteration.
	 * 
	 * EXAMPLE FILES STORED TO OUTPUT DIRECTORY
	 * (these will be generated based on the input example above)
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip0
	 * timestamp,latitude,longitude // 1st line of file
	 * 1000000000,52.0000,0.2000 // starting at A
	 * 1000000030,52.0001,0.2001 // leaving A
	 * ...
	 * ... // travelling to B
	 * ...
	 * 1000020000,52.1000,0.3000 // arrived at B
	 * // End of file outputFolder/travelHistoryFile_subtrip0
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip1
	 * timestamp,latitude,longitude // 1st line of file
	 * 1000020700,52.1001,0.3001 // leaving B
	 * ...
	 * ... // travelling back to A
	 * ...
	 * 1000040008,52.0002,0.2002 // back at A
	 * // End of file outputFolder/travelHistoryFile_subtrip1
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip2
	 * timestamp,latitude,longitude // 1st line of file
	 * 1000050030,52.0003,0.2003 // leaving A
	 * ...
	 * ... // travelling to C
	 * ...
	 * 1000080050,51.9000,0.1000 // arriving at C.
	 * // End of file outputFolder/travelHistoryFile_subtrip2
	 */
	public static void extractTripsFromTravelHistoryFile(File travelHistoryFile,
			File outputFolder) throws IOException {
		/* Preparing variables to read input file */
		int extractedTripsCount = 0;
		long currentTimestamp = 0L;
		double currentLatitude = 0.0;
		double currentLongitude = 0.0;
		Trip currentSubTrip = new Trip(
				generateName(travelHistoryFile, extractedTripsCount),
				new ArrayList<GpsPoint>());
		Scanner gpsInput = new Scanner(travelHistoryFile).useDelimiter(",|\\n");
		/* To skip "timestamp,latitude,longitude" line */
		gpsInput.nextLine();

		/* Main loop reading GPS data from bus history input file */
		while (gpsInput.hasNext()) {
			/*
			 * Note we're relying on correctly formatted input file here. If
			 * input contains wrong number of entries per line, or entries in
			 * wrong order, then the program will either throw an exception or
			 * silently terminate computing wrong results.
			 */
			long newTimestamp = gpsInput.nextLong();
			double newLatitude = gpsInput.nextDouble();
			double newLongitude = gpsInput.nextDouble();

			if (newSubtripAccordingToTimestamp(currentTimestamp,
					newTimestamp)) {
				if (currentSubTripFlushed(currentSubTrip, outputFolder)) {
					extractedTripsCount++;
				}
				currentSubTrip = new Trip(
						generateName(travelHistoryFile, extractedTripsCount),
						new ArrayList<GpsPoint>());
				currentTimestamp = newTimestamp;
			}

			if (newLatitude != currentLatitude
					|| newLongitude != currentLongitude) {
				currentTimestamp = newTimestamp;
				/*
				 * We add new entry to the output file only if the bus is moving
				 * according to the GPS transmitter.
				 */
				currentSubTrip.gpsPoints.add(
						new GpsPoint(newTimestamp, newLatitude, newLongitude));
			}
			currentLatitude = newLatitude;
			currentLongitude = newLongitude;
		}
		/* Don't forget to add the final sub trip */
		if (currentSubTripFlushed(currentSubTrip, outputFolder)) {
			extractedTripsCount++;
		}

		System.out.println("Extracted " + extractedTripsCount + " trips.");
	}

	public static void main(String args[]) throws IOException {
		File[] travelHistoryFiles = (new File(args[0])).listFiles();
		for (File travelHistoryFile : travelHistoryFiles) {
			extractTripsFromTravelHistoryFile(travelHistoryFile,
					new File(args[1]));
		}
	}

}
