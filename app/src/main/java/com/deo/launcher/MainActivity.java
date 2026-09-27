package com.deo.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.util.LruCache;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int PICK_WALLPAPER = 4107;
    private static final int PICK_WALLPAPER_FOLDER = 4108;
    private static final int PICK_BACKUP_EXPORT = 4109;
    private static final int PICK_BACKUP_IMPORT = 4110;
    private static final String GOOGLE_PHOTOS_CACHE_URL = "googlePhotosCacheUrl";
    private static final String GOOGLE_PHOTOS_CACHE_IMAGES = "googlePhotosCacheImages";
    private static final String PHOTO_FOLDER_CACHE_URI = "photoFolderCacheUri";
    private static final String PHOTO_FOLDER_CACHE_IMAGES = "photoFolderCacheImages";
    private static final int CALENDAR_CACHE_VERSION = 2;
    private static final int PANEL = 0xF21B1B1B;
    private static final int PANEL_FOCUSED = 0xFF2A2A2A;
    private static final int MUTED = 0xFFB7B7B7;
    private static final int ACCENT = 0xFFBCE7FF;
    private static final int LAVENDER = 0xFFB7B7B7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<IptvRepository.Channel> channels = new ArrayList<>();
    private final Set<String> hiddenChannelKeys = new HashSet<>();
    private final Set<String> youtubeMetadataInFlight = new HashSet<>();
    private final ExecutorService youtubeWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "youtube-card-metadata");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "public-calendar-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final LruCache<String, android.graphics.Bitmap> youtubeThumbnailCache = new LruCache<String, android.graphics.Bitmap>(12 * 1024) {
        @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private IptvRepository iptv;
    private ChannelLogoLoader logoLoader;
    private SharedPreferences preferences;
    private LinearLayout root, homeContent, appRow, youtubeRow, youtubeSection;
    private FrameLayout heroFrame;
    private final GlassBackdrop glassBackdrop = new GlassBackdrop();
    private ScrollView homeScroll;
    private boolean initialAppFocus;
    private String focusedPackage;
    private int restoredScroll;
    private ImageView wallpaperImage, slideA, slideB;
    private TextView clock, date, status, channelHeader, greeting, greetingSubtitle, youtubeHeading;
    private LinearLayout calendarEventBar, calendarEventItems;
    private TextView calendarEventTitle;
    private LinearLayout channelRows, channelCategoryTabs;
    private HorizontalScrollView channelCategoryScroll;
    private TextView channelsEmpty;
    private final LinkedHashMap<String, ChannelRow> channelRowMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, Button> channelTabMap = new LinkedHashMap<>();
    private List<String> renderedCategories = new ArrayList<>();
    private String selectedChannelCategory;
    private Runnable clockTick;
    private Runnable guideRefreshTick;
    private Runnable calendarRefreshTick;
    private Runnable googlePhotosRefreshTick;
    private Runnable slideshowTick;
    private final List<BackgroundPhoto> backgroundPhotos = new ArrayList<>();
    private final LruCache<String, android.graphics.Bitmap> backgroundBitmapCache = new LruCache<String, android.graphics.Bitmap>(20 * 1024) {
        @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private int backgroundIndex, backgroundFolderGeneration, backgroundImageGeneration;
    private boolean slideAActive = true;
    private boolean screenActive;
    private boolean appsLoaded;
    private boolean appsLoading;
    private boolean channelsRefreshing;
    private boolean guideRefreshing;
    private boolean calendarRefreshing;
    private String calendarRefreshingUrl = "";
    private int calendarRequestGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        if (state != null) {
            focusedPackage = state.getString("focusedPackage");
            restoredScroll = state.getInt("homeScroll", 0);
        }
        iptv = new IptvRepository(this);
        logoLoader = new ChannelLogoLoader(this);
        hiddenChannelKeys.addAll(preferences.getStringSet("hiddenChannels", Collections.emptySet()));
        // Hydrate the in-memory list before constructing Home so cached channels
        // are present in the first rendered frame after a process restart.
        channels.addAll(iptv.cachedChannels());
        String folder = preferences.getString("wallpaperFolder", "");
        String album = preferences.getString("wallpaperAlbum", "");
        if (!folder.isEmpty()) backgroundPhotos.addAll(cachedBackgroundFolder(folder));
        else if (!album.isEmpty()) backgroundPhotos.addAll(cachedBackgroundAlbum(album));
        buildHome();
        renderChannels();
        if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
        else if (!album.isEmpty()) maybeRefreshGooglePhotos(true);
        updateClock();
        clockTick = new Runnable() {
            @Override public void run() {
                updateClock();
                refreshCachedProgrammeDisplay();
                updateCalendarEventDisplay();
                handler.postDelayed(this, 30000);
            }
        };
        handler.postDelayed(clockTick, 30000);
        guideRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshLiveData();
                scheduleLiveRefreshCheck();
            }
        };
        updateCalendarEventDisplay();
        maybeRefreshPublicCalendar(false);
        calendarRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
            }
        };
        googlePhotosRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshGooglePhotos(false);
                scheduleGooglePhotosRefresh();
            }
        };
        if (state == null && folder.isEmpty() && album.isEmpty()) {
            handler.post(() -> openWallpaperFolderPicker());
        }
        if (state != null && state.getBoolean("restoreSettings", false)) {
            settingsScreen = new SettingsScreen();
            settingsScreen.section = state.getInt("restoreSettingsSection", SettingsScreen.APPEARANCE);
            settingsScreen.show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (settingsScreen != null) {
            outState.putBoolean("restoreSettings", true);
            outState.putInt("restoreSettingsSection", settingsScreen.section);
        }
        View selectedApp = appRow == null ? null : appRow.findFocus();
        if (selectedApp != null && selectedApp.getTag() instanceof String) focusedPackage = (String) selectedApp.getTag();
        outState.putString("focusedPackage", focusedPackage);
        outState.putInt("homeScroll", homeScroll == null ? 0 : homeScroll.getScrollY());
        super.onSaveInstanceState(outState);
    }

    private void openWallpaperFolderPicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, PICK_WALLPAPER_FOLDER);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No folder picker is available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private void openBackupExportPicker() {
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json");
        picker.putExtra(Intent.EXTRA_TITLE, "duo-launcher-backup.json");
        try { startActivityForResult(picker, PICK_BACKUP_EXPORT); }
        catch (Exception e) { Toast.makeText(this, "Could not open the file picker.", Toast.LENGTH_LONG).show(); }
    }

    private void openBackupImportPicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json");
        try { startActivityForResult(picker, PICK_BACKUP_IMPORT); }
        catch (Exception e) { Toast.makeText(this, "Could not open the file picker.", Toast.LENGTH_LONG).show(); }
    }

    private void exportBackup(Uri destination) {
        Toast.makeText(this, "Creating launcher backup…", Toast.LENGTH_SHORT).show();
        Thread worker = new Thread(() -> {
            String error = null;
            try {
                JSONObject backup = LauncherBackup.create(preferences,
                        getSharedPreferences("iptv", MODE_PRIVATE), iptv);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IllegalStateException("Could not write to that file.");
                    output.write(backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not create the backup." : e.getMessage();
            }
            String result = error;
            handler.post(() -> Toast.makeText(this,
                    result == null ? "Backup saved." : "Backup failed: " + result,
                    result == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
        }, "launcher-backup-export");
        worker.setDaemon(true);
        worker.start();
    }

    private void importBackup(Uri source) {
        Toast.makeText(this, "Restoring launcher backup…", Toast.LENGTH_SHORT).show();
        Thread worker = new Thread(() -> {
            String error = null;
            try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalStateException("Could not read that file.");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                final int maxBytes = 64 * 1024 * 1024;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalArgumentException("Backup file is larger than 64 MB.");
                    bytes.write(buffer, 0, count);
                }
                JSONObject backup = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                LauncherBackup.restore(backup, preferences, getSharedPreferences("iptv", MODE_PRIVATE), iptv);
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not restore that backup." : e.getMessage();
            }
            String result = error;
            handler.post(() -> {
                if (result != null) {
                    Toast.makeText(this, "Restore failed: " + result, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Backup restored.", Toast.LENGTH_SHORT).show();
                recreate();
            });
        }, "launcher-backup-import");
        worker.setDaemon(true);
        worker.start();
    }

    @Override protected void onResume() {
        super.onResume();
        screenActive = true;
        maybeRefreshLiveData();
        scheduleLiveRefreshCheck();
        maybeRefreshPublicCalendar(false);
        scheduleCalendarRefresh();
        scheduleGooglePhotosRefresh();
        if (settingsScreen != null) settingsScreen.refreshLauncherOverride();
        if (root != null && !appsLoaded) renderApps();
        if (settingsScreen == null && !backgroundPhotos.isEmpty()) {
            if (slideA.getVisibility() != View.VISIBLE && slideB.getVisibility() != View.VISIBLE) {
                showNextBackgroundPhoto();
            } else {
                scheduleNextBackgroundPhoto();
            }
        }
    }

    @Override protected void onPause() {
        screenActive = false;
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        backgroundImageGeneration++;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (clockTick != null) handler.removeCallbacks(clockTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        glassBackdrop.close();
        super.onDestroy();
    }

    private void buildHome() {
        boolean light = LiquidTheme.isLight(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        homeScroll = page;
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout scene = new FrameLayout(this);
        setContentView(scene);

        heroFrame = new FrameLayout(this);
        heroFrame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                light
                        ? new int[]{0xFF757575, 0xFFB8B8B8, 0xFFF5F5F3}
                        : new int[]{0xFF323232, 0xFF191919, 0xFF090909}));
        wallpaperImage = backgroundImage();
        slideA = backgroundImage();
        slideB = backgroundImage();
        heroFrame.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideA, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideB, new FrameLayout.LayoutParams(-1, -1));
        showCachedBackgroundImmediately();
        GradientDrawable shadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x28000000, 0x16000000, 0x16000000});
        View shade = new View(this);
        shade.setBackground(shadow);
        heroFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View sideShade = new View(this);
        sideShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x99000000, 0x22000000, 0x00000000}));
        heroFrame.addView(sideShade, new FrameLayout.LayoutParams(-1, -1));
        View bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000A1420, 0x180A1420, 0xD90A1420}));
        heroFrame.addView(bottomFade, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(heroFrame, new FrameLayout.LayoutParams(-1, -1));
        View browsingShade = new View(this);
        browsingShade.setBackgroundColor(0xFF091421);
        browsingShade.setAlpha(0f);
        scene.addView(browsingShade, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            browsingShade.setAlpha(Math.min(.72f, (float) y / dp(320) * .72f));
            glassBackdrop.invalidate();
        });
        FrameLayout heroContent = new FrameLayout(this);
        root.addView(heroContent, new LinearLayout.LayoutParams(-1, dp(Math.max(210, heroHeightDp() - 190))));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(42), dp(28), dp(42), dp(12));
        heroContent.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout timeStack = new LinearLayout(this);
        timeStack.setOrientation(LinearLayout.VERTICAL);
        timeStack.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        timeStack.setPadding(dp(16), dp(9), dp(18), dp(9));
        timeStack.setBackground(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 22));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
        top.addView(timeStack, timeParams);
        clock = new TextView(this);
        clock.setTextColor(LiquidTheme.text(this));
        clock.setTextSize(25);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setGravity(Gravity.LEFT);
        timeStack.addView(clock);
        date = new TextView(this);
        date.setTextColor(LiquidTheme.secondaryText(this));
        date.setTextSize(11);
        date.setLetterSpacing(.05f);
        date.setGravity(Gravity.LEFT);
        timeStack.addView(date);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout systemShortcuts = new LinearLayout(this);
        systemShortcuts.setGravity(Gravity.CENTER_VERTICAL);
        systemShortcuts.setPadding(dp(5), dp(4), dp(5), dp(4));
        systemShortcuts.setBackground(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 28));
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(-2, dp(52));
        shortcutParams.setMargins(dp(8), 0, 0, 0);
        top.addView(systemShortcuts, shortcutParams);
        addTopIcon(systemShortcuts, "Network", R.drawable.ic_network, this::openNetworkSettings);
        addTopIcon(systemShortcuts, "Android Settings", android.R.drawable.ic_menu_manage, this::openAndroidSettings);
        addTopIcon(systemShortcuts, "Launcher Settings", R.drawable.ic_launcher_settings, this::showSettings);

        LinearLayout heroBottom = new LinearLayout(this);
        heroBottom.setOrientation(LinearLayout.VERTICAL);
        heroBottom.setPadding(dp(48), 0, dp(48), dp(22));
        greeting = label("", 32, Color.WHITE, true);
        greeting.setTextColor(Color.WHITE);
        greeting.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        greeting.setShadowLayer(dp(12), 0, dp(2), light ? 0x22000000 : 0x77000000);
        heroBottom.addView(greeting);
        greetingSubtitle = label("", 14, Color.WHITE, false);
        greetingSubtitle.setTextColor(0xFFD4DFE8);
        heroBottom.addView(greetingSubtitle);

        heroContent.addView(heroBottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(48), dp(12), dp(48), dp(24));
        homeContent.setClipChildren(false);
        homeContent.setClipToPadding(false);
        homeContent.setBackgroundColor(Color.TRANSPARENT);
        root.addView(homeContent, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout appsHeading = new LinearLayout(this);
        appsHeading.setGravity(Gravity.CENTER_VERTICAL);
        homeContent.addView(appsHeading, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView appsTitle = label("Your apps", 14, Color.WHITE, true);
        appsTitle.setTextColor(0xFFE3EDF5);
        appsTitle.setLetterSpacing(.02f);
        appsHeading.addView(appsTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView appsHint = label("SELECT TO OPEN", 10, LiquidTheme.lavender(this), true);
        appsHint.setLetterSpacing(.12f);

        HorizontalScrollView appScroll = new HorizontalScrollView(this);
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setPadding(dp(8), dp(10), dp(8), dp(10));
        appScroll.setClipChildren(false);
        appScroll.setClipToPadding(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        appScroll.addView(appRow);
        homeContent.addView(appScroll, new LinearLayout.LayoutParams(-1, dp(132)));

        applyWallpaper();

        LinearLayout liveHeading = new LinearLayout(this);
        liveHeading.setGravity(Gravity.CENTER_VERTICAL);
        homeContent.addView(liveHeading, new LinearLayout.LayoutParams(-1, dp(56)));
        channelHeader = label("Live, right now", 22, Color.WHITE, true);
        channelHeader.setTextColor(Color.WHITE);
        channelHeader.setLetterSpacing(.01f);
        liveHeading.addView(channelHeader, new LinearLayout.LayoutParams(0, -2, 1));
        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        channelRows.setClipChildren(false);
        channelRows.setClipToPadding(false);
        homeContent.addView(channelRows, new LinearLayout.LayoutParams(-1, -2));
        channelCategoryScroll = new HorizontalScrollView(this);
        channelCategoryScroll.setHorizontalScrollBarEnabled(false);
        channelCategoryScroll.setClipChildren(false);
        channelCategoryScroll.setClipToPadding(false);
        channelCategoryTabs = new LinearLayout(this);
        channelCategoryTabs.setOrientation(LinearLayout.HORIZONTAL);
        channelCategoryTabs.setGravity(Gravity.CENTER_VERTICAL);
        channelCategoryTabs.setClipChildren(false);
        channelCategoryTabs.setClipToPadding(false);
        channelCategoryScroll.addView(channelCategoryTabs, new HorizontalScrollView.LayoutParams(-2, -1));
        channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(58)));
        channelsEmpty = label("Add a playlist in Settings to browse live TV.", 15, MUTED, false);
        channelsEmpty.setPadding(0, dp(16), 0, dp(20));
        status = label(channels.isEmpty()
                ? "Add an M3U and optional XMLTV URL in Settings to get started."
                : "Channels loaded from device cache.", 15, MUTED, false);
        status.setPadding(0, dp(12), 0, 0);
        homeContent.addView(status);

        youtubeSection = new LinearLayout(this);
        youtubeSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout youtubeTitleRow = new LinearLayout(this);
        youtubeTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        youtubeHeading = label("Made for your downtime", 22, Color.WHITE, true);
        youtubeHeading.setTextColor(Color.WHITE);
        youtubeHeading.setLetterSpacing(.01f);
        youtubeTitleRow.addView(youtubeHeading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView youtubeHint = label("VIDEOS & PLAYLISTS", 10, LiquidTheme.secondaryText(this), true);
        youtubeHint.setLetterSpacing(.12f);

        youtubeTitleRow.setPadding(0, dp(8), 0, 0);
        youtubeSection.addView(youtubeTitleRow, new LinearLayout.LayoutParams(-1, dp(56)));
        HorizontalScrollView youtubeScroll = new HorizontalScrollView(this);
        youtubeScroll.setHorizontalScrollBarEnabled(false);
        youtubeScroll.setPadding(dp(8), dp(10), dp(8), dp(10));
        youtubeScroll.setClipChildren(false);
        youtubeScroll.setClipToPadding(false);
        youtubeRow = new LinearLayout(this);
        youtubeRow.setOrientation(LinearLayout.HORIZONTAL);
        youtubeRow.setClipChildren(false);
        youtubeRow.setClipToPadding(false);
        youtubeScroll.addView(youtubeRow);
        youtubeSection.addView(youtubeScroll, new LinearLayout.LayoutParams(-1, dp(230)));
        homeContent.addView(youtubeSection, new LinearLayout.LayoutParams(-1, -2));
        renderYouTubeCards();

        calendarEventBar = new LinearLayout(this);
        calendarEventBar.setOrientation(LinearLayout.VERTICAL);
        calendarEventBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        calendarEventBar.setBackground(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 18));
        LinearLayout.LayoutParams calendarBarParams = new LinearLayout.LayoutParams(-1, -2);
        calendarBarParams.setMargins(0, dp(18), 0, dp(12));
        homeContent.addView(calendarEventBar, calendarBarParams);
        calendarEventTitle = label("HAPPENING NOW", 11, LiquidTheme.text(this), true);
        calendarEventTitle.setLetterSpacing(.14f);
        calendarEventBar.addView(calendarEventTitle, new LinearLayout.LayoutParams(-1, dp(22)));
        HorizontalScrollView calendarScroll = new HorizontalScrollView(this);
        calendarScroll.setHorizontalScrollBarEnabled(false);
        calendarScroll.setClipChildren(false);
        calendarScroll.setClipToPadding(false);
        calendarEventItems = new LinearLayout(this);
        calendarEventItems.setOrientation(LinearLayout.HORIZONTAL);
        calendarEventItems.setClipChildren(false);
        calendarEventItems.setClipToPadding(false);
        calendarScroll.addView(calendarEventItems);
        calendarEventBar.addView(calendarScroll, new LinearLayout.LayoutParams(-1, -2));
        calendarEventBar.setVisibility(View.GONE);
    }

    private int heroHeightDp() {
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return screenHeightDp;
    }

    private ImageView backgroundImage() {
        ImageView image = new BackdropImageView(this);
        image.setVisibility(View.GONE);
        return image;
    }

    private static final class BackdropImageView extends ImageView {
        private final Matrix cropMatrix = new Matrix();

        BackdropImageView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateTopCrop();
        }

        @Override public void setImageDrawable(Drawable drawable) {
            super.setImageDrawable(drawable);
            updateTopCrop();
        }

        @Override public void setImageBitmap(android.graphics.Bitmap bitmap) {
            super.setImageBitmap(bitmap);
            updateTopCrop();
        }

        @Override public void setImageResource(int resource) {
            super.setImageResource(resource);
            updateTopCrop();
        }

        private void updateTopCrop() {
            if (cropMatrix == null) return;
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0
                    || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) return;
            float scale = Math.max((float) getWidth() / drawable.getIntrinsicWidth(),
                    (float) getHeight() / drawable.getIntrinsicHeight());
            float horizontalOffset = (getWidth() - drawable.getIntrinsicWidth() * scale) / 2f;
            float verticalOffset = (getHeight() - drawable.getIntrinsicHeight() * scale) * 0.30f;
            cropMatrix.setScale(scale, scale);
            cropMatrix.postTranslate(horizontalOffset, verticalOffset);
            setImageMatrix(cropMatrix);
        }
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        if (LiquidTheme.isLight(this)) {
            if (color == Color.WHITE || color == 0xFFF4F6FA) color = LiquidTheme.text(this);
            else if (color == MUTED) color = LiquidTheme.secondaryText(this);
            else if (color == ACCENT) color = LiquidTheme.accent(this);
            else if (color == LAVENDER) color = LiquidTheme.lavender(this);
        }
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(LiquidTheme.text(this));
        b.setAllCaps(false);
        b.setFocusable(true);
        styleFocusableTile(b);
        return b;
    }

    private void stylePrimaryButton(Button button) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 18));
        button.setBackground(states);
        button.setTextColor(LiquidTheme.text(this));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setLetterSpacing(.035f);
        button.setOnFocusChangeListener(LiquidTheme::animateFocus);
    }

    private void updateClock() {
        if (clock == null) return;
        updateGreeting();
        boolean twentyFour = preferences.getBoolean("24h", false);
        Date now = new Date();
        DateFormat time = android.text.format.DateFormat.getTimeFormat(this);
        if (twentyFour) time = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        else time = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        clock.setText(time.format(now));
        date.setVisibility(preferences.getBoolean("showDate", true) ? View.VISIBLE : View.GONE);
        date.setText(DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(now));
    }

    private List<PublicCalendarRepository.Event> cachedCalendarEvents() {
        ArrayList<PublicCalendarRepository.Event> events = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(preferences.getString("publicCalendarEvents", "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                long start = value.optLong("start", 0L), end = value.optLong("end", 0L);
                if (end > start) events.add(new PublicCalendarRepository.Event(
                        value.optString("title", "Calendar event"), start, end,
                        value.optBoolean("allDay", false)));
            }
        } catch (Exception ignored) { }
        Collections.sort(events, (a, b) -> Long.compare(a.startMs, b.startMs));
        return events;
    }

    private void saveCalendarEvents(List<PublicCalendarRepository.Event> events) {
        JSONArray values = new JSONArray();
        for (PublicCalendarRepository.Event event : events) {
            JSONObject value = new JSONObject();
            try {
                value.put("title", event.title);
                value.put("start", event.startMs);
                value.put("end", event.endMs);
                value.put("allDay", event.allDay);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString("publicCalendarEvents", values.toString())
                .putLong("publicCalendarUpdatedAt", System.currentTimeMillis())
                .putInt("publicCalendarCacheVersion", CALENDAR_CACHE_VERSION).apply();
    }

    private void updateCalendarEventDisplay() {
        if (calendarEventBar == null || calendarEventItems == null) return;
        long now = System.currentTimeMillis();
        List<PublicCalendarRepository.Event> cached = cachedCalendarEvents();
        ArrayList<PublicCalendarRepository.Event> active = new ArrayList<>();
        for (PublicCalendarRepository.Event event : cached) {
            if (event.startMs <= now && now < event.endMs) active.add(event);
        }
        boolean happeningNow = !active.isEmpty();
        ArrayList<PublicCalendarRepository.Event> visible = active;
        if (!happeningNow) {
            visible = new ArrayList<>();
            for (PublicCalendarRepository.Event event : cached) {
                if (event.startMs > now) visible.add(event);
                if (visible.size() == 3) break;
            }
        }
        calendarEventItems.removeAllViews();
        calendarEventBar.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        if (visible.isEmpty()) return;
        if (calendarEventTitle != null) calendarEventTitle.setText(happeningNow ? "HAPPENING NOW" : "UPCOMING");
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        if (preferences.getBoolean("24h", false)) timeFormat = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        for (PublicCalendarRepository.Event event : visible) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(7), dp(12), dp(7));
            item.setBackground(roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.border(this), 1, 13));
            TextView title = label(event.title, 16, LiquidTheme.text(this), true);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(title, new LinearLayout.LayoutParams(-1, dp(26)));
            String timing = event.allDay
                    ? (happeningNow ? "Today · All day" : dateFormat.format(new Date(event.startMs)) + " · All day")
                    : (happeningNow ? "" : dateFormat.format(new Date(event.startMs)) + " · ")
                    + timeFormat.format(new Date(event.startMs)) + " – " + timeFormat.format(new Date(event.endMs));
            TextView time = label(timing, 12, LiquidTheme.secondaryText(this), false);
            item.addView(time, new LinearLayout.LayoutParams(-1, dp(20)));
            if (happeningNow) {
                ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                progress.setMax(1000);
                int value = (int) Math.max(0, Math.min(1000,
                        (now - event.startMs) * 1000L / Math.max(1L, event.endMs - event.startMs)));
                progress.setProgress(value);
                progress.setProgressTintList(android.content.res.ColorStateList.valueOf(LiquidTheme.accent(this)));
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
                progressParams.topMargin = dp(5);
                item.addView(progress, progressParams);
            }
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(390), dp(70));
            itemParams.setMargins(0, 0, dp(10), 0);
            calendarEventItems.addView(item, itemParams);
        }
    }

    private void maybeRefreshPublicCalendar(boolean manual) {
        String url = preferences.getString("publicCalendarUrl", "");
        if (url.isEmpty() || url.equals(calendarRefreshingUrl)) return;
        long interval = scheduleValue(SchedulePreferences.CALENDAR, SchedulePreferences.CALENDAR_VALUES,
                SchedulePreferences.DEFAULT_CALENDAR);
        if (!manual && interval <= 0) return;
        long lastUpdated = preferences.getLong("publicCalendarUpdatedAt", 0L);
        long lastAttempted = preferences.getLong("publicCalendarAttemptedAt", lastUpdated);
        boolean cacheNeedsUpgrade = preferences.getInt("publicCalendarCacheVersion", 0) < CALENDAR_CACHE_VERSION
                && preferences.getInt("publicCalendarAttemptedVersion", 0) < CALENDAR_CACHE_VERSION;
        if (!manual && !cacheNeedsUpgrade
                && System.currentTimeMillis() - Math.max(lastUpdated, lastAttempted) < interval) return;
        refreshPublicCalendar(url, manual);
    }

    private void refreshPublicCalendar(String url, boolean manual) {
        if (url.equals(calendarRefreshingUrl)) return;
        calendarRefreshingUrl = url;
        calendarRefreshing = true;
        preferences.edit().putLong("publicCalendarAttemptedAt", System.currentTimeMillis())
                .putInt("publicCalendarAttemptedVersion", CALENDAR_CACHE_VERSION).apply();
        int generation = ++calendarRequestGeneration;
        calendarWorker.execute(() -> {
            String error = null;
            List<PublicCalendarRepository.Event> events = null;
            try {
                String feed = PublicCalendarRepository.download(url);
                events = PublicCalendarRepository.parse(feed, System.currentTimeMillis());
            } catch (Exception failure) {
                error = failure.getMessage() == null ? "Could not refresh this calendar." : failure.getMessage();
            }
            String resultError = error;
            List<PublicCalendarRepository.Event> resultEvents = events;
            handler.post(() -> {
                if (generation != calendarRequestGeneration) return;
                calendarRefreshing = false;
                calendarRefreshingUrl = "";
                if (isFinishing() || !url.equals(preferences.getString("publicCalendarUrl", ""))) return;
                if (resultError == null && resultEvents != null) {
                    saveCalendarEvents(resultEvents);
                    updateCalendarEventDisplay();
                    if (settingsScreen != null) settingsScreen.onCalendarRefreshResult(
                            "Calendar refreshed · " + resultEvents.size() + " events cached.");
                } else if (settingsScreen != null) {
                    settingsScreen.onCalendarRefreshResult("Calendar refresh failed · " + resultError);
                }
                if (manual && resultError != null) {
                    Toast.makeText(this, "Calendar refresh failed: " + resultError, Toast.LENGTH_LONG).show();
                }
                scheduleCalendarRefresh();
            });
        });
    }

    private void scheduleCalendarRefresh() {
        if (calendarRefreshTick == null) return;
        handler.removeCallbacks(calendarRefreshTick);
        if (!screenActive || preferences.getString("publicCalendarUrl", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.CALENDAR, SchedulePreferences.CALENDAR_VALUES,
                SchedulePreferences.DEFAULT_CALENDAR);
        if (interval <= 0) return;
        long updated = preferences.getLong("publicCalendarUpdatedAt", 0L);
        long attempted = preferences.getLong("publicCalendarAttemptedAt", updated);
        long elapsed = Math.max(0L, System.currentTimeMillis() - Math.max(updated, attempted));
        handler.postDelayed(calendarRefreshTick, Math.max(1000L, interval - elapsed));
    }

    private void updateGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int period = hour < 12 ? 0 : hour < 17 ? 1 : hour < 21 ? 2 : 3;
        String[][] titles = {
                {"Easy morning", "Good morning", "Morning, take it slow"},
                {"Good afternoon", "A little breather", "Taking it easy?"},
                {"Good evening", "Time to unwind", "Evening, take it easy"},
                {"Late night, nice and easy", "Still up?", "A quiet night in"}
        };
        String[][] subtitles = {
                {"Coffee, comfort, and your favorites.", "Your shows are right here when you’re ready.", "A calm start, with everything close by."},
                {"Settle in with something you love.", "Your favorites are ready whenever you are.", "Live TV and apps, just a click away."},
                {"Find a comfy spot; your favorites are ready.", "Your screen is set for a quieter evening.", "Pick something good and get cozy."},
                {"No rush. Find something good to watch.", "Your favorites are here for a little longer.", "Keep it mellow; your screen is ready."}
        };
        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        int variant = day % titles[period].length;
        greeting.setText(titles[period][variant]);
        greetingSubtitle.setText(subtitles[period][variant]);
    }

    private void renderApps() {
        if (appRow == null || appsLoading) return;
        appsLoading = true;
        appRow.removeAllViews();
        TextView loading = label("Loading apps…", 15, MUTED, false);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        appRow.addView(loading, new LinearLayout.LayoutParams(dp(180), dp(72)));
        new Thread(() -> {
            List<AppEntry> loaded = installedApps();
            List<String> selected = selectedPackages(loaded);
            if (selected.isEmpty() && !preferences.getBoolean("customApps", false)) {
                for (int i = 0; i < Math.min(8, loaded.size()); i++) selected.add(loaded.get(i).packageName);
            }
            Set<String> selectedSet = new HashSet<>(selected);
            PackageManager packageManager = getPackageManager();
            ArrayList<AppEntry> withIcons = new ArrayList<>(loaded.size());
            for (AppEntry entry : loaded) {
                Drawable icon = null;
                if (selectedSet.contains(entry.packageName)) {
                    try { icon = packageManager.getApplicationIcon(entry.info); } catch (Exception ignored) { }
                }
                withIcons.add(new AppEntry(entry.info, entry.packageName, entry.label, icon));
            }
            handler.post(() -> showApps(withIcons));
        }, "app-discovery").start();
    }

    private void showApps(List<AppEntry> all) {
        if (appRow == null || isFinishing()) return;
        appsLoading = false;
        appsLoaded = true;
        View previous = appRow.findFocus();
        if (previous != null && previous.getTag() instanceof String) focusedPackage = (String) previous.getTag();
        appRow.removeAllViews();
        int available = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 112;
        int tileWidth = Math.max(76, Math.min(102, (available - 7 * 8) / 8));
        List<String> selected = selectedPackages(all);
        if (selected.isEmpty() && !preferences.getBoolean("customApps", false)) {
            for (int i = 0; i < Math.min(8, all.size()); i++) selected.add(all.get(i).packageName);
        }
        for (String packageName : selected) {
            AppEntry entry = findApp(all, packageName);
            if (entry == null) continue;
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setFocusable(true);
            tile.setClickable(true);
            StateListDrawable appTileStates = new StateListDrawable();
            boolean light = LiquidTheme.isLight(this);
            appTileStates.addState(new int[]{android.R.attr.state_focused},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            appTileStates.addState(new int[]{android.R.attr.state_pressed},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            appTileStates.addState(new int[]{},
                    roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 11));
            tile.setBackground(appTileStates);
            tile.setFocusableInTouchMode(false);
            tile.setElevation(dp(2));
            tile.setOnFocusChangeListener((view, hasFocus) -> {
                LiquidTheme.animateFocus(view, hasFocus);
                if (hasFocus && homeScroll != null) homeScroll.scrollTo(0, 0);
            });
            tile.setPadding(dp(6), dp(10), dp(6), dp(6));
            tile.setTag(packageName);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView name = label(entry.label, 11, LiquidTheme.text(this), true);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams appNameParams = new LinearLayout.LayoutParams(dp(tileWidth - 10), dp(34));
            appNameParams.topMargin = dp(4);
            tile.addView(name, appNameParams);
            tile.setOnClickListener(v -> launchApp(entry.packageName));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(tileWidth), dp(108));
            lp.setMargins(0, 0, dp(8), 0);
            appRow.addView(tile, lp);
            if (initialAppFocus && settingsScreen == null && packageName.equals(focusedPackage)) tile.requestFocus();
        }
        if (!initialAppFocus && appRow.getChildCount() > 0) {
            initialAppFocus = true;
            appRow.post(() -> {
                if (settingsScreen == null) {
                    appRow.getChildAt(0).requestFocus();
                    homeScroll.scrollTo(0, 0);
                } else if (restoredScroll > 0) {
                    homeScroll.scrollTo(0, restoredScroll);
                }
            });
        }
        if (selected.isEmpty()) {
            TextView empty = label("No apps selected", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            appRow.addView(empty, new LinearLayout.LayoutParams(dp(240), dp(72)));
        }
    }

    private List<YouTubeCard> savedYouTubeCards() {
        ArrayList<YouTubeCard> result = new ArrayList<>();
        String raw = preferences.getString("youtubeCards", "[]");
        try {
            JSONArray values = new JSONArray(raw);
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                YouTubeCard parsed = YouTubeCard.parse(value.optString("url", ""));
                if (parsed == null) continue;
                parsed.title = value.optString("title", parsed.title);
                parsed.autoTitle = value.optString("autoTitle", parsed.title);
                parsed.customTitle = value.optBoolean("customTitle", false);
                parsed.customThumbnail = value.optBoolean("customThumbnail", false);
                String savedThumbnail = value.optString("thumbnailUrl", "");
                if (!savedThumbnail.isEmpty()) parsed.thumbnailUrl = savedThumbnail;
                parsed.metadataCheckedAt = value.optLong("metadataCheckedAt", 0L);
                result.add(parsed);
            }
        } catch (Exception ignored) { }
        return result;
    }

    private void saveYouTubeCards(List<YouTubeCard> cards) {
        JSONArray values = new JSONArray();
        for (YouTubeCard card : cards) {
            JSONObject value = new JSONObject();
            try {
                value.put("url", card.url);
                value.put("title", card.title);
                value.put("autoTitle", card.autoTitle);
                value.put("customTitle", card.customTitle);
                value.put("customThumbnail", card.customThumbnail);
                value.put("thumbnailUrl", card.thumbnailUrl == null ? "" : card.thumbnailUrl);
                value.put("metadataCheckedAt", card.metadataCheckedAt);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString("youtubeCards", values.toString()).apply();
    }

    private void renderYouTubeCards() {
        if (youtubeRow == null || youtubeSection == null) return;
        youtubeRow.removeAllViews();
        List<YouTubeCard> cards = savedYouTubeCards();
        youtubeSection.setVisibility(cards.isEmpty() ? View.GONE : View.VISIBLE);
        boolean retryMissingArtwork = !preferences.getBoolean("youtubeArtworkFeedFallbackTried", false);
        if (retryMissingArtwork) preferences.edit().putBoolean("youtubeArtworkFeedFallbackTried", true).apply();
        for (YouTubeCard card : cards) {
            boolean hasThumbnail = card.thumbnailUrl != null && !card.thumbnailUrl.isEmpty();
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(false);
            tile.setClickable(true);
            tile.setPadding(dp(8), dp(8), dp(8), dp(7));
            StateListDrawable states = new StateListDrawable();
            boolean light = LiquidTheme.isLight(this);
            states.addState(new int[]{android.R.attr.state_focused},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            states.addState(new int[]{android.R.attr.state_pressed},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            states.addState(new int[]{},
                    roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 11));
            tile.setBackground(states);
            tile.setElevation(dp(2));
            tile.setOnFocusChangeListener(LiquidTheme::animateFocus);

            FrameLayout poster = new FrameLayout(this);
            GradientDrawable posterBackground = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    light
                            ? new int[]{0xFF47677A, 0xFF243B50, 0xFF182534}
                            : new int[]{0xFF46647A, 0xFF1B3044, 0xFF101B2A});
            posterBackground.setCornerRadius(dp(16));
            posterBackground.setStroke(dp(1), LiquidTheme.border(this));
            poster.setBackground(posterBackground);
            poster.setClipToOutline(true);
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setTag(card.url);
            poster.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));
            // The title becomes the artwork when YouTube has no usable thumbnail.
            TextView fallback = label(card.title, 20, Color.WHITE, true);
            fallback.setTextColor(Color.WHITE);
            fallback.setGravity(Gravity.BOTTOM | Gravity.LEFT);
            fallback.setMaxLines(3);
            fallback.setEllipsize(android.text.TextUtils.TruncateAt.END);
            fallback.setPadding(dp(15), dp(34), dp(15), dp(14));
            fallback.setShadowLayer(dp(8), 0, dp(2), Color.BLACK);
            fallback.setContentDescription("Artwork: " + card.title);
            poster.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            TextView type = label(YouTubeCard.VIDEO.equals(card.type) ? "VIDEO" : "PLAYLIST",
                    9, Color.WHITE, true);
            type.setTextColor(Color.WHITE);
            type.setLetterSpacing(.10f);
            type.setPadding(dp(9), dp(5), dp(9), dp(5));
            type.setBackground(roundedShape(0xDB121212, LiquidTheme.accent(this), 1, 5));
            FrameLayout.LayoutParams typeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            typeParams.setMargins(dp(8), dp(8), 0, 0);
            poster.addView(type, typeParams);
            tile.addView(poster, new LinearLayout.LayoutParams(dp(224), dp(hasThumbnail ? 126 : 178)));
            if (hasThumbnail) {
                TextView title = label(card.title, 14, LiquidTheme.text(this), true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                title.setPadding(dp(3), dp(6), dp(3), 0);
                tile.addView(title, new LinearLayout.LayoutParams(dp(224), dp(52)));
            }
            tile.setContentDescription((YouTubeCard.VIDEO.equals(card.type) ? "Play video: " : "Open playlist: ") + card.title);
            tile.setOnClickListener(view -> launchYouTubeCard(card));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), dp(198));
            params.setMargins(0, dp(2), dp(16), dp(8));
            youtubeRow.addView(tile, params);
            loadYouTubeThumbnail(card, thumbnail, fallback);
            long refreshAfter = card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()
                    ? 60L * 60L * 1000L : 24L * 60L * 60L * 1000L;
            if (card.metadataCheckedAt == 0L
                    || System.currentTimeMillis() - card.metadataCheckedAt > refreshAfter
                    || (retryMissingArtwork && (card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()))) {
                fetchYouTubeMetadata(card.url);
            }
        }
    }

    private void launchYouTubeCard(YouTubeCard card) {
        Uri target = Uri.parse(card.url);
        for (String packageName : new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}) {
            Intent deepLink = new Intent(Intent.ACTION_VIEW, target);
            deepLink.setPackage(packageName);
            if (deepLink.resolveActivity(getPackageManager()) != null) {
                try { startActivity(deepLink); return; } catch (Exception ignored) { }
            }
        }
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, target);
            if (browser.resolveActivity(getPackageManager()) != null) startActivity(browser);
            else Toast.makeText(this, "No app can open this YouTube link.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open this YouTube link.", Toast.LENGTH_LONG).show();
        }
    }

    private void fetchYouTubeMetadata(String url) {
        synchronized (youtubeMetadataInFlight) {
            if (!youtubeMetadataInFlight.add(url)) return;
        }
        youtubeWorker.execute(() -> {
            String title = null, thumbnail = null;
            HttpURLConnection connection = null;
            try {
                String endpoint = "https://www.youtube.com/oembed?format=json&url="
                        + URLEncoder.encode(url, "UTF-8");
                connection = (HttpURLConnection) new java.net.URL(endpoint).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int total = 0, count;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > 1024 * 1024) break;
                            output.write(buffer, 0, count);
                        }
                        JSONObject response = new JSONObject(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                        String candidateTitle = response.optString("title", "").trim();
                        String candidateThumbnail = response.optString("thumbnail_url", "").trim();
                        if (!candidateTitle.isEmpty()) title = candidateTitle;
                        if (isSafeYouTubeThumbnail(candidateThumbnail)) thumbnail = candidateThumbnail;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            YouTubeCard parsed = YouTubeCard.parse(url);
            if (thumbnail == null && parsed != null && YouTubeCard.PLAYLIST.equals(parsed.type)) {
                thumbnail = playlistFeedThumbnail(parsed.playlistId);
            }
            final String resultTitle = title, resultThumbnail = thumbnail;
            handler.post(() -> {
                synchronized (youtubeMetadataInFlight) { youtubeMetadataInFlight.remove(url); }
                List<YouTubeCard> cards = savedYouTubeCards();
                boolean changed = false;
                for (YouTubeCard card : cards) {
                    if (!card.url.equals(url)) continue;
                    if (resultTitle != null) {
                        card.autoTitle = resultTitle;
                        if (!card.customTitle) card.title = resultTitle;
                    }
                    if (resultThumbnail != null && !card.customThumbnail) card.thumbnailUrl = resultThumbnail;
                    card.metadataCheckedAt = System.currentTimeMillis();
                    changed = true;
                    break;
                }
                if (changed) {
                    saveYouTubeCards(cards);
                    renderYouTubeCards();
                }
            });
        });
    }

    private boolean isSafeYouTubeThumbnail(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && ("i.ytimg.com".equalsIgnoreCase(host) || "img.youtube.com".equalsIgnoreCase(host)
                    || host.endsWith(".ytimg.com"));
        } catch (Exception ignored) { return false; }
    }

    private String playlistFeedThumbnail(String playlistId) {
        if (playlistId == null || !playlistId.matches("[A-Za-z0-9_-]{8,}")) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(
                    "https://www.youtube.com/feeds/videos.xml?playlist_id=" + playlistId).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (java.io.InputStream input = connection.getInputStream()) {
                org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(input, "UTF-8");
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                    if ("thumbnail".equals(parser.getName())) {
                        String imageUrl = parser.getAttributeValue(null, "url");
                        if (isSafeYouTubeThumbnail(imageUrl)) return imageUrl;
                    } else if ("videoId".equals(parser.getName())) {
                        String videoId = parser.nextText();
                        if (videoId.matches("[A-Za-z0-9_-]{11}"))
                            return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void loadYouTubeThumbnail(YouTubeCard card, ImageView image, TextView fallback) {
        if (card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()) return;
        android.graphics.Bitmap cached = youtubeThumbnailCache.get(card.thumbnailUrl);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            fallback.setVisibility(View.GONE);
            return;
        }
        youtubeWorker.execute(() -> {
            String cacheName = youtubeThumbnailCacheName(card.thumbnailUrl);
            java.io.File imageFile = new java.io.File(getFilesDir(), cacheName);
            android.graphics.Bitmap bitmap = imageFile.isFile() ? decodeYouTubeImage(imageFile) : null;
            if (bitmap == null) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new java.net.URL(card.thumbnailUrl).openConnection();
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(8000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int count, total = 0;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > 8 * 1024 * 1024) break;
                            output.write(buffer, 0, count);
                        }
                        byte[] bytes = output.toByteArray();
                        bitmap = decodeYouTubeImage(bytes);
                        if (bitmap != null) {
                            java.io.File temporary = new java.io.File(getFilesDir(), cacheName + ".tmp");
                            try (java.io.FileOutputStream file = new java.io.FileOutputStream(temporary)) {
                                file.write(bytes);
                                file.flush();
                            }
                            if (imageFile.exists()) imageFile.delete();
                            temporary.renameTo(imageFile);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            if (bitmap == null) return;
            youtubeThumbnailCache.put(card.thumbnailUrl, bitmap);
            android.graphics.Bitmap result = bitmap;
            handler.post(() -> {
                if (card.url.equals(image.getTag())) {
                    image.setImageBitmap(result);
                    fallback.setVisibility(View.GONE);
                }
            });
        });
    }

    private android.graphics.Bitmap decodeYouTubeImage(java.io.File file) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        android.graphics.BitmapFactory.Options options = youtubeImageOptions(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private android.graphics.Bitmap decodeYouTubeImage(byte[] bytes) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                youtubeImageOptions(bounds.outWidth, bounds.outHeight));
    }

    private android.graphics.BitmapFactory.Options youtubeImageOptions(int width, int height) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        options.inSampleSize = Math.max(1, Math.min(width / Math.max(1, dp(452)), height / Math.max(1, dp(232))));
        return options;
    }

    private String youtubeThumbnailCacheName(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("youtube-");
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value));
            return key.toString();
        } catch (Exception ignored) { return "youtube-" + Integer.toHexString(url.hashCode()); }
    }

    private void addTopIcon(LinearLayout container, String title, int iconResource, Runnable action) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(LiquidTheme.text(this));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 22));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 22));
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        icon.setBackground(states);
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setOnFocusChangeListener(LiquidTheme::animateFocus);
        icon.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(2), 0, dp(2), 0);
        container.addView(icon, lp);
    }

    private void openNetworkSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        try { startActivity(intent); }
        catch (Exception ignored) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
            catch (Exception ignoredAgain) { }
        }
    }

    private void openAndroidSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
        catch (Exception ignored) { }
    }

    private boolean isLauncherOverrideEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        String expectedPackage = getPackageName();
        String expectedClass = LauncherOverrideService.class.getName();
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            if (expectedPackage.equals(info.getResolveInfo().serviceInfo.packageName)
                    && expectedClass.equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }

    private Drawable tileShape(int color, int strokeColor, int strokeWidth) {
        return roundedShape(color, strokeColor, strokeWidth, 18);
    }

    private Drawable roundedShape(int color, int strokeColor, int strokeWidth, int radiusDp) {
        return new GlassDrawable(this, glassBackdrop, color, strokeColor, strokeWidth, Math.max(16, radiusDp));
    }

    private Drawable gradientShape(int startColor, int endColor, int strokeColor, int strokeWidth, int radiusDp) {
        return roundedShape(startColor, strokeColor, strokeWidth, radiusDp);
    }

    private void styleFocusableTile(View tile) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{android.R.attr.state_pressed}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{}, tileShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1));
        tile.setBackground(states);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
    }

    private void launchApp(String packageName) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) try { startActivity(launch); } catch (Exception ignored) { }
    }

    private static final class AppEntry {
        final ApplicationInfo info;
        final String packageName, label;
        final Drawable icon;
        AppEntry(ApplicationInfo info, String packageName, String label, Drawable icon) { this.info = info; this.packageName = packageName; this.label = label; this.icon = icon; }
    }

    private List<AppEntry> installedApps() {
        ArrayList<AppEntry> entries = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (info.packageName.equals(getPackageName())) continue;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(info.packageName);
            if (launch == null) launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null) continue;
            String label = String.valueOf(pm.getApplicationLabel(info));
            entries.add(new AppEntry(info, info.packageName, label, null));
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    private List<String> selectedPackages(List<AppEntry> available) {
        String raw = preferences.getString("selectedApps", "");
        ArrayList<String> selected = new ArrayList<>();
        if (!raw.isEmpty()) for (String pkg : raw.split("\\n")) if (findApp(available, pkg) != null && !selected.contains(pkg)) selected.add(pkg);
        return selected;
    }

    private AppEntry findApp(List<AppEntry> all, String pkg) {
        for (AppEntry entry : all) if (entry.packageName.equals(pkg)) return entry;
        return null;
    }

    private void renderChannels() {
        if (channelRows == null) return;
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        LinkedHashMap<String, List<IptvRepository.Channel>> grouped = new LinkedHashMap<>();
        for (IptvRepository.Channel channel : channels) {
            if (hiddenChannelKeys.contains(channel.stableKey())) continue;
            String category = channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim();
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(channel);
        }
        int visibleCount = 0;
        for (List<IptvRepository.Channel> list : grouped.values()) visibleCount += list.size();
        if (channelHeader != null) channelHeader.setText(channels.isEmpty()
                ? "Live, right now" : "Live, right now  ·  " + visibleCount + " channels");
        if (grouped.isEmpty()) {
            channelsEmpty.setText(channels.isEmpty()
                    ? "Add a playlist in Settings to browse live TV."
                    : "All channels are hidden. Restore channels in Settings.");
            if (!renderedCategories.isEmpty() || channelRows.getChildCount() != 1 || channelRows.getChildAt(0) != channelsEmpty) {
                channelRows.removeAllViews();
                channelRowMap.clear();
                channelTabMap.clear();
                channelRows.addView(channelsEmpty);
                renderedCategories.clear();
                selectedChannelCategory = null;
            }
            return;
        }

        List<String> nextCategories = new ArrayList<>(grouped.keySet());
        if (!nextCategories.equals(renderedCategories)) {
            channelRows.removeAllViews();
            channelRowMap.clear();
            channelTabMap.clear();
            channelCategoryTabs.removeAllViews();
            renderedCategories = nextCategories;
            if (!renderedCategories.contains(selectedChannelCategory)) selectedChannelCategory = renderedCategories.get(0);
            channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(58)));
            ChannelRow row = createChannelRow();
            channelRowMap.put(selectedChannelCategory, row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(14));
            channelRows.addView(row.container, params);
            for (String category : renderedCategories) {
                Button tab = createChannelCategoryTab(category);
                channelTabMap.put(category, tab);
                LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(44));
                tabParams.setMargins(0, 0, dp(10), 0);
                channelCategoryTabs.addView(tab, tabParams);
            }
        }
        if (!renderedCategories.contains(selectedChannelCategory)) selectedChannelCategory = renderedCategories.get(0);
        for (Map.Entry<String, List<IptvRepository.Channel>> category : grouped.entrySet()) {
            Button tab = channelTabMap.get(category.getKey());
            if (tab != null) tab.setText(category.getKey() + "  ·  " + category.getValue().size());
            ChannelRow row = channelRowMap.get(selectedChannelCategory);
            if (row != null && category.getKey().equals(selectedChannelCategory)) row.adapter.setChannels(category.getValue());
        }
        updateChannelCategoryTabStyles();
    }

    private void refreshCachedProgrammeDisplay() {
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        for (ChannelRow row : channelRowMap.values()) row.adapter.refreshProgrammeLabels();
    }

    private Button createChannelCategoryTab(String category) {
        Button tab = actionButton(category);
        tab.setTextSize(14);
        tab.setPadding(dp(18), 0, dp(18), 0);
        tab.setMinWidth(dp(72));
        tab.setOnClickListener(view -> {
            if (category.equals(selectedChannelCategory)) return;
            selectedChannelCategory = category;
            updateChannelCategoryTabStyles();
            ChannelRow row = channelRowMap.isEmpty() ? null : channelRowMap.values().iterator().next();
            if (row != null) {
                channelRowMap.clear();
                channelRowMap.put(selectedChannelCategory, row);
                row.adapter.setChannels(channelsForCategory(category));
            }
            channelCategoryScroll.smoothScrollTo(Math.max(0, view.getLeft() - dp(24)), 0);
        });
        return tab;
    }

    private List<IptvRepository.Channel> channelsForCategory(String category) {
        ArrayList<IptvRepository.Channel> selected = new ArrayList<>();
        for (IptvRepository.Channel channel : channels) {
            if (hiddenChannelKeys.contains(channel.stableKey())) continue;
            String channelCategory = channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim();
            if (category.equals(channelCategory)) selected.add(channel);
        }
        return selected;
    }

    private void updateChannelCategoryTabStyles() {
        for (Map.Entry<String, Button> entry : channelTabMap.entrySet()) {
            boolean selected = entry.getKey().equals(selectedChannelCategory);
            Button tab = entry.getValue();
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, roundedShape(
                    LiquidTheme.raisedSurface(this),
                    LiquidTheme.accent(this), 2, 8));
            states.addState(new int[]{}, selected
                    ? roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 24)
                    : roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 8));
            tab.setBackground(states);
            tab.setTextColor(LiquidTheme.text(this));
            tab.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    private ChannelRow createChannelRow() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        RecyclerView list = new RecyclerView(this);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(4), dp(4), dp(4), dp(4));
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(4);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ChannelAdapter adapter = new ChannelAdapter(this, logoLoader, glassBackdrop, this::playChannel);
        list.setAdapter(adapter);
        container.addView(list, new LinearLayout.LayoutParams(-1, dp(238)));
        return new ChannelRow(container, adapter);
    }

    private void loadBackgroundFolder(Uri treeUri, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        List<BackgroundPhoto> cached = cachedBackgroundFolder(treeUri.toString());
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        else if (userSelected && cached.isEmpty()) setBackgroundPhotos(Collections.emptyList());
        Thread loader = new Thread(() -> {
            FolderScanResult scan = enumeratePhotos(treeUri);
            handler.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                if (!scan.success) {
                    if (userSelected) Toast.makeText(this,
                            "Could not read that folder. Keeping the saved slideshow.", Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundFolder(treeUri.toString(), scan.photos);
                if (!sameBackgroundPhotos(scan.photos)) setBackgroundPhotos(scan.photos);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected && scan.photos.isEmpty()) {
                    Toast.makeText(this, "No images were found in that folder.", Toast.LENGTH_LONG).show();
                } else if (userSelected) {
                    Toast.makeText(this, "Background slideshow updated · " + scan.photos.size() + " photos", Toast.LENGTH_SHORT).show();
                }
            });
        }, "background-folder-scan");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadBackgroundAlbum(String shareUrl, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        List<BackgroundPhoto> cached = cachedBackgroundAlbum(shareUrl);
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        preferences.edit().putLong("googlePhotosAttemptedAt", System.currentTimeMillis()).apply();
        Thread loader = new Thread(() -> {
            List<BackgroundPhoto> photos = new ArrayList<>();
            String failure = null;
            try {
                List<String> urls = GooglePhotosAlbumRepository.fetchImageUrls(shareUrl);
                if (urls.isEmpty()) throw new IllegalStateException("No photos were found. Check that link sharing is on.");
                for (int i = 0; i < urls.size(); i++) photos.add(new BackgroundPhoto(Uri.parse(urls.get(i)), "Google Photos " + (i + 1)));
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Could not load that Google Photos album." : e.getMessage();
            }
            List<BackgroundPhoto> result = photos;
            String error = failure;
            handler.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                if (error != null || result.isEmpty()) {
                    if (userSelected) Toast.makeText(this, error == null ? "No public photos were found in that album." : error, Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundAlbum(shareUrl, result);
                preferences.edit().putString("wallpaperAlbum", shareUrl).remove("wallpaperFolder")
                        .putLong("googlePhotosUpdatedAt", System.currentTimeMillis()).apply();
                if (!sameBackgroundPhotos(result)) setBackgroundPhotos(result);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected) Toast.makeText(this, "Google Photos slideshow updated · " + result.size() + " photos", Toast.LENGTH_SHORT).show();
                scheduleGooglePhotosRefresh();
            });
        }, "google-photos-album");
        loader.setDaemon(true);
        loader.start();
    }

    private void maybeRefreshGooglePhotos(boolean appLaunch) {
        String album = preferences.getString("wallpaperAlbum", "");
        if (album.isEmpty() || !preferences.getString("wallpaperFolder", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.GOOGLE_PHOTOS,
                SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS);
        if (interval == SchedulePreferences.MANUAL) return;
        if (interval == SchedulePreferences.EVERY_LAUNCH) {
            if (appLaunch) loadBackgroundAlbum(album, false);
            return;
        }
        long updated = preferences.getLong("googlePhotosUpdatedAt", 0L);
        long attempted = preferences.getLong("googlePhotosAttemptedAt", updated);
        if (System.currentTimeMillis() - Math.max(updated, attempted) >= interval) {
            loadBackgroundAlbum(album, false);
        }
    }

    private void scheduleGooglePhotosRefresh() {
        if (googlePhotosRefreshTick == null) return;
        handler.removeCallbacks(googlePhotosRefreshTick);
        if (!screenActive || preferences.getString("wallpaperAlbum", "").isEmpty()
                || !preferences.getString("wallpaperFolder", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.GOOGLE_PHOTOS,
                SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS);
        if (interval <= 0) return;
        long updated = preferences.getLong("googlePhotosUpdatedAt", 0L);
        long attempted = preferences.getLong("googlePhotosAttemptedAt", updated);
        long elapsed = Math.max(0L, System.currentTimeMillis() - Math.max(updated, attempted));
        handler.postDelayed(googlePhotosRefreshTick, Math.max(1000L, interval - elapsed));
    }

    private List<BackgroundPhoto> cachedBackgroundAlbum(String shareUrl) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        if (!shareUrl.equals(preferences.getString(GOOGLE_PHOTOS_CACHE_URL, ""))) return photos;
        try {
            JSONArray urls = new JSONArray(preferences.getString(GOOGLE_PHOTOS_CACHE_IMAGES, "[]"));
            for (int i = 0; i < urls.length(); i++) {
                String url = urls.optString(i, "");
                if (!url.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(url), "Google Photos " + (i + 1)));
            }
        } catch (Exception ignored) { }
        return photos;
    }

    private List<BackgroundPhoto> cachedBackgroundFolder(String folderUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        if (!folderUri.equals(preferences.getString(PHOTO_FOLDER_CACHE_URI, ""))) return photos;
        try {
            JSONArray values = new JSONArray(preferences.getString(PHOTO_FOLDER_CACHE_IMAGES, "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String uri = value.optString("uri", "");
                if (!uri.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(uri), value.optString("name", "Photo")));
            }
        } catch (Exception ignored) { }
        return photos;
    }

    private void cacheBackgroundFolder(String folderUri, List<BackgroundPhoto> photos) {
        JSONArray values = new JSONArray();
        for (BackgroundPhoto photo : photos) {
            JSONObject value = new JSONObject();
            try {
                value.put("uri", photo.uri.toString());
                value.put("name", photo.name);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString(PHOTO_FOLDER_CACHE_URI, folderUri)
                .putString(PHOTO_FOLDER_CACHE_IMAGES, values.toString()).apply();
    }

    private void cacheBackgroundAlbum(String shareUrl, List<BackgroundPhoto> photos) {
        JSONArray urls = new JSONArray();
        for (BackgroundPhoto photo : photos) urls.put(photo.uri.toString());
        preferences.edit()
                .putString(GOOGLE_PHOTOS_CACHE_URL, shareUrl)
                .putString(GOOGLE_PHOTOS_CACHE_IMAGES, urls.toString())
                .apply();
    }

    private boolean sameBackgroundPhotos(List<BackgroundPhoto> photos) {
        if (backgroundPhotos.size() != photos.size()) return false;
        for (int i = 0; i < photos.size(); i++) {
            if (!backgroundPhotos.get(i).uri.equals(photos.get(i).uri)) return false;
        }
        return true;
    }

    private void showCachedBackgroundImmediately() {
        if (backgroundPhotos.isEmpty()) return;
        slideshowTick = this::showNextBackgroundPhoto;
        Uri uri = backgroundPhotos.get(0).uri;
        String url = uri.toString();
        int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1280);
        int targetHeight = Math.min(dp(heroHeightDp()), 720);
        String scheme = uri.getScheme();
        android.graphics.Bitmap bitmap;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            java.io.File file = new java.io.File(new java.io.File(getCacheDir(), "google-photos"),
                    remoteWallpaperCacheName(url));
            if (!file.isFile()) return;
            bitmap = decodeBackgroundImage(file, targetWidth, targetHeight);
        } else {
            bitmap = decodeWallpaper(uri, targetWidth, targetHeight);
        }
        if (bitmap == null) return;
        backgroundBitmapCache.put(url, bitmap);
        slideA.setImageBitmap(bitmap);
        slideA.setVisibility(View.VISIBLE);
        slideA.setAlpha(1f);
        slideAActive = false;
        backgroundIndex = backgroundPhotos.size() > 1 ? 1 : 0;
    }

    private FolderScanResult enumeratePhotos(Uri treeUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            boolean success = collectPhotos(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", visited, photos, 0);
            Collections.sort(photos, (left, right) -> left.name.compareToIgnoreCase(right.name));
            return new FolderScanResult(photos, success);
        } catch (Exception ignored) { return new FolderScanResult(photos, false); }
    }

    private boolean collectPhotos(Uri treeUri, String documentId, String parentPath, Set<String> visited,
                                  List<BackgroundPhoto> photos, int depth) {
        if (depth > 16 || !visited.add(documentId)) return true;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return false;
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectPhotos(treeUri, childId, parentPath + (name == null ? "" : name) + "/", visited, photos, depth + 1);
                } else if (isImageDocument(mime, name)) {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    photos.add(new BackgroundPhoto(documentUri, parentPath + (name == null ? childId : name)));
                }
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean isImageDocument(String mime, String name) {
        if (mime != null && mime.startsWith("image/")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif")
                || lower.endsWith(".avif") || lower.endsWith(".bmp");
    }

    private void setBackgroundPhotos(List<BackgroundPhoto> photos) {
        backgroundBitmapCache.evictAll();
        backgroundPhotos.clear();
        backgroundPhotos.addAll(photos);
        backgroundIndex = 0;
        backgroundImageGeneration++;
        slideA.animate().cancel();
        slideB.animate().cancel();
        slideA.setImageDrawable(null);
        slideB.setImageDrawable(null);
        slideA.setVisibility(View.GONE);
        slideB.setVisibility(View.GONE);
        slideAActive = true;
        wallpaperImage.setVisibility(backgroundPhotos.isEmpty() ? View.VISIBLE : View.GONE);
        if (backgroundPhotos.isEmpty()) applyWallpaper();
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (backgroundPhotos.isEmpty()) { slideshowTick = null; return; }
        slideshowTick = this::showNextBackgroundPhoto;
        if (screenActive && settingsScreen == null) showNextBackgroundPhoto();
    }

    private void scheduleNextBackgroundPhoto() {
        if (slideshowTick == null || !screenActive || settingsScreen != null || backgroundPhotos.size() < 2) return;
        handler.removeCallbacks(slideshowTick);
        long interval = scheduleValue(SchedulePreferences.SLIDESHOW,
                SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW);
        if (interval > 0) handler.postDelayed(slideshowTick, interval);
    }

    private long scheduleValue(String key, long[] values, long fallback) {
        return SchedulePreferences.get(preferences, key, values, fallback);
    }

    private void showNextBackgroundPhoto() {
        if (backgroundPhotos.isEmpty() || !screenActive || settingsScreen != null || isFinishing()) return;
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        int index = backgroundIndex % backgroundPhotos.size();
        BackgroundPhoto photo = backgroundPhotos.get(index);
        int generation = ++backgroundImageGeneration;
        ImageView incoming = slideAActive ? slideA : slideB;
        ImageView outgoing = slideAActive ? slideB : slideA;
        new Thread(() -> {
            android.graphics.Bitmap bitmap = decodeWallpaper(photo.uri,
                    Math.min(getResources().getDisplayMetrics().widthPixels, 1280),
                    Math.min(dp(heroHeightDp()), 720));
            handler.post(() -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                backgroundIndex = (index + 1) % backgroundPhotos.size();
                if (bitmap == null) {
                    scheduleNextBackgroundPhoto();
                    return;
                }
                glassBackdrop.prepare(bitmap, true, () -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                incoming.setImageBitmap(bitmap);
                incoming.setVisibility(View.VISIBLE);
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 900 : 0).start();
                outgoing.animate().alpha(0f).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 900 : 0).withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setImageDrawable(null);
                }).start();
                slideAActive = !slideAActive;
                scheduleNextBackgroundPhoto();
                });
            });
        }, "background-photo-decode").start();
    }

    private static final class BackgroundPhoto {
        final Uri uri;
        final String name;
        BackgroundPhoto(Uri uri, String name) { this.uri = uri; this.name = name; }
    }

    private static final class FolderScanResult {
        final List<BackgroundPhoto> photos;
        final boolean success;
        FolderScanResult(List<BackgroundPhoto> photos, boolean success) {
            this.photos = photos;
            this.success = success;
        }
    }

    private static final class ChannelRow {
        final LinearLayout container;
        final ChannelAdapter adapter;
        ChannelRow(LinearLayout container, ChannelAdapter adapter) {
            this.container = container;
            this.adapter = adapter;
        }
    }

    private void playChannel(IptvRepository.Channel channel) {
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        Intent play = new Intent(this, PlaybackActivity.class);
        play.putExtra("name", channel.name);
        play.putExtra("now", channel.now);
        play.putExtra("nowStartMs", channel.nowStartMs);
        play.putExtra("nowEndMs", channel.nowEndMs);
        play.putExtra("next", channel.next);
        try { play.putExtra("programmes", IptvRepository.serializeProgrammes(channel.programmes).toString()); }
        catch (Exception ignored) { }
        play.putExtra("url", channel.streamUrl);
        boolean channelHasLicenseConfig = !channel.drmLicenseUrl.isEmpty() || !channel.drmHeaders.isEmpty();
        Map<String, String> headers = channelHasLicenseConfig ? channel.drmHeaders : iptv.drmHeaders();
        String licenseUrl = channelHasLicenseConfig ? channel.drmLicenseUrl : iptv.drmLicenseUrl();
        boolean widevine = "widevine".equals(channel.drmType) || !licenseUrl.isEmpty();
        play.putExtra("widevine", widevine);
        play.putExtra("licenseUrl", licenseUrl);
        ArrayList<String> headerPairs = new ArrayList<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            headerPairs.add(header.getKey()); headerPairs.add(header.getValue());
        }
        play.putStringArrayListExtra("licenseHeaders", headerPairs);
        JSONArray zapList = new JSONArray();
        int selectedIndex = 0;
        for (IptvRepository.Channel item : channels) {
            if (hiddenChannelKeys.contains(item.stableKey())) continue;
            JSONObject entry = new JSONObject();
            try {
                entry.put("name", item.name); entry.put("url", item.streamUrl);
                entry.put("now", item.now); entry.put("nowStartMs", item.nowStartMs);
                entry.put("nowEndMs", item.nowEndMs); entry.put("next", item.next);
                boolean hasConfig = !item.drmLicenseUrl.isEmpty() || !item.drmHeaders.isEmpty();
                String itemLicense = hasConfig ? item.drmLicenseUrl : iptv.drmLicenseUrl();
                Map<String, String> itemHeaders = hasConfig ? item.drmHeaders : iptv.drmHeaders();
                entry.put("widevine", "widevine".equals(item.drmType) || !itemLicense.isEmpty());
                entry.put("licenseUrl", itemLicense);
                JSONObject headersObject = new JSONObject();
                for (Map.Entry<String, String> h : itemHeaders.entrySet()) headersObject.put(h.getKey(), h.getValue());
                entry.put("headers", headersObject);
                if (item.sameRenderIdentity(channel)) selectedIndex = zapList.length();
                zapList.put(entry);
            } catch (Exception ignored) { }
        }
        play.putExtra("zapChannels", zapList.toString());
        play.putExtra("zapIndex", selectedIndex);
        startActivity(play);
    }

    private void refreshChannels() {
        if (channelsRefreshing) return;
        channelsRefreshing = true;
        status.setText("Refreshing channels…");
        if (settingsScreen != null) settingsScreen.onRefreshResult("Refreshing channels…");
        iptv.refreshChannels((loaded, message) -> handler.post(() -> {
            channelsRefreshing = false;
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            status.setText(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
            if (!channels.isEmpty() && !iptv.guideUrl().isEmpty() && iptv.isGuideStale(channels)) refreshGuide(false);
            scheduleLiveRefreshCheck();
        }));
    }

    private void refreshGuide(boolean userRequested) {
        if (guideRefreshing) return;
        guideRefreshing = true;
        String refreshing = userRequested ? "Refreshing programme guide…" : "Updating today's programme guide…";
        status.setText(refreshing);
        if (settingsScreen != null) settingsScreen.onRefreshResult(refreshing);
        iptv.refreshGuide(new ArrayList<>(channels), (loaded, message) -> handler.post(() -> {
            guideRefreshing = false;
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            status.setText(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
            scheduleLiveRefreshCheck();
        }));
    }

    private void maybeRefreshLiveData() {
        if (!screenActive) return;
        if (!iptv.playlistUrl().isEmpty() && iptv.isChannelsStale()) {
            refreshChannels();
        } else if (!channels.isEmpty() && !iptv.guideUrl().isEmpty() && iptv.isGuideStale(channels)) {
            refreshGuide(false);
        }
    }

    private void scheduleLiveRefreshCheck() {
        if (guideRefreshTick == null) return;
        handler.removeCallbacks(guideRefreshTick);
        if (!screenActive) return;
        long channelDelay = iptv.playlistUrl().isEmpty() ? Long.MAX_VALUE : iptv.millisUntilChannelsRefresh();
        long guideDelay = channels.isEmpty() || iptv.guideUrl().isEmpty()
                ? Long.MAX_VALUE : iptv.millisUntilGuideRefresh();
        long delay = Math.min(channelDelay, guideDelay);
        if (delay != Long.MAX_VALUE) handler.postDelayed(guideRefreshTick, Math.max(60_000L, delay));
    }

    private SettingsScreen settingsScreen;

    private void styleDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 26));
            window.setDimAmount(.35f);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) {
                styleFocusableTile(button);
                button.setTextColor(LiquidTheme.text(this));
                button.setAllCaps(false);
            }
        }
    }

    private void showSettings() {
        if (settingsScreen != null) return;
        settingsScreen = new SettingsScreen();
        settingsScreen.show();
    }

    private final class SettingsScreen {
        private static final int LIVE = 0, CHANNELS = 1, APPS = 2, APPEARANCE = 3, CLOCK = 4, DRM = 5, HOME = 6, BACKUP = 7, YOUTUBE = 8, CALENDAR = 9, SCHEDULES = 10;
        private final String[] sections = {"Live TV", "Channels", "Apps", "Appearance", "Clock", "Playback & DRM", "Home button", "Backup & restore", "YouTube", "Calendar", "Schedules"};
        private final String[] sectionGlyphs = {"✦", "◈", "▦", "▧", "◷", "◉", "⌂", "⇧", "▶", "◷", "↻"};
        private final String[] descriptions = {
                "Playlist and programme guide", "Choose which channels appear on Home",
                "Choose and order your home shortcuts", "Photo backdrop and wallpaper",
                "Time and date on Home", "Default Widevine license settings",
                "Open Duo when the TV Home button opens its built-in launcher",
                "Save or restore launcher settings and channels", "Save videos and playlists on Home",
                "Show events from a public Google Calendar", "Choose when content updates automatically"
        };
        private final Dialog dialog = new Dialog(MainActivity.this, android.R.style.Theme_Material_NoActionBar);
        private final Button[] tabs = new Button[sections.length];
        private final FrameLayout detail = new FrameLayout(MainActivity.this);
        private int section = LIVE;
        private View firstControl;
        private EditText playlistField, guideField, licenseField, headersField, calendarUrlField;
        private EditText youtubeUrlField, youtubeTitleField, youtubeArtField;
        private TextView liveFeedback, cacheSummary, guideSummary, youtubeFeedback, calendarFeedback, calendarSummary;
        private LinearLayout youtubeSavedList;
        private ChannelSettingsAdapter channelAdapter;
        private AppSettingsAdapter appAdapter;
        private RecyclerView appList;
        private List<AppEntry> availableApps;
        private boolean loadingApps, appsChanged, channelsChanged, youtubeChanged;
        private String draftPlaylist = iptv.playlistUrl();
        private String draftGuide = iptv.guideUrl();
        private String draftLicense = iptv.drmLicenseUrl();
        private String draftHeaders = iptv.drmHeaderText();
        private String draftYoutubeUrl = "";
        private String draftYoutubeTitle = "";
        private String draftYoutubeArt = "";
        private String draftCalendarUrl = preferences.getString("publicCalendarUrl", "");

        void show() {
            LinearLayout shell = new LinearLayout(MainActivity.this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(42), dp(24), dp(42), dp(28));
            // Let the paused home photograph remain part of the Settings composition.
            shell.setBackgroundColor(LiquidTheme.isLight(MainActivity.this) ? 0x334B6373 : 0x52040C16);

            LinearLayout heading = new LinearLayout(MainActivity.this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            shell.addView(heading, new LinearLayout.LayoutParams(-1, dp(82)));
            TextView brand = label("DUO", 22, LiquidTheme.text(MainActivity.this), true);
            brand.setLetterSpacing(.16f);
            heading.addView(brand, new LinearLayout.LayoutParams(dp(72), -2));
            View divider = new View(MainActivity.this);
            divider.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(38));
            dividerParams.setMargins(0, 0, dp(18), 0);
            heading.addView(divider, dividerParams);
            LinearLayout headingCopy = new LinearLayout(MainActivity.this);
            headingCopy.setOrientation(LinearLayout.VERTICAL);
            headingCopy.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(headingCopy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = label("Your space", 21, LiquidTheme.text(MainActivity.this), true);
            headingCopy.addView(title);
            TextView hint = label("SETTINGS  ·  PERSONALIZE YOUR TV", 10, MUTED, true);
            hint.setLetterSpacing(.11f);
            headingCopy.addView(hint);
            Button close = settingsButton("Close");
            close.setOnClickListener(v -> requestClose());
            heading.addView(close, new LinearLayout.LayoutParams(dp(124), dp(48)));

            LinearLayout body = new LinearLayout(MainActivity.this);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
            bodyParams.topMargin = dp(14);
            shell.addView(body, bodyParams);
            LinearLayout rail = new LinearLayout(MainActivity.this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setPadding(dp(12), dp(12), dp(12), dp(12));
            rail.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 24));
            ScrollView railScroll = new ScrollView(MainActivity.this);
            railScroll.setFillViewport(true);
            railScroll.setSmoothScrollingEnabled(true);
            railScroll.setClipChildren(false);
            railScroll.setClipToPadding(false);
            railScroll.setPadding(dp(10), dp(10), dp(10), dp(10));
            railScroll.setVerticalScrollBarEnabled(true);
            railScroll.setScrollbarFadingEnabled(false);
            railScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            railScroll.addView(rail, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(250), -1);
            railParams.rightMargin = dp(12);
            body.addView(railScroll, railParams);
            for (int i = 0; i < sections.length; i++) {
                final int target = i;
                Button tab = settingsButton(sections[i]);
                tab.setText(sectionGlyphs[i] + "   " + sections[i]);
                tab.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                tab.setPadding(dp(16), 0, dp(12), 0);
                tab.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
                tab.setId(View.generateViewId());
                tab.setOnClickListener(v -> {
                    captureDrafts();
                    section = target;
                    renderSection();
                    tab.requestFocus();
                });
                tabs[i] = tab;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
                params.setMargins(0, 0, 0, dp(5));
                rail.addView(tab, params);
            }
            detail.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 26));
            body.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));

            dialog.setContentView(shell);
            dialog.setCancelable(false);
            dialog.setOnKeyListener((ignored, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BACK) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) requestClose();
                return true;
            });
            dialog.setOnDismissListener(ignored -> {
                if (settingsScreen == this) settingsScreen = null;
                if (appsChanged) renderApps();
                if (channelsChanged) renderChannels();
                if (youtubeChanged) renderYouTubeCards();
                if (screenActive && !backgroundPhotos.isEmpty()) {
                    if (slideA.getVisibility() == View.VISIBLE || slideB.getVisibility() == View.VISIBLE) {
                        scheduleNextBackgroundPhoto();
                    } else showNextBackgroundPhoto();
                }
            });
            renderSection();
            dialog.show();
            if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
            backgroundImageGeneration++;
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams windowAttributes = window.getAttributes();
                windowAttributes.dimAmount = .12f;
                window.setAttributes(windowAttributes);
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            tabs[section].requestFocus();
        }

        private void captureDrafts() {
            if (playlistField != null) draftPlaylist = playlistField.getText().toString();
            if (guideField != null) draftGuide = guideField.getText().toString();
            if (licenseField != null) draftLicense = licenseField.getText().toString();
            if (headersField != null) draftHeaders = headersField.getText().toString();
            if (youtubeUrlField != null) draftYoutubeUrl = youtubeUrlField.getText().toString();
            if (youtubeTitleField != null) draftYoutubeTitle = youtubeTitleField.getText().toString();
            if (youtubeArtField != null) draftYoutubeArt = youtubeArtField.getText().toString();
            if (calendarUrlField != null) draftCalendarUrl = calendarUrlField.getText().toString();
            playlistField = guideField = licenseField = headersField = null;
            youtubeUrlField = youtubeTitleField = youtubeArtField = null;
            calendarUrlField = null;
            liveFeedback = null;
        }

        private boolean hasUnsavedEdits() {
            captureDrafts();
            return !draftPlaylist.trim().equals(iptv.playlistUrl())
                    || !draftGuide.trim().equals(iptv.guideUrl())
                    || !draftLicense.trim().equals(iptv.drmLicenseUrl())
                    || !draftHeaders.trim().equals(iptv.drmHeaderText())
                    || !draftCalendarUrl.trim().equals(preferences.getString("publicCalendarUrl", ""));
        }

        private void requestClose() {
            if (!hasUnsavedEdits()) { dialog.dismiss(); return; }
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.isLight(MainActivity.this) ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert))
                    .setTitle("Discard unsaved edits?")
                    .setMessage("Playlist, DRM, or calendar changes have not been saved.")
                    .setPositiveButton("Discard", (ignored, which) -> dialog.dismiss())
                    .setNegativeButton("Keep editing", null).show();
            styleDialog(prompt);
        }

        private void renderSection() {
            firstControl = null;
            detail.removeAllViews();
            boolean light = LiquidTheme.isLight(MainActivity.this);
            for (int i = 0; i < tabs.length; i++) {
                boolean selected = i == section;
                tabs[i].setTextColor(LiquidTheme.text(MainActivity.this));
                tabs[i].setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
                StateListDrawable tabStates = new StateListDrawable();
                tabStates.addState(new int[]{android.R.attr.state_focused}, roundedShape(
                        LiquidTheme.raisedSurface(MainActivity.this), LiquidTheme.accent(MainActivity.this), 2, 8));
                tabStates.addState(new int[]{android.R.attr.state_selected}, roundedShape(
                        LiquidTheme.raisedSurface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 8));
                tabStates.addState(new int[]{}, roundedShape(
                        LiquidTheme.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 8));
                tabs[i].setSelected(selected);
                tabs[i].setBackground(tabStates);
            }
            LinearLayout page = new LinearLayout(MainActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(32), dp(26), dp(32), dp(28));
            detail.addView(page, new FrameLayout.LayoutParams(-1, -1));
            TextView title = label(sections[section], 28, LiquidTheme.text(MainActivity.this), true);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            page.addView(title);
            TextView subtitle = label(descriptions[section], 14, LiquidTheme.secondaryText(MainActivity.this), false);
            subtitle.setMaxLines(2);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, dp(46));
            subParams.setMargins(0, dp(3), 0, 0);
            page.addView(subtitle, subParams);
            switch (section) {
                case LIVE: renderLive(page); break;
                case CHANNELS: renderChannelSettings(page); break;
                case APPS: renderAppSettings(page); break;
                case APPEARANCE: renderAppearance(page); break;
                case CLOCK: renderClock(page); break;
                case DRM: renderDrm(page); break;
                case HOME: renderHomeOverride(page); break;
                case BACKUP: renderBackup(page); break;
                case YOUTUBE: renderYouTubeSettings(page); break;
                case CALENDAR: renderCalendarSettings(page); break;
                case SCHEDULES: renderSchedules(page); break;
                default: break;
            }
            if (firstControl != null) {
                firstControl.setId(View.generateViewId());
                tabs[section].setNextFocusRightId(firstControl.getId());
                firstControl.setNextFocusLeftId(tabs[section].getId());
            }
        }

        private void renderHomeOverride(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            boolean enabled = isLauncherOverrideEnabled();
            form.addView(label(enabled
                    ? "Duo launcher override is enabled"
                    : "Duo launcher override is not enabled", 18, Color.WHITE, true));
            addFormHint(form, "Some TVs keep their built-in launcher as Home even when another launcher is selected. Enable Duo Launcher in Android Accessibility settings to reopen Duo whenever the TV opens its built-in launcher.");
            addFormHint(form, "This uses an Accessibility service. Android requires you to turn it on in system settings. You can turn it off there at any time.");
            addFormButton(form, "Open Accessibility settings", v -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open Accessibility settings.", Toast.LENGTH_LONG).show();
                }
            });
            addFormButton(form, "Try setting Duo as the Home app", v -> {
                try {
                    startActivity(new Intent("android.settings.HOME_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) { Toast.makeText(MainActivity.this, "Could not open Home settings.", Toast.LENGTH_LONG).show(); }
                }
            });
        }

        private void renderBackup(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Create a JSON backup of launcher settings, app and channel choices, IPTV and DRM details, and saved channels.");
            addFormHint(form, "Backup files contain playlist and DRM credentials. Keep them private. Wallpaper folder access may need to be granted again when restoring on another TV.");
            addFormButton(form, "Create backup file", v -> openBackupExportPicker());
            addFormButton(form, "Restore from backup", v -> openBackupImportPicker());
            addFormHint(form, "Restoring replaces the current launcher settings and saved channel cache.");
        }

        private void renderSchedules(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Schedules run while Duo is open. Overdue content is checked when Home resumes; manual refresh buttons continue to work when automatic refresh is disabled.");
            addScheduleButton(form, "Photo slideshow", SchedulePreferences.SLIDESHOW,
                    SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW, true, false,
                    () -> {
                        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
                        scheduleNextBackgroundPhoto();
                    });
            addScheduleButton(form, "IPTV playlist", SchedulePreferences.PLAYLIST,
                    SchedulePreferences.PLAYLIST_VALUES, SchedulePreferences.DEFAULT_PLAYLIST, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, "Programme guide", SchedulePreferences.GUIDE,
                    SchedulePreferences.GUIDE_VALUES, SchedulePreferences.DEFAULT_GUIDE, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, "Public calendar", SchedulePreferences.CALENDAR,
                    SchedulePreferences.CALENDAR_VALUES, SchedulePreferences.DEFAULT_CALENDAR, false, false,
                    () -> { maybeRefreshPublicCalendar(false); scheduleCalendarRefresh(); });
            addScheduleButton(form, "Google Photos album", SchedulePreferences.GOOGLE_PHOTOS,
                    SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS, false, true,
                    () -> { maybeRefreshGooglePhotos(false); scheduleGooglePhotosRefresh(); });
            addFormHint(form, "Paused keeps the current photo visible. Every launch refreshes Google Photos once when Duo starts. Manual only disables automatic network refreshes.");
            addFormButton(form, "Restore schedule defaults", v -> {
                preferences.edit()
                        .putLong(SchedulePreferences.SLIDESHOW, SchedulePreferences.DEFAULT_SLIDESHOW)
                        .putLong(SchedulePreferences.PLAYLIST, SchedulePreferences.DEFAULT_PLAYLIST)
                        .putLong(SchedulePreferences.GUIDE, SchedulePreferences.DEFAULT_GUIDE)
                        .putLong(SchedulePreferences.CALENDAR, SchedulePreferences.DEFAULT_CALENDAR)
                        .putLong(SchedulePreferences.GOOGLE_PHOTOS, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS)
                        .apply();
                maybeRefreshLiveData();
                scheduleLiveRefreshCheck();
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
                scheduleGooglePhotosRefresh();
                scheduleNextBackgroundPhoto();
                renderSection();
            });
        }

        private void addScheduleButton(LinearLayout form, String title, String key, long[] values,
                                       long fallback, boolean slideshow, boolean googlePhotos,
                                       Runnable apply) {
            long current = scheduleValue(key, values, fallback);
            Button button = addFormButton(form, title + "  ·  "
                    + SchedulePreferences.label(current, slideshow, googlePhotos), null);
            button.setOnClickListener(v -> {
                long next = SchedulePreferences.next(scheduleValue(key, values, fallback), values, fallback);
                preferences.edit().putLong(key, next).apply();
                button.setText(title + "  ·  " + SchedulePreferences.label(next, slideshow, googlePhotos));
                apply.run();
            });
        }

        private void renderYouTubeSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Paste a public YouTube video or playlist link to add a portrait card to Home. Add an optional title; the full artwork thumbnail stays visible on the card.");
            youtubeUrlField = addInput(form, "Video or playlist link", "https://www.youtube.com/watch?v=…", draftYoutubeUrl, false);
            youtubeTitleField = addInput(form, "Card title · optional", "Use the YouTube title", draftYoutubeTitle, false);
            youtubeArtField = addInput(form, "Artwork video link · optional", "Use for playlist artwork", draftYoutubeArt, false);
            addFormButton(form, "Add to Home", view -> {
                String value = youtubeUrlField == null ? draftYoutubeUrl : youtubeUrlField.getText().toString();
                String customTitle = youtubeTitleField == null ? draftYoutubeTitle : youtubeTitleField.getText().toString().trim();
                String artworkVideo = youtubeArtField == null ? draftYoutubeArt : youtubeArtField.getText().toString().trim();
                YouTubeCard parsed = YouTubeCard.parse(value);
                if (parsed == null) {
                    if (youtubeFeedback != null) youtubeFeedback.setText("Enter a valid YouTube video or playlist link.");
                    return;
                }
                List<YouTubeCard> cards = savedYouTubeCards();
                for (YouTubeCard card : cards) {
                    if (card.url.equals(parsed.url)) {
                        if (youtubeFeedback != null) youtubeFeedback.setText("That link is already on Home.");
                        return;
                    }
                }
                if (!customTitle.isEmpty()) {
                    parsed.title = customTitle;
                    parsed.customTitle = true;
                }
                if (!artworkVideo.isEmpty() && !parsed.useArtworkFromVideo(artworkVideo)) {
                    if (youtubeFeedback != null) youtubeFeedback.setText("Artwork must be a YouTube video link.");
                    return;
                }
                cards.add(parsed);
                saveYouTubeCards(cards);
                youtubeChanged = true;
                draftYoutubeUrl = "";
                draftYoutubeTitle = "";
                draftYoutubeArt = "";
                if (youtubeUrlField != null) youtubeUrlField.setText("");
                if (youtubeTitleField != null) youtubeTitleField.setText("");
                if (youtubeArtField != null) youtubeArtField.setText("");
                if (youtubeFeedback != null) youtubeFeedback.setText("Added to Home.");
                renderYouTubeSavedList();
                if (youtubeUrlField != null) youtubeUrlField.requestFocus();
            });
            youtubeFeedback = label("", 13, LiquidTheme.secondaryText(MainActivity.this), false);
            youtubeFeedback.setPadding(0, dp(4), 0, dp(8));
            form.addView(youtubeFeedback);
            TextView savedTitle = label("SAVED CARDS", 12, LiquidTheme.text(MainActivity.this), true);
            savedTitle.setLetterSpacing(.12f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(36));
            titleParams.topMargin = dp(10);
            form.addView(savedTitle, titleParams);
            youtubeSavedList = new LinearLayout(MainActivity.this);
            youtubeSavedList.setOrientation(LinearLayout.VERTICAL);
            form.addView(youtubeSavedList);
            renderYouTubeSavedList();
        }

        private void renderYouTubeSavedList() {
            if (youtubeSavedList == null) return;
            youtubeSavedList.removeAllViews();
            List<YouTubeCard> cards = savedYouTubeCards();
            if (cards.isEmpty()) {
                youtubeSavedList.addView(label("No YouTube cards saved yet.", 14,
                        LiquidTheme.secondaryText(MainActivity.this), false));
                return;
            }
            for (YouTubeCard card : cards) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(7), dp(8), dp(7));
                row.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                        LiquidTheme.border(MainActivity.this), 1, 13));
                LinearLayout copy = new LinearLayout(MainActivity.this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView title = label(card.title, 15, LiquidTheme.text(MainActivity.this), true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(title);
                TextView detail = label((YouTubeCard.VIDEO.equals(card.type) ? "VIDEO" : "PLAYLIST")
                        + ((card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()) ? "  ·  Add art" : "")
                        + "  ·  " + card.url, 11, LiquidTheme.secondaryText(MainActivity.this), false);
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                copy.addView(detail);
                row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                Button remove = settingsButton("Remove");
                remove.setContentDescription("Remove " + card.title + " from Home");
                Button editTitle = settingsButton("Title");
                editTitle.setContentDescription("Edit title for " + card.title);
                Button editArt = settingsButton("Art");
                editArt.setContentDescription("Edit artwork for " + card.title);
                LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(88), dp(46));
                editParams.setMargins(dp(8), 0, 0, 0);
                row.addView(editTitle, editParams);
                LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(78), dp(46));
                artParams.setMargins(dp(6), 0, 0, 0);
                row.addView(editArt, artParams);
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(100), dp(46));
                removeParams.setMargins(dp(6), 0, 0, 0);
                row.addView(remove, removeParams);
                editTitle.setOnClickListener(view -> editYouTubeTitle(card));
                editArt.setOnClickListener(view -> editYouTubeArtwork(card));
                remove.setOnClickListener(view -> {
                    List<YouTubeCard> latest = savedYouTubeCards();
                    ArrayList<YouTubeCard> retained = new ArrayList<>();
                    for (YouTubeCard saved : latest) if (!saved.url.equals(card.url)) retained.add(saved);
                    saveYouTubeCards(retained);
                    youtubeChanged = true;
                    if (youtubeFeedback != null) youtubeFeedback.setText("Removed from Home.");
                    renderYouTubeSavedList();
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(68));
                rowParams.setMargins(0, 0, 0, dp(8));
                youtubeSavedList.addView(row, rowParams);
            }
        }

        private void editYouTubeArtwork(YouTubeCard selected) {
            EditText artworkInput = new EditText(MainActivity.this);
            artworkInput.setSingleLine(true);
            artworkInput.setHint("YouTube video link");
            artworkInput.setTextColor(LiquidTheme.text(MainActivity.this));
            artworkInput.setHintTextColor(LiquidTheme.secondaryText(MainActivity.this));
            artworkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            artworkInput.setPadding(dp(24), dp(12), dp(24), dp(12));
            artworkInput.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog artworkDialog = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.isLight(MainActivity.this) ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert))
                    .setTitle("Card artwork")
                    .setMessage("Paste a YouTube video link to use its thumbnail. Leave empty to retry automatic artwork.")
                    .setView(artworkInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", null)
                    .create();
            artworkDialog.setOnShowListener(ignored -> artworkDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String videoLink = artworkInput.getText().toString().trim();
                        YouTubeCard image = videoLink.isEmpty() ? null : YouTubeCard.parse(videoLink);
                        if (!videoLink.isEmpty() && (image == null || !YouTubeCard.VIDEO.equals(image.type))) {
                            artworkInput.setError("Enter a YouTube video link");
                            return;
                        }
                        List<YouTubeCard> cards = savedYouTubeCards();
                        for (YouTubeCard card : cards) {
                            if (!card.url.equals(selected.url)) continue;
                            card.customThumbnail = image != null;
                            card.thumbnailUrl = image == null
                                    ? (YouTubeCard.VIDEO.equals(card.type)
                                    ? "https://i.ytimg.com/vi/" + card.videoId + "/hqdefault.jpg" : null)
                                    : image.thumbnailUrl;
                            card.metadataCheckedAt = 0L;
                            break;
                        }
                        saveYouTubeCards(cards);
                        youtubeChanged = true;
                        renderYouTubeSavedList();
                        renderYouTubeCards();
                        artworkDialog.dismiss();
                    }));
            artworkDialog.show();
            styleDialog(artworkDialog);
        }

        private void editYouTubeTitle(YouTubeCard selected) {
            EditText titleInput = new EditText(MainActivity.this);
            titleInput.setSingleLine(true);
            titleInput.setHint("Use the YouTube title");
            titleInput.setText(selected.customTitle ? selected.title : "");
            titleInput.setTextColor(LiquidTheme.text(MainActivity.this));
            titleInput.setHintTextColor(LiquidTheme.secondaryText(MainActivity.this));
            titleInput.setSelectAllOnFocus(true);
            titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            int inset = dp(24);
            titleInput.setPadding(inset, dp(12), inset, dp(12));
            titleInput.setBackground(roundedShape(
                    LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.isLight(MainActivity.this) ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert))
                    .setTitle("Card title")
                    .setMessage("Clear the title to use the title provided by YouTube when available.")
                    .setView(titleInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String custom = titleInput.getText().toString().trim();
                        List<YouTubeCard> latest = savedYouTubeCards();
                        for (YouTubeCard card : latest) {
                            if (!card.url.equals(selected.url)) continue;
                            card.customTitle = !custom.isEmpty();
                            if (!custom.isEmpty()) card.title = custom;
                            else card.title = card.autoTitle;
                            break;
                        }
                        saveYouTubeCards(latest);
                        youtubeChanged = true;
                        renderYouTubeSavedList();
                    }).show();
            styleDialog(prompt);
        }

        private void renderCalendarSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Add a public Google Calendar iCal address. In Google Calendar, open Settings → Integrate calendar → Public address in iCal format. Only events happening now appear on Home.");
            calendarUrlField = addInput(form, "Public calendar iCal URL", "https://calendar.google.com/calendar/ical/…/public/basic.ics", draftCalendarUrl, false);
            calendarSummary = label(calendarSummaryText(), 13, LiquidTheme.secondaryText(MainActivity.this), false);
            calendarSummary.setPadding(dp(2), dp(8), dp(2), dp(4));
            form.addView(calendarSummary);
            addFormButton(form, "Save calendar and refresh", view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString().trim();
                if (entered.isEmpty()) {
                    preferences.edit().remove("publicCalendarUrl").remove("publicCalendarEvents")
                            .remove("publicCalendarUpdatedAt").remove("publicCalendarAttemptedAt")
                            .remove("publicCalendarCacheVersion").remove("publicCalendarAttemptedVersion").apply();
                    draftCalendarUrl = "";
                    if (calendarUrlField != null) calendarUrlField.setText("");
                    if (calendarFeedback != null) calendarFeedback.setText("Calendar removed.");
                    updateCalendarEventDisplay();
                    if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                    return;
                }
                String normalized = PublicCalendarRepository.normalizeUrl(entered);
                if (normalized == null) {
                    if (calendarFeedback != null) calendarFeedback.setText("Use a public Google Calendar iCal URL from Integrate calendar.");
                    return;
                }
                String previous = preferences.getString("publicCalendarUrl", "");
                if (!normalized.equals(previous)) {
                    preferences.edit().putString("publicCalendarUrl", normalized)
                            .putString("publicCalendarEvents", "[]").remove("publicCalendarUpdatedAt")
                            .remove("publicCalendarAttemptedAt").remove("publicCalendarCacheVersion")
                            .remove("publicCalendarAttemptedVersion").apply();
                    updateCalendarEventDisplay();
                }
                draftCalendarUrl = normalized;
                if (calendarUrlField != null) calendarUrlField.setText(normalized);
                if (calendarFeedback != null) calendarFeedback.setText("Refreshing public calendar…");
                if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                refreshPublicCalendar(normalized, true);
            });
            addFormButton(form, "Refresh saved calendar now", view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString().trim();
                if (!entered.equals(preferences.getString("publicCalendarUrl", ""))) {
                    if (calendarFeedback != null) calendarFeedback.setText("Save the calendar URL before refreshing.");
                } else if (entered.isEmpty()) {
                    if (calendarFeedback != null) calendarFeedback.setText("Add a public calendar URL first.");
                } else {
                    if (calendarFeedback != null) calendarFeedback.setText("Refreshing public calendar…");
                    refreshPublicCalendar(entered, true);
                }
            });
            calendarFeedback = label("", 13, LiquidTheme.secondaryText(MainActivity.this), false);
            calendarFeedback.setPadding(dp(2), dp(7), dp(2), dp(4));
            form.addView(calendarFeedback);
            addFormHint(form, "The on-device schedule is checked when Duo opens. Change its automatic refresh cadence under Schedules. Events appear while their start and end times are active.");
        }

        private String calendarSummaryText() {
            long updatedAt = preferences.getLong("publicCalendarUpdatedAt", 0L);
            JSONArray events;
            try { events = new JSONArray(preferences.getString("publicCalendarEvents", "[]")); }
            catch (Exception ignored) { events = new JSONArray(); }
            String updated = updatedAt == 0L ? "Not refreshed yet"
                    : "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "Public calendar cache · " + events.length() + " events · " + updated;
        }

        void onCalendarRefreshResult(String message) {
            if (settingsScreen != this) return;
            if (calendarFeedback != null) calendarFeedback.setText(message);
            if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
        }

        void refreshLauncherOverride() {
            if (settingsScreen == this && section == HOME) renderSection();
        }

        private LinearLayout scrollForm(LinearLayout page) {
            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setFillViewport(false);
            scroll.setSmoothScrollingEnabled(true);
            scroll.setVerticalScrollBarEnabled(true);
            scroll.setScrollbarFadingEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout form = new LinearLayout(MainActivity.this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(2), dp(8), dp(12), dp(8));
            scroll.addView(form);
            return form;
        }

        private EditText addInput(LinearLayout form, String labelText, String hintText, String value, boolean multiline) {
            boolean light = LiquidTheme.isLight(MainActivity.this);
            TextView title = label(labelText, 15, LiquidTheme.text(MainActivity.this), true);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(28));
            labelParams.setMargins(0, dp(8), 0, 0);
            form.addView(title, labelParams);
            EditText field = new EditText(MainActivity.this);
            field.setText(value);
            field.setHint(hintText);
            field.setTextColor(LiquidTheme.text(MainActivity.this));
            field.setHintTextColor(LiquidTheme.secondaryText(MainActivity.this));
            field.setTextSize(16);
            field.setSelectAllOnFocus(!multiline);
            field.setInputType(multiline
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            if (!multiline) field.setSingleLine(true);
            field.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
            field.setPadding(dp(16), dp(8), dp(16), dp(8));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, gradientShape(
                    LiquidTheme.raisedSurface(MainActivity.this), LiquidTheme.raisedSurface(MainActivity.this),
                    LiquidTheme.accent(MainActivity.this), 2, 15));
            states.addState(new int[]{}, gradientShape(
                    LiquidTheme.surface(MainActivity.this), LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 15));
            field.setBackground(states);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(multiline ? 102 : 56));
            fieldParams.setMargins(0, 0, 0, dp(10));
            form.addView(field, fieldParams);
            if (firstControl == null) firstControl = field;
            return field;
        }

        private Button addFormButton(LinearLayout form, String text, View.OnClickListener listener) {
            Button button = settingsButton(text);
            button.setOnClickListener(listener);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
            params.setMargins(0, dp(8), 0, dp(8));
            form.addView(button, params);
            if (firstControl == null) firstControl = button;
            return button;
        }

        private Button settingsButton(String text) {
            Button button = actionButton(text);
            button.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
            return button;
        }

        private void addFormHint(LinearLayout form, String value) {
            boolean light = LiquidTheme.isLight(MainActivity.this);
            TextView hint = label(value, 13, LiquidTheme.secondaryText(MainActivity.this), false);
            hint.setLineSpacing(dp(2), 1f);
            hint.setPadding(dp(14), dp(11), dp(14), dp(11));
            hint.setBackground(roundedShape(light ? 0xEEFFFFFF : 0xDD252525,
                    LiquidTheme.border(MainActivity.this), 1, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(3), 0, dp(8));
            hint.setLayoutParams(params);
            form.addView(hint);
        }

        private void addSaveActions(LinearLayout page, View.OnClickListener save,
                                    View.OnClickListener discard, View.OnClickListener refresh) {
            LinearLayout actions = new LinearLayout(MainActivity.this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(54));
            rowParams.setMargins(0, dp(8), 0, dp(6));
            page.addView(actions, rowParams);
            Button saveButton = settingsButton("Save changes");
            stylePrimaryButton(saveButton);
            saveButton.setOnClickListener(save);
            actions.addView(saveButton, new LinearLayout.LayoutParams(dp(158), -1));
            Button discardButton = settingsButton("Discard edits");
            discardButton.setOnClickListener(discard);
            LinearLayout.LayoutParams discardParams = new LinearLayout.LayoutParams(dp(158), -1);
            discardParams.setMargins(dp(12), 0, 0, 0);
            actions.addView(discardButton, discardParams);
            if (refresh != null) {
                Button refreshButton = settingsButton("Refresh channels");
                refreshButton.setOnClickListener(refresh);
                LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(158), -1);
                refreshParams.setMargins(dp(12), 0, 0, 0);
                actions.addView(refreshButton, refreshParams);
            }
        }

        private void renderLive(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            playlistField = addInput(form, "M3U playlist", "https://example.com/playlist.m3u", draftPlaylist, false);
            guideField = addInput(form, "Programme guide · XML, XML.GZ or JSON", "https://www.epgschedule.com/data/index-lite.json", draftGuide, false);
            addFormHint(form, "XMLTV matches by tvg-id. JSON guides match channel IDs or a unique channel name. EPGSchedule times use India Standard Time.");
            cacheSummary = label(cacheSummaryText(), 13, LiquidTheme.secondaryText(MainActivity.this), false);
            cacheSummary.setPadding(dp(2), dp(8), dp(2), dp(4));
            form.addView(cacheSummary);
            guideSummary = label(guideSummaryText(), 13, LiquidTheme.secondaryText(MainActivity.this), false);
            guideSummary.setPadding(dp(2), dp(6), dp(2), dp(4));
            form.addView(guideSummary);
            addFormButton(form, "Refresh programme guide now", v -> {
                String enteredGuide = guideField == null ? draftGuide : guideField.getText().toString().trim();
                if (enteredGuide.isEmpty() || !enteredGuide.equals(iptv.guideUrl())) {
                    if (liveFeedback != null) liveFeedback.setText("Save the programme guide URL before refreshing it.");
                    return;
                }
                refreshGuide(true);
            });
            addFormHint(form, "Playlist and programme-guide refresh cadences are configured independently under Schedules. You can also refresh them manually here.");
            addSaveActions(page, v -> {
                captureDrafts();
                String playlist = draftPlaylist.trim(), guide = draftGuide.trim();
                boolean changed = !playlist.equals(iptv.playlistUrl()) || !guide.equals(iptv.guideUrl());
                iptv.saveUrls(playlist, guide);
                draftPlaylist = playlist; draftGuide = guide;
                if (changed) {
                    channels.clear();
                    channels.addAll(iptv.cachedChannels());
                    renderChannels();
                }
                renderSection();
                if (changed) maybeRefreshLiveData();
                if (liveFeedback != null) liveFeedback.setText(changed
                        ? "Settings saved. Automatic refresh uses the cadences under Schedules."
                        : "No changes to save.");
            }, v -> {
                draftPlaylist = iptv.playlistUrl(); draftGuide = iptv.guideUrl(); renderSection();
            }, v -> {
                String enteredPlaylist = playlistField == null
                        ? draftPlaylist : playlistField.getText().toString().trim();
                if (enteredPlaylist.isEmpty() || !enteredPlaylist.equals(iptv.playlistUrl())) {
                    if (liveFeedback != null) liveFeedback.setText("Save the playlist URL before refreshing channels.");
                    return;
                }
                refreshChannels();
            });
            liveFeedback = label(status.getText().toString(), 14, MUTED, false);
            page.addView(liveFeedback);
        }

        private void renderDrm(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            licenseField = addInput(form, "Widevine license URL", "Optional default license endpoint", draftLicense, false);
            headersField = addInput(form, "License request headers", "One per line · Name: Value", draftHeaders, true);
            addFormHint(form, "Channel-specific KODIPROP settings in the playlist take priority over these defaults.");
            addSaveActions(page, v -> {
                captureDrafts();
                iptv.saveDrmSettings(draftLicense, draftHeaders);
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText();
                renderSection();
                Toast.makeText(MainActivity.this, "Playback settings saved", Toast.LENGTH_SHORT).show();
            }, v -> {
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText(); renderSection();
            }, null);
        }

        private void renderClock(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Changes appear on the Home clock immediately.");
            Button time = addFormButton(form, "24-hour time · " + (preferences.getBoolean("24h", false) ? "On" : "Off"), null);
            time.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean("24h", false);
                preferences.edit().putBoolean("24h", enabled).apply();
                time.setText("24-hour time · " + (enabled ? "On" : "Off"));
                updateClock();
            });
            Button show = addFormButton(form, "Show date · " + (preferences.getBoolean("showDate", true) ? "On" : "Off"), null);
            show.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean("showDate", true);
                preferences.edit().putBoolean("showDate", enabled).apply();
                show.setText("Show date · " + (enabled ? "On" : "Off"));
                updateClock();
            });
        }

        private void renderAppearance(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormHint(form, "Choose a cinematic light or dark appearance for Home, Settings, and the TV player.");
            String mode = preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM);
            addFormButton(form, "System default" + (LiquidTheme.SYSTEM.equals(mode) ? "  ·  Selected" : ""),
                    v -> changeLiquidGlassMode(LiquidTheme.SYSTEM));
            addFormButton(form, "Light" + (LiquidTheme.LIGHT.equals(mode) ? "  ·  Selected" : ""),
                    v -> changeLiquidGlassMode(LiquidTheme.LIGHT));
            addFormButton(form, "Dark" + (LiquidTheme.DARK.equals(mode) ? "  ·  Selected" : ""),
                    v -> changeLiquidGlassMode(LiquidTheme.DARK));
            addFormHint(form, "A selected photo folder or Google Photos public album uses the slideshow cadence configured under Schedules and takes priority over a single wallpaper.");
            boolean hasFolder = !preferences.getString("wallpaperFolder", "").isEmpty();
            TextView folderState = label(hasFolder
                    ? "Photo folder selected · " + backgroundPhotos.size() + " images found"
                    : "No photo folder selected", 16, Color.WHITE, true);
            form.addView(folderState);
            addFormButton(form, "Choose photo folder", v -> {
                openWallpaperFolderPicker();
            });
            if (hasFolder) addFormButton(form, "Clear photo folder", v -> {
                backgroundFolderGeneration++;
                preferences.edit().remove("wallpaperFolder")
                        .remove(PHOTO_FOLDER_CACHE_URI)
                        .remove(PHOTO_FOLDER_CACHE_IMAGES).apply();
                String album = preferences.getString("wallpaperAlbum", "");
                if (!album.isEmpty()) loadBackgroundAlbum(album, false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            String albumUrl = preferences.getString("wallpaperAlbum", "");
            form.addView(label(albumUrl.isEmpty() ? "No Google Photos album selected" : "Google Photos album selected · " + backgroundPhotos.size() + " images found", 16, Color.WHITE, true));
            EditText albumField = addInput(form, "Google Photos public album link", "Paste a link shared with anyone", albumUrl, false);
            addFormHint(form, "The album must be shared with anyone who has the link. It cycles with the home photo slideshow.");
            addFormButton(form, "Use Google Photos album", v -> {
                String link = albumField.getText().toString().trim();
                if (link.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Paste a Google Photos shared album link first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Loading public album…", Toast.LENGTH_SHORT).show();
                loadBackgroundAlbum(link, true);
            });
            if (!albumUrl.isEmpty()) addFormButton(form, "Clear Google Photos album", v -> {
                backgroundFolderGeneration++;
                preferences.edit().remove("wallpaperAlbum")
                        .remove(GOOGLE_PHOTOS_CACHE_URL)
                        .remove(GOOGLE_PHOTOS_CACHE_IMAGES)
                        .remove("googlePhotosUpdatedAt")
                        .remove("googlePhotosAttemptedAt")
                        .apply();
                String folder = preferences.getString("wallpaperFolder", "");
                if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            View separator = new View(MainActivity.this);
            separator.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(-1, dp(1));
            separatorParams.setMargins(0, dp(18), 0, dp(18));
            form.addView(separator, separatorParams);
            boolean hasWallpaper = preferences.contains("wallpaper");
            form.addView(label(hasWallpaper ? "Single wallpaper selected" : "No single wallpaper selected", 16, Color.WHITE, true));
            addFormButton(form, "Choose single wallpaper", v -> {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("image/*");
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(picker, PICK_WALLPAPER);
            });
            if (hasWallpaper) addFormButton(form, "Clear single wallpaper", v -> {
                preferences.edit().remove("wallpaper").apply();
                applyWallpaper();
                renderSection();
            });
        }

        private void changeLiquidGlassMode(String mode) {
            if (mode.equals(preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM))) return;
            preferences.edit().putString(LiquidTheme.PREF_MODE, mode).apply();
            recreate();
        }

        void refreshAppearance() {
            if (settingsScreen == this && section == APPEARANCE) renderSection();
        }

        void onRefreshResult(String message) {
            if (settingsScreen == this && liveFeedback != null) liveFeedback.setText(message);
            if (settingsScreen == this && cacheSummary != null) cacheSummary.setText(cacheSummaryText());
            if (settingsScreen == this && guideSummary != null) guideSummary.setText(guideSummaryText());
            if (settingsScreen == this && section == CHANNELS && channelAdapter != null) channelAdapter.setQuery(channelAdapter.query);
        }

        private String cacheSummaryText() {
            long updatedAt = iptv.lastUpdatedAt();
            String updated = updatedAt == 0 ? "Not refreshed yet"
                    : "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "On-device channel cache · " + channels.size()
                    + (channels.size() == 1 ? " channel" : " channels") + " · " + updated;
        }

        private String guideSummaryText() {
            long updatedAt = iptv.lastGuideUpdatedAt();
            String updated = updatedAt == 0 ? "Not refreshed yet"
                    : "Last refreshed " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "Programme guide · daily refresh · " + updated;
        }

        private void renderChannelSettings(LinearLayout page) {
            EditText search = addInput(page, "Find a channel", "Search by channel name", "", false);
            search.setInputType(InputType.TYPE_CLASS_TEXT);
            TextView count = label("", 14, MUTED, false);
            page.addView(count, new LinearLayout.LayoutParams(-1, dp(30)));
            RecyclerView list = new RecyclerView(MainActivity.this);
            list.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            list.setClipChildren(false);
            list.setItemViewCacheSize(6);
            list.setOverScrollMode(View.OVER_SCROLL_NEVER);
            channelAdapter = new ChannelSettingsAdapter(count);
            list.setAdapter(channelAdapter);
            page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { channelAdapter.setQuery(s.toString()); }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            channelAdapter.setQuery("");
        }

        private void renderAppSettings(LinearLayout page) {
            TextView hint = label("Select an app to show it on Home. Use arrows to change the order of selected apps.", 14, MUTED, false);
            page.addView(hint, new LinearLayout.LayoutParams(-1, dp(42)));
            appList = new RecyclerView(MainActivity.this);
            appList.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            appList.setClipChildren(false);
            appList.setItemViewCacheSize(6);
            appList.setOverScrollMode(View.OVER_SCROLL_NEVER);
            appAdapter = new AppSettingsAdapter(appList);
            appList.setAdapter(appAdapter);
            page.addView(appList, new LinearLayout.LayoutParams(-1, 0, 1));
            if (availableApps != null) appAdapter.setApps(availableApps);
            else if (!loadingApps) {
                loadingApps = true;
                new Thread(() -> {
                    List<AppEntry> loaded = installedApps();
                    handler.post(() -> {
                        if (settingsScreen != this) return;
                        availableApps = loaded;
                        loadingApps = false;
                        if (section == APPS && appAdapter != null) appAdapter.setApps(loaded);
                    });
                }, "settings-app-discovery").start();
            }
            firstControl = appList;
        }

        private final class ChannelSettingsAdapter extends RecyclerView.Adapter<ChannelSettingsAdapter.Holder> {
            private final List<IptvRepository.Channel> visible = new ArrayList<>();
            private final TextView count;
            private String query = "";

            ChannelSettingsAdapter(TextView count) { this.count = count; }

            void setQuery(String value) {
                query = value.trim().toLowerCase(Locale.getDefault());
                visible.clear();
                for (IptvRepository.Channel channel : channels) {
                    if (query.isEmpty() || channel.name.toLowerCase(Locale.getDefault()).contains(query)) visible.add(channel);
                }
                count.setText(visible.size() + " of " + channels.size() + " channels · "
                        + shownChannelCount() + " shown");
                notifyDataSetChanged();
            }

            private int shownChannelCount() {
                int shown = 0;
                for (IptvRepository.Channel channel : channels) {
                    if (!hiddenChannelKeys.contains(channel.stableKey())) shown++;
                }
                return shown;
            }

            @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(4), dp(18), dp(4));
                styleFocusableTile(row);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(0, 0, 0, dp(7));
                row.setLayoutParams(params);
                LinearLayout text = new LinearLayout(MainActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                TextView name = label("", 16, Color.WHITE, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.addView(name);
                TextView group = label("", 13, MUTED, false);
                group.setSingleLine(true);
                text.addView(group);
                TextView state = label("", 14, LiquidTheme.text(MainActivity.this), true);
                row.addView(state);
                return new Holder(row, name, group, state);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                IptvRepository.Channel channel = visible.get(position);
                holder.name.setText(channel.name);
                holder.group.setText(channel.group.isEmpty() ? "Uncategorized" : channel.group);
                boolean shown = !hiddenChannelKeys.contains(channel.stableKey());
                holder.state.setText(shown ? "Shown" : "Hidden");
                holder.state.setTextColor(shown ? LiquidTheme.text(MainActivity.this)
                        : LiquidTheme.secondaryText(MainActivity.this));
                holder.itemView.setOnClickListener(v -> {
                    String key = channel.stableKey();
                    if (hiddenChannelKeys.contains(key)) hiddenChannelKeys.remove(key);
                    else hiddenChannelKeys.add(key);
                    preferences.edit().putStringSet("hiddenChannels", new HashSet<>(hiddenChannelKeys)).apply();
                    channelsChanged = true;
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
                    count.setText(visible.size() + " of " + channels.size() + " channels · "
                            + shownChannelCount() + " shown");
                });
            }

            @Override public int getItemCount() { return visible.size(); }

            final class Holder extends RecyclerView.ViewHolder {
                final TextView name, group, state;
                Holder(View row, TextView name, TextView group, TextView state) {
                    super(row);
                    this.name = name; this.group = group; this.state = state;
                }
            }
        }

        private final class AppSettingsAdapter extends RecyclerView.Adapter<AppSettingsAdapter.Holder> {
            private final RecyclerView list;
            private final List<AppEntry> entries = new ArrayList<>();
            private final List<AppEntry> rows = new ArrayList<>();
            private final List<String> selectedOrder = new ArrayList<>();
            private boolean initialized;

            AppSettingsAdapter(RecyclerView list) { this.list = list; }

            void setApps(List<AppEntry> apps) {
                entries.clear(); entries.addAll(apps);
                if (!initialized) {
                    selectedOrder.addAll(selectedPackages(apps));
                    if (selectedOrder.isEmpty() && !preferences.getBoolean("customApps", false)) {
                        for (int i = 0; i < Math.min(8, apps.size()); i++) selectedOrder.add(apps.get(i).packageName);
                    }
                    initialized = true;
                }
                rebuild();
            }

            private void rebuild() {
                rows.clear();
                for (String pkg : selectedOrder) {
                    AppEntry entry = findApp(entries, pkg);
                    if (entry != null) rows.add(entry);
                }
                for (AppEntry entry : entries) if (!selectedOrder.contains(entry.packageName)) rows.add(entry);
                notifyDataSetChanged();
            }

            private void persistAndFocus(String pkg, int control) {
                preferences.edit().putBoolean("customApps", true)
                        .putString("selectedApps", String.join("\n", selectedOrder)).apply();
                appsChanged = true;
                rebuild();
                int index = -1;
                for (int i = 0; i < rows.size(); i++) if (rows.get(i).packageName.equals(pkg)) { index = i; break; }
                if (index < 0) return;
                final int target = index;
                list.scrollToPosition(target);
                list.post(() -> {
                    RecyclerView.ViewHolder found = list.findViewHolderForAdapterPosition(target);
                    if (!(found instanceof Holder)) return;
                    Holder holder = (Holder) found;
                    View focus = control == 1 && holder.up.isEnabled() ? holder.up
                            : control == 2 && holder.down.isEnabled() ? holder.down : holder.toggle;
                    focus.requestFocus();
                });
            }

            @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(0, 0, 0, dp(7));
                row.setLayoutParams(params);
                Button toggle = settingsButton("");
                toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                toggle.setPadding(dp(18), 0, dp(12), 0);
                row.addView(toggle, new LinearLayout.LayoutParams(0, dp(60), 1));
                Button up = settingsButton("↑");
                LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                upParams.setMargins(dp(8), 0, 0, 0);
                row.addView(up, upParams);
                Button down = settingsButton("↓");
                LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                downParams.setMargins(dp(8), 0, 0, 0);
                row.addView(down, downParams);
                return new Holder(row, toggle, up, down);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                AppEntry entry = rows.get(position);
                int selected = selectedOrder.indexOf(entry.packageName);
                holder.toggle.setText((selected >= 0 ? "✓  " : "+  ") + entry.label);
                holder.toggle.setTextColor(LiquidTheme.text(MainActivity.this));
                holder.toggle.setOnClickListener(v -> {
                    if (selectedOrder.contains(entry.packageName)) selectedOrder.remove(entry.packageName);
                    else selectedOrder.add(entry.packageName);
                    persistAndFocus(entry.packageName, 0);
                });
                holder.up.setVisibility(selected >= 0 ? View.VISIBLE : View.GONE);
                holder.down.setVisibility(selected >= 0 ? View.VISIBLE : View.GONE);
                holder.up.setEnabled(selected > 0);
                holder.down.setEnabled(selected >= 0 && selected < selectedOrder.size() - 1);
                holder.up.setOnClickListener(v -> {
                    int current = selectedOrder.indexOf(entry.packageName);
                    if (current > 0) { Collections.swap(selectedOrder, current, current - 1); persistAndFocus(entry.packageName, 1); }
                });
                holder.down.setOnClickListener(v -> {
                    int current = selectedOrder.indexOf(entry.packageName);
                    if (current >= 0 && current < selectedOrder.size() - 1) {
                        Collections.swap(selectedOrder, current, current + 1);
                        persistAndFocus(entry.packageName, 2);
                    }
                });
            }

            @Override public int getItemCount() { return rows.size(); }

            final class Holder extends RecyclerView.ViewHolder {
                final Button toggle, up, down;
                Holder(View row, Button toggle, Button up, Button down) {
                    super(row);
                    this.toggle = toggle; this.up = up; this.down = down;
                }
            }
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == PICK_BACKUP_EXPORT) {
                exportBackup(data.getData());
                return;
            }
            if (requestCode == PICK_BACKUP_IMPORT) {
                importBackup(data.getData());
                return;
            }
        }
        if (requestCode == PICK_WALLPAPER_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri folderUri = data.getData();
            int grantFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(folderUri, grantFlags); } catch (Exception ignored) { }
            preferences.edit().putString("wallpaperFolder", folderUri.toString()).remove("wallpaperAlbum").apply();
            loadBackgroundFolder(folderUri, true);
            if (settingsScreen != null) settingsScreen.refreshAppearance();
            return;
        }
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        preferences.edit().putString("wallpaper", uri.toString()).apply();
        applyWallpaper();
        if (settingsScreen != null) settingsScreen.refreshAppearance();
    }

    private void applyWallpaper() {
        if (wallpaperImage == null || heroFrame == null) return;
        String uriValue = preferences.getString("wallpaper", null);
        int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1920);
        int targetHeight = Math.min(dp(heroHeightDp()), 1080);
        new Thread(() -> {
            android.graphics.Bitmap decoded = uriValue == null ? null : decodeWallpaper(Uri.parse(uriValue), targetWidth, targetHeight);
            if (decoded == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                decoded = BitmapFactory.decodeResource(getResources(), R.drawable.default_wallpaper, options);
            }
            final android.graphics.Bitmap bitmap = decoded;
            handler.post(() -> {
                if (isFinishing() || isDestroyed() || !java.util.Objects.equals(uriValue, preferences.getString("wallpaper", null))) return;
                if (bitmap == null) return;
                if (!backgroundPhotos.isEmpty()) {
                    wallpaperImage.setImageBitmap(bitmap);
                    wallpaperImage.setVisibility(View.GONE);
                    return;
                }
                glassBackdrop.prepare(bitmap, false, () -> {
                    wallpaperImage.setImageBitmap(bitmap);
                    wallpaperImage.setVisibility(View.VISIBLE);
                });
            });
        }, "wallpaper-decode").start();
    }

    private android.graphics.Bitmap decodeWallpaper(Uri uri, int targetWidth, int targetHeight) {
        try {
            String key = uri.toString();
            android.graphics.Bitmap cached = backgroundBitmapCache.get(key);
            if (cached != null && !cached.isRecycled()) return cached;
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return decodeRemoteWallpaper(uri, targetWidth, targetHeight);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
            int sample = 1;
            while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                    && sample < (1 << 29)) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                android.graphics.Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                if (bitmap != null) backgroundBitmapCache.put(key, bitmap);
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private android.graphics.Bitmap decodeRemoteWallpaper(Uri uri, int targetWidth, int targetHeight) {
        String key = uri.toString();
        android.graphics.Bitmap cached = backgroundBitmapCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        java.io.File cacheDirectory = new java.io.File(getCacheDir(), "google-photos");
        java.io.File cacheFile = new java.io.File(cacheDirectory, remoteWallpaperCacheName(key));
        if (cacheFile.isFile()) {
            android.graphics.Bitmap diskCached = decodeBackgroundImage(cacheFile, targetWidth, targetHeight);
            if (diskCached != null) {
                cacheFile.setLastModified(System.currentTimeMillis());
                backgroundBitmapCache.put(key, diskCached);
                return diskCached;
            }
            cacheFile.delete();
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(key).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
            try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 12 * 1024 * 1024) return null;
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
                int sample = 1;
                while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                        && sample < (1 << 29)) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap != null) {
                    backgroundBitmapCache.put(key, bitmap);
                    cacheDirectory.mkdirs();
                    java.io.File temporary = new java.io.File(cacheDirectory, cacheFile.getName() + ".tmp");
                    try (java.io.FileOutputStream file = new java.io.FileOutputStream(temporary)) {
                        file.write(bytes);
                        file.flush();
                    }
                    if (cacheFile.exists()) cacheFile.delete();
                    temporary.renameTo(cacheFile);
                    trimGooglePhotosCache(cacheDirectory);
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private android.graphics.Bitmap decodeBackgroundImage(java.io.File file, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
        int sample = 1;
        while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                && sample < (1 << 29)) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private String remoteWallpaperCacheName(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value));
            return key + ".img";
        } catch (Exception ignored) { return Integer.toHexString(url.hashCode()) + ".img"; }
    }

    private void trimGooglePhotosCache(java.io.File directory) {
        java.io.File[] files = directory.listFiles(file -> file.isFile() && !file.getName().endsWith(".tmp"));
        if (files == null || files.length <= 100) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int i = 0; i < files.length - 100; i++) files[i].delete();
    }
}
