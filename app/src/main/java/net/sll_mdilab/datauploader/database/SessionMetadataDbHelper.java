package net.sll_mdilab.datauploader.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static net.sll_mdilab.datauploader.database.SessionMetadataContract.SessionMetadataEntry;

public class SessionMetadataDbHelper extends SQLiteOpenHelper {
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
        "CREATE TABLE " + SessionMetadataEntry.TABLE_NAME + " (" +
                SessionMetadataEntry._ID + " INTEGER PRIMARY KEY," +
                SessionMetadataEntry.COLUMN_NAME_SESSION_ID + TEXT_TYPE + "UNIQUE" + COMMA_SEP +
                SessionMetadataEntry.COLUMN_NAME_UPLOADED + TEXT_TYPE +
        " )";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + SessionMetadataEntry.TABLE_NAME;

    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "SessionMetadata.db";

    public SessionMetadataDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }
}
