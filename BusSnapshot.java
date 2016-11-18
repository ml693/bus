/*
 * TODO(ml693): add documentation
 */

package bus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bus.Utils.GpsPoint;

class BusSnapshot {
	final int vehicleId;
	final Utils.GpsPoint gpsPoint;

	static int ExtractVehicleId(String snapshot) {
		Pattern pattern = Pattern.compile("vehicle_id" + "\":\"" + "[0-9]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Integer.parseInt(matcher.group().substring(13));
	}

	static int ExtractTimestamp(String snapshot) {
		Pattern pattern = Pattern.compile("timestamp" + "\":" + "[0-9]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Integer.parseInt(matcher.group().substring(11));
	}

	static double ExtractCoordinate(String coordinate, String snapshot) {
		Pattern pattern = Pattern.compile(coordinate + "\":" + "[^,]+");
		Matcher matcher = pattern.matcher(snapshot);
		matcher.find();
		return Double.parseDouble(
				matcher.group().substring(coordinate.length() + 2));
	}

	BusSnapshot(String snapshot) {
		vehicleId = ExtractVehicleId(snapshot);
		gpsPoint = new GpsPoint(ExtractTimestamp(snapshot),
				ExtractCoordinate("latitude", snapshot),
				ExtractCoordinate("longitude", snapshot));
	}
}
