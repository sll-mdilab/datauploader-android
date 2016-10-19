package net.sll_mdilab.datauploader.fhir;

import android.content.Context;
import android.util.Log;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

import net.sll_mdilab.datauploader.exception.UnauthorizedException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.parser.IParser;

public class FhirUploader {
    private static final String TAG = "uploader";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final HttpRequestFactory requestFactory =
        HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) {
                request.setParser(new JsonObjectParser(JSON_FACTORY));
            }
        });

    private FhirContext mFhirContext = FhirContext.forDstu2();
    private IParser mParser = mFhirContext.newJsonParser();
    private String mBaseUrl;
    private String mAuthToken;

    public FhirUploader(String baseUrl) {
        mBaseUrl = baseUrl;
    }

    public void uploadObservations(Context activityContext, Observation observation){
        String requestBody = mParser.encodeResourceToString(observation);

        HttpContent requestContent;
        try {
            requestContent = new ByteArrayContent("application/json+fhir", requestBody.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding.", e);
        }

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAuthorization("Bearer " + mAuthToken);

        Log.d(TAG, "Uploading observation.");
        Log.d(TAG, "Observation content: " + requestBody);

        GenericUrl url = new GenericUrl(mBaseUrl + "/Observation");
        try {
            Log.d(TAG, "buildPostRequest()");
            HttpRequest request = requestFactory.buildPostRequest(url, requestContent).setHeaders(requestHeaders);
            Log.d(TAG, "execute()");

            request.execute();
        } catch (HttpResponseException e) {
            Log.e(TAG, "Got HTTP error " + e.getStatusCode()  + " when uploading observations on POST: " + e.getStatusMessage());
            throw new UnauthorizedException(e, mAuthToken);
        }
        catch (IOException e) {
            throw new RuntimeException("Exception occurred while uploading observations", e);
        }

        Log.d(TAG, "Done uploading.");
    }

    public void setAuthToken(String authToken) {
        mAuthToken = authToken;
    }
}
