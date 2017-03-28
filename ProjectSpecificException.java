package bus;

// If I want to throw an exception of my own, I will throw this exception.
class ProjectSpecificException extends Exception {
	private static final long serialVersionUID = 1L;

	ProjectSpecificException(String message) {
		super(message);
	}

	static ProjectSpecificException tripDoesNotHaveEnoughPoints(String name) {
		return new ProjectSpecificException(
				name + " can not be constructed because it does not have "
						+ Trip.MINIMUM_NUMBER_OF_GPS_POINTS + " GPS points.");
	}

	static ProjectSpecificException historicalTripMissingImportantPoints(
			String tripName) {
		return new ProjectSpecificException(tripName + " missing GPS points");
	}
}