package net.sll_mdilab.datauploader.database;

import android.provider.BaseColumns;

public final class SessionMetadataContract {
    public static abstract class SessionMetadataEntry implements BaseColumns {
        public static final String TABLE_NAME = "entry";
        public static final String COLUMN_NAME_SESSION_ID = "session_id";
        public static final String COLUMN_NAME_UPLOADED = "uploaded";
    }
}
