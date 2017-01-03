/*
 * Auxiliary file for code fragments used in more than one class.
 */
package bus;

import bus.ProjectSpecificException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

public class Utils {

	static Scanner csvScanner(File file) throws IOException {
		/* \r is for windows-style line separator */
		return new Scanner(file).useDelimiter(",|\r?\n");
	}

	static void writeLine(BufferedWriter writer, String line)
			throws IOException {
		writer.write(line);
		writer.newLine();
	}

	static ArrayList<Trip> extractTripsFromFiles(File[] files)
			throws Exception {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		for (File file : files) {
			trips.add(new Trip(file));
		}
		return trips;
	}

	/*
	 * Geometric distance between two GPS points in space, when latitude
	 * represents y coordinate and longitude represents x coordinate;
	 */
	static double Distance(GpsPoint p1, GpsPoint p2) {
		return Math.sqrt(squaredDistance(p1, p2));
	}

	static double squaredDistance(GpsPoint p1, GpsPoint p2) {
		double latitudeDifference = p1.latitude - p2.latitude;
		/*
		 * In UK difference in latitude looks about twice larger than
		 * difference in longitude, hence need to rescale.
		 */
		double longitudeDifference = 2 * (p1.longitude - p2.longitude);
		return latitudeDifference * latitudeDifference
				+ longitudeDifference * longitudeDifference;
	}

	private static boolean fileMatchesExpectation(String fileName,
			String expectation) throws ProjectSpecificException {
		switch (expectation) {
		case "file":
			return new File(fileName).isFile();
		case "folder":
			return new File(fileName).isDirectory();
		default:
			throw (new ProjectSpecificException(
					"Argument expectation is given wrong name"));
		}
	}

	/*
	 * Function checking whether appropriate files and folders exist. E.g.
	 * checkCommandLineArguments({"a", "b", "b/c"}, "file", "folder", "file")
	 * Checks whether there exists a file "a", a folder "b" and a file "c"
	 * inside the folder "b".
	 */
	static void checkCommandLineArguments(String[] args, String... fileOrFolder)
			throws ProjectSpecificException {
		if (args.length != fileOrFolder.length) {
			throw (new ProjectSpecificException(
					"Wrong number of command line arguments. Should be "
							+ fileOrFolder.length + " but " + args.length
							+ " were given."));
		}

		for (int i = 0; i < args.length; i++) {
			if (!fileMatchesExpectation(args[i], fileOrFolder[i])) {
				throw (new ProjectSpecificException(
						"Command line argument number " + i
								+ " (counting from 0) is wrong. It has to be a "
								+ fileOrFolder[i] + " name."));
			}
		}
	}

	static long convertDateToTimestamp(String date) throws ParseException {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				"yyyy-mm-dd HH:mm:ss");
		Date time = simpleDateFormat.parse(date);
		return time.getTime() / 1000;
	}

	static String convertTimestampToDate(Long timestamp) throws ParseException {
		Date date = new Date(timestamp * 1000);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");
		return simpleDateFormat.format(date);
	}
}
