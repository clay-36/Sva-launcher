package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

/** Uses the Home photo disk cache, with separate album metadata for screensavers. */
final class ScreensaverPhotoRepository {
    private final Context context;
    private final SharedPreferences prefs;
    ScreensaverPhotoRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }
    List<String> cached(String album) {
        String json = "[]";
        if (!album.isEmpty() && album.equals(prefs.getString("screensaverCacheUrl", "")))
            json = prefs.getString("screensaverCacheImages", "[]");
        else if (!album.isEmpty() && album.equals(prefs.getString("googlePhotosCacheUrl", "")))
            json = prefs.getString("googlePhotosCacheImages", "[]");
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String url = array.optString(i);
                if (url.startsWith("https://")) result.add(url);
            }
        } catch (Exception ignored) { }
        return result;
    }
    List<String> refresh(String album, boolean force) throws Exception {
        List<String> existing = cached(album);
        long cadence = SchedulePreferences.get(prefs, SchedulePreferences.GOOGLE_PHOTOS,
                SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS);
        long updated = prefs.getLong("screensaverCacheUpdatedAt", 0);
        boolean same = album.equals(prefs.getString("screensaverCacheUrl", ""));
        if (!force && !existing.isEmpty() && (cadence == 0 || same && cadence > 0
                && System.currentTimeMillis() - updated < cadence)) return existing;
        List<String> urls = GooglePhotosAlbumRepository.fetchImageUrls(album);
        if (urls.isEmpty()) throw new IOException("No photos found. Check that the album is publicly shared.");
        prefs.edit().putString("screensaverCacheUrl", album)
                .putString("screensaverCacheImages", new JSONArray(urls).toString())
                .putLong("screensaverCacheUpdatedAt", System.currentTimeMillis()).apply();
        return urls;
    }
    Bitmap load(String url) {
        HttpURLConnection connection = null;
        File temporary = null;
        try {
            File directory = new File(context.getCacheDir(), "google-photos");
            directory.mkdirs();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
            StringBuilder key = new StringBuilder();
            for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b));
            File file = new File(directory, key + ".img");
            Bitmap cached = decode(file);
            if (cached != null) { file.setLastModified(System.currentTimeMillis()); return cached; }
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            if (connection.getResponseCode() != 200) return null;
            temporary = File.createTempFile("dream-", ".tmp", directory);
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int total = 0, count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || (total += count) > 12 * 1024 * 1024) return null;
                    out.write(buffer, 0, count);
                }
            }
            Bitmap bitmap = decode(temporary);
            if (bitmap != null) {
                temporary.renameTo(file);
                File[] files = directory.listFiles(f -> f.getName().endsWith(".img"));
                if (files != null && files.length > 100) {
                    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                    for (int i = 0; i < files.length - 100; i++) files[i].delete();
                }
            }
            return bitmap;
        } catch (Exception ignored) { return null; }
        finally {
            if (connection != null) connection.disconnect();
            if (temporary != null) temporary.delete();
        }
    }
    Bitmap loadCached(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
            StringBuilder key = new StringBuilder();
            for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b));
            return decode(new File(new File(context.getCacheDir(), "google-photos"), key + ".img"));
        } catch (Exception ignored) { return null; }
    }
    private Bitmap decode(File file) {
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.toString(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 1920 || bounds.outHeight / options.inSampleSize > 1080)
            options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.toString(), options);
    }
}
