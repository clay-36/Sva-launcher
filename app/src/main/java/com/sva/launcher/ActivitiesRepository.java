package com.sva.launcher;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads only recommendations exposed by the device TV provider. Never modifies publisher data. */
final class ActivitiesRepository {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int REQUEST_PERMISSION = 7409;
    static final class Channel {
        long id;
        String key, title, publisher;
        final List<Program> programs = new ArrayList<>();
    }
    static final class Program {
        String title, artwork, intent;
    }
    static List<Channel> load(ContentResolver resolver) {
        Map<Long, Channel> channels = new LinkedHashMap<>();
        Uri uri = TvContract.Channels.CONTENT_URI.buildUpon().appendQueryParameter("preview", "true").build();
        try (Cursor c = resolver.query(uri, new String[]{"_id", "package_name", "display_name", "type", "internal_provider_id"}, null, null, null)) {
            if (c != null) while (c.moveToNext() && channels.size() < 200) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(c.getString(3))) continue;
                Channel channel = new Channel();
                channel.id = c.getLong(0); channel.publisher = c.getString(1); channel.title = c.getString(2);
                String providerId = c.getString(4);
                channel.key = channel.publisher + ":" + (providerId == null || providerId.isEmpty() ? channel.id : providerId);
                channels.put(channel.id, channel);
            }
        }
        // No selection clause: ordinary apps cannot use selection on this provider.
        try (Cursor c = resolver.query(TvContract.PreviewPrograms.CONTENT_URI,
                new String[]{"channel_id", "title", "poster_art_uri", "intent_uri"}, null, null, null)) {
            if (c != null) while (c.moveToNext()) {
                Channel channel = channels.get(c.getLong(0));
                if (channel == null || channel.programs.size() >= 30) continue;
                Program program = new Program(); program.title = c.getString(1); program.artwork = c.getString(2); program.intent = c.getString(3);
                if (program.intent != null && !program.intent.isEmpty()) channel.programs.add(program);
            }
        }
        return new ArrayList<>(channels.values());
    }
}
