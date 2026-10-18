package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Toast;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private String activeDrmType = "";
    private JSONArray zapChannels;
    private JSONArray programmeSchedule;
    private int zapIndex;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideMessage;
    private final Runnable programmeTick = new Runnable() {
        @Override public void run() {
            boolean panelWasVisible = infoPanel != null && infoPanel.getVisibility() == View.VISIBLE;
            if (!updateProgrammeFromCache()) updateProgrammeProgress();
            if (!panelWasVisible && infoPanel != null) { Motion.reset(infoPanel); infoPanel.setVisibility(View.GONE); }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Intent playback;
        boolean missingSession = false;
        try { playback = PlaybackSession.restore(this, getIntent()); }
        catch (Exception ignored) { playback = new Intent(); missingSession = true; }
        String name = playback.getStringExtra("name");
        currentChannelName = name == null ? "Channel" : name;
        String streamUrl = playback.getStringExtra("url");
        String licenseUrl = playback.getStringExtra("licenseUrl");

        try { zapChannels = new JSONArray(playback.getStringExtra("zapChannels")); } catch (Exception ignored) { zapChannels = new JSONArray(); }
        zapIndex = playback.getIntExtra("zapIndex", 0);
        JSONObject activeChannel = zapChannels.optJSONObject(zapIndex);
        programmeSchedule = activeChannel == null ? null : activeChannel.optJSONArray("programmes");
        if (programmeSchedule == null) {
            try { programmeSchedule = new JSONArray(playback.getStringExtra("programmes")); }
            catch (Exception ignored) { programmeSchedule = null; }
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        frame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(26), dp(19), dp(26), dp(18));
        infoPanel.setBackground(new GlassDrawable(this, null, LiquidTheme.surface(this),
                LiquidTheme.border(this), 1, 24));
        infoPanel.setElevation(dp(8));
        boolean initialFavorite = activeChannel != null ? activeChannel.optBoolean("favorite", false)
                : playback.getBooleanExtra("favorite", false);
        message = new TextView(this);
        message.setText("Loading " + (initialFavorite ? "★ " : "") + currentChannelName + "…");
        message.setTextColor(LiquidTheme.text(this));
        message.setTextSize(12);
        message.setLetterSpacing(.08f);
        message.setTypeface(LiquidTheme.font(this));
        message.setShadowLayer(dp(8), 0, 0, 0x44000000);
        infoPanel.addView(message);
        programmeTitle = new TextView(this);
        programmeTitle.setTextColor(LiquidTheme.text(this));
        programmeTitle.setTextSize(23);
        programmeTitle.setTypeface(LiquidTheme.font(this));
        LinearLayout.LayoutParams programmeTitleParams = new LinearLayout.LayoutParams(-1, -2);
        programmeTitleParams.topMargin = dp(7);
        infoPanel.addView(programmeTitle, programmeTitleParams);
        programmeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        programmeProgress.setMax(1000);
        programmeProgress.setProgressTintList(ColorStateList.valueOf(LiquidTheme.accent(this)));
        programmeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(LiquidTheme.border(this)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.topMargin = dp(8);
        infoPanel.addView(programmeProgress, progressParams);
        programmeTimes = new TextView(this);
        programmeTimes.setTextColor(LiquidTheme.secondaryText(this));
        programmeTimes.setTextSize(12);
        LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(-1, -2);
        timesParams.topMargin = dp(5);
        infoPanel.addView(programmeTimes, timesParams);
        nextProgramme = new TextView(this);
        nextProgramme.setTextColor(LiquidTheme.secondaryText(this));
        nextProgramme.setTextSize(13);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = dp(5);
        infoPanel.addView(nextProgramme, nextParams);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(64)), -2, Gravity.BOTTOM | Gravity.START);
        messageParams.setMargins(dp(32), 0, dp(32), dp(28));
        frame.addView(infoPanel, messageParams);
        setContentView(frame);
        if (!updateProgrammeFromCache()) {
            updateProgramInfo(playback.getStringExtra("now"), playback.getLongExtra("nowStartMs", 0),
                    playback.getLongExtra("nowEndMs", 0), playback.getStringExtra("next"));
        }

        if (missingSession) {
            showError("Playback session is unavailable. Go back and select the channel again.");
            return;
        }
        String encodedRequest = playback.getStringExtra("playbackRequest");
        if (encodedRequest != null) {
            try { startPlayback(PlaybackRequest.fromJson(new JSONObject(encodedRequest))); }
            catch (Exception ignored) { showError("Invalid channel playback configuration."); }
            return;
        }
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            showError("Missing channel stream URL.");
            return;
        }
        Map<String, String> oldHeaders = new LinkedHashMap<>();
        ArrayList<String> pairs = playback.getStringArrayListExtra("licenseHeaders");
        if (pairs != null) for (int i = 0; i + 1 < pairs.size(); i += 2) oldHeaders.put(pairs.get(i), pairs.get(i + 1));
        startPlayback(new PlaybackRequest(streamUrl, "", playback.getBooleanExtra("widevine", false) ? "widevine" : "",
                licenseUrl == null ? "" : licenseUrl, "", new LinkedHashMap<>(), oldHeaders));
    }

    private void startPlayback(PlaybackRequest request) {
        activeDrmType = request.drmType;
        try {
            Motion.show(infoPanel, false);
            message.setVisibility(View.VISIBLE);
            releasePlayer();
            player = IptvPlayback.create(this, request);
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
                    if (!activeDrmType.isEmpty() || containsDrmFailure(error)) {
                        showError(("clearkey".equals(activeDrmType) ? "ClearKey" : "Widevine")
                                + " playback failed. Check the channel license settings and TV support.");
                    } else {
                        showError("Could not play this channel. The stream or codec may not be supported by the TV.");
                    }
                }
            });
            player.prepare();
            player.play();
        } catch (Exception e) {
            showError("clearkey".equals(activeDrmType)
                    ? "Could not start ClearKey playback. Check the DASH format, keys or license URL."
                    : "widevine".equals(activeDrmType)
                    ? "Could not start Widevine playback. Check the channel DRM settings."
                    : "Could not start this stream. Check the stream URL and playlist options.");
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
            if (isFavoriteKey(event.getKeyCode())) { toggleActiveFavorite(); return true; }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isFavoriteKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_BOOKMARK
                || keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BUTTON_Y;
    }

    private void toggleActiveFavorite() {
        if (zapChannels == null || zapIndex < 0 || zapIndex >= zapChannels.length()) return;
        JSONObject item = zapChannels.optJSONObject(zapIndex);
        if (item == null) return;
        String stableKey = item.optString("stableKey", "");
        if (stableKey.isEmpty()) return;
        Set<String> favorites = new HashSet<>(getSharedPreferences("launcher", 0)
                .getStringSet(FavoriteChannels.PREF_FAVORITES, Collections.emptySet()));
        boolean wasFav = favorites.contains(stableKey);
        if (wasFav) favorites.remove(stableKey);
        else favorites.add(stableKey);
        getSharedPreferences("launcher", 0).edit()
                .putStringSet(FavoriteChannels.PREF_FAVORITES, favorites).apply();
        boolean nowFav = !wasFav;
        try { item.put("favorite", nowFav); } catch (Exception ignored) { }
        message.setText((nowFav ? "★ " : "") + currentChannelName);
        Motion.show(infoPanel, false);
        hideMessageAfter(2500);
        Toast.makeText(this, (nowFav ? "Added to" : "Removed from") + " favourites: " + currentChannelName, Toast.LENGTH_SHORT).show();
    }

    private void changeChannel(int direction) {
        zapIndex = (zapIndex + direction + zapChannels.length()) % zapChannels.length();
        JSONObject item = zapChannels.optJSONObject(zapIndex);
        if (item == null) return;
        String name = item.optString("name", "Channel");
        currentChannelName = name;
        boolean isFav = item.optBoolean("favorite", false);
        message.setText((isFav ? "★ " : "") + name);
        message.setVisibility(View.VISIBLE);
        Motion.show(infoPanel, false);
        programmeSchedule = item.optJSONArray("programmes");
        if (!updateProgrammeFromCache()) {
            updateProgramInfo(item.optString("now", ""), item.optLong("nowStartMs", 0),
                    item.optLong("nowEndMs", 0), item.optString("next", ""));
        }
        hideMessageAfter(2000);
        JSONObject request = item.optJSONObject("playbackRequest");
        if (request != null) startPlayback(PlaybackRequest.fromJson(request));
        else {
            startPlayback(new PlaybackRequest(item.optString("url"), "", item.optBoolean("widevine") ? "widevine" : "",
                    item.optString("licenseUrl"), "", new LinkedHashMap<>(),
                    StreamOptions.jsonHeaders(item.optJSONObject("headers"))));
        }
        if (player != null && player.getPlayerError() == null) Motion.reveal(infoPanel);
    }

    private void showError(String text) {
        if (hideMessage != null) handler.removeCallbacks(hideMessage);
        handler.removeCallbacks(programmeTick);
        if (programmeTitle != null) programmeTitle.setVisibility(View.GONE);
        if (programmeProgress != null) programmeProgress.setVisibility(View.GONE);
        if (programmeTimes != null) programmeTimes.setVisibility(View.GONE);
        if (nextProgramme != null) nextProgramme.setVisibility(View.GONE);
        if (infoPanel != null) Motion.show(infoPanel, false);
        if (message != null) message.setVisibility(View.VISIBLE);
        if (message != null) message.setText(text);
    }

    private void hideMessageAfter(long delayMs) {
        if (hideMessage != null) handler.removeCallbacks(hideMessage);
        hideMessage = () -> {
            if (infoPanel != null) Motion.hide(infoPanel);
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
        Motion.cancelTree(infoPanel);
        super.onDestroy();
    }

    private void releasePlayer() {
        if (player == null) return;
        if (playerView != null) playerView.setPlayer(null);
        player.release();
        player = null;
    }
}
