package bus;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

class HistoryIntoTripsSplitter {

	/*
	 * Program takes CSV files, each file showing a GPS history of a single bus.
	 * It (roughly) splits the GPS history into multiple time intervals and
	 * stores each time interval as an output CSV file. The expectation is that
	 * each time interval represents a single trip a bus has made. This
	 * behaviour is repeated for each file in the input folder.
	 * 
	 * WARNING: How exactly the content of output files should be generated is
	 * not specified precisely. Output content generation procedure might change
	 * if we think it's better to extract trips in another way. Some lines from
	 * the input file might be missing (e.g. if a bus was on and standing for
	 * 30min, we will remove all lines showing that it was standing at the same
	 * place).
	 * 
	 * // Create files output_folder/trip_subtrip0, output_folder/trip_subtrip1,
	 * ...
	 * java bus.HistoryIntoTripsSplitter trips_folder output_folder
	 */
	public static void main(String args[]) throws Exception {
		Utils.checkCommandLineArguments(args, "folder", "folder");
		splitHistoryIntoTrips(new File(args[0]), new File(args[1]));
	}

	public static void splitHistoryIntoTrips(File historyFolder,
			File outputFolder) {
		File[] travelHistoryFiles = historyFolder.listFiles();
		for (File travelHistoryFile : travelHistoryFiles) {
			extractTripsFromTravelHistoryFile(travelHistoryFile, outputFolder);
		}
	}

	// TODO(ml693): use Utils.SAME_PLACE field instead
	private static final double DISTANCE_SHOWING_THAT_BUS_IS_MOVING = 0.000018;
	private static final int POINTS_THAT_ARE_TURNING_AROUND = 2;
	private static final long CONSECUTIVE_POINTS_TIME_DIFFERENCE_LIMIT = 600L;

	/*
	 * We want to delimit the trip if the bus has turned around.
	 * This heuristic will delimit such trips with certain level of success.
	 */
	static boolean busIsTurningAround(ArrayList<GpsPoint> points) {
		for (int p = points.size() - 1; p >= points.size()
				- POINTS_THAT_ARE_TURNING_AROUND && p > 0; p--) {
			boolean busIsMovingForward = true;
			for (int i = points.size() - POINTS_THAT_ARE_TURNING_AROUND
					- 1; i > 0; i--) {
				if (points.get(p).ratioToSegmentCorners(points.get(i - 1),
						points.get(i)) == 1.0) {
					busIsMovingForward = false;
					break;
				}
			}
			if (busIsMovingForward) {
				return false;
			}
		}
		return points.size() > Trip.MINIMUM_NUMBER_OF_GPS_POINTS;
	}

	private static boolean busMovedSignificantDistance(
			ArrayList<GpsPoint> points, GpsPoint currentPoint,
			GpsPoint newPoint) {
		if (points.size() == 0) {
			/* We allow bus to start moving first */
			return true;
		}
		GpsPoint lastPoint = points.get(points.size() - 1);
		return Utils.distance(lastPoint, currentPoint) + Utils.distance(
				currentPoint, newPoint) >= DISTANCE_SHOWING_THAT_BUS_IS_MOVING;
	}

	private static boolean busJumpedUnrealisticDistance(GpsPoint currentPoint,
			GpsPoint newPoint) {
		return (Utils.distance(currentPoint, newPoint) > 0.0002);
	}

	/*
	 * If enough recent GPS points are gathered, creates a trip from them and
	 * writes it to a file. Does nothing otherwise.
	 */
	private static boolean flushGpsPoints(ArrayList<GpsPoint> currentGpsPoints,
			File travelHistoryFile, int extractedTripsCount,
			File outputFolder) {
		try {
			String newName = generateName(travelHistoryFile,
					extractedTripsCount);
			new Trip(newName, currentGpsPoints).writeToFolder(outputFolder);
			return true;
		} catch (ProjectSpecificException exception) {
			return false;
		}
	}

	private static String generateName(File travelHistoryFile,
			int extractedTripsCount) {
		return travelHistoryFile.getName() + "_subtrip" + extractedTripsCount;
	}

