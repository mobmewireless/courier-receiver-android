package in.mobme.courier;

/**
 * Format in which the Courier Server API responds to web requests.
 */
public final class ApiResponseContainer {
    public String status;
    public String description;
    public String error_code;
    public String sender_id;

    public ApiResponseContainer() {
        status = "failure";
    }
}
