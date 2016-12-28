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
}