	/*
	 * EXPLANATION OF travelHistoryFile ARGUMENT
	 * 
	 * Input is a CSV file containing a TIME-ORDERED list of GPS locations
	 * for a SINGLE bus. Requirement is that 2 consecutive lines i and (i+1)
	 * should have time_i <= time_i+1. There is no restriction placed on how
	 * large or small the difference (timestamp_i+1 - timestamp_i) should be. In
	 * practise the difference will be ~30s, but might become larger when:
	 * 
	 * (i) The bus stopped at line_i, turned itself and GPS transmitter off,
	 * after 1h turned back on, hence the time difference will be 1h. Note
	 * that latitude and longitude values in line_i will match line_i+1.
	 * 
	 * (ii) The GPS transmitter failed to transmit data while travelling for 20
	 * minutes, then started to function again. The time difference will be
	 * 20min but in this case there might be a big change in latitude/longitude
	 * values between line_i and line_i+1, if the bus moved much.
	 * 
	 * Latitude and longitude coordinates need only to show the approximate
	 * location where the bus is. That is, if a bus has travelled through the
	 * same location twice, the latitude/longitude coordinates should be roughly
	 * equal, but do not need to match precisely.
	 * 
	 * EXAMPLE CONTENTS OF travelHistoryFile ARGUMENT
	 * 
	 * // New file
	 * time,latitude,longitude // 1st line of file
	 * 2016-10-18 06:00:00,52.0000,0.2000 // starting in A
	 * 2016-10-18 06:00:30,52.0001,0.2001 // leaving A
	 * ...
	 * ... // travelling to B
	 * ...
	 * 2016-10-18 07:00:00,52.1000,0.3000 // arrived at B
	 * 2016-10-18 07:00:30,52.1000,0.3000 // staying in B
	 * ...
	 * ... // staying in B
	 * ...
	 * 2016-10-18 07:40:00,52.1001,0.3001 // leaving B
	 * ...
	 * ... // travelling back to A
	 * ...
	 * 2016-10-18 08:30:03,52.0002,0.2002 // back at A
	 * 2016-10-18 08:30,34,52.0002,0.2002 // staying in A
	 * ...
	 * ... // staying in A
	 * ...
	 * 2016-10-18 09:00:02,52.0003,0.2003 // leaving A
	 * ...
	 * ... // travelling to C
	 * ...
	 * 2016-10-18 10:10:00,51.9000,0.1000 // arrived at C.
	 * // End of file
	 * 
	 * OUTPUT FILES EXPLANATION
	 * 
	 * Output will be multiple CSV files, each containing a TIME-ORDERED list of
	 * GPS locations for a SINGLE bus. Each output file viewed as a sequence of
	 * lines is a subsequence of the input file. Each output file should contain
	 * a single bus sub trip. For example, if input file contains a history of a
	 * bus having iterated through one route 5 times, then this program will
	 * create 5 output files, the i-th output file containing the history of the
	 * i-th iteration.
	 * 
	 * EXAMPLE FILES STORED TO OUTPUT DIRECTORY
	 * (these will be generated based on the input example above)
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip0
	 * timestamp,latitude,longitude // 1st line of file
	 * 2016-10-18 06:00:00,52.0000,0.2000 // starting at A
	 * 2016-10-18 06:00:30,52.0001,0.2001 // leaving A
	 * ...
	 * ... // travelling to B
	 * ...
	 * 2016-10-18 07:00:00,52.1000,0.3000 // arrived at B
	 * // End of file outputFolder/travelHistoryFile_subtrip0
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip1
	 * timestamp,latitude,longitude // 1st line of file
	 * 2016-10-18 07:40:00,52.1001,0.3001 // leaving B
	 * ...
	 * ... // travelling back to A
	 * ...
	 * 2016-10-18 08:30:03,52.0002,0.2002 // back at A
	 * // End of file outputFolder/travelHistoryFile_subtrip1
	 * 
	 * // New file outputFolder/travelHistoryFile_subtrip2
	 * timestamp,latitude,longitude // 1st line of file
	 * 2016-10-18 09:00:02,52.0003,0.2003 // leaving A
	 * ...
	 * ... // travelling to C
	 * ...
	 * 2016-10-18 10:10:00,51.9000,0.1000 // arriving at C.
	 * // End of file outputFolder/travelHistoryFile_subtrip2
	 */
	public static void extractTripsFromTravelHistoryFile(File travelHistoryFile,
			File outputFolder) {
		System.out.println("Scanning file " + travelHistoryFile.getName());
		Scanner gpsInput = Utils.csvScanner(travelHistoryFile);
		/* To skip "time,latitude,longitude" line */
		gpsInput.nextLine();

		/* Preparing variables to read input file */
		int extractedTripsCount = 0;
		ArrayList<GpsPoint> points = new ArrayList<GpsPoint>();
		GpsPoint currentPoint = new GpsPoint(
				Utils.convertDateToTimestamp(gpsInput.next()),
				gpsInput.nextDouble(), gpsInput.nextDouble());
		long currentTimestamp = currentPoint.timestamp;

		/* Main loop reading GPS data from bus history input file */
		while (gpsInput.hasNext()) {
			GpsPoint newPoint = new GpsPoint(
					Utils.convertDateToTimestamp(gpsInput.next()),
					gpsInput.nextDouble(), gpsInput.nextDouble());

			if (newPoint.timestamp
					- currentTimestamp > CONSECUTIVE_POINTS_TIME_DIFFERENCE_LIMIT
					|| busJumpedUnrealisticDistance(currentPoint, newPoint)) {
				if (flushGpsPoints(points, travelHistoryFile,
						extractedTripsCount, outputFolder)) {
					extractedTripsCount++;
				}
				points = new ArrayList<GpsPoint>();
				currentPoint = newPoint;
			}

			if (busMovedSignificantDistance(points, currentPoint, newPoint)) {
				points.add(currentPoint);
				currentTimestamp = newPoint.timestamp;
			}
			currentPoint = newPoint;

			if (busIsTurningAround(points)) {
				ArrayList<GpsPoint> aroundPoints = new ArrayList<GpsPoint>(
						points.subList(
								points.size() - POINTS_THAT_ARE_TURNING_AROUND,
								points.size()));
				for (int i = 0; i < POINTS_THAT_ARE_TURNING_AROUND; i++) {
					points.remove(points.size() - 1);
				}
				if (flushGpsPoints(points, travelHistoryFile,
						extractedTripsCount, outputFolder)) {
					extractedTripsCount++;
				}
				points = aroundPoints;
			}
		}

		/* Don't forget to add the final sub trip */
		if (flushGpsPoints(points, travelHistoryFile, extractedTripsCount,
				outputFolder)) {
			extractedTripsCount++;
		}
		System.out.println("Extracted " + extractedTripsCount + " trips.");
	}
}
