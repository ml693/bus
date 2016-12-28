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

	void write(BufferedWriter writer) throws IOException {
		Utils.writeLine(writer, name + "," + String.format("%.4f", latitude)
				+ "," + String.format("%.4f", longitude));
	}

	void println() {
		System.out.println(
				"Stop: " + name + ",lat=" + latitude + ",lon=" + longitude);
	}

}
