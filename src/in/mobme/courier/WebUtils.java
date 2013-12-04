package in.mobme.courier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Handles communication with web servers.
 */
public class WebUtils {
    private static WebUtils instance;
    private static final String TAG = Constants.TAG + "_WebUtils";

    private final HttpClient httpClient;
    private final Gson gson;

    private WebUtils() {
        httpClient = new DefaultHttpClient();
        gson = new Gson();
    }

    public static WebUtils getInstance() {
        if (instance == null) {
            instance = new WebUtils();
        }

        return instance;
    }

    public ApiResponseContainer get(String apiUrl)
            throws ClientProtocolException, IOException {
        Log.d(TAG, "GET " + apiUrl);

        HttpGet httpGet = new HttpGet(apiUrl);

        // Make the HTTP request.
        String stringResponse = makeHttpRequest(httpGet);

        return parseJson(stringResponse);
    }

    public ApiResponseContainer post(String apiUrl, List<NameValuePair> params)
            throws ClientProtocolException, IOException {
        Log.d(TAG, "POST " + apiUrl);

        HttpPost httpPost = new HttpPost(apiUrl);

        if (params.isEmpty()) {
            // TODO: Raise an exception for empty params.
        }

        // URL Encoding the POST parameters.
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(params));
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, "UnsupportedEncodingException");
            e.printStackTrace();
        }

        // Make the HTTP request.
        String stringResponse = makeHttpRequest(httpPost);

        return parseJson(stringResponse);
    }

    // Makes a formed HTTP request, catching possible errors.
    private String makeHttpRequest(HttpUriRequest request)
            throws ClientProtocolException, IOException {
        String response = "RESPONSE_PENDING";

        Log.d(TAG, "Making HTTP Request");

        HttpResponse httpResponse = httpClient.execute(request);

        // Let's make a string out of the response object.
        response = EntityUtils.toString(httpResponse.getEntity());

        return response;
    }

    private ApiResponseContainer parseJson(String jsonString) {
        ApiResponseContainer container = new ApiResponseContainer();

        // Parse the string response as JSON.
        try {
            container = gson.fromJson(jsonString, ApiResponseContainer.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "JsonSyntaxException");
            e.printStackTrace();
        }

        return container;
    }
}
