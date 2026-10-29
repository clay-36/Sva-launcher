package com.sva.launcher;

import android.content.Context;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Asynchronous logo loader with bounded memory, request queue, and disk cache. */
final class ChannelLogoLoader {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_DISK_BYTES = 24L * 1024L * 1024L;
    private static final int MAX_DISK_FILES = 128;

    private final File cacheDir;
    private final int maxLogoPx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memoryCache;
    private final ConcurrentMap<String, Object> fetchLocks = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(96),
            runnable -> { Thread thread = new Thread(runnable, "channel-logo"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    ChannelLogoLoader(Context context) {
        this(context, Math.round(96 * context.getResources().getDisplayMetrics().density));
    }

    ChannelLogoLoader(Context context, int maxImagePx) {
        cacheDir = new File(context.getCacheDir(), "channel-logos");
        cacheDir.mkdirs();
        maxLogoPx = maxImagePx;
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager == null ? 128 : activityManager.getMemoryClass();
        int cacheBytes = Math.max(2 * 1024 * 1024, memoryClass * 1024 * 1024 / 10);
        memoryCache = new LruCache<String, Bitmap>(cacheBytes) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };
    }

    void load(String address, ImageView image, View fallback) {
        image.setTag(address);
        image.setVisibility(View.INVISIBLE);
        fallback.setVisibility(View.VISIBLE);
        if (address == null || address.trim().isEmpty()) return;
        Bitmap cached = memoryCache.get(address);
        if (cached != null) { display(address, cached, image, fallback); return; }
        if (closed) return;
        executor.execute(() -> {
            Bitmap bitmap = null;
            File file = cacheFile(address);
            if (file.isFile()) bitmap = decode(file);
            if (bitmap == null) bitmap = fetch(address, file);
            if (bitmap != null) {
                memoryCache.put(address, bitmap);
                Bitmap loaded = bitmap;
                main.post(() -> { if (!closed) display(address, loaded, image, fallback); });
            }
        });
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        fetchLocks.clear();
        memoryCache.evictAll();
    }

    private void display(String address, Bitmap bitmap, ImageView image, View fallback) {
        Object current = image.getTag();
        if (!address.equals(current)) return;
        image.setImageBitmap(bitmap);
        image.setVisibility(View.VISIBLE);
        fallback.setVisibility(View.GONE);
    }

    private Bitmap fetch(String address, File target) {
        Object lock = fetchLocks.computeIfAbsent(target.getAbsolutePath(), key -> new Object());
        synchronized (lock) {
            try {
                if (target.isFile()) return decode(target);
                return download(address, target);
            } finally {
                fetchLocks.remove(target.getAbsolutePath(), lock);
            }
        }
    }

    private Bitmap download(String address, File target) {
        HttpURLConnection connection = null;
        File temp = new File(target.getPath() + ".tmp");
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "SvaLauncher/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            if (connection.getContentLength() > MAX_IMAGE_BYTES) return null;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_IMAGE_BYTES) { temp.delete(); return null; }
                    output.write(buffer, 0, count);
                }
            }
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) {
                temp.delete();
                return null;
            }
            Bitmap result = decode(target);
            trimDiskCache();
            return result;
        } catch (Exception ignored) {
            temp.delete();
            return null;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private Bitmap decode(File file) {
        return decode(file, maxLogoPx);
    }

    private Bitmap decode(File file, int targetPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) { return null; }
    }

    private static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / sample > target || height / sample > target) sample *= 2;
        return sample;
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private File cacheFile(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(32);
            for (byte b : digest) {
                key.append(HEX_CHARS[(b >> 4) & 0x0f]).append(HEX_CHARS[b & 0x0f]);
            }
            return new File(cacheDir, key + ".img");
        } catch (Exception e) { return new File(cacheDir, Integer.toHexString(address.hashCode()) + ".img"); }
    }

    private void trimDiskCache() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long bytes = 0;
        for (File file : files) bytes += file.length();
        int remaining = files.length;
        if (remaining <= MAX_DISK_FILES && bytes <= MAX_DISK_BYTES) return;
        for (File file : files) {
            if (remaining <= MAX_DISK_FILES && bytes <= MAX_DISK_BYTES) break;
            bytes -= file.length();
            remaining--;
            file.delete();
        }
    }
}
