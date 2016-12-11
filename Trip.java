package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import bus.Utils.GpsPoint;

/*
 * Trip is either a past path that a bus followed WITHOUT PAUSING (short break
 * at the bus stop does not count as pause) or a predetermined future route that
 * a bus SHOULD FOLLOW. For example, Cambridge - London - back to Cambridge
 * would be a trip.
 */
public class Trip {
	final String name;
	final ArrayList<GpsPoint> gpsPoints;

	Trip(File file) throws IOException {
		name = file.getName();
		gpsPoints = new ArrayList<GpsPoint>();
		Scanner scanner = new Scanner(file).useDelimiter(",|\\n");

		/* To skip "timestamp,latitude,longitude" line */
		scanner.nextLine();

		/* Reading Gps points one by one */
		while (scanner.hasNext()) {
			gpsPoints.add(new GpsPoint(scanner.nextInt(), scanner.nextDouble(),
					scanner.nextDouble()));
		}
		scanner.close();
	}

	Trip(String name, ArrayList<GpsPoint> gpsPoints) {
		this.name = name;
		this.gpsPoints = gpsPoints;
	}

	/* Adds an offset to each trip's timestamp */
	Trip shiftTimeTo(long startingTimestamp) {
		Trip shiftedTrip = new Trip(name, new ArrayList<GpsPoint>());
		long timeShift = startingTimestamp - gpsPoints.get(0).timestamp;
		for (GpsPoint point : gpsPoints) {
			GpsPoint shiftedPoint = new GpsPoint(point.timestamp + timeShift,
					point.latitude, point.longitude);
			shiftedTrip.gpsPoints.add(shiftedPoint);
		}
		return shiftedTrip;
	}

	void writeToFolder(String folderName) throws IOException {
		writeToFolder(new File(folderName));
	}

	void writeToFolder(File folderName) throws IOException {
		BufferedWriter writer = new BufferedWriter(
				new FileWriter(folderName + "/" + name));
		Utils.WriteLine(writer, "timestamp,latitude,longitude");
		for (GpsPoint point : gpsPoints) {
			point.Write(writer);
		}
		writer.close();
	}

	GpsPoint lastPoint() {
		return gpsPoints.get(gpsPoints.size() - 1);
	}

	/* Returns the length of this trip in seconds */
	long duration() {
		return lastPoint().timestamp - gpsPoints.get(0).timestamp;
	}
}
