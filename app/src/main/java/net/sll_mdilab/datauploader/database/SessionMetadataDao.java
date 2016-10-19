package net.sll_mdilab.datauploader.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import static net.sll_mdilab.datauploader.database.SessionMetadataContract.SessionMetadataEntry;

public class SessionMetadataDao {
    private final SessionMetadataDbHelper mSessionMetadataDbHelper;
    private final SQLiteDatabase mSessionMetadataDb;
    private final Context mContext;

    private static final String[] projection = {
        SessionMetadataEntry.COLUMN_NAME_SESSION_ID,
        SessionMetadataEntry.COLUMN_NAME_UPLOADED
    };
    private static final String selection = SessionMetadataEntry.COLUMN_NAME_SESSION_ID + " = ?";

    public static class SessionMetadata {
        private String mSessionId;
        private String mUploaded;

        public String getSessionId() {
            return mSessionId;
        }

        public void setSessionId(String sessionId) {
            mSessionId = sessionId;
        }

        public String getUploaded() {
            return mUploaded;
        }

        public void setUploaded(String uploaded) {
            mUploaded = uploaded;
        }
    }

    public SessionMetadataDao(Context context) {
        mContext = context;

        mSessionMetadataDbHelper = new SessionMetadataDbHelper(mContext);
        mSessionMetadataDb = mSessionMetadataDbHelper.getWritableDatabase();
    }

    public SessionMetadata findBySessionId(String sessionId) {
        String[] selectionArgs = {sessionId};

        Cursor c = mSessionMetadataDb.query(SessionMetadataEntry.TABLE_NAME, projection, selection,
                selectionArgs, null, null, null);

        if(!c.moveToFirst()) {
            return null;
        }
        return createSessionMetadata(c);

    }

    private SessionMetadata createSessionMetadata(Cursor c) {
        SessionMetadata sessionMetadata = new SessionMetadata();
        sessionMetadata.setSessionId(c.getString(c.getColumnIndexOrThrow(SessionMetadataEntry
                .COLUMN_NAME_SESSION_ID)));
        sessionMetadata.setUploaded(c.getString(c.getColumnIndexOrThrow(SessionMetadataEntry
                .COLUMN_NAME_UPLOADED)));
        return sessionMetadata;
    }

    public void update(SessionMetadata sessionMetadata) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(SessionMetadataEntry.COLUMN_NAME_SESSION_ID, sessionMetadata.getSessionId());
        contentValues.put(SessionMetadataEntry.COLUMN_NAME_UPLOADED, sessionMetadata.getUploaded());

        if(mSessionMetadataDb.update(SessionMetadataEntry.TABLE_NAME, contentValues, selection, new String[] {sessionMetadata.getSessionId()}) < 1) {
            mSessionMetadataDb.insert(SessionMetadataEntry.TABLE_NAME, null, contentValues);
        }
    }
}
