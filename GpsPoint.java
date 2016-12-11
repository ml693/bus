package bus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GpsPoint {
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
	static double ExtractCoordinate(String coordinate, String jsonTextEntry) {
		Pattern pattern = Pattern.compile(coordinate + "\":" + "[^,]+");
		Matcher matcher = pattern.matcher(jsonTextEntry);
		matcher.find();
		return Double.parseDouble(
				matcher.group().substring(coordinate.length() + 2));
	}

	void Write(BufferedWriter writer) throws IOException {
		Utils.WriteLine(writer,
				timestamp + "," + String.format("%.4f", latitude) + ","
						+ String.format("%.4f", longitude));
	}

	void println() {
		System.out.println(timestamp + "," + String.format("%.4f", latitude)
				+ "," + String.format("%.4f", longitude));
	}
}