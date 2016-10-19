package net.sll_mdilab.datauploader.activity;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.SessionReadResult;

import net.sll_mdilab.datauploader.R;
import net.sll_mdilab.datauploader.adapter.SessionAdapter;
import net.sll_mdilab.datauploader.adapter.SessionListItem;
import net.sll_mdilab.datauploader.auth.KarolinskaAuthenticator;
import net.sll_mdilab.datauploader.converter.ObservationConverter;
import net.sll_mdilab.datauploader.database.SessionMetadataDao;
import net.sll_mdilab.datauploader.exception.UnauthorizedException;
import net.sll_mdilab.datauploader.fhir.FhirUploader;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import ca.uhn.fhir.model.dstu2.resource.Observation;

public class UploadActivity extends AppCompatActivity {

    public static final int MY_PERMISSIONS_REQUEST_BODY_SENSORS = 0;

    private static String TAG = "uploader";

    private AccountManager accountManager;
    private GoogleApiClient mClient = null;
    private SessionAdapter mSessionArrayAdapter;
    private ObservationConverter observationConverter = new ObservationConverter();
    private ListView mSessionListView;
    private FhirUploader mFhirUploader;
    private SessionMetadataDao mSessionMetadataDao;
    private String mPatientId;

    private AdapterView.OnItemClickListener mSessionListClickedHandler = new AdapterView
            .OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            SessionListItem sessionListItem = (SessionListItem) parent.getItemAtPosition(position);
            Session session = sessionListItem.getSession();
            Log.d(TAG, "Clicked session with name " + session.getName() + " startTime " + session
                    .getStartTime(TimeUnit.MILLISECONDS));

            uploadSessionData((SessionListItem) parent.getItemAtPosition(position));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSessionMetadataDao = new SessionMetadataDao(getApplicationContext());

        accountManager = AccountManager.get(this);

        setContentView(R.layout.activity_upload);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFhirUploader = new FhirUploader(getString(R.string.fhir_base_url));

        mSessionArrayAdapter = new SessionAdapter(this);
        mSessionArrayAdapter.setNotifyOnChange(false);

        mSessionListView = (ListView) findViewById(R.id.observation_list_view);
        mSessionListView.setAdapter(mSessionArrayAdapter);
        mSessionListView.setOnItemClickListener(mSessionListClickedHandler);

        if (!checkPermissions()) {
            Log.i(TAG, "Lacking permissions. Requesting...");
            requestPermissions();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                Log.d(TAG, "Clicked refresh.");

                if (mClient.isConnected()) {
                    Log.d(TAG, "Connected, updating fitness data.");
                    updateFitnessData();
                } else {
                    Log.d(TAG, "Not connected, adding connection callback.");

                    mClient.registerConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            updateFitnessData();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {

                        }
                    });
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart()");

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        buildFitnessClient();

