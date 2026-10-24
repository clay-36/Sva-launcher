package com.sva.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Actual playback against a loopback server; no provider account or public media is contacted. */
@UnstableApi
public final class IptvPlaybackTest extends InstrumentationTestCase {
    private static final String PAIR = "000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff";
    private FixtureServer server;

    @Override protected void setUp() throws Exception { super.setUp(); server = new FixtureServer(); }
    @Override protected void tearDown() throws Exception { server.close(); super.tearDown(); }

    private PlaybackRequest request(String path, String mime, String drm, String license, String keys,
                                    Map<String, String> stream, Map<String, String> licenseHeaders) {
        return new PlaybackRequest(server.url(path), mime, drm, license, keys, stream, licenseHeaders);
    }

    private PlaybackRequest plain(String path, String mime) {
        return request(path, mime, "", "", "", Collections.emptyMap(), Collections.emptyMap());
    }

    public void testActivityBufferingIndicatorClearsOnReadyErrorAndRelease() throws Exception {
        server.hold = true;
        Intent intent = new Intent(getInstrumentation().getTargetContext(), PlaybackActivity.class)
                .putExtra("name", "Buffering fixture")
                .putExtra("playbackRequest", plain("/plain/sample.mp4", "").toJson().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PlaybackActivity activity = (PlaybackActivity)getInstrumentation().startActivitySync(intent);
        try {
            assertTrue(server.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            android.view.View buffering = activity.findViewById(androidx.media3.ui.R.id.exo_buffering);
            getInstrumentation().runOnMainSync(() -> assertEquals(android.view.View.VISIBLE, buffering.getVisibility()));
            server.release.countDown();
            awaitActivityPlayer(activity);
            getInstrumentation().runOnMainSync(() -> assertEquals(android.view.View.GONE, buffering.getVisibility()));
            java.lang.reflect.Method start = PlaybackActivity.class.getDeclaredMethod("startPlayback", PlaybackRequest.class);
            start.setAccessible(true);
            getInstrumentation().runOnMainSync(() -> {
                try { start.invoke(activity, plain("/missing", "")); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            java.lang.reflect.Field player = PlaybackActivity.class.getDeclaredField("player"); player.setAccessible(true);
            AtomicBoolean failed = new AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (!failed.get() && SystemClock.uptimeMillis() < deadline) {
                getInstrumentation().runOnMainSync(() -> {
                    try { failed.set(((ExoPlayer)player.get(activity)).getPlayerError() != null); }
                    catch (Exception e) { throw new AssertionError(e); }
                });
                SystemClock.sleep(25);
            }
            assertTrue("Missing stream must produce an error", failed.get());
            getInstrumentation().runOnMainSync(() -> assertEquals(android.view.View.GONE, buffering.getVisibility()));
            java.lang.reflect.Method release = PlaybackActivity.class.getDeclaredMethod("releasePlayer"); release.setAccessible(true);
            getInstrumentation().runOnMainSync(() -> {
                try { release.invoke(activity); }
                catch (Exception e) { throw new AssertionError(e); }
                assertEquals(android.view.View.GONE, buffering.getVisibility());
            });
        } finally { server.release.countDown(); getInstrumentation().runOnMainSync(activity::finish); }
    }

    public void testOrdinaryHlsDashTsAndMp4RemainPlayable() throws Exception {
        play(plain("/plain/plain.m3u8", ""));
        play(plain("/plain/dash.mpd", ""));
        play(plain("/plain/sample.ts", ""));
        play(plain("/plain/sample.mp4", ""));
        assertTrue(server.violations.toString(), server.violations.isEmpty());
    }

    public void testExplicitHintsAndHeadersCoverManifestSegmentsAndHlsKeys() throws Exception {
        Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer stream");
        play(request("/protected/hls", StreamOptions.HLS, "", "", "", headers, Collections.emptyMap()));
        play(request("/protected/dash", StreamOptions.DASH, "", "", "", headers, Collections.emptyMap()));
        assertTrue(server.paths.contains("/protected/key.bin"));
        assertTrue(server.paths.contains("/protected/encrypted-0.ts"));
        assertTrue(server.paths.contains("/protected/dash-init.mp4"));
        assertTrue(server.paths.contains("/protected/dash-1.m4s"));
        assertTrue(server.violations.toString(), server.violations.isEmpty());
    }

    public void testClearKeyInlineAndLicenseEndpointActuallyDecode() throws Exception {
        play(request("/ck/dash", StreamOptions.DASH, "clearkey", "", PAIR,
                Collections.emptyMap(), Collections.emptyMap()));
        play(request("/protected/ck/dash", StreamOptions.DASH, "clearkey", server.url("/license"), "",
                Collections.singletonMap("Authorization", "Bearer stream"),
                Collections.singletonMap("Authorization", "Bearer license")));
        assertTrue("The ClearKey license endpoint was not requested", server.paths.contains("/license"));
        assertTrue(server.violations.toString(), server.violations.isEmpty());
    }

    public void testWidevineConfigurationIsUnchanged() throws Exception {
        IptvRepository.Channel channel = new IptvRepository.Channel("Test", "https://test/video.mpd", "", "", "",
                "widevine", "https://license.test/own", Collections.singletonMap("Authorization", "own"));
        PlaybackRequest request = PlaybackRequest.resolve(channel, "https://license.test/default", Collections.singletonMap("Authorization", "default"));
        getInstrumentation().runOnMainSync(() -> {
            MediaItem item = IptvPlayback.mediaItem(request);
            assertNotNull(item.localConfiguration);
            assertNull(item.localConfiguration.mimeType);
            MediaItem.DrmConfiguration drm = item.localConfiguration.drmConfiguration;
            assertNotNull(drm); assertEquals(C.WIDEVINE_UUID, drm.scheme);
            assertEquals("https://license.test/own", drm.licenseUri.toString());
            assertEquals("own", drm.licenseRequestHeaders.get("Authorization"));
            assertFalse(drm.multiSession);
        });
    }

    public void testChannelSwitchDropsPreviousHeadersAndDrmConfiguration() throws Exception {
        PlaybackRequest first = request("/protected/ck/dash", StreamOptions.DASH, "clearkey", "", PAIR,
                Collections.singletonMap("Authorization", "Bearer stream"), Collections.emptyMap());
        PlaybackRequest second = request("/protected/hls", StreamOptions.HLS, "", "", "",
                Collections.singletonMap("Authorization", "Bearer stream"), Collections.emptyMap());
        PlaybackRequest third = plain("/plain/hls", StreamOptions.HLS);
        JSONArray zap = new JSONArray().put(new JSONObject().put("name", "ClearKey").put("playbackRequest", first.toJson()))
                .put(new JSONObject().put("name", "Protected").put("playbackRequest", second.toJson()))
                .put(new JSONObject().put("name", "Ordinary").put("playbackRequest", third.toJson()));
        Intent intent = new Intent(getInstrumentation().getTargetContext(), PlaybackActivity.class)
                .putExtra("name", "Protected").putExtra("playbackRequest", first.toJson().toString())
                .putExtra("zapChannels", zap.toString()).putExtra("zapIndex", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PlaybackActivity activity = (PlaybackActivity)getInstrumentation().startActivitySync(intent);
        try {
            awaitActivityPlayer(activity);
            getInstrumentation().runOnMainSync(() -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
            awaitActivityPlayer(activity);
            assertTrue(server.paths.contains("/protected/hls"));
            getInstrumentation().runOnMainSync(() -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
            awaitActivityPlayer(activity);
            assertTrue(server.paths.contains("/plain/hls"));
            assertTrue(server.violations.toString(), server.violations.isEmpty());
        } finally { getInstrumentation().runOnMainSync(activity::finish); }
    }

    public void testLargeDrmPlaylistLaunchUsesSmallIntentAndStillSwitchesChannels() throws Exception {
        PlaybackRequest drm = request("/protected/ck/dash", StreamOptions.DASH, "clearkey", "", PAIR,
                Collections.singletonMap("Authorization", "Bearer stream"), Collections.emptyMap());
        JSONArray channels = new JSONArray();
        for (int i = 0; i < 5000; i++) channels.put(new JSONObject().put("name", "Channel " + i)
                .put("playbackRequest", drm.toJson()).put("now", "Current programme").put("next", "Next programme"));
        int selected = 2345;
        Intent payload = new Intent(getInstrumentation().getTargetContext(), PlaybackActivity.class)
                .putExtra("name", "Channel " + selected).putExtra("playbackRequest", drm.toJson().toString())
                .putExtra("zapChannels", channels.toString()).putExtra("zapIndex", selected)
                .putExtra("nowStartMs", 1700000000000L).putExtra("nowEndMs", 1700003600000L);
        assertTrue("Fixture must exceed Binder's transaction capacity", parcelSize(payload) > 1024 * 1024);
        Context context = getInstrumentation().getTargetContext();
        Intent launch = PlaybackSession.createIntent(context, payload).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertTrue("Only a small session identifier should cross Binder", parcelSize(launch) < 2048);
        assertFalse(launch.hasExtra("zapChannels")); assertFalse(launch.hasExtra("playbackRequest"));
        Intent restored = PlaybackSession.restore(context, launch);
        assertEquals(selected, restored.getIntExtra("zapIndex", -1));
        assertEquals(1700000000000L, restored.getLongExtra("nowStartMs", 0));
        assertEquals(1700003600000L, restored.getLongExtra("nowEndMs", 0));
        assertEquals(5000, new JSONArray(restored.getStringExtra("zapChannels")).length());
        // Reading again represents a fresh Activity/process recovering from the original token.
        assertEquals(payload.getStringExtra("playbackRequest"),
                PlaybackSession.restore(context, new Intent(launch)).getStringExtra("playbackRequest"));
        PlaybackActivity activity = (PlaybackActivity)getInstrumentation().startActivitySync(launch);
        try {
            awaitActivityPlayer(activity);
            assertTrue(parcelSize(activity.getIntent()) < 2048);
            getInstrumentation().runOnMainSync(() -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
            awaitActivityPlayer(activity);
            java.lang.reflect.Field index = PlaybackActivity.class.getDeclaredField("zapIndex"); index.setAccessible(true);
            assertEquals(selected + 1, index.getInt(activity));
            assertTrue(server.violations.toString(), server.violations.isEmpty());
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            new File(new File(context.getCacheDir(), "playback-sessions"), launch.getStringExtra(PlaybackSession.EXTRA) + ".json").delete();
        }
    }

    public void testUnavailablePlaybackSessionShowsRecoverableError() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        for (String token : new String[]{"../invalid", UUID.randomUUID().toString()}) {
            Intent launch = new Intent(context, PlaybackActivity.class).putExtra(PlaybackSession.EXTRA, token)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PlaybackActivity activity = (PlaybackActivity)getInstrumentation().startActivitySync(launch);
            try {
                getInstrumentation().waitForIdleSync();
                java.lang.reflect.Field message = PlaybackActivity.class.getDeclaredField("message"); message.setAccessible(true);
                assertTrue(((android.widget.TextView)message.get(activity)).getText().toString().contains("select the channel again"));
            } finally { getInstrumentation().runOnMainSync(activity::finish); }
        }
    }

    private int parcelSize(Intent intent) {
        Parcel parcel = Parcel.obtain();
        try { intent.writeToParcel(parcel, 0); return parcel.dataSize(); }
        finally { parcel.recycle(); }
    }

    public void testCacheAndBackupRetainOptionsAndReadOldChannels() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        File root = new File(target.getCacheDir(), "iptv-cache-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        String preferencePrefix = "iptv-test-" + System.nanoTime();
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return root; }
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(preferencePrefix + name, mode);
            }
        };
        try {
            IptvRepository repository = new IptvRepository(isolated);
            JSONObject old = new JSONObject().put("name", "Old").put("url", "https://test/old.m3u8");
            JSONObject fresh = new JSONObject().put("name", "New").put("url", "https://test/opaque")
                    .put("mimeType", StreamOptions.DASH).put("drmType", "clearkey").put("clearKeyData", PAIR)
                    .put("streamHeaders", new JSONObject().put("Authorization", "Bearer stream"));
            repository.restoreCachedChannels(new JSONArray().put(old).put(fresh));
            List<IptvRepository.Channel> loaded = new IptvRepository(isolated).cachedChannels();
            assertEquals(2, loaded.size()); assertEquals("", loaded.get(0).mimeType);
            assertTrue(loaded.get(0).streamHeaders.isEmpty()); assertEquals(PAIR, loaded.get(1).clearKeyData);
            assertEquals(StreamOptions.DASH, loaded.get(1).mimeType);
            JSONObject exported = repository.exportCachedChannels().getJSONObject(1);
            assertEquals(PAIR, exported.getString("clearKeyData"));
            assertEquals("Bearer stream", exported.getJSONObject("streamHeaders").getString("Authorization"));
            JSONObject backup = LauncherBackup.create(isolated.getSharedPreferences("launcher", 0),
                    isolated.getSharedPreferences("iptv", 0), repository);
            LauncherBackup.restore(backup, isolated.getSharedPreferences("launcher", 0), isolated.getSharedPreferences("iptv", 0), repository);
            assertEquals(PAIR, repository.cachedChannels().get(1).clearKeyData);
        } finally {
            target.deleteSharedPreferences(preferencePrefix + "launcher"); target.deleteSharedPreferences(preferencePrefix + "iptv");
            delete(root);
        }
    }

    private void play(PlaybackRequest request) throws Exception {
        AtomicReference<ExoPlayer> player = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean frame = new AtomicBoolean();
        ImageReader images = ImageReader.newInstance(160, 90, ImageFormat.YUV_420_888, 3);
        images.setOnImageAvailableListener(reader -> { try (Image image = reader.acquireLatestImage()) { } }, new Handler(Looper.getMainLooper()));
        try {
            getInstrumentation().runOnMainSync(() -> {
                ExoPlayer p = IptvPlayback.create(getInstrumentation().getTargetContext(), request); player.set(p);
                p.setVideoSurface(images.getSurface());
                p.addListener(new Player.Listener() {
                    @Override public void onRenderedFirstFrame() { frame.set(true); }
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException error) { failure.set(error.getErrorCodeName()); }
                });
                p.prepare(); p.play();
            });
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (!frame.get() && failure.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
            assertNull("Playback failed with " + failure.get(), failure.get());
            assertTrue("No video frame was rendered", frame.get());
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (player.get() != null) player.get().release(); images.close(); });
        }
    }

    private void awaitActivityPlayer(PlaybackActivity activity) throws Exception {
        java.lang.reflect.Field field = PlaybackActivity.class.getDeclaredField("player"); field.setAccessible(true);
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean ready = new AtomicBoolean(); AtomicReference<String> error = new AtomicReference<>();
        while (!ready.get() && error.get() == null && SystemClock.uptimeMillis() < deadline) {
            getInstrumentation().runOnMainSync(() -> {
                try {
                    ExoPlayer p = (ExoPlayer)field.get(activity);
                    if (p != null) {
                        if (p.getPlayerError() != null) error.set(p.getPlayerError().getErrorCodeName());
                        ready.set(p.getVideoSize().width > 0 && p.getCurrentPosition() > 200);
                    }
                } catch (Exception e) { throw new AssertionError(e); }
            });
            SystemClock.sleep(30);
        }
        assertNull(error.get(), error.get()); assertTrue("Activity player never became ready", ready.get());
    }

    private void delete(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child); file.delete(); }

    private final class FixtureServer implements Closeable {
        final java.net.ServerSocket socket;
        final Thread worker;
        volatile boolean hold;
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        final List<String> violations = Collections.synchronizedList(new ArrayList<>());
        FixtureServer() throws Exception {
            socket = new java.net.ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket client = socket.accept()) { serve(client); }
                    catch (Exception e) { if (!socket.isClosed()) violations.add("Fixture server error: " + e.getClass().getSimpleName()); }
                }
            }, "iptv-fixture-server"); worker.start();
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + path; }
        void serve(Socket client) throws Exception {
            client.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(client.getInputStream());
            String request = line(in); if (request.isEmpty()) return;
            String path = request.split(" ")[1].split("\\?")[0]; paths.add(path);
            if (hold) { entered.countDown(); release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
            if (path.equals("/missing")) { respond(client, 404, new byte[0], null, ""); return; }
            Map<String, String> headers = StreamOptions.headers();
            String header;
            while (!(header = line(in)).isEmpty()) {
                int colon = header.indexOf(':'); if (colon > 0) headers.put(header.substring(0, colon), header.substring(colon + 1).trim());
            }
            int length = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            for (int i = 0; i < length; i++) if (in.read() < 0) break;
            String expected = path.equals("/license") ? "Bearer license" : path.startsWith("/protected/") ? "Bearer stream" : null;
            if (!Objects.equals(expected, headers.get("Authorization"))) {
                violations.add("Wrong header scope for " + path); respond(client, 403, new byte[0], null, ""); return;
            }
            byte[] bytes;
            if (path.equals("/license")) bytes = StreamOptions.clearKeyResponse(PAIR);
            else {
                String asset = path.substring(path.lastIndexOf('/') + 1);
                if (asset.equals("hls")) asset = path.startsWith("/protected/") ? "encrypted.m3u8" : "plain.m3u8";
                if (asset.equals("dash")) asset = path.contains("/ck/") ? "ck.mpd" : "dash.mpd";
                try (InputStream input = getInstrumentation().getContext().getAssets().open("iptv/" + asset);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                }
            }
            respond(client, 200, bytes, headers.get("Range"), path.equals("/license") ? "application/json" : "application/octet-stream");
        }
        void respond(Socket client, int code, byte[] bytes, String range, String type) throws Exception {
            int start = 0, end = bytes.length - 1;
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-", -1); start = Integer.parseInt(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) end = Math.min(end, Integer.parseInt(parts[1])); code = 206;
            }
            int size = Math.max(0, end - start + 1);
            String response = "HTTP/1.1 " + code + " OK\r\nContent-Length: " + size + "\r\nContent-Type: " + type + "\r\nConnection: close\r\n";
            if (code == 206) response += "Content-Range: bytes " + start + "-" + end + "/" + bytes.length + "\r\n";
            OutputStream out = client.getOutputStream(); out.write((response + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (size > 0) out.write(bytes, start, size); out.flush();
        }
        String line(InputStream in) throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); int value;
            while ((value = in.read()) >= 0 && value != '\n') {
                if (out.size() > 16384) throw new IOException("Header too large"); if (value != '\r') out.write(value);
            }
            return out.toString("UTF-8");
        }
        @Override public void close() throws IOException { release.countDown(); socket.close(); try { worker.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    }
}
