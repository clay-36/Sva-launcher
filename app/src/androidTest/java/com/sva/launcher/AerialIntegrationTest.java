package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class AerialIntegrationTest extends InstrumentationTestCase {
    private Activity activity;
    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void onMain(Runnable run) { getInstrumentation().runOnMainSync(run); }
    @Override protected void tearDown() throws Exception {
        if (activity != null) onMain(activity::finish);
        getInstrumentation().waitForIdleSync(); super.tearDown();
    }
    public void testBundledCollectionsAndSdrFallback() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        List<AerialCatalog.Clip> all = AerialCatalog.load(context, "all");
        assertTrue(all.size() > 250);
        for (String provider : AerialCatalog.PROVIDERS) {
            List<AerialCatalog.Clip> clips = AerialCatalog.load(context, provider);
            assertFalse(provider, clips.isEmpty());
            for (AerialCatalog.Clip clip : clips) {
                assertTrue(clip.url.startsWith("https://"));
                assertFalse(clip.url.toLowerCase(Locale.ROOT).contains("4k-hdr"));
                assertFalse(clip.title.isEmpty());
            }
        }
        List<AerialCatalog.Clip> fixture = AerialCatalog.parse("{\"assets\":["
                + "{\"id\":\"a\",\"title\":\"Place\",\"url-1080-H264\":\"https://test/h264.mov\",\"url-1080-SDR\":\"https://test/hevc.mov\"},"
                + "{\"id\":\"b\",\"accessibilityLabel\":\"Ocean\",\"url-1080-SDR\":\"https://test/sdr.mov\"},"
                + "{\"id\":\"c\",\"url-4K-HDR\":\"https://test/hdr.mov\"}]}", "{}");
        assertEquals(2, fixture.size()); assertEquals("https://test/h264.mov", fixture.get(0).url);
        assertEquals("https://test/sdr.mov", fixture.get(1).url);
    }
    public void testScreensaverRendersMutedVideoAndReleasesOnExit() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File video = new File(context.getCacheDir(), "aerial-test.mp4");
        try (InputStream input = getInstrumentation().getContext().getAssets().open("iptv/sample.mp4");
             OutputStream output = new FileOutputStream(video)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        activity = TestActivities.start(getInstrumentation(), new Intent(context, ScreensaverPreviewActivity.class)
                .putExtra("aerialPreview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ScreensaverContent content = (ScreensaverContent)field(activity, "slideshow");
        AerialVideoView aerial = (AerialVideoView)field(content, "aerial");
        assertNotNull(aerial);
        ScreensaverView overlay = (ScreensaverView)field(content, "photos");
        assertTrue((Boolean)field(overlay, "overlayOnly"));
        CountDownLatch rendered = new CountDownLatch(1);
        try {
            onMain(() -> {
                try {
                    aerial.stop();
                    Method play = AerialVideoView.class.getDeclaredMethod("play", List.class); play.setAccessible(true);
                    play.invoke(aerial, Collections.singletonList(new AerialCatalog.Clip("fixture", video.toURI().toString(), "Fixture landscape")));
                    ExoPlayer player = (ExoPlayer)field(aerial, "player");
                    assertEquals(0f, player.getVolume(), .001f);
                    assertEquals(Player.REPEAT_MODE_ALL, player.getRepeatMode());
                    assertTrue(player.getTrackSelectionParameters().disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_AUDIO));
                    player.addListener(new Player.Listener() { @Override public void onRenderedFirstFrame() { rendered.countDown(); } });
                } catch (Exception e) { throw new AssertionError(e); }
            });
            assertTrue("Video did not render", rendered.await(10, TimeUnit.SECONDS));
            onMain(() -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
            getInstrumentation().waitForIdleSync(); SystemClock.sleep(100);
            assertNull(field(aerial, "player")); assertTrue((Boolean)field(overlay, "stopped"));
        } finally { video.delete(); }
    }
    public void testOnlineWallpaperFirstFrame() throws Exception {
        android.os.Bundle args = ((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments();
        if (!"true".equals(args.getString("onlineAerial"))) return;
        Context context = getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        boolean old = prefs.getBoolean(AerialCatalog.WALLPAPER, false);
        String provider = prefs.getString(AerialCatalog.PROVIDER, "all");
        prefs.edit().putBoolean(AerialCatalog.WALLPAPER, true).putString(AerialCatalog.PROVIDER, "amazon").commit();
        try {
            activity = TestActivities.start(getInstrumentation(), new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AerialVideoView aerial = (AerialVideoView)field(activity, "aerialWallpaper");
            long deadline = SystemClock.uptimeMillis() + 45000;
            boolean ready = false;
            while (SystemClock.uptimeMillis() < deadline) {
                boolean[] rendered = {false};
                onMain(() -> {
                    try {
                        ExoPlayer player = (ExoPlayer)field(aerial, "player");
                        View surface = (View)field(aerial, "surface");
                        rendered[0] = player != null && player.isPlaying() && surface.getAlpha() == 1f;
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                if (rendered[0]) { ready = true; break; }
                SystemClock.sleep(100);
            }
            String[] diagnostic = {""};
            onMain(() -> {
                try {
                    ExoPlayer player = (ExoPlayer)field(aerial, "player");
                    diagnostic[0] = "active=" + field(activity, "screenActive") + ", settings=" + field(activity, "settingsScreen")
                            + ", visible=" + aerial.getVisibility() + ", attached=" + aerial.isAttachedToWindow()
                            + ", provider=" + field(aerial, "activeProvider") + ", worker=" + field(aerial, "worker")
                            + ", player=" + player + ", state=" + (player == null ? -1 : player.getPlaybackState())
                            + ", error=" + (player == null ? "" : player.getPlayerError())
                            + ", alpha=" + ((View)field(aerial, "surface")).getAlpha();
                } catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue("Online wallpaper did not render: " + diagnostic[0], ready);
            android.graphics.Bitmap capture = getInstrumentation().getUiAutomation().takeScreenshot();
            try (OutputStream output = new FileOutputStream(new File(context.getFilesDir(), "aerial-home-test.png"))) {
                capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } finally { capture.recycle(); }
        } finally { prefs.edit().putBoolean(AerialCatalog.WALLPAPER, old).putString(AerialCatalog.PROVIDER, provider).commit(); }
    }

    public void testWallpaperEnabledAndReleasedWhenLauncherPauses() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        boolean old = prefs.getBoolean(AerialCatalog.WALLPAPER, false);
        prefs.edit().putBoolean(AerialCatalog.WALLPAPER, true).commit();
        try {
            activity = TestActivities.start(getInstrumentation(), new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AerialVideoView aerial = (AerialVideoView)field(activity, "aerialWallpaper");
            assertEquals(View.VISIBLE, aerial.getVisibility());
            // Pause through a real activity transition, rather than directly calling lifecycle methods.
            Activity settings = TestActivities.start(getInstrumentation(), new Intent(context, AerialSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync();
            assertNull(field(aerial, "player")); assertNull(field(aerial, "worker"));
            onMain(settings::finish);
        } finally { prefs.edit().putBoolean(AerialCatalog.WALLPAPER, old).commit(); }
    }
}
