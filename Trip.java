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

	final String name;
	final ArrayList<GpsPoint> gpsPoints;

	Trip(File file) throws IOException, ParseException {
		name = file.getName();
		gpsPoints = new ArrayList<GpsPoint>();
		Scanner scanner = Utils.csvScanner(file);

		/* To skip "time,latitude,longitude" line */
		scanner.nextLine();

		/* Reading GPS points one by one */
		while (scanner.hasNext()) {
			gpsPoints.add(
					new GpsPoint(Utils.convertDateToTimestamp(scanner.next()),
							scanner.nextDouble(), scanner.nextDouble()));
		}
		scanner.close();
	}

	/* Reads trip only until specific moment of time. */
	Trip(File file, long untilTimestamp) throws IOException, ParseException {
		Trip wholeTrip = new Trip(file);
		this.name = wholeTrip.name;
		this.gpsPoints = new ArrayList<GpsPoint>();
		for (GpsPoint point : wholeTrip.gpsPoints) {
			if (point.timestamp > untilTimestamp) {
				return;
			}
			this.gpsPoints.add(point);
		}
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

	Trip rename(String newName) {
		return new Trip(newName, gpsPoints);
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

	static ArrayList<Trip> extractTripsFromFolder(File folder)
			throws IOException, ParseException {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		File[] files = folder.listFiles();
		for (File file : files) {
			trips.add(new Trip(file));
		}
		return trips;
	}

	static ArrayList<Trip> extractTripsFromFolder(File folder,
			long untilTimestamp) throws IOException, ParseException {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		File[] files = folder.listFiles();
		for (File file : files) {
			Trip trip = new Trip(file, untilTimestamp);
			if (trip.gpsPoints.size() > 0) {
				trips.add(trip);
			}
		}
		return trips;
	}

	/* The file to which we output is specified by trips name */
	void writeToFolder(File folder) throws IOException, ParseException {
		BufferedWriter writer = new BufferedWriter(
				new FileWriter(folder + "/" + name));
		Utils.writeLine(writer, "time,latitude,longitude");
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
