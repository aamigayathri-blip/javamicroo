package campus;

/** Business-rule or validation failure carrying the HTTP status to return. */
public class ServiceException extends RuntimeException {
    private final int status;

    public ServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
