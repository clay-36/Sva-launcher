package com.sva.launcher;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Java adaptation of Aerial Views' provider catalogue and SDR quality selection.
 * Upstream: theothernt/AerialViews, GPL-3.0; see third_party/aerialviews/NOTICE.md. */
final class AerialCatalog {
    static final String WALLPAPER = "aerialWallpaper";
    static final String SCREENSAVER = "screensaverAerial";
    static final String PROVIDER = "aerialProvider";
    static final String[] PROVIDERS = {"all", "apple", "jetson", "robin", "amazon"};
    static final class Clip {
        final String id, url, title;
        Clip(String id, String url, String title) { this.id = id; this.url = url; this.title = title; }
    }
    static int providerIndex(String provider) {
        for (int i = 0; i < PROVIDERS.length; i++) if (PROVIDERS[i].equals(provider)) return i;
        return 0;
    }
    static List<Clip> load(Context context, String provider) throws Exception {
        int[] catalogs = {R.raw.aerial_tvos26, R.raw.aerial_comm1, R.raw.aerial_comm2, R.raw.aerial_fireos8};
        int[] strings = {R.raw.aerial_tvos26_strings, R.raw.aerial_comm1_strings,
                R.raw.aerial_comm2_strings, R.raw.aerial_fireos8_strings};
        LinkedHashMap<String, Clip> clips = new LinkedHashMap<>();
        int selected = providerIndex(provider);
        for (int i = 0; i < catalogs.length; i++) {
            if (selected != 0 && selected != i + 1) continue;
            for (Clip clip : parse(read(context, catalogs[i]), read(context, strings[i]))) clips.put(clip.url, clip);
        }
        return new ArrayList<>(clips.values());
    }
    private static String read(Context context, int resource) throws Exception {
        try (InputStream input = context.getResources().openRawResource(resource);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    static List<Clip> parse(String catalog, String locations) throws Exception {
        JSONArray assets = new JSONObject(catalog).getJSONArray("assets");
        JSONObject strings = new JSONObject(locations);
        List<Clip> clips = new ArrayList<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String url = asset.optString("url-1080-H264", "");
            if (url.isEmpty()) url = asset.optString("url-1080-SDR", "");
            if (!(url.startsWith("https://") || url.startsWith("http://"))) continue;
            String title = strings.optString(asset.optString("localizedNameKey", ""), "");
            if (title.isEmpty()) title = asset.optString("title", "");
            String location = asset.optString("accessibilityLabel", "");
            if (title.isEmpty()) title = location;
            else if (!location.isEmpty() && !title.equals(location)) title += " · " + location;
            clips.add(new Clip(asset.optString("id", url), url, title));
        }
        return clips;
    }
}
