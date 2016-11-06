/**
 * This program takes osm file and extracts bus stops "of consideration" into csv file.
 * By "of consideration" we mean that user specifies [min latitude, max latitude],
 * [min longitude, max longitude] intervals and only bus stops in this box will be extracted.
 * 
 * Example usage:
 * 
 *  // 1) To extract all bus stops in Cambridge city from a larger file
 *  java BusStopsExtractor cambridgeshire.osm bus_stops.csv 0.01 0.22 52.15 52.26 
 * 
 *  // 2) To extract all bus stops in a file
 *  java BusStopsExtractor cambridgeshire.osm bus_stops.csv
 * 
 *  // 3) The following is equivalent to (2)
 *  java BusStopsExtractor cambridgeshire.osm bus_stops.csv -90.0 90.0 -180.0 180.0
 *  
 *  // 4) The general command pattern is
 *  java BusStopsExtractor [input_file.osm] [output_file.csv] [min_lat] [max_lat] [min_lon] [max_lon]
 *  
 *  Warning: the assumption is that the osm file is correctly formatted 
 *  (e.g. different nodes/ways should be separated into different lines,
 *  nodes should contain latitude and longitude coordinates in the first line, etc).
 */
package bus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BusStopsExtractor {

	static double ExtractCoordinate(String line, String coordinate) {
		/**
		 * TODO(ml693): Pattern matcher for now deals only with positive
		 * coordinates. If coordinate is of the form -0.123, the matcher will
		 * not accept it. Fix matcher.
		 */
		Pattern pattern = Pattern.compile(coordinate + "=" + '"' + "[^ ]+");
		Matcher matcher = pattern.matcher(line);
		matcher.find();
		String preformattedCoordinate = matcher.group();
		return Double.parseDouble(preformattedCoordinate.substring(5, preformattedCoordinate.length() - 1));
	}

	/*
	 * TODO(ml693): same method is present in BusStopsExtractor file. Think
	 * where to move the method to avoid code duplication.
	 */
	static void WriteLine(BufferedWriter file, String line) throws IOException {
		file.write(line);
		file.newLine();
	}

	static double minLatitude = -90.0;
	static double maxLatitude = 90.0;
	static double minLongitude = -180.0;
	static double maxLongitude = 180.0;

	static boolean InRegion(double latitude, double longitude) {
		final boolean latitudeCorrect = (latitude >= minLatitude && latitude <= maxLatitude);
		final boolean longitudeCorrect = (longitude >= minLongitude && longitude <= maxLongitude);
		return latitudeCorrect && longitudeCorrect;
	}

	static String ExtractTagsValue(String line) {
		return line.substring(13, line.length() - 3).replaceAll("[^a-zA-Z. ]", "").substring(3);
	}

	public static void main(String[] args) throws IOException {
		/*
		 * Performing checks that command line arguments are correct, extracting
		 * values from the arguments.
		 */
		if (args.length < 2) {
			System.err.println("You need to specify input and output files!");
			return;
		}
		if (args.length != 2 && args.length != 6) {
			System.err.println("Wrong command line arguments. 2 files are required together with 4 optional numbers");
			return;
		}
		if (args.length == 6) {
			minLatitude = Double.parseDouble(args[2]);
			maxLatitude = Double.parseDouble(args[3]);
			minLongitude = Double.parseDouble(args[4]);
			maxLongitude = Double.parseDouble(args[5]);
		}
		BufferedReader originalMapInput = new BufferedReader(new FileReader(args[0]));
		BufferedWriter busStopsOutput = new BufferedWriter(new FileWriter(args[1]));

		/*
		 * The main loop which reads input (osm file) and produces output (csv
		 * file).
		 */
		WriteLine(busStopsOutput, "Latitude, Longitude, Name, Note");
		String line = originalMapInput.readLine();
		while (line != null) {
			/* If line is a node, we will extract its info */
			if (line.contains("<node") && !line.contains("/>")) {
				final double latitude = ExtractCoordinate(line, "lat");
				final double longitude = ExtractCoordinate(line, "lon");
				final boolean inRegion = InRegion(latitude, longitude);
				/**
				 * The following fields will be populated in the while loop
				 * below
				 */
				boolean nodeIsBusStop = false;
				String name = "unknown";
				String note = "unknown";

				while (!line.contains("</node>")) {
					line = originalMapInput.readLine();
					if (line.contains("bus_stop")) {
						nodeIsBusStop = true;
					}
					if (line.contains("<tag k=" + '"' + "name")) {
						name = ExtractTagsValue(line);
					}
					if (line.contains("<tag k=" + '"' + "note")) {
						note = ExtractTagsValue(line);
					}
				}

				/*
				 * If node turned out to be a bus stop, we will output its info
				 */
				if (nodeIsBusStop && inRegion) {
					WriteLine(busStopsOutput, latitude + "," + longitude + "," + name + "," + note);
				}
			}
			line = originalMapInput.readLine();
		}

		originalMapInput.close();
		busStopsOutput.close();
	}
}
