package com.deo.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

@UnstableApi
public final class PlaybackActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout infoPanel;
    private TextView message, programmeTitle, programmeTimes, nextProgramme;
    private ProgressBar programmeProgress;
    private String currentChannelName = "Channel";
    private String currentProgramme = "";
    private String followingProgramme = "";
    private long programmeStartMs, programmeEndMs;
    private boolean widevine;
    private JSONArray zapChannels;
    private JSONArray programmeSchedule;
    private int zapIndex;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideMessage;
    private final Runnable programmeTick = new Runnable() {
        @Override public void run() {
            boolean panelWasVisible = infoPanel != null && infoPanel.getVisibility() == View.VISIBLE;
            if (!updateProgrammeFromCache()) updateProgrammeProgress();
            if (!panelWasVisible && infoPanel != null) infoPanel.setVisibility(View.GONE);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String name = getIntent().getStringExtra("name");
        currentChannelName = name == null ? "Channel" : name;
        String streamUrl = getIntent().getStringExtra("url");
        String licenseUrl = getIntent().getStringExtra("licenseUrl");
        widevine = getIntent().getBooleanExtra("widevine", false);
        try { zapChannels = new JSONArray(getIntent().getStringExtra("zapChannels")); } catch (Exception ignored) { zapChannels = new JSONArray(); }
        zapIndex = getIntent().getIntExtra("zapIndex", 0);
        JSONObject activeChannel = zapChannels.optJSONObject(zapIndex);
        programmeSchedule = activeChannel == null ? null : activeChannel.optJSONArray("programmes");
        if (programmeSchedule == null) {
            try { programmeSchedule = new JSONArray(getIntent().getStringExtra("programmes")); }
            catch (Exception ignored) { programmeSchedule = null; }
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        frame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(24), dp(18), dp(24), dp(18));
        boolean light = LiquidTheme.isLight(this);
        GradientDrawable messageBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                light ? new int[]{0xEFFFFFFF, 0xE8EAF0F4} : new int[]{0xDB28394C, 0xDA101721});
        messageBackground.setCornerRadius(dp(20));
        messageBackground.setStroke(dp(1), LiquidTheme.border(this));
        infoPanel.setBackground(messageBackground);
        infoPanel.setElevation(dp(8));
        message = new TextView(this);
        message.setText("Loading " + currentChannelName + "…");
        message.setTextColor(LiquidTheme.accent(this));
        message.setTextSize(12);
        message.setLetterSpacing(.08f);
        message.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        message.setShadowLayer(dp(8), 0, 0, light ? 0x44087F73 : 0x8870E8D3);
        infoPanel.addView(message);
        programmeTitle = new TextView(this);
        programmeTitle.setTextColor(LiquidTheme.text(this));
        programmeTitle.setTextSize(23);
        programmeTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams programmeTitleParams = new LinearLayout.LayoutParams(-1, -2);
        programmeTitleParams.topMargin = dp(7);
        infoPanel.addView(programmeTitle, programmeTitleParams);
        programmeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        programmeProgress.setMax(1000);
        programmeProgress.setProgressTintList(ColorStateList.valueOf(LiquidTheme.accent(this)));
        programmeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(LiquidTheme.border(this)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(470), dp(7));
        progressParams.topMargin = dp(8);
        infoPanel.addView(programmeProgress, progressParams);
        programmeTimes = new TextView(this);
        programmeTimes.setTextColor(LiquidTheme.secondaryText(this));
        programmeTimes.setTextSize(12);
        LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(-1, -2);
        timesParams.topMargin = dp(5);
        infoPanel.addView(programmeTimes, timesParams);
        nextProgramme = new TextView(this);
        nextProgramme.setTextColor(LiquidTheme.lavender(this));
        nextProgramme.setTextSize(13);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = dp(5);
        infoPanel.addView(nextProgramme, nextParams);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        messageParams.setMargins(dp(28), 0, dp(28), dp(26));
        frame.addView(infoPanel, messageParams);
        setContentView(frame);
        if (!updateProgrammeFromCache()) {
            updateProgramInfo(getIntent().getStringExtra("now"), getIntent().getLongExtra("nowStartMs", 0),
                    getIntent().getLongExtra("nowEndMs", 0), getIntent().getStringExtra("next"));
        }

        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            showError("Missing channel stream URL.");
            return;
        }
        startPlayback(streamUrl, licenseUrl, getIntent().getStringArrayListExtra("licenseHeaders"));
    }

    private void startPlayback(String streamUrl, String licenseUrl, ArrayList<String> headerPairs) {
        try {
            infoPanel.setVisibility(View.VISIBLE);
            message.setVisibility(View.VISIBLE);
            releasePlayer();
            MediaItem.Builder mediaItem = new MediaItem.Builder().setUri(Uri.parse(streamUrl));
            if (widevine) {
                MediaItem.DrmConfiguration.Builder drm = new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID);
                if (licenseUrl != null && !licenseUrl.trim().isEmpty()) drm.setLicenseUri(Uri.parse(licenseUrl));
                Map<String, String> headers = new LinkedHashMap<>();
                if (headerPairs != null) {
                    for (int i = 0; i + 1 < headerPairs.size(); i += 2) headers.put(headerPairs.get(i), headerPairs.get(i + 1));
                }
                if (!headers.isEmpty()) drm.setLicenseRequestHeaders(headers);
                mediaItem.setDrmConfiguration(drm.build());
            }
            player = new ExoPlayer.Builder(this).build();
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setPreferredAudioLanguages("hi", "hin")
                    .build());
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        message.setText("Playing · Up/Down to change channel");
                        hideMessageAfter(2000);
                    }
                    else if (playbackState == Player.STATE_ENDED) message.setText("Stream ended · Back to return");
                }

                @Override public void onPlayerError(PlaybackException error) {
                    if (widevine || containsDrmFailure(error)) {
                        showError("Widevine playback failed. Check the license URL and headers, account access, and TV support.");
                    } else {
                        showError("Could not play this channel. The stream or codec may not be supported by the TV.");
                    }
                }
            });
            player.setMediaItem(mediaItem.build());
            player.prepare();
            player.play();
        } catch (Exception e) {
            showError(widevine
                    ? "Could not start Widevine playback. Check the channel DRM settings."
                    : "Could not start this stream: " + e.getMessage());
        }
    }

    private boolean containsDrmFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String type = cause.getClass().getSimpleName().toLowerCase(java.util.Locale.US);
            if (type.contains("drm") || type.contains("license")) return true;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && zapChannels != null && zapChannels.length() > 0) { changeChannel(-1); return true; }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && zapChannels != null && zapChannels.length() > 0) { changeChannel(1); return true; }
        }
        return super.dispatchKeyEvent(event);
    }

    private void changeChannel(int direction) {
        zapIndex = (zapIndex + direction + zapChannels.length()) % zapChannels.length();
        JSONObject item = zapChannels.optJSONObject(zapIndex);
        if (item == null) return;
        String name = item.optString("name", "Channel");
        currentChannelName = name;
        message.setText(name);
        message.setVisibility(View.VISIBLE);
        infoPanel.setVisibility(View.VISIBLE);
        programmeSchedule = item.optJSONArray("programmes");
        if (!updateProgrammeFromCache()) {
            updateProgramInfo(item.optString("now", ""), item.optLong("nowStartMs", 0),
                    item.optLong("nowEndMs", 0), item.optString("next", ""));
        }
        hideMessageAfter(2000);
        ArrayList<String> headers = new ArrayList<>();
        JSONObject headerObject = item.optJSONObject("headers");
        if (headerObject != null) {
            java.util.Iterator<String> keys = headerObject.keys();
            while (keys.hasNext()) {
                String key = keys.next(); headers.add(key); headers.add(headerObject.optString(key));
            }
        }
        widevine = item.optBoolean("widevine", false);
        startPlayback(item.optString("url", ""), item.optString("licenseUrl", ""), headers);
    }

    private void showError(String text) {
        if (hideMessage != null) handler.removeCallbacks(hideMessage);
        handler.removeCallbacks(programmeTick);
        if (programmeTitle != null) programmeTitle.setVisibility(View.GONE);
        if (programmeProgress != null) programmeProgress.setVisibility(View.GONE);
        if (programmeTimes != null) programmeTimes.setVisibility(View.GONE);
        if (nextProgramme != null) nextProgramme.setVisibility(View.GONE);
        if (infoPanel != null) infoPanel.setVisibility(View.VISIBLE);
        if (message != null) message.setVisibility(View.VISIBLE);
        if (message != null) message.setText(text);
    }

    private void hideMessageAfter(long delayMs) {
        if (hideMessage != null) handler.removeCallbacks(hideMessage);
        hideMessage = () -> {
            if (infoPanel != null) infoPanel.setVisibility(View.GONE);
        };
        handler.postDelayed(hideMessage, delayMs);
    }

    private void updateProgramInfo(String title, long startMs, long endMs, String next) {
        currentProgramme = title == null ? "" : title.trim();
        followingProgramme = next == null ? "" : next.trim();
        programmeStartMs = startMs;
        programmeEndMs = endMs;
        handler.removeCallbacks(programmeTick);
        boolean hasTitle = !currentProgramme.isEmpty();
        boolean hasTiming = hasTitle && programmeEndMs > programmeStartMs;
        programmeTitle.setText(currentProgramme);
        programmeTitle.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        programmeProgress.setVisibility(hasTiming ? View.VISIBLE : View.GONE);
        programmeTimes.setVisibility(hasTiming ? View.VISIBLE : View.GONE);
        nextProgramme.setText(followingProgramme.isEmpty() ? "" : "Next · " + followingProgramme);
        nextProgramme.setVisibility(followingProgramme.isEmpty() ? View.GONE : View.VISIBLE);
        infoPanel.setVisibility(View.VISIBLE);
        updateProgrammeProgress();
        if (hasTiming || hasUpcomingCachedProgramme()) handler.postDelayed(programmeTick, 30000);
    }

    private boolean updateProgrammeFromCache() {
        if (programmeSchedule == null || programmeSchedule.length() == 0) return false;
        long now = System.currentTimeMillis();
        JSONObject current = null;
        JSONObject next = null;
        for (int i = 0; i < programmeSchedule.length(); i++) {
            JSONObject programme = programmeSchedule.optJSONObject(i);
            if (programme == null) continue;
            long start = programme.optLong("startMs", 0);
            long end = programme.optLong("endMs", 0);
            if (start <= now && end > now
                    && (current == null || start > current.optLong("startMs", 0))) {
                current = programme;
            } else if (start > now
                    && (next == null || start < next.optLong("startMs", Long.MAX_VALUE))) {
                next = programme;
            }
        }
        updateProgramInfo(current == null ? "" : current.optString("title", ""),
                current == null ? 0 : current.optLong("startMs", 0),
                current == null ? 0 : current.optLong("endMs", 0),
                next == null ? "" : next.optString("title", ""));
        return true;
    }

    private boolean hasUpcomingCachedProgramme() {
        if (programmeSchedule == null) return false;
        long now = System.currentTimeMillis();
        for (int i = 0; i < programmeSchedule.length(); i++) {
            JSONObject programme = programmeSchedule.optJSONObject(i);
            if (programme != null && programme.optLong("endMs", 0) > now) return true;
        }
        return false;
    }

    private void updateProgrammeProgress() {
        if (programmeProgress == null || programmeEndMs <= programmeStartMs) return;
        long duration = programmeEndMs - programmeStartMs;
        long elapsed = Math.max(0L, Math.min(duration, System.currentTimeMillis() - programmeStartMs));
        programmeProgress.setProgress((int) Math.min(1000L, elapsed * 1000L / duration));
        long minutesLeft = Math.max(0L, (programmeEndMs - System.currentTimeMillis() + 59999L) / 60000L);
        java.text.DateFormat time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault());
        programmeTimes.setText(time.format(new Date(programmeStartMs)) + " – " + time.format(new Date(programmeEndMs))
                + " · " + minutesLeft + " min left");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onStop() {
        releasePlayer();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (hideMessage != null) handler.removeCallbacks(hideMessage);
        handler.removeCallbacks(programmeTick);
        releasePlayer();
        super.onDestroy();
    }

    private void releasePlayer() {
        if (player == null) return;
        if (playerView != null) playerView.setPlayer(null);
        player.release();
        player = null;
    }
}
