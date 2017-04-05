package bus;

import java.io.File;

class Prediction {
	final long predictedTimestamp;
	/* The timestamp at which the prediction is made */
	final long predictionTimestamp;

	/* Boolean fields are used only to calculate the average prediction. */
	final boolean recent;
	final boolean equallyCongested;

	/* The stop for which I am predicting */
	final BusStop busStop;
	final String name;

	Prediction(long predictedTimestamp, long predictionTimestamp,
			boolean recent, boolean equallyCongested, BusStop busStop,
			String predictionName) {
		this.predictedTimestamp = predictedTimestamp;
		this.predictionTimestamp = predictionTimestamp;
		this.recent = recent;
		this.equallyCongested = equallyCongested;
		this.busStop = busStop;
		this.name = predictionName;
	}

	/*
	 * Example that will get appended:
	 * // name,prediction_timestamp,actual_arrival_timestamp,prediction_error
	 * day18_bus14365_subtrip0,2016-01-18 13:45:46,2016-01-18 13:52:06,-100
	 */
	void appendToFile(File file, long actualArrivalTimestamp) {
		String predictionLine = name + ","
				+ Utils.convertTimestampToDate(predictionTimestamp) + ","
				+ Utils.convertTimestampToDate(actualArrivalTimestamp) + ","
				+ (actualArrivalTimestamp - predictedTimestamp);
		Utils.appendLineToFile(file, predictionLine);
	}

}