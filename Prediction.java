package bus;

import java.io.File;

class Prediction {
	/*
	 * The time we predict the bus will arrive at the stop. This is the most
	 * important class field, hence I am making it final and enforcing
	 * constructor to accept it. All other fields are optional.
	 */
	final long predictedTimestamp;

	/* The timestamp at which the prediction is made */
	long predictionTimestamp;

	/* Boolean fields are used only to calculate the average prediction. */
	boolean recent;
	boolean equallyCongested;

	/* Name of the trip for which I am predicting */
	String name;

	/* The route for which I am predicting */
	Route route;
	/* From which stop I am predicting the arrival time */
	int fromStopIndex;
	/* To which stop I am predicting the arrival time */
	int toStopIndex;

	Prediction(long predictedTimestamp) {
		this.predictedTimestamp = predictedTimestamp;
	}

	/*
	 * Example that will get appended:
	 * // name,prediction_timestamp,actual_arrival_timestamp,prediction_error
	 * day18_bus14365_subtrip0,2016-01-18 13:45:46,2016-01-18 13:52:06,-100
	 */
	void appendToFile(File file, long actualArrivalTimestamp) {
		String predictionLine = name + "," + route.name + "," + fromStopIndex
				+ "," + toStopIndex + ","
				+ Utils.convertTimestampToDate(predictionTimestamp) + ","
				+ Utils.convertTimestampToDate(predictedTimestamp) + ","
				+ Utils.convertTimestampToDate(actualArrivalTimestamp) + ","
				+ (predictedTimestamp - actualArrivalTimestamp);
		Utils.appendLineToFile(file, predictionLine);
	}

}