package bus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GpsPoint {
	final long timestamp;
	final double latitude;
	final double longitude;

	public GpsPoint(long timestamp, double latitude, double longitude) {
		this.timestamp = timestamp;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/*
	 * Example jsonTextEntry: '{vehicle_id="4","timestamp":1476227188,
	 * "latitude":51.89,"longitude":0.453, "trip_id":EBS_22}'
	 */
	public GpsPoint(String jsonTextEntry) {
		timestamp = extractTimestamp(jsonTextEntry);
		latitude = extractCoordinate("latitude", jsonTextEntry);
		longitude = extractCoordinate("longitude", jsonTextEntry);
	}

	/* Helper function to construct GpsPoint from jsonTextEntry */
	private long extractTimestamp(String jsonTextEntry) {
		/*
		 * It's important to include \" character to extract
		 * "timestamp" but not "received_timestamp".
		 */
		String timestampRegex = "\"timestamp\":";
		String integerRegex = "[0-9]+";
		Pattern pattern = Pattern.compile(timestampRegex + integerRegex);
		Matcher matcher = pattern.matcher(jsonTextEntry);
		matcher.find();
		return Long.parseLong(matcher.group().substring(12));
	}

	/* Helper function to construct GpsPoint from jsonTextEntry */
	private double extractCoordinate(String coordinate, String jsonTextEntry) {
		Pattern pattern = Pattern.compile(coordinate + "\":" + "[^,]+");
		Matcher matcher = pattern.matcher(jsonTextEntry);
		matcher.find();
		return Double.parseDouble(
				matcher.group().substring(coordinate.length() + 2));
	}

	/* For printing */
	private String serializeToString() {
		String date = Utils.convertTimestampToDate(timestamp);
		return date + "," + String.format("%.5f", latitude) + ","
				+ String.format("%.5f", longitude);
	}

	void write(BufferedWriter writer) throws IOException {
		Utils.writeLine(writer, serializeToString());
	}

	void println() {
		System.out.println(serializeToString());
	}

	private static double DISTANCE_TOO_SMALL_TO_CONSIDER = 0.00002f;
	private static double SIGNIFICANT_RATIO_THRESHOLD = 1.05f;

	/*
	 * Returns the ratio (a + b) / c, where a and b are distances to segment's
	 * corners, c is the segment's length. For GPS point very close to the
	 * segment we return 1 instead of a ratio.
	 */
	double ratioToSegmentCorners(GpsPoint corner1, GpsPoint corner2) {
		double distanceToCorners = Utils.distance(this, corner1)
				+ Utils.distance(this, corner2);
		if (distanceToCorners <= DISTANCE_TOO_SMALL_TO_CONSIDER) {
			return 1.0;
		}
		double ratioError = distanceToCorners
				/ Utils.distance(corner1, corner2);
		return ratioError < SIGNIFICANT_RATIO_THRESHOLD ? 1.0 : ratioError;
	}
}