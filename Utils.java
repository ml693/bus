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

import bus.Utils.GpsPoint;

public class Utils {

	static void WriteLine(BufferedWriter writer, String line)
			throws IOException {
		writer.write(line);
		writer.newLine();
	}

	static class GpsPoint {
		final long timestamp;
		final double latitude;
		final double longitude;

		public GpsPoint(long time, double lat, double lon) {
			timestamp = time;
			latitude = lat;
			longitude = lon;
		}

		public GpsPoint(String jsonTextEntry) {
			timestamp = ExtractTimestamp(jsonTextEntry);
			latitude = ExtractCoordinate("latitude", jsonTextEntry);
			longitude = ExtractCoordinate("longitude", jsonTextEntry);
		}

		/* Helper function to construct GpsPoint from jsonTextEntry */
		static int ExtractTimestamp(String jsonTextEntry) {
			/*
			 * It's important to include \" character to extract
			 * "timestamp" but not "received_timestamp".
			 */
			String timestampRegex = "\"timestamp\":";
			String integerRegex = "[0-9]+";
			Pattern pattern = Pattern.compile(timestampRegex + integerRegex);
			Matcher matcher = pattern.matcher(jsonTextEntry);
			matcher.find();
			return Integer.parseInt(matcher.group().substring(12));
		}

		/* Helper function to construct GpsPoint from jsonTextEntry */
		static double ExtractCoordinate(String coordinate,
				String jsonTextEntry) {
			Pattern pattern = Pattern.compile(coordinate + "\":" + "[^,]+");
			Matcher matcher = pattern.matcher(jsonTextEntry);
			matcher.find();
			return Double.parseDouble(
					matcher.group().substring(coordinate.length() + 2));
		}

		void Write(BufferedWriter writer) throws IOException {
			WriteLine(writer, timestamp + "," + String.format("%.4f", latitude)
					+ "," + String.format("%.4f", longitude));
		}

		void println() {
			System.out.println(timestamp + "," + String.format("%.4f", latitude)
					+ "," + String.format("%.4f", longitude));
		}
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
