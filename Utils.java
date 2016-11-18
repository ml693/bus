/*
 * Auxiliary file for code fragments used in more than one class.
 */
package bus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bus.Utils.GpsPoint;

public class Utils {

	static void WriteLine(BufferedWriter file, String line) throws IOException {
		file.write(line);
		file.newLine();
	}

	static class GpsPoint {
		final int timestamp;
		final double latitude;
		final double longitude;

		public GpsPoint(int time, double lat, double lon) {
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
			Pattern pattern = Pattern.compile("timestamp" + "\":" + "[0-9]+");
			Matcher matcher = pattern.matcher(jsonTextEntry);
			matcher.find();
			return Integer.parseInt(matcher.group().substring(11));
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

		void Write(BufferedWriter file) throws IOException {
			WriteLine(file, timestamp + "," + String.format("%.4f", latitude)
					+ "," + String.format("%.4f", longitude));
		}
	}

	/*
	 * Geometric distance between two GPS points in space, when latitude
	 * represents y coordinate and longitude represents x coordinate;
	 */
	static double Distance(GpsPoint p1, GpsPoint p2) {
		double squaredDistance = Math.pow((p1.latitude - p2.latitude), 2)
				+ Math.pow(p1.longitude - p2.longitude, 2);
		return Math.sqrt(squaredDistance);
	}
}
