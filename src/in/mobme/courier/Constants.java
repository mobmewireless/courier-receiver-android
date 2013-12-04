package in.mobme.courier;

/**
 * Holds all constant values used by the application.
 */
public class Constants {
    static final String TAG = "CourierClient";

    static final String PROPERTY_API_HOST = "api_host";
    static final String PROPERTY_API_PORT = "api_port";
    static final String PROPERTY_REG_ID = "registration_id";
    static final String PROPERTY_SENDER_ID = "sender_id";
    static final String PROPERTY_REGISTERED_API_URL_BASE = "registered_api_url_base";

    static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;
}
