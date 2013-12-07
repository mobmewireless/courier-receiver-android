package in.mobme.courier;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * The 'main' activity.
 */
public class MainActivity extends Activity {
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME = "onServerExpirationTimeMs";
    private static final String TAG = Constants.TAG + "_MainActivity";

    private TextView logView;
    private GoogleCloudMessaging gcm;
    private String[] hostAndPort = {};
    private EditText editTextApiHost;
    private EditText editTextApiPort;
    private SharedPreferences defaultPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = (TextView) findViewById(R.id.text_view_log);
        hostAndPort = getSavedHostAndPort();
        editTextApiHost = (EditText) findViewById(R.id.edit_text_api_host);
        editTextApiPort = (EditText) findViewById(R.id.edit_text_api_port);

        String apiUrl = getRegisteredApiUrl();

        if (apiUrl.length() > 0) {
            addToLog("This courier receiver is already registered to " + apiUrl);
        } else {
            addToLog("This courier receiver isn't yet registered to a Courier server.");
        }

        // Update editText-s to display host and port, it any.
        if (hostAndPort[0].length() > 0) {
            Log.d(TAG, "Writing host and port found in prefs into text boxes.");
            editTextApiHost.setText(hostAndPort[0]);
            editTextApiPort.setText(hostAndPort[1]);
        } else {
            Log.d(TAG, "Did not find API host and port in prefs.");
        }
    }

    private void addToLog(String logString) {
        DateFormat ft = DateFormat.getTimeInstance();
        logView.setText(ft.format(new Date()) + ": " + logString + "\n"
                + logView.getText());
    }

    private String getRegisteredApiUrl() {
        return getDefaultPreferences().getString(
                Constants.PROPERTY_REGISTERED_API_URL_BASE, "");
    }

    private String[] getSavedHostAndPort() {
        String apiHost = getDefaultPreferences().getString(
                Constants.PROPERTY_API_HOST, "");
        String apiPort = getDefaultPreferences().getString(
                Constants.PROPERTY_API_PORT, "");
        return new String[] { apiHost, apiPort };
    }

    /**
     * Gets the current registration id for application on GCM service.
     * <p>
     * If result is empty, the registration has failed.
     * 
     * @return Registration ID, or empty string if the registration is not
     *         complete.
     */
    private String getRegistrationId() {
        String registrationId = getDefaultPreferences().getString(
                Constants.PROPERTY_REG_ID, "");

        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = getDefaultPreferences().getInt(
                PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion();
        if (registeredVersion != currentVersion || isRegistrationExpired()) {
            Log.v(TAG, "App version changed or registration expired.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getDefaultPreferences() {
        if (defaultPreferences == null) {
            defaultPreferences = PreferenceManager
                    .getDefaultSharedPreferences(this);
        }

        return defaultPreferences;
    }

    private String getSenderId() {
        String senderId = getDefaultPreferences().getString(
                Constants.PROPERTY_SENDER_ID, "");
        return senderId;
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private int getAppVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Checks if the registration has expired.
     * 
     * <p>
     * To avoid the scenario where the device sends the registration to the
     * server but the server loses it, the app developer may choose to
     * re-register after REGISTRATION_EXPIRY_TIME_MS.
     * 
     * @return true if the registration has expired.
     */
    private boolean isRegistrationExpired() {
        // checks if the information is not stale
        long expirationTime = getDefaultPreferences().getLong(
                PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * Stores the registration id, app versionCode, and expiration time in the
     * application's {@code SharedPreferences}.
     * 
     * @param context
     *            application's context.
     * @param regId
     *            registration id
     */
    private void setRegistrationId(String regId) {
        int appVersion = getAppVersion();
        Log.v(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = getDefaultPreferences().edit();
        editor.putString(Constants.PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        long expirationTime = System.currentTimeMillis()
                + Constants.REGISTRATION_EXPIRY_TIME_MS;

        Log.v(TAG, "Setting registration expiry time to "
                + new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /*
     * OnClick responder for the register button.
     */
    public void onClickRegisterButton(View view) {
        Log.d(TAG, "Beginning API registration process...");

        // Hide the keyboard.
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        // Lets pull out details from the form fields.
        EditText apiTokenEditText = (EditText) findViewById(R.id.edit_text_api_token);
        String apiToken = apiTokenEditText.getText().toString();

        boolean hasError = false;

        if (TextUtils.isEmpty(editTextApiHost.getText())) {
            editTextApiHost.setError("Host should not be empty.");
            hasError = true;
        }

        if (hasError) {
            Log.e(TAG, "Missing required params");
            return;
        }

        // Disable the register button.
        findViewById(R.id.button_register).setEnabled(false);

        String apiHostString = editTextApiHost.getText().toString();
        String apiPortString = editTextApiPort.getText().toString();
        String apiUrlBase = apiHostString + ":" + apiPortString;

        // Let's add an 'http://' to the beginning if it's not there.
        if (!apiHostString.startsWith("http")) {
            apiUrlBase = "http://" + apiUrlBase;
        }

        Log.d(TAG, "Storing API host and port strings in shared preferences...");
        setApiHostAndPort(apiHostString, apiPortString);

        // Asynchronously retrieve GCM Sender ID from API.
        Log.d(TAG, "Creating a new ApiRetrieveGcmSenderIdTask with "
                + apiUrlBase + " " + apiToken);
        new ApiRetrieveGcmSenderIdTask(apiUrlBase, apiToken).execute();
    }

    /*
     * Stores the API's host and port strings in shared preferences so that they
     * can be retrieved the next time the application is opened.
     */
    private void setApiHostAndPort(String apiHostString, String apiPortString) {
        SharedPreferences.Editor editor = getDefaultPreferences().edit();
        editor.putString(Constants.PROPERTY_API_HOST, apiHostString);
        editor.putString(Constants.PROPERTY_API_PORT, apiPortString);
        editor.commit();
    }

    /*
     * Contacts the Courier Server API to retrieve the sender ID, for GCM
     * registration. Triggers GCM Registration once successful, with the
     * retrieved sender ID.
     */
    private class ApiRetrieveGcmSenderIdTask extends
            AsyncTask<Void, String, ApiResponseContainer> {
        String apiUrlBase;
        String apiToken;

        public ApiRetrieveGcmSenderIdTask(String apiUrlBase, String apiToken) {
            this.apiUrlBase = apiUrlBase;
            this.apiToken = apiToken;
        }

        @Override
        protected ApiResponseContainer doInBackground(Void... args) {
            WebUtils webUtils = WebUtils.getInstance();

            String apiUrl = apiUrlBase + "/api/sender_id";

            try {
                return webUtils.get(apiUrl);
            } catch (ClientProtocolException e) {
                publishProgress("Failed to retrieve GCM Sender ID from API. ClientProtocolException: "
                        + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                publishProgress("Failed to retrieve GCM Sender ID from API. IOException: "
                        + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            for (String logMessage : progress) {
                addToLog(logMessage);
            }
        }

        @Override
        protected void onPostExecute(ApiResponseContainer apiResponse) {
            if (apiResponse == null) {
                return;
            }

            if (apiResponse.status.equals("success")) {
                Log.d(TAG, "Retrieved sender ID: " + apiResponse.sender_id);
                addToLog("Successfully fetched Courier Server's Sender ID: "
                        + apiResponse.sender_id);

                // Store the Sender ID in shared preferences.
                SharedPreferences.Editor editor = getDefaultPreferences()
                        .edit();
                editor.putString(Constants.PROPERTY_SENDER_ID,
                        apiResponse.sender_id);
                editor.commit();

                // Asynchronously register with the GCM.
                String registrationId = getRegistrationId();

                if (registrationId.length() > 0) {
                    Log.d(TAG,
                            "There's a GCM registration ID in store. No need to regenerate.");
                    Log.d(TAG, "Stored ID: " + registrationId);
                    new ApiRegistrationTask(apiUrlBase, registrationId,
                            apiToken).execute();
                } else {
                    Log.d(TAG,
                            "Couldn't find a GCM registraiton ID. Attempting to acquire...");
                    new GcmRegistrationTask(apiUrlBase, apiToken).execute();
                }
            } else {
                Log.d(TAG, "Failed to retrieve GCM Sender ID with status: "
                        + apiResponse.status);
                addToLog("Failed to retrieve GCM Sender ID with status: "
                        + apiResponse.status);
            }
        }
    }

    /*
     * Registers with GCM with supplied sender ID. Triggers GCM ID Registration
     * with API afterwards.
     */
    private class GcmRegistrationTask extends AsyncTask<Void, String, String> {
        String apiUrlBase;
        String apiToken;

        public GcmRegistrationTask(String apiUrlBase, String apiToken) {
            this.apiUrlBase = apiUrlBase;
            this.apiToken = apiToken;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if (gcm == null) {
                    gcm = GoogleCloudMessaging.getInstance(MainActivity.this);
                }

                String registrationId = gcm.register(getSenderId());

                // Save the registration ID - no need to register again.
                setRegistrationId(registrationId);

                publishProgress("GCM registration completed successfully!");

                return registrationId;
            } catch (IOException ex) {
                publishProgress("Failed to register with Google Cloud Messaging. IOException: "
                        + ex.getMessage());
                ex.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            for (String logMessage : progress) {
                addToLog(logMessage);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // Asynchronously register generated GCM Registration ID with
                // the API.
                new ApiRegistrationTask(apiUrlBase, result, apiToken).execute();
                Log.d(TAG, "GCM registration ID is " + result);
            }
        }
    }

    /*
     * Contacts the Courier Server API with GCM registration ID, completing
     * registration process.
     */
    private class ApiRegistrationTask extends
            AsyncTask<Void, String, ApiResponseContainer> {
        String apiUrlBase;
        String registrationId;
        String apiToken;

        public ApiRegistrationTask(String apiUrlBase, String registrationId,
                String apiToken) {
            this.apiUrlBase = apiUrlBase;
            this.registrationId = registrationId;
            this.apiToken = apiToken;
        }

        @Override
        protected ApiResponseContainer doInBackground(Void... params) {
            String apiUrl = apiUrlBase + "/api/devices/register";

            Log.d(TAG, "POST " + apiUrl);

            // Build POST parameters.
            List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
            nameValuePair
                    .add(new BasicNameValuePair("push_id", registrationId));
            nameValuePair.add(new BasicNameValuePair("token", apiToken));

            WebUtils webUtils = WebUtils.getInstance();

            try {
                return webUtils.post(apiUrl, nameValuePair);
            } catch (ClientProtocolException e) {
                publishProgress("Failed to register GCM ID with the API. ClientProtocolException: "
                        + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                publishProgress("Failed to register GCM ID with the API. IOException: "
                        + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            for (String logMessage : progress) {
                addToLog(logMessage);
            }
        }

        @Override
        protected void onPostExecute(ApiResponseContainer apiResponse) {
            if (apiResponse == null) {
                return;
            }

            if (apiResponse.status.equals("success")) {
                // Store successful registration event in shared preferences so
                // that details of linking can be displayed when the application
                // is started the next time.
                SharedPreferences.Editor editor = getDefaultPreferences()
                        .edit();
                editor.putString(Constants.PROPERTY_REGISTERED_API_URL_BASE,
                        this.apiUrlBase);
                editor.commit();

                Log.d(TAG, "Registration process successfully completed.");

                addToLog("Successfully registered with courier server at "
                        + this.apiUrlBase);
            } else {
                addToLog("Courier server responded with unknown status: "
                        + apiResponse.status);
            }
        }
    }
}
