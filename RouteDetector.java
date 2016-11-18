package bus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import bus.Utils.GpsPoint;
import bus.Utils.Route;
import bus.Utils.TravelHistory;

class RouteDetector {

	static ArrayList<GpsPoint> PointsFromFile(File file) throws IOException {
		ArrayList<GpsPoint> points = new ArrayList<GpsPoint>();
		Scanner scanner = new Scanner(file).useDelimiter(",|\\n");
		/* To skip "timestamp,latitude,longitude" line */
		scanner.nextLine();
		while (scanner.hasNext()) {
			points.add(new GpsPoint(scanner.nextInt(), scanner.nextDouble(),
					scanner.nextDouble()));
		}
		scanner.close();
		return points;
	}

	/*
	 * The similarity measure works as follows. For each point p in the travel
	 * history, we find the "best" segment in the route (two consecutive route's
	 * points) and sum p distance to both segment's corners. "Best" segment
	 * is the one which has the minimal sum. We then return sum of sums ranged
	 * over all points in the travel history.
	 * 
	 * The intuition behind this measure is that when a bus is following the
	 * route exactly, each point in its history will lie on one of the route's
	 * segment, hence the point's distance to the "best" segment will be the
	 * "best" segment's length. If the bus is not following the route exactly,
	 * then point's distance to the "best" segment due to triangle inequality
	 * will be larger.
	 *
	 * TODO(ml693): improve the SimilarityMeasure procedure for unusual cases.
	 */
	static double SimilarityMeasure(TravelHistory travelHistory, Route route) {
		double measure = 0.0;
		for (GpsPoint point : travelHistory) {
			measure += DistanceSumToBestSegmentCorners(point, route);

		}
		return measure;
	}
	
	static double DistanceSumToBestSegmentCorners(GpsPoint point,
			Route route) {
		double minDistance = Double.MAX_VALUE;
		for (int i = 1; i < route.size(); i++) {
			minDistance = Math.min(minDistance,
					Utils.Distance(point, route.get(i - 1))
							+ Utils.Distance(point, route.get(i)));
		}
		return minDistance;
	}

	/*
	 * Program predicts which trip the bus is following given a file showing
	 * the most recent bus GPS travel history, and a directory containing all
	 * possible bus trips.
	 */
	public static void main(String args[]) throws IOException {
		TravelHistory recentHistory = (TravelHistory) PointsFromFile(
				new File(args[0]));
		File[] routeFiles = new File(args[1]).listFiles();
		File bestRouteFile = null;
		double smallestMeasure = Double.MAX_VALUE;

		for (File routeFile : routeFiles) {
			Route route = (Route) PointsFromFile(routeFile);
			double currentMeasure = SimilarityMeasure(recentHistory, route);
			if (currentMeasure < smallestMeasure) {
				smallestMeasure = currentMeasure;
				bestRouteFile = routeFile;
			}
		}

		System.out.println(bestRouteFile.getName());
	}

}