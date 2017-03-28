/*
 * Auxiliary file for code fragments used in more than one class.
 */
package bus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

public class Utils {

	private static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static final long MILLISECONDS_IN_ONE_SECOND = 1000;
	private static final double SAME_SPOT_DISTANCE_RANGE = 0.000007;

	static long convertDateToTimestamp(String date) {
		try {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
					SIMPLE_DATE_FORMAT);
			Date time = simpleDateFormat.parse(date);
			return time.getTime() / MILLISECONDS_IN_ONE_SECOND;
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	static String convertTimestampToDate(Long timestamp) {
		Date date = new Date(timestamp * MILLISECONDS_IN_ONE_SECOND);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				SIMPLE_DATE_FORMAT);
		return simpleDateFormat.format(date);
	}

	/*
	 * Geometric distance between two GPS points in space, when latitude
	 * represents y coordinate and longitude represents x coordinate;
	 */
	static double distance(GpsPoint p1, GpsPoint p2) {
		double scaleToRadians = Math.PI / 180f;
		double sinLatitude1 = Math.sin(p1.latitude * scaleToRadians);
		double sinLatitude2 = Math.sin(p2.latitude * scaleToRadians);
		double cosLatitude1 = Math.cos(p1.latitude * scaleToRadians);
		double cosLatitude2 = Math.cos(p2.latitude * scaleToRadians);
		double cosLongitude = Math
				.cos((p1.longitude - p2.longitude) * scaleToRadians);
		return Math.acos(sinLatitude1 * sinLatitude2
				+ cosLatitude1 * cosLatitude2 * cosLongitude);
	}

	static Scanner csvScanner(File file) {
		try {
			/* \r is for windows-style line separator */
			return new Scanner(file).useDelimiter(",|\r?\n");
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	static void writeLine(BufferedWriter writer, String line)
			throws IOException {
		writer.write(line);
		writer.newLine();
	}

	static void appendLineToFile(File file, String line) {
		try {
			FileWriter writer = new FileWriter(file, true);
			writer.write(line + "\n");
			writer.close();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	static ArrayList<File> filesInFolder(String folderName) {
		return new ArrayList<File>(
				Arrays.asList(new File(folderName).listFiles()));
	}

	static ArrayList<Trip> extractTripsFromFiles(File[] files)
			throws Exception {
		ArrayList<Trip> trips = new ArrayList<Trip>();
		for (File file : files) {
			trips.add(new Trip(file));
		}
		return trips;
	}

	/*
	 * Function checking whether appropriate files and folders exist. E.g.
	 * checkCommandLineArguments({"a", "b", "b/c"}, "file", "folder", "file")
	 * Checks whether there exists a file "a", a folder "b" and a file "c"
	 * inside the folder "b".
	 */
	static void checkCommandLineArguments(String[] args, String... fileOrFolder)
			throws ProjectSpecificException {
		if (args.length != fileOrFolder.length) {
			throw (new ProjectSpecificException(
					"Wrong number of command line arguments. Should be "
							+ fileOrFolder.length + " but " + args.length
							+ " were given."));
		}

		for (int i = 0; i < args.length; i++) {
			if (!fileMatchesExpectation(args[i], fileOrFolder[i])) {
				throw (new ProjectSpecificException(
						"Command line argument number " + i
								+ " (counting from 0) is wrong. It has to be a "
								+ fileOrFolder[i] + " name."));
			}
		}
	}

	private static boolean fileMatchesExpectation(String fileName,
			String expectation) throws ProjectSpecificException {
		switch (expectation) {
		case "file":
			return new File(fileName).isFile();
		case "folder":
			return new File(fileName).isDirectory();
		default:
			throw (new ProjectSpecificException(
					"Argument expectation is given wrong name"));
		}
	}

	static boolean samePlace(GpsPoint p1, GpsPoint p2) {
		return Utils.distance(p1, p2) < SAME_SPOT_DISTANCE_RANGE;
	}

	private static final Random randomBitGenerator = new Random();

	static boolean randomBit() {
		return (randomBitGenerator.nextInt(2) == 1);
	}
}