        handleAuthentication();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BODY_SENSORS},
                MY_PERMISSIONS_REQUEST_BODY_SENSORS);
    }

    private void buildFitnessClient() {
        Log.d(TAG, "buildFitnessClient()");
        if (mClient == null && checkPermissions()) {
            mClient = new GoogleApiClient.Builder(this)
                    .addApi(Fitness.SESSIONS_API)
                    .addScope(new Scope(Scopes.FITNESS_BODY_READ))
                    .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                    .addScope(new Scope(Scopes.FITNESS_NUTRITION_READ))
                    .addConnectionCallbacks(
                            new GoogleApiClient.ConnectionCallbacks() {
                                @Override
                                public void onConnected(Bundle bundle) {
                                    Log.i(TAG, "Fitness client initiated");
                                    updateFitnessData();
                                }

                                @Override
                                public void onConnectionSuspended(int i) {
                                    if (i == GoogleApiClient.ConnectionCallbacks
                                            .CAUSE_NETWORK_LOST) {
                                        Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                    } else if (i
                                            == GoogleApiClient.ConnectionCallbacks
                                            .CAUSE_SERVICE_DISCONNECTED) {
                                        Log.i(TAG,
                                                "Connection lost.  Reason: Service Disconnected");
                                    }
                                }
                            }
                    )
                    .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            Log.i(TAG, "Google Play services connection failed. Cause: " +
                                    result.toString());
                            Snackbar.make(
                                    UploadActivity.this.findViewById(R.id.observation_list_view),
                                    "Exception while connecting to Google Play services: " +
                                            result.getErrorMessage(),
                                    Snackbar.LENGTH_INDEFINITE).show();
                        }
                    })
                    .build();
        }
    }

    private void handleAuthentication() {
        Account[] accounts = accountManager.getAccountsByType(KarolinskaAuthenticator.KAROLINSKA_ACCOUNT_TYPE);

        if (accounts.length < 1) {

            Log.d(TAG, "No T5 accounts present.");

            final UploadActivity thisActivity = this;

            accountManager.addAccount(KarolinskaAuthenticator.KAROLINSKA_ACCOUNT_TYPE, null, null, null, this,
                    new AccountManagerCallback<Bundle>() {
                        @Override
                        public void run(AccountManagerFuture<Bundle> future) {
                            try {
                                Log.d(TAG, "Account created callback");
                                Bundle bundle = future.getResult();

                                String accountName = (String) bundle.get(AccountManager
                                        .KEY_ACCOUNT_NAME);
                                String accountType = (String) bundle.get(AccountManager
                                        .KEY_ACCOUNT_TYPE);

                                Account[] accounts = accountManager.getAccountsByType
                                        (KarolinskaAuthenticator.KAROLINSKA_ACCOUNT_TYPE);

                                Account account = accounts[0];
                                mPatientId = account.name;

                                accountManager.getAuthToken(
                                        account,
                                        KarolinskaAuthenticator.KAROLINSKA_WELLNESS_AUTH_TOKEN_TYPE,
                                        null,
                                        thisActivity,
                                        new OnTokenAcquired(),
                                        new Handler(new Handler.Callback() {
                                            @Override
                                            public boolean handleMessage(Message msg) {
                                                Log.e("uploader", "Failed to retrieve auth token: " +
                                                        msg.toString());
                                                return false;
                                            }
                                        }));

                            } catch (OperationCanceledException e) {
                                Log.d("uploader", "addAccount failed", e);
                            } catch (IOException e) {
                                Log.d("uploader", "addAccount failed", e);
                            } catch (AuthenticatorException e) {
                                Log.d("uploader", "addAccount failed", e);
                            }
                        }
                    }, null);
        } else {
            Account account = accounts[0];

            accountManager.getAuthToken(
                    account,
                    KarolinskaAuthenticator.KAROLINSKA_WELLNESS_AUTH_TOKEN_TYPE,
                    null,
                    this,
                    new OnTokenAcquired(),
                    new Handler(new Handler.Callback() {
                        @Override
                        public boolean handleMessage(Message msg) {
                            Log.e("uploader", "Failed to retrieve auth token: " + msg.toString());
                            return false;
                        }
                    }));

            mPatientId = account.name;
        }
    }

    private class OnTokenAcquired implements AccountManagerCallback<Bundle> {
        @Override
        public void run(AccountManagerFuture<Bundle> future) {

            Log.d(TAG, "OnTokenAcquired()");

            // Get the result of the operation from the AccountManagerFuture.
            Bundle bundle = null;
            try {
                bundle = future.getResult();
            } catch (Exception e) {
                Log.e(TAG, "Failed to retrieve auth result.", e);
                return;
            }

            Intent launch = (Intent) bundle.get(AccountManager.KEY_INTENT);
            if (launch != null) {
                startActivityForResult(launch, 0);
                return;
            }
            Log.i(TAG, "Found token in bundle:" + bundle.getString(AccountManager.KEY_AUTHTOKEN));
            mFhirUploader.setAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));

            Log.d(TAG, "Found authentication token.");
        }
    }

    private void updateFitnessData() {
        new UpdateFitnessDataTask().execute();
    }

    private class UpdateFitnessDataTask extends AsyncTask<Void, Void, List<SessionListItem>> {
        @Override
        protected void onPostExecute(List<SessionListItem> items) {
            mSessionArrayAdapter.clear();
            mSessionArrayAdapter.addAll(items);
            mSessionArrayAdapter.notifyDataSetChanged();
        }

        @Override
        protected List<SessionListItem> doInBackground(Void... params) {
            Log.d(TAG, "UpdateFitnessDataTask.doInBackground()");
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            long startTime = cal.getTimeInMillis();

            java.text.DateFormat dateFormat = DateFormat.getDateInstance();
            Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
            Log.i(TAG, "Range End: " + dateFormat.format(endTime));

            SessionReadRequest readRequest = new SessionReadRequest.Builder()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_HEART_RATE_BPM)

                    .readSessionsFromAllApps()
                    .build();

            SessionReadResult sessionReadResult =
                    Fitness.SessionsApi.readSession(mClient, readRequest)
                            .await(1, TimeUnit.MINUTES);

            List<SessionListItem> items = new ArrayList<>();

            for (Session session : sessionReadResult.getSessions()) {
                SessionListItem item = new SessionListItem();
                item.setSession(session);
                item.setDataSets(sessionReadResult.getDataSet(session));

                SessionMetadataDao.SessionMetadata sessionMetadata = mSessionMetadataDao
                        .findBySessionId(item.getSession().getIdentifier());

                if (sessionMetadata != null) {
                    item.setIsUploaded(sessionMetadata.getUploaded() != null);
                }

                items.add(item);
            }

            return items;
        }
    }

    private void uploadSessionData(final SessionListItem sessionListItem) {
        Log.d(TAG, "Uploading session data.");

        Log.d(TAG, "Data sets found: " + sessionListItem.getDataSets().size());

        final Context activityContext = this;

        @SuppressWarnings("unchecked")
        AsyncTask<SessionListItem, Integer, SessionListItem> uploadTask = new
                AsyncTask<SessionListItem,
                        Integer, SessionListItem>() {
                    private final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

                    private Exception mCaughtException;
                    private ProgressDialog mProgressDialog;
                    private int mNumObservations;

                    @Override
                    protected void onPreExecute() {
                        isoDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                        mProgressDialog = new ProgressDialog(activityContext);
                        mProgressDialog.setMessage("Uploading data to server...");
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setCancelable(false);
                        mProgressDialog.setCanceledOnTouchOutside(false);
                        mProgressDialog.setProgressNumberFormat(null);
                        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        mProgressDialog.show();
                    }

                    @Override
                    protected void onProgressUpdate(Integer... numUploadedObservations) {
                        mProgressDialog.setProgress((int) ((numUploadedObservations[0] / (float)
                                mNumObservations) * 100));
                        Log.d(TAG, "Progress: " + ((int) ((numUploadedObservations[0] / (float)
                                mNumObservations) * 100)));
                    }

                    @Override
                    protected SessionListItem doInBackground(SessionListItem... sessionListItems) {
                        SessionListItem sessionListItem = sessionListItems[0];

                        Log.d(TAG, "PatientId. " + mPatientId);

                        List<Observation> observations = new ArrayList<>();
                        for (DataSet dataSet : sessionListItem.getDataSets()) {
                            Log.d(TAG, "Data points found: " + dataSet.getDataPoints().size());
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                observations.add(observationConverter.convertToObservation
                                        (dataPoint, mPatientId));
                            }
                        }

                        mNumObservations = observations.size();
                        int numUploadedObservations = 0;

                        try {
                            for (Observation observation : observations) {
                                mFhirUploader.uploadObservations(activityContext, observation);
                                publishProgress(++numUploadedObservations);
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "UploadTask caught exception", e);
                            mCaughtException = e;
                        }

                        return sessionListItem;
                    }

                    @Override
                    protected void onPostExecute(SessionListItem sessionListItem) {
                        mProgressDialog.dismiss();

                        if (mCaughtException != null) {
                            if (mCaughtException instanceof UnauthorizedException) {
                                accountManager.invalidateAuthToken(KarolinskaAuthenticator
                                        .KAROLINSKA_ACCOUNT_TYPE, (
                                        (UnauthorizedException) mCaughtException).getAuthToken());
                                mProgressDialog.dismiss();
                                handleAuthentication();
                            } else {
                                Toast.makeText(activityContext, R.string.toast_server_error, Toast
                                        .LENGTH_SHORT)
                                        .show();
                            }
                        } else {
                            Toast.makeText(activityContext, R.string.toast_datapoints_uploaded, Toast
                                    .LENGTH_SHORT)
                                    .show();
                            sessionListItem.setIsUploaded(true);

                            SessionMetadataDao.SessionMetadata sessionMetadata = new
                                    SessionMetadataDao.SessionMetadata();
                            sessionMetadata.setSessionId(sessionListItem.getSession()
                                    .getIdentifier());
                            sessionMetadata.setUploaded(isoDateFormat.format(new Date()));

                            mSessionMetadataDao.update(sessionMetadata);
                            mSessionArrayAdapter.notifyDataSetChanged();
                        }
                    }
                }.execute(sessionListItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.upload_menu, menu);
        return true;
    }
}
