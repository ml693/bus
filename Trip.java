package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
	static final int MINIMUM_NUMBER_OF_GPS_POINTS = 8;

	final String name;
	final ArrayList<GpsPoint> gpsPoints;

	/* Reads trip only until specific moment of time. */
	Trip(File file, long untilTimestamp) throws ProjectSpecificException {
		this.name = file.getName();
		this.gpsPoints = new ArrayList<GpsPoint>();
		Scanner scanner = Utils.csvScanner(file);
		/* To skip "time,latitude,longitude" line */
		scanner.nextLine();

		/* Reading GPS points one by one */
		while (scanner.hasNext()) {
			GpsPoint point = new GpsPoint(
					Utils.convertDateToTimestamp(scanner.next()),
					scanner.nextDouble(), scanner.nextDouble());
			if (point.timestamp > untilTimestamp) {
				break;
			}
			this.gpsPoints.add(point);
		}
		scanner.close();

		if (this.gpsPoints.size() < MINIMUM_NUMBER_OF_GPS_POINTS) {
			throw ProjectSpecificException
					.tripDoesNotHaveEnoughPoints(file.getName());
		}
	}

	Trip(String name, ArrayList<GpsPoint> gpsPoints)
			throws ProjectSpecificException {
		if (gpsPoints.size() < MINIMUM_NUMBER_OF_GPS_POINTS) {
			throw ProjectSpecificException.tripDoesNotHaveEnoughPoints(name);
		}
		this.name = name;
		this.gpsPoints = gpsPoints;
	}

	static Trip readFromFile(File file) {
		try {
			return new Trip(file, Long.MAX_VALUE);
		} catch (ProjectSpecificException exception) {
			throw new RuntimeException(exception);
		}
	}

	Trip makeCopy() {
		return makeCopyWithNewName(name);
	}

	Trip makeCopyWithNewName(String newName) {
		try {
			return new Trip(newName, this.gpsPoints);
		} catch (ProjectSpecificException exception) {
			/* This code path should never be reached */
			throw new RuntimeException(exception);
		}
	}

	Trip subTrip(int fromIndex, int toIndex) throws ProjectSpecificException {
		return new Trip(name,
				new ArrayList<GpsPoint>(gpsPoints.subList(fromIndex, toIndex)));
	}

	static ArrayList<Trip> extractTripsFromFolder(File folder) {
		return extractTripsFromFolder(folder, Long.MAX_VALUE);
	}

	static ArrayList<Trip> extractTripsFromFolder(File folder,
			long untilTimestamp) {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		File[] files = folder.listFiles();

		if (files == null) {
			System.out.println(
					"Something wrong with " + folder.getAbsolutePath());
		}

		for (File file : files) {
			try {
				Trip trip = new Trip(file, untilTimestamp);
				trips.add(trip);
			} catch (ProjectSpecificException exception) {
			}

			if (trips.size() % 1000 == 0 && trips.size() > 0) {
				System.out.println("Succesfully added " + trips.size()
						+ " trips from " + files.length + " possible.");
			}
		}
		return trips;
	}

	/* The file to which we output is specified by trips name */
	void writeToFolder(File folder) {
		try {
			BufferedWriter writer = new BufferedWriter(
					new FileWriter(folder + "/" + name));
			Utils.writeLine(writer, "time,latitude,longitude");
			for (GpsPoint point : gpsPoints) {
				point.write(writer);
			}
			writer.close();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	GpsPoint firstPoint() {
		return gpsPoints.get(0);
	}

	GpsPoint lastPoint() {
		return gpsPoints.get(gpsPoints.size() - 1);
	}

	/* Returns the length of this trip in seconds */
	long duration() {
		return lastPoint().timestamp - firstPoint().timestamp;
	}

}
