package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Scanner;

/*
 * Trip is a consecutive list of GPS points that a bus followed.
 * 
 * NOTE: an instance of Trip class doesn't have to be a full trip (e.g. if a bus
 * had a journey Cambridge - Stansted - London - back to Cambridge, then
 * Stansted - London part can be considered as a trip on its own).
 */
public class Trip {
	static final Long MILLISECONDS_IN_SECOND = 1000L;

	final String name;
	final ArrayList<GpsPoint> gpsPoints;

	Trip(File file) throws IOException, ParseException {
		name = file.getName();
		gpsPoints = new ArrayList<GpsPoint>();
		Scanner scanner = Utils.csvScanner(file);

		/* To skip "timestamp,latitude,longitude" line */
		scanner.nextLine();

		/* Reading GPS points one by one */
		while (scanner.hasNext()) {
			gpsPoints.add(
					new GpsPoint(Utils.convertDateToTimestamp(scanner.next()),
							scanner.nextDouble(), scanner.nextDouble()));
		}
		scanner.close();
	}

	Trip(String name, ArrayList<GpsPoint> gpsPoints) {
		this.name = name;
		this.gpsPoints = gpsPoints;
	}

	/* To construct empty trip to which we will add GPS points. */
	Trip(String name) {
		this.name = name;
		gpsPoints = new ArrayList<GpsPoint>();
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

	Trip extractTimeInterval(long from, long until) {
		int startingIndex = 0;
		while (startingIndex < gpsPoints.size()
				&& gpsPoints.get(startingIndex).timestamp <= from) {
			startingIndex++;
		}
		int lastIndex = startingIndex;
		while (lastIndex < gpsPoints.size()
				&& gpsPoints.get(lastIndex).timestamp <= until) {
			lastIndex++;
		}
		return new Trip(name, new ArrayList<GpsPoint>(
				gpsPoints.subList(startingIndex, lastIndex)));
	}

	static ArrayList<Trip> extractTripsFromFolder(File folder)
			throws IOException, ParseException {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		File[] files = folder.listFiles();
		for (File file : files) {
			trips.add(new Trip(file));
		}
		return trips;
	}

	void writeToFolder(String folderName) throws IOException, ParseException {
		writeToFolder(new File(folderName));
	}

	void writeToFolder(File folder) throws IOException, ParseException {
		folder.mkdir();
		BufferedWriter writer = new BufferedWriter(
				new FileWriter(folder + "/" + name));
		Utils.writeLine(writer, "timestamp,latitude,longitude");
		for (GpsPoint point : gpsPoints) {
			point.write(writer);
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
