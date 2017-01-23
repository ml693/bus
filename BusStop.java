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

	String serialize() {
		return name + "," + String.format("%.5f", latitude) + ","
				+ String.format("%.5f", longitude);
	}

	void write(BufferedWriter writer) throws IOException {
		Utils.writeLine(writer, serialize());
	}

	void println() {
		System.out.println(serialize());
	}

}
