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

	static ProjectSpecificException tripDoesNotHaveEnoughPoints() {
		return new ProjectSpecificException(
				"Trip can not be constructed because it does not have "
						+ Trip.MINIMUM_NUMBER_OF_GPS_POINTS + " GPS points.");
	}
}