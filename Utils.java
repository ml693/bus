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

	/*
	 * This should be used to extract either a travel history or a trip.
	 * WARNING: trip and travel history in this project are viewed as different
	 * things, although they both are ArrayList<GpsPoint>.
	 * TODO(ml693): figure out how to merge trip and history into 1 thing.
	 * 
	 * Travel history is bus GPS history for some time interval.
	 * Suppose a bus started to travel from point A and after 2 hours it has
	 * travelled through points B and C. Then all of {ABC, AB, BC, A, B, C}
	 * would be valid histories:
	 *
	 * Trip is either a past path that a bus followed
	 * WITHOUT PAUSING (short break at the bus stop does not count as pause) or
	 * a predetermined future route that a bus SHOULD FOLLOW. For example,
	 * Cambridge - London - back to Cambridge would be a trip.
	 * 
	 * It's often the case that the bus does NOT FOLLOW any trip exactly, hence
	 * the travel history tells the exact path a bus followed for
	 * some interval of time.
	 */
	static ArrayList<GpsPoint> gpsPointsFromFile(File file) throws IOException {
		ArrayList<GpsPoint> points = new ArrayList<GpsPoint>();
		Scanner scanner = new Scanner(file).useDelimiter(",|\\n");
		/* To skip "timestamp,latitude,longitude" line */
		scanner.nextLine();
		while (scanner.hasNext()) {
			points.add(new GpsPoint(scanner.nextInt(), scanner.nextDouble(),
					scanner.nextDouble()));
		}
		scanner.close();
		return points;
	}

	static void CheckCommandLineArguments(String[] args) throws IOException {
		if (args.length != 2) {
			throw (new IOException("Wrong command line arguments provided. "
					+ "These should be [path_to_file] [path_to_folder]"));
		}
	}
}
