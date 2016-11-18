/*
 * Program takes an input CSV file showing a GPS history of a single bus. It
 * (roughly) splits the GPS history into multiple time intervals and stores each
 * time interval as an output CSV file. The expectation is that each time
 * interval represents a single trip a bus has made.
 * 
 * WARNING: How exactly the content of output files should be generated is not
 * specified precisely. Output content generation procedure might change if we
 * think it's better to extract trips in another way. Some lines from the input
 * file might be missing (e.g. if a bus was on and standing for 30min, we will
 * remove all lines showing that it was standing at the same place).
 * 
 * 
 * 
 * EXAMPLE PROGRAM USAGE:
 * 
 * // To create output files output_folder/1, output_folder/2, ...
 * java bus.RouteExtractor bus_history_file output_folder
 * 
 * // To create output files with custom prefix
 * // output_folder/route1, output_folder/route2, ...
 * java bus.RouteExtractor bus_history_file output_folder route
 * 
 * 
 * 
 * INPUT FILE EXPLANATION (bus_history_file above)
 * 
 * Input is a CSV file containing a TIMESTAMP-ORDERED list of GPS locations for
 * a SINGLE bus. Timestamp is the POSIX time. Requirement is that 2 consecutive
 * lines i and (i+1) should have timestamp_i <= timestamp_i+1. There is no
 * restriction placed on how large or small the difference (timestamp_i+1 -
 * timestamp_i) should be. In practise the difference will be ~30s, but might
 * become larger in such example cases:
 * 
 * (i) The bus stopped at line_i, turned itself and GPS transmitter off, after
 * 1h turned back on, hence the timestamp difference will be ~1h = 3600.
 * Note that latitude and longitude values in line_i will match values in
 * line_i+1.
 * 
 * (ii) The GPS transmitter failed to transmit data while travelling for ~20
 * minutes, then started to function again. The timestamp difference will be
 * ~20min = 1200. Note that in this case there might be a fairly big change in
 * latitude/longitude values between line_i and line_i+1, if the bus moved much.
 * 
 * Latitude and longitude coordinates need only to show the approximate location
 * where the bus is. That is, if a bus has travelled through the same location
 * twice, the latitude/longitude coordinates should be roughly equal, but do not
 * need to match precisely.
 * 
 * 
 * 
 * EXAMPLE CONTENTS OF INPUT FILE HAVING NAME args[0]:
 * 
 * ||||||||||||||||||||||||| NEW FILE args[0] |||||||||||||||||||||||||||||||||
 * || timestamp,latitude,longitude // 1st line of file
 * || 1000000000,52.0000,0.2000 // starting in A
 * || 1000000030,52.0001,0.2001 // leaving A
 * || ...
 * || ... // travelling to B
 * || ...
 * || 1000020000,52.1000,0.3000 // arrived at B
 * || 1000020050,52.1000,0.3000 // staying in B
 * || ...
 * || ... // staying in B
 * || ...
 * || 1000020700,52.1001,0.3001 // leaving B
 * || ...
 * || ... // travelling back to A
 * || ...
 * || 1000040008,52.0002,0.2002 // back at A
 * || 1000040070,52.0002,0.2002 // staying in A
 * || ...
 * || ... // staying in A
 * || ...
 * || 1000050030,52.0003,0.2003 // leaving A
 * || ...
 * || ... // travelling to C
 * || ...
 * || 1000080050,51.9000,0.1000 // arrived at C.
 * |||||||||||||||||||||||| END OF FILE args[0] |||||||||||||||||||||||||||||||
 * 
 * 
 * 
 * OUTPUT FILES EXPLANATION (output_folder/1, output_folder/2 files above)
 * 
 * Output will be multiple CSV files, each containing a TIMESTAMP-ORDERED list
 * of GPS locations for a SINGLE bus. Each output file viewed as a list of lines
 * is a sublist of the input file. Each output file should contain a single bus
 * trip. For example, if input file contains a history of a bus having iterated
 * through one route 5 times, then this program will create 5 output files, the
 * i-th output file containing the history of the i-th iteration.
 * 
 * 
 * 
 * EXAMPLE FILES STORED TO OUTPUT DIRECTORY HAVING NAME args[1]:
 * (these will be generated based on the input example above)
 * 
 * |||||||||||||||||||||||| NEW FILE args[1]/route1 |||||||||||||||||||||||||||
 * || timestamp,latitude,longitude // 1st line of file
 * || 1000000000,52.0000,0.2000 // starting at A
 * || 1000000030,52.0001,0.2001 // leaving A
 * || ...
 * || ... // travelling to B
 * || ...
 * || 1000020000,52.1000,0.3000 // arrived at B
 * |||||||||||||||||||||||| END OF FILE args[1]/route2 ||||||||||||||||||||||||
 * 
 * 
 * 
 * ||||||||||||||||||||||| NEW FILE args[1]/route2 ||||||||||||||||||||||||||||
 * || timestamp,latitude,longitude // 1st line of file
 * || 1000020700,52.1001,0.3001 // leaving B
 * || ...
 * || ... // travelling back to A
 * || ...
 * || 1000040008,52.0002,0.2002 // back at A
 * ||||||||||||||||||||||| END OF FILE args[1]/route3 |||||||||||||||||||||||||
 * 
 * 
 * 
 * ||||||||||||||||||||||| NEW FILE args[1]/route2 ||||||||||||||||||||||||||||
 * || timestamp,latitude,longitude // 1st line of file
 * || 1000050030,52.0003,0.2003 // leaving A
 * || ...
 * || ... // travelling to C
 * || ...
 * || 1000080050,51.9000,0.1000 // arriving at C.
 * ||||||||||||||||||||||| END OF FILE args[1]/route3 |||||||||||||||||||||||||
 */
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

