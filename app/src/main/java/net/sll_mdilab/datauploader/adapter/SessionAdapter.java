package net.sll_mdilab.datauploader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.fitness.data.Session;

import net.sll_mdilab.datauploader.R;

import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SessionAdapter extends ArrayAdapter<SessionListItem> {
    private static final  SimpleDateFormat sLongDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat sShortDateFormat = new SimpleDateFormat("HH:mm:ss");

    private final Context mContext;

    private static class ViewHolder {
        public final TextView title;
        public final TextView start;
        public final TextView end;
        public final ImageView imageView;

        public ViewHolder(TextView title, TextView start, TextView end, ImageView imageView) {
            this.title = title;
            this.start = start;
            this.end = end;
            this.imageView = imageView;
        }
    }

    public SessionAdapter(Context context) {
        super(context, R.layout.session_list_item);
        mContext = context;
    }

    public SessionAdapter(Context context, SessionListItem[] objects) {
        super(context, R.layout.session_list_item, objects);
        mContext = context;
    }

    public SessionAdapter(Context context, int resource, List<SessionListItem> objects) {
        super(context, resource, objects);
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView title;
        TextView start;
        TextView end;
        ImageView imageView;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context
                    .LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.session_list_item, null);

            title = (TextView) convertView.findViewById(R.id.session_list_item_text_view);
            start = (TextView) convertView.findViewById(R.id.session_list_item_start_time_text);
            end = (TextView) convertView.findViewById(R.id.session_list_item_end_time_text);
            imageView = (ImageView) convertView.findViewById(R.id.session_list_item_image);

            convertView.setTag(new ViewHolder(title, start, end, imageView));
        } else {
            ViewHolder viewHolder = (ViewHolder) convertView.getTag();
            title = viewHolder.title;
            start = viewHolder.start;
            end = viewHolder.end;
            imageView = viewHolder.imageView;
        }

        SessionListItem item = getItem(position);
        Session session = item.getSession();

        title.setText(formatSessionName(session));
        start.setText(sLongDateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS)));

        int drawableId = item.isUploaded() ? R.drawable.ic_done_black_48dp : R.drawable.ic_cloud_upload_black_48dp;
        imageView.setImageDrawable(mContext.getDrawable(drawableId));

        if(session.getStartTime(TimeUnit.DAYS) == session.getEndTime(TimeUnit.DAYS)) {
            end.setText(sShortDateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));
        } else {
            end.setText(sLongDateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));
        }

        return convertView;
    }

    private String formatSessionName(Session session) {
        String sessionName = session.getName();
        if(StringUtils.isBlank(sessionName)) {
            return mContext.getString(R.string.unnamed_session);
        } else if (sessionName.contains("-") && !sessionName.startsWith("-")) {
            return sessionName.substring(0, sessionName.indexOf("-"));
        }
        return sessionName;
    }
}
