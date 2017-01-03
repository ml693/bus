package bus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
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
	private String serializeToString() throws ParseException {
		String date = Utils.convertTimestampToDate(timestamp);
		return date + "," + String.format("%.4f", latitude) + ","
				+ String.format("%.4f", longitude);
	}

	void write(BufferedWriter writer) throws IOException, ParseException {
		Utils.writeLine(writer, serializeToString());
	}
}