class TripsExtractor {
	/*
	 * The main method which decides whether the bus started a new trip or not.
	 * We check that based on how long the bus was standing in the same place.
	 * Such heuristic works for now, but can be freely changed if we want.
	 */
	public static boolean NewTripAccordingToTimestamp(int currentTimestamp,
			int newTimestamp) {
		return (newTimestamp - currentTimestamp) > 600;
	}

	public static void main(String args[]) throws IOException {
		if (args.length < 1 || args.length > 3) {
			System.err.println("Wrong command line arguments provided. "
					+ "These should be [input_file_path] [output_directory_path]"
					+ "and optional [output_files_name_prefix]");
			return;
		}

		/* Preparing variables to output trips */
		BufferedWriter tripOutput = null;
		int routeNumber = 0;
		String outputDirectoryName = args.length == 3 ? args[1] + args[2]
				: args[1];

		/* Preparing variables to read input file */
		int currentTimestamp = 0;
		double currentLatitude = 0.0;
		double currentLongitude = 0.0;
		Scanner gpsInput = new Scanner(new File(args[0])).useDelimiter(",|\\n");
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
			int newTimestamp = gpsInput.nextInt();
			double newLatitude = gpsInput.nextDouble();
			double newLongitude = gpsInput.nextDouble();

			/* If we believe that bus ended one trip and started another */
			if (NewTripAccordingToTimestamp(currentTimestamp, newTimestamp)) {
				/* Then we stop writing to current route file */
				if (tripOutput != null) {
					tripOutput.close();
				}
				/* And start creating new output file */
				routeNumber++;
				tripOutput = new BufferedWriter(
						new FileWriter(outputDirectoryName + routeNumber));
				Utils.WriteLine(tripOutput, "timestamp,latitude,longitude");
			}

			if (newLatitude != currentLatitude
					|| newLongitude != currentLongitude) {
				currentTimestamp = newTimestamp;
				/*
				 * We add new entry to the output file only if the bus is moving
				 * according to the GPS transmitter.
				 */
				Utils.WriteLine(tripOutput,
						newTimestamp + "," + String.format("%.4f", newLatitude)
								+ "," + String.format("%.4f", newLongitude));
			}
			currentLatitude = newLatitude;
			currentLongitude = newLongitude;
		}

		gpsInput.close();
		tripOutput.close();
	}

}