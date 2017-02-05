package bus;

import java.io.BufferedWriter;
import java.io.IOException;

public class BusStop {
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

	boolean atBusStop(GpsPoint point) {
		GpsPoint stopPoint = new GpsPoint(0L, this.latitude, this.longitude);
		return (Utils.distance(point, stopPoint) < 0.00002);
	}

	void write(BufferedWriter writer) throws IOException {
		Utils.writeLine(writer, serializeToString());
	}

	void println() {
		System.out.println(serializeToString());
	}

}
