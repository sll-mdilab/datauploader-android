package net.sll_mdilab.datauploader.adapter;

import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.Session;

import java.util.List;

public class SessionListItem {
    private Session mSession;
    private List<DataSet> mDataSets;
    private boolean mIsUploaded;

    public Session getSession() {
        return mSession;
    }

    public void setSession(Session session) {
        mSession = session;
    }

    @Override
    public String toString() {
        return mSession.toString();
    }

    public List<DataSet> getDataSets() {
        return mDataSets;
    }

    public void setDataSets(List<DataSet> dataSets) {
        mDataSets = dataSets;
    }

    public boolean isUploaded() {
        return mIsUploaded;
    }

    public void setIsUploaded(boolean isUploaded) {
        mIsUploaded = isUploaded;
    }
}
