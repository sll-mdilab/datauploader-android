package net.sll_mdilab.datauploader.activity;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.nimbusds.jose.JWSObject;

import net.sll_mdilab.datauploader.R;
import net.sll_mdilab.datauploader.auth.KarolinskaAuthClient;
import net.sll_mdilab.datauploader.auth.KarolinskaAuthenticator;

import java.io.IOException;
import java.util.Arrays;

public class KarolinskaAuthenticatorActivity extends AccountAuthenticatorActivity {
    public static final String TAG = "uploader";
    public static final String ARG_IS_ADDING_NEW_ACCOUNT = "arg_is_adding_new_account";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private WaitForCredentialsTask mWaitForCredentialsTask;
    private LocalServerReceiver receiver;
    private AccountManager accountManager;
    private KarolinskaAuthClient karolinskaAuthClient;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate()");

        accountManager = AccountManager.get(getApplicationContext());
        karolinskaAuthClient = new KarolinskaAuthClient(getString(R.string.fhir_base_url));

        setContentView(R.layout.activity_login);

        webView = (WebView) findViewById(R.id.authentication_web_view);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart()");

        mWaitForCredentialsTask = new WaitForCredentialsTask(this, "t5user", "");
        mWaitForCredentialsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop()");

        stopReceiver();

        mWaitForCredentialsTask.cancel(true);
    }

    private void stopReceiver() {
        try {
            if(receiver != null) {
                Log.d(TAG, "Stopping receiver");
                receiver.stop();
            } else {
                Log.d(TAG, "Receiver is null");
            }
        } catch (IOException e) {
            Log.e(TAG, "Stopping receiver jetty failed.", e);
        }
    }

    private class WaitForCredentialsTask extends AsyncTask<Void, Void, Intent> {
        private final Context context;
        private final String userName;

        public WaitForCredentialsTask(Context context, String userName, String userPass) {
            this.context = context;
            this.userName = userName;
        }

        @Override
        protected Intent doInBackground(Void... params) {
            TokenResponse tokenResponse = getAuthToken(context);
            String accessToken = tokenResponse.getAccessToken();

            String idToken = tokenResponse.getUnknownKeys().get("id_token").toString();
            String userId = getUserIdFromIdToken(idToken);

            final Intent res = new Intent();
            res.putExtra(AccountManager.KEY_ACCOUNT_NAME, userId);
            res.putExtra(AccountManager.KEY_ACCOUNT_TYPE, KarolinskaAuthenticator
                    .KAROLINSKA_ACCOUNT_TYPE);
            res.putExtra(AccountManager.KEY_AUTHTOKEN, accessToken);
            return res;
        }

        private String getUserIdFromIdToken(String idToken) {
            JWSObject jwsObject;
            try {
                jwsObject = JWSObject.parse(idToken);
            } catch (java.text.ParseException e) {
                Log.e(TAG, "Id token parsing failed.", e);
                throw new RuntimeException("Id token parsing failed.", e);
            }

            String profile = (String) jwsObject.getPayload().toJSONObject().get("profile");
            Log.d(TAG, "Profile: " + profile);

            if(profile.contains("Patient/")) {
                return profile.substring(profile.indexOf("Patient/") + "Patient/".length());
            }

            if(profile.contains("Practitioner/")) {
                return profile.substring(profile.indexOf("Practitioner/") + "Practitioner/".length());
            }

            return null;
        }

        @Override
        protected void onCancelled(Intent result) {
            Log.d(TAG, "onCancelled()");
        }

        @Override
        protected void onPostExecute(Intent intent) {
            finishLogin(intent);
        }
    }

    private void finishLogin(Intent intent) {
        Log.d(TAG, "finishLogin()");

        String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

        final Account account = new Account(accountName, intent.getStringExtra(AccountManager
                .KEY_ACCOUNT_TYPE));

        if (getIntent().getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            Log.d(TAG, "Is creating new account. Setting token...");
            String authToken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);

            accountManager.addAccountExplicitly(account, "", null);

            Log.d(TAG, "Setting auth token.");
            accountManager.setAuthToken(account, KarolinskaAuthenticator.KAROLINSKA_WELLNESS_AUTH_TOKEN_TYPE,
                    authToken);
        }

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    public TokenResponse getAuthToken(Context context) {

        KarolinskaAuthClient.OauthUriHolder uriHolder = karolinskaAuthClient.getConformance();

        Log.d(TAG, "Authorize uri: " + uriHolder.getAuthorizeUri());
        Log.d(TAG, "Token uri: " + uriHolder.getTokenUri());

        startReceiver();

        Log.d(TAG, "Creating auth flow...");
        AuthorizationCodeFlow authFlow = new AuthorizationCodeFlow.Builder(BearerToken
                .authorizationHeaderAccessMethod(), HTTP_TRANSPORT, JSON_FACTORY, new GenericUrl
                (uriHolder.getTokenUri()), new ClientParametersAuthentication("capacity", null),
                "capacity", uriHolder.getAuthorizeUri()).setScopes(Arrays.asList("openid", "profile")).build();

        Log.d(TAG, "Creating request url...");

        String redirectUri;
        try {
            redirectUri = receiver.getRedirectUri();
        } catch (IOException e) {
            throw new RuntimeException("Getting redirect uri failed");
        }

        final AuthorizationCodeRequestUrl requestUrl = authFlow.newAuthorizationUrl().setRedirectUri(redirectUri);

        Log.d(TAG, "Request url is " + requestUrl.build());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl(requestUrl.build());
            }
        });

        Log.d(TAG, "Starting server for obtaining auth code.");
        String authCode = waitForAuthCode();
        Log.d(TAG, "Got auth code.");

        TokenResponse tokenResponse = getTokenResponse(authFlow, redirectUri, authCode);
        karolinskaAuthClient.fetchPatientsUntilOk(tokenResponse.getAccessToken());

        return tokenResponse;
    }

    private void startReceiver() {
        stopReceiver();

        receiver = new LocalServerReceiver.Builder().setHost(
                "localhost").setPort(9876).build();
    }

    private String waitForAuthCode() {
        try {
            return receiver.waitForCode();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch auth code.", e);
            throw new RuntimeException("Failed to fetch auth code.", e);
        } finally {
            try {
                Log.e(TAG, "Stopping receiver jetty");
                receiver.stop();
            } catch (IOException e) {
                Log.e(TAG, "Stopping receiver jetty failed.", e);
            }
        }
    }

    private TokenResponse getTokenResponse(AuthorizationCodeFlow authFlow, String redirectUri,
                                           String authCode) {
        AuthorizationCodeTokenRequest tokenRequest = authFlow.newTokenRequest(authCode);
        tokenRequest.setRedirectUri(redirectUri);
        TokenResponse tokenResponse;

        Log.d(TAG, "Fetching access token.");
        try {
            tokenResponse = tokenRequest.execute();
        } catch (IOException e) {
            Log.e(TAG, "Token request failed", e);
            throw new RuntimeException("Token request failed.", e);
        }

        Log.d(TAG, "Got access token.");
        return tokenResponse;
    }
}