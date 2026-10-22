package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import androidx.media3.common.util.UnstableApi;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Keep playlists and credentials off Binder; only a private session identifier crosses activities. */
@UnstableApi
final class PlaybackSession {
    static final String EXTRA = "playbackSession";
    private static final long RETENTION_MS = 24L * 60 * 60 * 1000;

    private PlaybackSession() { }

    static Intent createIntent(Context context, Intent payload) throws Exception {
        JSONObject data = new JSONObject();
        if (payload.getExtras() != null) {
            for (String key : payload.getExtras().keySet()) data.put(key, payload.getExtras().get(key));
        }
        File directory = new File(context.getCacheDir(), "playback-sessions");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException(AppLanguage.text(R.string.ui_cannot_save_playback_session, "Cannot save playback session."));
        File[] old = directory.listFiles();
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        if (old != null) for (File file : old) if (file.lastModified() < cutoff) file.delete();
        String token = UUID.randomUUID().toString();
        AtomicFile file = new AtomicFile(new File(directory, token + ".json"));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException error) {
            file.failWrite(output);
            throw new IOException(AppLanguage.text(R.string.ui_cannot_save_playback_session, "Cannot save playback session."));
        }
        return new Intent(context, PlaybackActivity.class).putExtra(EXTRA, token);
    }

    static Intent restore(Context context, Intent launch) throws Exception {
        String token = launch.getStringExtra(EXTRA);
        if (token == null) return launch; // Existing direct playback callers remain supported.
        if (!token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IOException(AppLanguage.text(R.string.ui_invalid_playback_session, "Invalid playback session."));
        AtomicFile file = new AtomicFile(new File(new File(context.getCacheDir(), "playback-sessions"), token + ".json"));
        JSONObject data = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        Intent payload = new Intent();
        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equals("zapIndex")) payload.putExtra(key, data.optInt(key));
            else if (key.equals("nowStartMs") || key.equals("nowEndMs")) payload.putExtra(key, data.optLong(key));
            else payload.putExtra(key, data.optString(key));
        }
        // Leave the Activity's actual Intent small so recreation also uses the saved session.
        return payload;
    }
}
