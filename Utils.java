/*
 * Auxiliary file for code fragments used in more than one class.
 */
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

public class Utils {

	static void WriteLine(BufferedWriter writer, String line)
			throws IOException {
		writer.write(line);
		writer.newLine();
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
		double longitudeDifference = p1.longitude - p2.longitude;
		return latitudeDifference * latitudeDifference
				+ longitudeDifference * longitudeDifference;
	}

	private static boolean fileMatchesExpectation(String fileName,
			String expectation) throws IOException {
		switch (expectation) {
		case "file":
			return new File(fileName).isFile();
		case "folder":
			return new File(fileName).isDirectory();
		default:
			throw (new IOException("Argument expectation is given wrong name"));
		}
	}

	static void check2CommandLineArguments(String[] args, String firstArgument,
			String secondArgument) throws IOException {
		if (args.length != 2) {
			throw (new IOException(
					"Wrong number of command line arguments. Should be 2 but "
							+ args.length + " were given."));
		}
		if (!fileMatchesExpectation(args[0], firstArgument)) {
			throw (new IOException(
					"First command line argument is wrong. It has to be a "
							+ firstArgument + " name."));
		}
		if (!fileMatchesExpectation(args[0], firstArgument)) {
			throw (new IOException(
					"Second command line argument is wrong. It has to be a "
							+ secondArgument + " name."));
		}
	}
}
