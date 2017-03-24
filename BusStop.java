package bus;

import java.io.BufferedWriter;
import java.io.IOException;

public class BusStop {
	private static final double AT_STOP_RANGE = 0.000007;

	final String name;
	final double latitude;
	final double longitude;

	BusStop(String name, double latitude, double longitude) {
		this.name = name;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	String serializeToString() {
		return name + "," + String.format("%.5f", latitude) + ","
				+ String.format("%.5f", longitude);
	}

	void write(BufferedWriter writer) throws IOException {
		Utils.writeLine(writer, serializeToString());
	}

	void println() {
		System.out.println(serializeToString());
	}

	boolean atStop(GpsPoint point) {
		GpsPoint stopPoint = new GpsPoint(0L, this.latitude, this.longitude);
		return (Utils.distance(point, stopPoint) < AT_STOP_RANGE);
	}
}
