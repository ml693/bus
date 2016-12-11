/*
 * Auxiliary file for code fragments used in more than one class.
 */
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	static void CheckCommandLineArguments(String[] args) throws IOException {
		if (args.length != 2) {
			throw (new IOException("Wrong command line arguments provided. "
					+ "These should be [path_to_file] [path_to_folder]"));
		}
	}
}
