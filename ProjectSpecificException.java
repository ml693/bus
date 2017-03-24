package bus;

/*
 * Whenever we want to throw an exception of our own, we should throw this
 * exception.
 */
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

	static ProjectSpecificException tripDoesNotPassThroughFirstStop(
			String tripName, String stopName) {
		return new ProjectSpecificException(
				tripName + " trip does not pass through stop " + stopName);
	}
}