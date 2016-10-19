package net.sll_mdilab.datauploader.auth;

import android.util.Log;

import com.google.api.client.http.GenericUrl;
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

import java.io.IOException;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.resource.Conformance;
import ca.uhn.fhir.parser.IParser;


public class KarolinskaAuthClient {
    private static final String TAG = "uploader";
    private static final String METADATA_PATH = "/metadata?_format=json";
    private static final String PATIENT_PATH = "/Patient?_format=json";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final HttpRequestFactory requestFactory =
            HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                    request.setParser(new JsonObjectParser(JSON_FACTORY));
                }
            });

    private final String mFhirBaseUri;

    public KarolinskaAuthClient(String fhirBaseUri) {
        mFhirBaseUri = fhirBaseUri;
    }

    public OauthUriHolder getConformance() {
        Log.d(TAG, "Fetching fhir metadata...");
        GenericUrl url = new GenericUrl(mFhirBaseUri + METADATA_PATH);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            String responseString = request.execute().parseAsString();

            Log.d(TAG, "Got metadata: " + responseString);

            return extractMetadata(responseString);

        } catch (IOException e) {
            Log.e(TAG, "Exception when fetching metadata", e);
            throw new RuntimeException("Failed to build get request for metadata.", e);
        }
    }

    public void fetchPatientsUntilOk(String authToken) {
        Log.d(TAG, "Fetching patient...");
        GenericUrl url = new GenericUrl(mFhirBaseUri + PATIENT_PATH);
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAuthorization("Bearer " + authToken);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url).setHeaders(requestHeaders);
            request.execute();

            Log.d(TAG, "Got patiens.");
        } catch (HttpResponseException e) {
            if(e.getStatusCode() == 401) {
                Log.d(TAG, "Got 401, retrying patient fetch.");
                fetchPatientsUntilOk(authToken);
            } else {
                Log.e(TAG, "Unexpected reponse code " + e.getStatusCode() + " when fetching patients", e);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unexpected exception when fetching patients", e);
        }
    }

    private OauthUriHolder extractMetadata(String metadataJson) {
        FhirContext fhirContext = FhirContext.forDstu2();
        IParser parser = fhirContext.newJsonParser();

        Conformance conformance = parser.parseResource(Conformance.class, metadataJson);

        Conformance.RestSecurity restSecurity = conformance.getRestFirstRep().getSecurity();

        List<ExtensionDt> oauthExtensions = restSecurity.getUndeclaredExtensionsByUrl
                ("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
        if (oauthExtensions.isEmpty()) {
            throw new RuntimeException("Oauth extension not found.");
        }

        List<ExtensionDt> authorizeExtensions = oauthExtensions.get(0)
                .getUndeclaredExtensionsByUrl("authorize");
        List<ExtensionDt> tokenExtensions = oauthExtensions.get(0).getUndeclaredExtensionsByUrl
                ("token");

        if (authorizeExtensions.isEmpty() || tokenExtensions.isEmpty()) {
            throw new RuntimeException("Oauth extension not found.");
        }

        return new OauthUriHolder(authorizeExtensions.get(0).getValueAsPrimitive().getValueAsString(),
                tokenExtensions.get(0).getValueAsPrimitive().getValueAsString());
    }

    public static class OauthUriHolder {
        private String mAuthorizeUri;
        private String mTokenUri;

        public OauthUriHolder(String authorizeUri, String tokenUri) {
            mTokenUri = tokenUri;
            mAuthorizeUri = authorizeUri;
        }

        public String getTokenUri() {
            return mTokenUri;
        }

        public String getAuthorizeUri() {
            return mAuthorizeUri;
        }
    }

}
