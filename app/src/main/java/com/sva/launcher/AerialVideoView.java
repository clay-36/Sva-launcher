package com.sva.launcher;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Muted, lifecycle-owned video surface shared by Home and the Android dream. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class AerialVideoView extends FrameLayout {
    private final PlayerView surface;
    private final TextView status, location;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private ExecutorService worker;
    private int generation, failures;
    private List<AerialCatalog.Clip> clips = Collections.emptyList();
    private final boolean screensaver;
    private String activeProvider;
    private Runnable retry;
    private final Runnable stalled = () -> skipUnavailable();

    AerialVideoView(Context context, boolean screensaver) {
        super(context);
        this.screensaver = screensaver;
        setBackgroundResource(R.drawable.mountain_wallpaper);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        surface = (PlayerView) LayoutInflater.from(context).inflate(R.layout.aerial_player, this, false);
        surface.setKeepContentOnPlayerReset(false);
        addView(surface, new LayoutParams(-1, -1));
        status = new TextView(context);
        status.setTextColor(Color.WHITE); status.setTextSize(16); status.setGravity(Gravity.CENTER);
        status.setShadowLayer(4, 0, 2, Color.BLACK);
        status.setPadding(32, 16, 32, 16);
        addView(status, new LayoutParams(-1, -2, Gravity.CENTER));
        location = new TextView(context);
        location.setTextColor(Color.WHITE); location.setTextSize(15);
        location.setMaxLines(2); location.setEllipsize(android.text.TextUtils.TruncateAt.END);
        location.setTypeface(LiquidTheme.font(context));
        location.setShadowLayer(4, 0, 2, Color.BLACK);
        int padding = LiquidTheme.dp(context, 32);
        location.setPadding(padding, padding, padding, 0);
        addView(location, new LayoutParams(-1, -2, Gravity.TOP));
        location.setVisibility(screensaver ? VISIBLE : GONE);
    }

    void start() {
        String provider = getContext().getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString(AerialCatalog.PROVIDER, "all");
        if (provider.equals(activeProvider) && (player != null || worker != null)) return;
        stop(); activeProvider = provider;
        int ticket = ++generation;
        status.setText(screensaver ? getContext().getString(R.string.aerial_loading) : "");
        status.setVisibility(screensaver ? VISIBLE : GONE);
        surface.setAlpha(0f);
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                List<AerialCatalog.Clip> loaded = AerialCatalog.load(getContext().getApplicationContext(), provider);
                Collections.shuffle(loaded);
                main.post(() -> { if (ticket == generation) play(loaded); });
            } catch (Exception error) { main.post(() -> { if (ticket == generation) unavailable(); }); }
        });
    }

    private void play(List<AerialCatalog.Clip> loaded) {
        if (loaded.isEmpty()) { unavailable(); return; }
        clips = loaded;
        List<MediaItem> playlist = new ArrayList<>();
        for (AerialCatalog.Clip clip : clips) playlist.add(new MediaItem.Builder()
                .setUri(clip.url).setMediaId(clip.id).build());
        androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("SvaLauncher/AerialVideos").setConnectTimeoutMs(7000).setReadTimeoutMs(7000);
        player = new ExoPlayer.Builder(getContext())
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        new androidx.media3.datasource.DefaultDataSource.Factory(getContext(), http)))
                .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 1000, 2000).build()).build();
        player.setVolume(0f);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true).build());
        player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        surface.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                failures = 0;
                if (retry != null) main.removeCallbacks(retry);
                surface.setAlpha(1f); status.setVisibility(GONE);
            }
            @Override public void onPlaybackStateChanged(int state) {
                main.removeCallbacks(stalled);
                if (state == Player.STATE_BUFFERING) main.postDelayed(stalled, 15000);
            }
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                int index = player == null ? -1 : player.getCurrentMediaItemIndex();
                if (index >= 0 && index < clips.size()) location.setText(clips.get(index).title);
            }
            @Override public void onPlayerError(PlaybackException error) { skipUnavailable(); }
        });
        player.setMediaItems(playlist);
        player.prepare(); player.play();
    }

    private void skipUnavailable() {
        main.removeCallbacks(stalled);
        if (retry != null) main.removeCallbacks(retry);
        surface.setAlpha(0f);
        if (player == null) return;
        if (++failures >= Math.min(5, clips.size())) { unavailable(); return; }
        int ticket = generation;
        retry = () -> {
            if (ticket != generation || player == null) return;
            player.seekTo((player.getCurrentMediaItemIndex() + 1) % clips.size(), 0);
            player.prepare(); player.play();
        };
        main.postDelayed(retry, 500);
    }

    private void unavailable() {
        main.removeCallbacks(stalled);
        surface.setAlpha(0f); location.setText("");
        status.setText(R.string.aerial_unavailable);
        status.setVisibility(screensaver ? VISIBLE : GONE);
        if (player != null) player.stop();
    }
    void stop() {
        ++generation; failures = 0; activeProvider = null;
        main.removeCallbacksAndMessages(null);
        retry = null;
        if (worker != null) { worker.shutdownNow(); worker = null; }
        surface.setPlayer(null);
        if (player != null) { player.release(); player = null; }
        clips = Collections.emptyList();
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
