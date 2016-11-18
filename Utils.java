/*
 * Auxiliary file containing small fragments of code that are used in more than
 * one file.
 */
package bus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Utils {

	static void WriteLine(BufferedWriter file, String line) throws IOException {
		file.write(line);
		file.newLine();
	}

	/*
	 * TravelHistory class is bus GPS history for some time interval.
	 * Suppose a bus started to travel from point A and after 2 hours it has so
	 * far travelled through points B, and C. Then all of the following would be
	 * valid histories:
	 * A - B - C;
	 * A - B;
	 * B - C;
	 * A;
	 * B;
	 * C;
	 */
	@SuppressWarnings("serial")
	static class TravelHistory extends ArrayList<GpsPoint> {
	}

	/*
	 * This class defines what a route is. Route is a predetermined
	 * path that a bus SHOULD follow. But it's often the case
	 * that the bus does NOT follow the route exactly. The travel
	 * history class above tells the exact path bus followed.
	 */
	static class Route extends ArrayList<GpsPoint> {
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
