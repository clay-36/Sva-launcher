package com.sva.launcher;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.app.NotificationManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.LruCache;
import android.text.InputType;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import android.widget.GridLayout;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends LocalizedActivity {
    private static final int TAB_HOME = 0, TAB_TV = 1, TAB_GAMES = 2, TAB_EVENTS = 3, TAB_SAVED = 4, TAB_NEWS = 5, TAB_ACTIVITIES = 6;
    private final String[] TAB_TITLES = {AppLanguage.get(R.string.ui_home), AppLanguage.get(R.string.ui_live_tv), AppLanguage.get(R.string.ui_games), AppLanguage.get(R.string.ui_events), AppLanguage.get(R.string.ui_saved), AppLanguage.get(R.string.ui_news), AppLanguage.get(R.string.ui_activities)};
    private static final String PREF_LIVE_TV_POSITION = "liveTvNavigationPosition";
    static final String ACTION_WATCH_REMINDER = "com.sva.launcher.WATCH_REMINDER";
    static final String EXTRA_REMINDER_CHANNEL_KEY = "reminderChannelKey";
    static final String EXTRA_REMINDER_STREAM_URL = "reminderStreamUrl";
    private static final int PICK_WALLPAPER = 4107;
    private static final int PICK_WALLPAPER_FOLDER = 4108;
    private static final int PICK_BACKUP_EXPORT = 4109;
    private static final int PICK_BACKUP_IMPORT = 4110;
    private static final int REQUEST_WEATHER_LOCATION = 4111;
    private static final String GOOGLE_PHOTOS_CACHE_URL = "googlePhotosCacheUrl";
    private static final String GOOGLE_PHOTOS_CACHE_IMAGES = "googlePhotosCacheImages";
    private static final String PHOTO_FOLDER_CACHE_URI = "photoFolderCacheUri";
    private static final String PHOTO_FOLDER_CACHE_IMAGES = "photoFolderCacheImages";
    private static final int CALENDAR_CACHE_VERSION = 5;
    private static final int PANEL = 0xF21B1B1B;
    private static final int PANEL_FOCUSED = 0xFF2A2A2A;
    private static final int MUTED = 0xFFB7B7B7;
    private static final int ACCENT = 0xFF7BE7F4;
    private static final int LAVENDER = 0xFFB7B7B7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LifecycleWork lifecycleWork = new LifecycleWork(this, handler);
    private final List<IptvRepository.Channel> channels = new ArrayList<>();
    private final Set<String> hiddenChannelKeys = new HashSet<>();
    private final Set<String> favoriteChannelKeys = new HashSet<>();
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
    private final ExecutorService ioWorker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "launcher-io");
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
    private LinearLayout root, homeContent, liveContent, gamesContent, eventsContent, savedContent, newsContent, activitiesContent;
    private ActivitiesPanel activitiesPanel;
    private LinearLayout appRow, youtubeSection;
    private RecyclerView youtubeRow;
    private SavedCardAdapter savedCardAdapter;
    private String renderedSavedSource;
    private boolean retrySavedArtwork;
    private android.os.Parcelable savedListState, gamesListState;
    private RecyclerView gameGrid;
    private GameCardAdapter gameAdapter;
    private int gameLoadGeneration, gameFocusGeneration;
    private int pendingGameFocus = RecyclerView.NO_POSITION;
    private GridLayout calendarMonthGrid;
    private String parsedCalendarSource, renderedCalendarSource;
    private List<PublicCalendarRepository.Event> parsedCalendarEvents = Collections.emptyList();
    private String liveStatus;
    private boolean liveStatusVisible = true;
    private TextView calendarHeaderDate, calendarHeaderEvents;
    private Button previousMonthButton, nextMonthButton;
    private TextView gamesEmpty, youtubeEmpty, todayEmpty, monthTitle;
    private TextView calendarDetailDate, calendarDetailDay, calendarDetailMoon;
    private TextView calendarDetailMoonName, calendarDetailIllumination, calendarDetailEvent;
    private LinearLayout todayHeading, todayItems;
    private LinearLayout calendarDetailsPanel;
    private YearMonth displayedMonth = YearMonth.now();
    private LocalDate selectedCalendarDay = LocalDate.now();
    private YearMonth renderedMonth;
    private LocalDate renderedToday;
    private long renderedCalendarStamp = -1L;
    private final View[] tabPages = new View[TAB_TITLES.length];
    private final Button[] tabButtons = new Button[TAB_TITLES.length];
    private final View[] tabFocus = new View[TAB_TITLES.length];
    private final View[] tabEntryFocus = new View[TAB_TITLES.length];
    private final int[] tabScroll = new int[TAB_TITLES.length];
    private final NavigationGlyph[] navigationGlyphs = new NavigationGlyph[TAB_TITLES.length];
    private int activeTab = TAB_HOME;
    private boolean tabsInitialized;
    private boolean switchingTabs;
    private boolean compactNavigation;
    private LinearLayout tabRow;
    private LinearLayout systemShortcuts;
    private View browsingShade;
    private FrameLayout heroFrame;
    private final GlassBackdrop glassBackdrop = new GlassBackdrop();
    private FrameLayout homePage;
    private boolean initialAppFocus;
    private String focusedPackage;
    private ImageView wallpaperImage, slideA, slideB;
    private TextView clock, clockPeriod, date, status;
    private LinearLayout weatherPanel;
    private TextView weatherTemperature, weatherCondition, weatherDetails, weatherLocation, weatherMetrics;
    private TextView weatherSymbol, homeCalendarEyebrow, homeCalendarTitle, homeCalendarSubtitle;
    private LinearLayout timeCard, weatherCard, homeCalendarCard;
    private View clockDivider;
    private View timeRule, eventRule, calendarDivider;
    private TextView calendarIcon;
    private NavigationGlyph calendarEventGlyph;
    private LinearLayout widgetHeader;
    private Animator currentWidgetThemeAnimator;
    private HeaderWidgetThemes currentWidgetThemes;
    private final LruCache<String, HeaderWidgetThemes> widgetThemeCache = new LruCache<>(12);
    private LinearLayout nowPlayingSection, nowPlayingCard;
    private Button musicToggle;
    private ImageView musicArtwork;
    private TextView musicTitle, musicArtist;
    private ProgressBar musicProgress;
    private MediaController activeMediaController;
    private Runnable mediaTick;
    private LinearLayout channelRows, channelCategoryTabs;
    private HorizontalScrollView channelCategoryScroll;
    private TextView channelsEmpty;
    private LinearLayout guideSection;
    private TextView guideEmpty;
    private Button guideToggle;
    private String pendingReminderChannelKey, pendingReminderStreamUrl;
    private final LinkedHashMap<String, ChannelRow> channelRowMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, Button> channelTabMap = new LinkedHashMap<>();
    private List<String> renderedCategories = new ArrayList<>();
    private String selectedChannelCategory;
    private Runnable clockTick;
    private Runnable guideRefreshTick;
    private Runnable calendarRefreshTick;
    private Runnable googlePhotosRefreshTick;
    private Runnable weatherRefreshTick;
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
    private boolean appsRefreshPending;
    private boolean packageReceiverRegistered;
    private final Runnable refreshInstalledApps = () -> {
        appsLoaded = false;
        if (screenActive && this.settingsScreen == null) renderApps();
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Replacement installs send remove/add/replaced in quick succession.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            handler.removeCallbacks(refreshInstalledApps);
            handler.postDelayed(refreshInstalledApps, 200);
        }
    };
    private TaskProgress liveProgress, weatherProgress, homeCalendarProgress, eventsProgress;
    private boolean exportingBackup;
    private boolean albumLoading;
    private String loadingAlbumUrl = "";
    private boolean channelsRefreshing;
    private boolean guideRefreshing;
    private boolean calendarRefreshing;
    private String calendarRefreshingUrl = "";
    private int calendarRequestGeneration;
    private WeatherRepository.Forecast weatherForecast;
    private int weatherRequestGeneration;
    private boolean weatherRefreshing, weatherRefreshedThisLaunch, pendingDeviceWeatherLocation;
    private LocationListener weatherLocationListener;
    private CancellationSignal weatherLocationCancellation;
    private Runnable weatherLocationTimeout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::handleBack);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, packages, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageReceiver, packages);
        packageReceiverRegistered = true;
        if (state != null) {
            focusedPackage = state.getString("focusedPackage");
            savedListState = state.getParcelable("savedListState");
            gamesListState = state.getParcelable("gamesListState");
            activeTab = Math.max(TAB_HOME, Math.min(TAB_ACTIVITIES, state.getInt("activeTab", TAB_HOME)));
            for (int i = 0; i < tabScroll.length; i++) tabScroll[i] = state.getInt("tabScroll" + i, 0);
            tabScroll[TAB_HOME] = 0;
            try { selectedCalendarDay = LocalDate.parse(state.getString("selectedCalendarDay", LocalDate.now().toString())); }
            catch (Exception ignored) { selectedCalendarDay = LocalDate.now(); }
            displayedMonth = YearMonth.from(selectedCalendarDay);
        }
        iptv = new IptvRepository(this);
        acceptReminderIntent(getIntent());
        logoLoader = new ChannelLogoLoader(this);
        weatherForecast = cachedWeather();
        hiddenChannelKeys.addAll(HiddenChannels.load(preferences));
        favoriteChannelKeys.addAll(FavoriteChannels.load(preferences));
        String folder = preferences.getString("wallpaperFolder", "");
        String album = preferences.getString("wallpaperAlbum", "");
        if (!folder.isEmpty()) backgroundPhotos.addAll(cachedBackgroundFolder(folder));
        else if (!album.isEmpty()) backgroundPhotos.addAll(cachedBackgroundAlbum(album));
        buildHome();
        renderChannels();
        loadCachedChannels();
        if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
        else if (!album.isEmpty()) maybeRefreshGooglePhotos(true);
        updateClock();
        clockTick = new Runnable() {
            @Override public void run() {
                if (!screenActive) return;
                if (activeTab == TAB_HOME) updateClock();
                if (activeTab == TAB_TV) refreshCachedProgrammeDisplay();
                if (activeTab == TAB_HOME || activeTab == TAB_EVENTS) updateCalendarEventDisplay();
                handler.postDelayed(this, 30000);
            }
        };
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
        weatherRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshWeather(false);
                scheduleWeatherRefresh();
            }
        };
        if (state != null && state.getBoolean("restoreSettings", false)) {
            settingsScreen = new SettingsScreen();
            settingsScreen.section = Math.max(0, Math.min(SettingsScreen.LANGUAGE, state.getInt("restoreSettingsSection", SettingsScreen.APPEARANCE)));
            settingsScreen.restoreDrafts(state);
            settingsScreen.show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (settingsScreen != null) {
            outState.putBoolean("restoreSettings", true);
            outState.putInt("restoreSettingsSection", settingsScreen.section);
            settingsScreen.saveDrafts(outState);
        }
        View selectedApp = appRow == null ? null : appRow.findFocus();
        if (selectedApp != null && selectedApp.getTag() instanceof String) focusedPackage = (String) selectedApp.getTag();
        outState.putString("focusedPackage", focusedPackage);
        outState.putParcelable("savedListState", youtubeRow == null ? savedListState : youtubeRow.getLayoutManager().onSaveInstanceState());
        outState.putParcelable("gamesListState", gameGrid == null ? gamesListState : gameGrid.getLayoutManager().onSaveInstanceState());
        outState.putInt("activeTab", activeTab);
        for (int i = 0; i < tabPages.length; i++)
            outState.putInt("tabScroll" + i, i == TAB_HOME ? 0
                    : tabPages[i] == null ? tabScroll[i] : tabPages[i].getScrollY());
        outState.putString("selectedCalendarDay", selectedCalendarDay.toString());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptReminderIntent(intent);
        if (!ACTION_WATCH_REMINDER.equals(intent.getAction())) selectTab(TAB_HOME, true);
        if (!channels.isEmpty()) openPendingReminder();
    }

    // Physical TV remotes and Android <33 still dispatch the legacy callback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }

    @android.annotation.SuppressLint("GestureBackNavigation") // Default task behavior after our custom Back handling.
    private void handleBack() {
        if (settingsScreen != null) { settingsScreen.requestClose(); return; }
        if (activeTab != TAB_HOME) { selectTab(TAB_HOME, true); return; }
        super.onBackPressed();
    }

    private void acceptReminderIntent(Intent intent) {
        if (intent == null || !ACTION_WATCH_REMINDER.equals(intent.getAction())) return;
        pendingReminderChannelKey = intent.getStringExtra(EXTRA_REMINDER_CHANNEL_KEY);
        pendingReminderStreamUrl = intent.getStringExtra(EXTRA_REMINDER_STREAM_URL);
    }

    private void openPendingReminder() {
        if (pendingReminderChannelKey == null) return;
        String key = pendingReminderChannelKey, url = pendingReminderStreamUrl;
        pendingReminderChannelKey = pendingReminderStreamUrl = null;
        IptvRepository.Channel fallback = null;
        int matches = 0;
        for (IptvRepository.Channel channel : channels) {
            if (!channel.stableKey().equals(key) || HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            if (channel.streamUrl.equals(url)) { playChannel(channel); return; }
            fallback = channel;
            matches++;
        }
        if (matches == 1) playChannel(fallback);
        else Toast.makeText(this, AppLanguage.get(R.string.ui_that_reminder_channel_is_no_longer_available), Toast.LENGTH_LONG).show();
    }

    private void openGameLibrary() {
        Intent intent = new Intent(this, GameLibraryActivity.class);
        ImageView visible = null;
        for (ImageView slide : new ImageView[]{slideA, slideB}) {
            if (slide != null && slide.getVisibility() == View.VISIBLE && slide.getDrawable() != null
                    && (visible == null || slide.getAlpha() > visible.getAlpha())) visible = slide;
        }
        if (visible != null && visible.getTag() instanceof Uri) {
            Uri uri = (Uri) visible.getTag();
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                java.io.File cached = new java.io.File(new java.io.File(getCacheDir(), "google-photos"),
                        remoteWallpaperCacheName(uri.toString()));
                if (cached.isFile()) intent.putExtra(GameLibraryActivity.EXTRA_BACKGROUND_URI, Uri.fromFile(cached).toString());
            } else {
                intent.putExtra(GameLibraryActivity.EXTRA_BACKGROUND_URI, uri.toString());
            }
        }
        startActivity(intent);
    }

    private void openWallpaperFolderPicker() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_IMAGE_FOLDER), PICK_WALLPAPER_FOLDER);
    }

    private void openBackupExportPicker() {
        if (exportingBackup || restoringBackup) return;
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_SAVE_JSON), PICK_BACKUP_EXPORT);
    }

    private void openBackupImportPicker() {
        if (exportingBackup || restoringBackup) return;
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_JSON), PICK_BACKUP_IMPORT);
    }

    private void exportBackup(Uri destination) {
        if (exportingBackup || restoringBackup) return;
        exportingBackup = true;
        updateTaskProgress();
        Thread worker = new Thread(() -> {
            String error = null;
            try {
                AppLanguage.sync(MainActivity.this);
                JSONObject backup = LauncherBackup.create(preferences,
                        getSharedPreferences("iptv", MODE_PRIVATE), iptv);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IllegalStateException(AppLanguage.get(R.string.ui_could_not_write_to_that_file));
                    output.write(backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? AppLanguage.get(R.string.ui_could_not_create_the_backup) : e.getMessage();
            }
            String result = error;
            lifecycleWork.post(() -> {
                exportingBackup = false;
                updateTaskProgress();
                Toast.makeText(this,
                        result == null ? AppLanguage.get(R.string.ui_backup_saved) : AppLanguage.get(R.string.ui_backup_failed_1_s, result),
                        result == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            });
        }, "launcher-backup-export");
        worker.setDaemon(true);
        worker.start();
    }

    private void importBackup(Uri source) {
        if (exportingBackup || restoringBackup) return;
        restoringBackup = true;
        if (settingsScreen != null) settingsScreen.renderSection();
        updateTaskProgress();
        Thread worker = new Thread(() -> {
            String error = null;
            try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalStateException(AppLanguage.get(R.string.ui_could_not_read_that_file));
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                final int maxBytes = 64 * 1024 * 1024;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalArgumentException(AppLanguage.get(R.string.ui_backup_file_is_larger_than_64_mb));
                    bytes.write(buffer, 0, count);
                }
                JSONObject backup = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                LauncherBackup.restore(backup, preferences, getSharedPreferences("iptv", MODE_PRIVATE), iptv);

            } catch (Exception e) {
                error = e.getMessage() == null ? AppLanguage.get(R.string.ui_could_not_restore_that_backup) : e.getMessage();
            }
            String result = error;
            lifecycleWork.post(() -> {
                restoringBackup = false;
                updateTaskProgress();
                if (settingsScreen != null) settingsScreen.renderSection();
                if (result != null) {
                    Toast.makeText(this, AppLanguage.get(R.string.ui_restore_failed_1_s, result), Toast.LENGTH_LONG).show();
                    return;
                }
                AppLanguage.restored(MainActivity.this);
                hiddenChannelKeys.clear();
                hiddenChannelKeys.addAll(HiddenChannels.load(preferences));
                favoriteChannelKeys.clear();
                favoriteChannelKeys.addAll(FavoriteChannels.load(preferences));
                renderChannels();
                if (!channels.isEmpty()) {
                    ((SvaApplication) getApplication()).guideReminders().reconcile(channels, hiddenChannelKeys);
                }
                if (settingsScreen != null) {
                    settingsScreen.resetDrafts();
                    settingsScreen.channelsChanged = true;
                    if (settingsScreen.channelAdapter != null) {
                        settingsScreen.channelAdapter.refresh();
                    }
                }
                Toast.makeText(this, AppLanguage.get(R.string.ui_backup_restored), Toast.LENGTH_SHORT).show();
                recreate();
            });
        }, "launcher-backup-import");
        worker.setDaemon(true);
        worker.start();
    }

    private final Runnable screensaverIdle = this::startScreensaverIfIdle;

    private void startScreensaverIfIdle() {
        boolean playing = activeMediaController != null && activeMediaController.getPlaybackState() != null
                && activeMediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        if (screenActive && settingsScreen == null && hasWindowFocus() && !playing) {
            startActivity(new Intent(this, ScreensaverPreviewActivity.class));
        } else if (screenActive) scheduleScreensaverIdle();
    }

    private void scheduleScreensaverIdle() {
        handler.removeCallbacks(screensaverIdle);
        if (preferences == null || !screenActive) return;
        int minutes = ScreensaverPreferences.idleMinutes(preferences.getInt(ScreensaverPreferences.IDLE, 0));
        if (minutes > 0) handler.postDelayed(screensaverIdle, minutes * 60_000L);
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        scheduleScreensaverIdle();
    }

    @Override protected void onResume() {
        super.onResume();
        screenActive = true;
        updateClock();
        updateCalendarEventDisplay();
        handler.removeCallbacks(clockTick);
        handler.postDelayed(clockTick, 30000);
        Set<String> freshHidden = HiddenChannels.load(preferences);
        Set<String> freshFavorites = FavoriteChannels.load(preferences);
        if (!freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
            hiddenChannelKeys.clear();
            hiddenChannelKeys.addAll(freshHidden);
            favoriteChannelKeys.clear();
            favoriteChannelKeys.addAll(freshFavorites);
            renderChannels();
        }
        scheduleScreensaverIdle();
        maybeRefreshLiveData();
        scheduleLiveRefreshCheck();
        maybeRefreshPublicCalendar(false);
        scheduleCalendarRefresh();
        scheduleGooglePhotosRefresh();
        maybeRefreshWeather(false);
        scheduleWeatherRefresh();
        if (settingsScreen != null) settingsScreen.refreshLauncherOverride();
        updateNowPlaying();
        if (mediaTick == null) mediaTick = new Runnable() {
            @Override public void run() {
                if (!screenActive) return;
                updateNowPlaying();
                handler.postDelayed(this, activeMediaController != null ? 1000 : 10000);
            }
        };
        handler.removeCallbacks(mediaTick);
        handler.postDelayed(mediaTick, activeMediaController != null ? 1000 : 10000);
        if (root != null) renderApps();
        if (gameGrid != null) renderGames();
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
        if (clockTick != null) handler.removeCallbacks(clockTick);
        handler.removeCallbacks(screensaverIdle);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        if (weatherRefreshTick != null) handler.removeCallbacks(weatherRefreshTick);
        if (mediaTick != null) handler.removeCallbacks(mediaTick);
        cancelWeatherLocation();
        backgroundImageGeneration++;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageReceiver);
            packageReceiverRegistered = false;
        }
        lifecycleWork.close();
        if (activitiesPanel != null) activitiesPanel.close();
        if (clockTick != null) handler.removeCallbacks(clockTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        if (weatherRefreshTick != null) handler.removeCallbacks(weatherRefreshTick);
        if (mediaTick != null) handler.removeCallbacks(mediaTick);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        handler.removeCallbacksAndMessages(null);
        calendarRequestGeneration++;
        backgroundFolderGeneration++;
        backgroundImageGeneration++;
        weatherRequestGeneration++;
        cancelWeatherLocation();
        youtubeWorker.shutdownNow();
        calendarWorker.shutdownNow();
        ioWorker.shutdownNow();
        if (logoLoader != null) logoLoader.close();
        youtubeThumbnailCache.evictAll();
        backgroundBitmapCache.evictAll();
        glassBackdrop.close();
        Motion.cancelTree(getWindow().getDecorView());
        if (settingsScreen != null && settingsScreen.dialog != null && settingsScreen.dialog.isShowing()) {
            settingsScreen.dialog.dismiss();
        }
        super.onDestroy();
    }

    private void loadCachedChannels() {
        lifecycleWork.execute(ioWorker, () -> {
            List<IptvRepository.Channel> cached = iptv.cachedChannels();
            lifecycleWork.post(() -> {
                if (isFinishing() || isDestroyed() || !channels.isEmpty()) return;
                channels.addAll(cached);
                renderChannels();
                if (!cached.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                        .reconcile(channels, hiddenChannelKeys);
                openPendingReminder();
                if (!cached.isEmpty()) setLiveStatusVisible(false);
                scheduleLiveRefreshCheck();
                if (settingsScreen != null && settingsScreen.channelAdapter != null) {
                    settingsScreen.channelAdapter.refresh();
                }
            });
        });
    }

    private void buildHome() {
        boolean light = LiquidTheme.isLight(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        homePage = page;
        FrameLayout scene = new FrameLayout(this);
        setContentView(scene);

        heroFrame = new FrameLayout(this);
        heroFrame.setBackgroundColor(light ? LiquidTheme.background(this) : 0xFF071417);
        wallpaperImage = backgroundImage();
        wallpaperImage.setImageResource(R.drawable.mountain_wallpaper);
        wallpaperImage.setVisibility(View.VISIBLE);
        slideA = backgroundImage();
        slideB = backgroundImage();
        heroFrame.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideA, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideB, new FrameLayout.LayoutParams(-1, -1));
        showCachedBackgroundImmediately();
        GradientDrawable shadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0x00000000, 0x00000000, 0x00000000, 0x00000000}
                      : new int[]{0x33000000, 0x09000000, 0x23000000, 0x9B031013});
        View shade = new View(this);
        shade.setBackground(shadow);
        heroFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View sideShade = new View(this);
        sideShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                light ? new int[]{0x00000000, 0x00000000, 0x00000000}
                      : new int[]{0xB5000D10, 0x44000D10, 0x00000000}));
        heroFrame.addView(sideShade, new FrameLayout.LayoutParams(-1, -1));
        View bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0x00000000, 0x00000000, 0x00000000}
                      : new int[]{0x00030C0E, 0x18030C0E, 0xE5030C0E}));
        heroFrame.addView(bottomFade, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(heroFrame, new FrameLayout.LayoutParams(-1, -1));
        browsingShade = new View(this);
        browsingShade.setBackgroundColor(light ? 0xFFE9F1EE : 0xFF071417);
        browsingShade.setAlpha(0f);
        scene.addView(browsingShade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout tabShell = new LinearLayout(this);
        tabShell.setOrientation(LinearLayout.VERTICAL);
        tabShell.setClipChildren(false);
        tabShell.setClipToPadding(false);
        scene.addView(tabShell, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        compactNavigation = screenWidthDp < 800;
        int outerPadding = compactNavigation ? 16 : 33;
        int dockHeight = 96;
        navigation.setPadding(dp(outerPadding), dp(16), dp(outerPadding), dp(22));
        tabShell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(dockHeight)));

        navigation.setClipChildren(false);
        navigation.setClipToPadding(false);
        LinearLayout navbar = new LinearLayout(this);
        navbar.setGravity(Gravity.CENTER_VERTICAL);
        navbar.setClipChildren(false);
        navbar.setClipToPadding(false);
        navbar.setPadding(dp(10), 0, dp(14), 0);
        navbar.setBackground(roundedShape(light ? 0xE9F9FCF8 : 0xD508181A,
                light ? 0x887C9E9A : 0x55C4D9D2, 1, 24));
        navbar.setElevation(dp(6));
        navigation.addView(navbar, new LinearLayout.LayoutParams(-2, dp(58)));

        int weatherMaxWidth = compactNavigation ? 165 : 195;
        HorizontalScrollView tabScroller = new HorizontalScrollView(this);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabScroller.setFillViewport(false);
        tabScroller.setClipChildren(true);
        tabScroller.setClipToPadding(true);
        int maxTabWidth = dp(Math.max(280, screenWidthDp - outerPadding * 2 - 220));
        LinearLayout.LayoutParams tabGroupParams = new LinearLayout.LayoutParams(maxTabWidth, dp(52));
        navbar.addView(tabScroller, tabGroupParams);
        tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setClipChildren(false);
        tabRow.setClipToPadding(false);
        tabRow.setPadding(dp(4), dp(3), dp(4), dp(3));
        tabScroller.addView(tabRow, new HorizontalScrollView.LayoutParams(-2, -1));
        for (int i = 0; i < TAB_TITLES.length; i++) {
            final int tab = i;
            Button button = actionButton(TAB_TITLES[i]);
            button.setId(View.generateViewId());
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setTextSize(14);
            int horizontalButtonPadding = compactNavigation ? 12 : (i == TAB_HOME ? 18 : 10);
            button.setPadding(dp(horizontalButtonPadding), 0, dp(horizontalButtonPadding), 0);
            NavigationGlyph glyph = new NavigationGlyph(i);
            navigationGlyphs[i] = glyph;
            glyph.setBounds(0, 0, dp(21), dp(21));
            button.setCompoundDrawablesRelative(glyph, null, null, null);
            button.setCompoundDrawablePadding(dp(7));
            GradientDrawable selectedPill = new GradientDrawable();
            selectedPill.setColor(LiquidTheme.accent(this));
            selectedPill.setCornerRadius(dp(24));
            GradientDrawable focusedPill = new GradientDrawable();
            focusedPill.setColor(LiquidTheme.accent(this));
            focusedPill.setCornerRadius(dp(24));
            focusedPill.setStroke(dp(2), light ? 0x66000000 : 0x66FFFFFF);
            StateListDrawable tabStates = new Motion.States();
            tabStates.addState(new int[]{android.R.attr.state_focused}, focusedPill);
            tabStates.addState(new int[]{android.R.attr.state_selected}, selectedPill);
            tabStates.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            button.setBackground(tabStates);
            button.setSelected(i == activeTab);
            button.setTypeface(LiquidTheme.font(this));
            int selectedTextColor = light ? Color.WHITE : 0xFF071417;
            int unselectedTextColor = light ? 0xFF24403D : 0xD4FFFFFF;
            button.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                    new int[]{selectedTextColor, unselectedTextColor}));
            button.setOnClickListener(v -> selectTab(tab, false));
            button.setOnFocusChangeListener((view, focused) -> {
                LiquidTheme.animateContainedFocus(view, focused);
                if (focused) selectTab(tab, false);
            });
            tabButtons[i] = button;
        }
        rebuildNavigationButtons();
        int tabContentWidth = tabRow.getPaddingLeft() + tabRow.getPaddingRight();
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            Button tab = (Button) tabRow.getChildAt(i);
            android.graphics.drawable.Drawable icon = tab.getCompoundDrawablesRelative()[0];
            int iconWidth = icon == null ? 0 : Math.max(icon.getIntrinsicWidth(), icon.getBounds().width());
            int textWidth = Math.round(tab.getPaint().measureText(tab.getText().toString()));
            tabContentWidth += tab.getPaddingLeft() + textWidth + tab.getPaddingRight();
            if (iconWidth > 0) tabContentWidth += iconWidth + tab.getCompoundDrawablePadding();
        }
        tabRow.setLayoutParams(new HorizontalScrollView.LayoutParams(tabContentWidth, -1));
        tabGroupParams.width = Math.min(maxTabWidth, tabContentWidth);
        tabScroller.setLayoutParams(tabGroupParams);
        LinearLayout timeStack = new LinearLayout(this);
        timeStack.setOrientation(LinearLayout.VERTICAL);
        timeStack.setGravity(Gravity.CENTER_VERTICAL);
        timeStack.setPadding(0, 0, 0, 0);
        clock = new TextView(this);
        clock.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        // Keep the clock deliberately distinct from the compact Manrope UI copy.
        // The wider medium face remains easy to read from across the room.
        clock.setTextSize(compactNavigation ? 40 : 46);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setLetterSpacing(.015f);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setGravity(Gravity.CENTER_VERTICAL);
        timeLine.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        clockDivider = new View(this);
        clockDivider.setBackgroundColor(light ? LiquidTheme.border(this) : 0x667E8588);
        LinearLayout.LayoutParams clockDividerParams = new LinearLayout.LayoutParams(dp(1), dp(32));
        clockDividerParams.setMargins(dp(10), 0, dp(10), 0);
        clockDivider.setVisibility(View.GONE);
        clockPeriod = label("", compactNavigation ? 16 : 20, light ? LiquidTheme.secondaryText(this) : 0xFFBEC4CD, false);
        clockPeriod.setPadding(dp(10), dp(18), 0, 0);
        timeLine.addView(clockPeriod, new LinearLayout.LayoutParams(-2, -2));
        timeStack.addView(timeLine, new LinearLayout.LayoutParams(-2, -2));
        date = label("", compactNavigation ? 11 : 13, light ? LiquidTheme.secondaryText(this) : 0xFFF5F7F7, false);
        date.setTypeface(LiquidTheme.font(this));
        date.setLetterSpacing("en".equals(AppLanguage.locale().getLanguage()) ? .16f : 0f);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
        dateParams.topMargin = dp(2);
        timeStack.addView(date, 0, dateParams);

        weatherPanel = new LinearLayout(this);
        weatherPanel.setOrientation(LinearLayout.HORIZONTAL);
        weatherPanel.setGravity(Gravity.CENTER_VERTICAL);
        weatherPanel.setFocusable(false);
        weatherPanel.setClickable(false);
        weatherPanel.setClipChildren(false);
        weatherPanel.setClipToPadding(false);
        weatherPanel.setPadding(0, 0, 0, 0);
        weatherPanel.setContentDescription(AppLanguage.get(R.string.ui_weather_unavailable));
        weatherSymbol = label("☀", compactNavigation ? 36 : 42, 0xFFFFBD38, true);
        weatherSymbol.setGravity(Gravity.CENTER);
        weatherPanel.addView(weatherSymbol, new LinearLayout.LayoutParams(dp(60), -1));
        LinearLayout weatherCopy = new LinearLayout(this);
        weatherCopy.setOrientation(LinearLayout.VERTICAL);
        weatherCopy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams weatherCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        weatherCopyParams.setMarginStart(dp(6));
        weatherPanel.addView(weatherCopy, weatherCopyParams);
        weatherTemperature = label(AppLanguage.get(R.string.ui_add_weather), 16, light ? LiquidTheme.text(this) : Color.WHITE, true);
        weatherTemperature.setTypeface(Typeface.create(LiquidTheme.font(this), Typeface.BOLD));
        weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        weatherTemperature.setIncludeFontPadding(false);
        weatherTemperature.setSingleLine(true);
        weatherTemperature.setEllipsize(TextUtils.TruncateAt.END);
        weatherTemperature.setMaxWidth(dp(weatherMaxWidth));
        LinearLayout weatherHeading = new LinearLayout(this);
        weatherHeading.setGravity(Gravity.CENTER_VERTICAL);
        weatherHeading.addView(weatherTemperature, new LinearLayout.LayoutParams(0, -2, 1));
        weatherProgress = new TaskProgress(this, true);
        weatherHeading.addView(weatherProgress);
        weatherCopy.addView(weatherHeading, new LinearLayout.LayoutParams(-1, -2));
        weatherCondition = label(AppLanguage.get(R.string.ui_local_conditions), 13, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherCondition.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        weatherCondition.setIncludeFontPadding(false);
        weatherCondition.setSingleLine(true);
        weatherCondition.setMaxWidth(dp(weatherMaxWidth));
        weatherCondition.setEllipsize(TextUtils.TruncateAt.END);
        weatherCopy.addView(weatherCondition);
        LinearLayout weatherFacts = new LinearLayout(this);
        weatherFacts.setOrientation(LinearLayout.VERTICAL);
        weatherFacts.setGravity(Gravity.CENTER_VERTICAL);
        weatherFacts.setPadding(dp(16), 0, 0, 0);
        weatherDetails = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherLocation = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherLocation.setSingleLine(true);
        weatherLocation.setEllipsize(TextUtils.TruncateAt.END);
        weatherMetrics = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFF0F1F3, false);
        weatherFacts.addView(weatherDetails);
        weatherFacts.addView(weatherLocation);
        weatherFacts.addView(weatherMetrics);
        weatherPanel.addView(weatherFacts, new LinearLayout.LayoutParams(0, -1, 1.15f));

        systemShortcuts = new LinearLayout(this);
        systemShortcuts.setGravity(Gravity.CENTER_VERTICAL);
        systemShortcuts.setClipChildren(false);
        systemShortcuts.setClipToPadding(false);
        systemShortcuts.setPadding(dp(3), dp(3), dp(3), dp(3));
        navbar.addView(systemShortcuts, new LinearLayout.LayoutParams(-2, dp(52)));
        addTopIcon(systemShortcuts, AppLanguage.get(R.string.ui_network), R.drawable.ic_network, this::openNetworkSettings);
        addTopIcon(systemShortcuts, AppLanguage.get(R.string.ui_tv_input), R.drawable.ic_tv_input, this::openTvInputs);
        addTopIcon(systemShortcuts, AppLanguage.get(R.string.ui_android_settings), R.drawable.ic_android_settings, this::openAndroidSettings);
        addTopIcon(systemShortcuts, AppLanguage.get(R.string.ui_launcher_settings), R.drawable.ic_launcher_settings, this::showSettings);
        FrameLayout viewport = new FrameLayout(this);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        tabShell.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        tabPages[TAB_HOME] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        liveContent = addTabContentPage(viewport, TAB_TV);
        gamesContent = addTabContentPage(viewport, TAB_GAMES);
        eventsContent = addTabContentPage(viewport, TAB_EVENTS);
        savedContent = addTabContentPage(viewport, TAB_SAVED);
        newsContent = addTabContentPage(viewport, TAB_NEWS);
        activitiesContent = addTabContentPage(viewport, TAB_ACTIVITIES);
        tabShell.removeView(navigation);
        tabShell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(dockHeight)));
        FrameLayout heroContent = new FrameLayout(this);
        root.addView(heroContent, new LinearLayout.LayoutParams(-1,
                dp(Math.max(180, heroHeightDp() - dockHeight - 128))));
        renderWeather();

        int topCardHeight = dp(compactNavigation ? 78 : 82);
        int weatherWidth = dp(compactNavigation ? 240 : 270);

        // Top-Left: Time
        timeCard = new LinearLayout(this);
        timeCard.setId(View.generateViewId());
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setGravity(Gravity.CENTER_VERTICAL);
        timeCard.setPadding(dp(16), dp(2), dp(16), dp(2));
        timeCard.setElevation(0);
        timeCard.setFocusable(false);
        timeCard.setClickable(false);
        timeCard.addView(timeStack, new LinearLayout.LayoutParams(-1, -2));

        // Top-Center: Event
        homeCalendarCard = new LinearLayout(this);
        homeCalendarCard.setId(View.generateViewId());
        homeCalendarCard.setGravity(Gravity.CENTER_VERTICAL);
        homeCalendarCard.setPadding(dp(12), dp(6), dp(12), dp(6));
        homeCalendarCard.setElevation(0);
        calendarIcon = label("", 26, light ? LiquidTheme.accent(this) : 0xFF81B8F2, true);
        calendarEventGlyph = new NavigationGlyph(TAB_EVENTS);
        calendarEventGlyph.setBounds(0, 0, dp(27), dp(27));
        calendarEventGlyph.setGlyphColor(light ? LiquidTheme.accent(this) : 0xFF81B8F2);
        calendarIcon.setCompoundDrawables(null, calendarEventGlyph, null, null);
        calendarIcon.setGravity(Gravity.CENTER);
        homeCalendarCard.addView(calendarIcon, new LinearLayout.LayoutParams(dp(44), dp(46)));
        calendarDivider = new View(this);
        calendarDivider.setBackgroundColor(light ? LiquidTheme.border(this) : 0x558FADB2);
        LinearLayout.LayoutParams calendarDividerParams = new LinearLayout.LayoutParams(dp(1), dp(36));
        calendarDividerParams.setMargins(dp(6), 0, dp(10), 0);
        homeCalendarCard.addView(calendarDivider, calendarDividerParams);

        LinearLayout calendarCopy = new LinearLayout(this);
        calendarCopy.setOrientation(LinearLayout.VERTICAL);
        calendarCopy.setGravity(Gravity.CENTER_VERTICAL);
        homeCalendarCard.addView(calendarCopy, new LinearLayout.LayoutParams(0, -2, 1));
        homeCalendarEyebrow = label(AppLanguage.get(R.string.ui_up_next), 10, light ? LiquidTheme.secondaryText(this) : 0xFFBEC4CD, false);
        LinearLayout calendarHeading = new LinearLayout(this);
        calendarHeading.setGravity(Gravity.CENTER_VERTICAL);
        calendarHeading.addView(homeCalendarEyebrow, new LinearLayout.LayoutParams(0, -2, 1));
        homeCalendarProgress = new TaskProgress(this, true);
        calendarHeading.addView(homeCalendarProgress);
        calendarCopy.addView(calendarHeading, new LinearLayout.LayoutParams(-1, -2));
        homeCalendarTitle = label(AppLanguage.get(R.string.ui_no_events_today), 16, light ? LiquidTheme.text(this) : Color.WHITE, true);
        homeCalendarTitle.setSingleLine(true);
        homeCalendarTitle.setMaxLines(1);
        homeCalendarTitle.setEllipsize(TextUtils.TruncateAt.END);
        calendarCopy.addView(homeCalendarTitle);
        homeCalendarSubtitle = label(AppLanguage.get(R.string.ui_open_your_calendar), 11, light ? LiquidTheme.secondaryText(this) : 0xFFBCCBD0, false);
        homeCalendarSubtitle.setSingleLine(true);
        homeCalendarSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        calendarCopy.addView(homeCalendarSubtitle);
        homeCalendarCard.setFocusable(false);
        homeCalendarCard.setClickable(false);
        updateHomeCalendarWidget();

        // Top-Right: Weather
        LinearLayout weatherStack = new LinearLayout(this);
        weatherStack.setOrientation(LinearLayout.VERTICAL);
        weatherStack.setGravity(Gravity.END);
        weatherStack.setClipChildren(false);
        weatherStack.setClipToPadding(false);

        weatherCard = new LinearLayout(this);
        weatherCard.setId(View.generateViewId());
        weatherCard.setOrientation(LinearLayout.HORIZONTAL);
        weatherCard.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.setPadding(dp(12), dp(6), dp(14), dp(6));
        weatherCard.setElevation(0);
        weatherCard.setFocusable(false);
        weatherCard.setClickable(false);
        weatherCard.addView(weatherPanel, new LinearLayout.LayoutParams(-1, -1));


        nowPlayingSection = new LinearLayout(this);
        nowPlayingSection.setOrientation(LinearLayout.VERTICAL);
        nowPlayingSection.setVisibility(View.GONE);
        nowPlayingSection.setClipChildren(false);
        nowPlayingSection.setClipToPadding(false);
        TextView musicLabel = label(AppLanguage.get(R.string.ui_now_playing_586fa7a), 11, light ? LiquidTheme.accent(this) : 0xFF8BE4F5, true);
        musicLabel.setLetterSpacing(.18f);
        LinearLayout.LayoutParams musicLabelParams = new LinearLayout.LayoutParams(-1, -2);
        musicLabelParams.topMargin = dp(10);
        nowPlayingSection.addView(musicLabel, musicLabelParams);
        nowPlayingCard = new LinearLayout(this);
        nowPlayingCard.setGravity(Gravity.CENTER_VERTICAL);
        nowPlayingCard.setClipChildren(false);
        nowPlayingCard.setClipToPadding(false);
        nowPlayingCard.setPadding(dp(8), dp(6), dp(8), dp(6));
        nowPlayingCard.setBackground(roundedShape(
                light ? LiquidTheme.surface(this) : 0x7A091F21,
                light ? LiquidTheme.border(this) : 0x33FFFFFF, 1, 13));
        musicArtwork = new ImageView(this);
        musicArtwork.setImageResource(android.R.drawable.ic_media_play);
        musicArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowPlayingCard.addView(musicArtwork, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout musicCopy = new LinearLayout(this);
        musicCopy.setOrientation(LinearLayout.VERTICAL);
        musicCopy.setPadding(dp(8), 0, dp(4), 0);
        musicTitle = label("", 13, light ? LiquidTheme.text(this) : Color.WHITE, true);
        musicTitle.setSingleLine(true);
        musicTitle.setEllipsize(TextUtils.TruncateAt.END);
        musicCopy.addView(musicTitle);
        musicArtist = label("", 10, light ? LiquidTheme.secondaryText(this) : 0xFFB8CFCC, false);
        musicArtist.setSingleLine(true);
        musicArtist.setEllipsize(TextUtils.TruncateAt.END);
        musicCopy.addView(musicArtist);
        musicProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        musicProgress.setMax(1000);
        musicProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(LiquidTheme.accent(this)));
        LinearLayout.LayoutParams musicProgressParams = new LinearLayout.LayoutParams(-1, dp(3));
        musicProgressParams.topMargin = dp(6);
        musicCopy.addView(musicProgress, musicProgressParams);
        nowPlayingCard.addView(musicCopy, new LinearLayout.LayoutParams(0, -2, 1));
        musicToggle = actionButton("Ⅱ");
        musicToggle.setTextColor(LiquidTheme.accent(this));
        musicToggle.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        musicToggle.setContentDescription(AppLanguage.get(R.string.ui_pause_playback));
        musicToggle.setOnClickListener(v -> toggleMediaPlayback());
        LinearLayout.LayoutParams musicToggleParams = new LinearLayout.LayoutParams(dp(36), dp(40));
        musicToggleParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        nowPlayingCard.addView(musicToggle, musicToggleParams);
        nowPlayingSection.addView(nowPlayingCard, new LinearLayout.LayoutParams(weatherWidth, dp(68)));
        weatherStack.addView(nowPlayingSection);

        widgetHeader = new LinearLayout(this);
        widgetHeader.setGravity(Gravity.CENTER_VERTICAL);
        widgetHeader.setClipChildren(false);
        widgetHeader.setClipToPadding(false);
        widgetHeader.setPadding(dp(14), dp(4), dp(10), dp(4));
        widgetHeader.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams timeCardParams = new LinearLayout.LayoutParams(0, -1, 28);
        timeCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        widgetHeader.addView(timeCard, timeCardParams);
        timeRule = new View(this);
        timeRule.setBackgroundColor(light ? LiquidTheme.border(this) : 0x55CDD0D4);
        widgetHeader.addView(timeRule, new LinearLayout.LayoutParams(dp(1), dp(48)));
        LinearLayout.LayoutParams weatherCardParams = new LinearLayout.LayoutParams(0, -1, 40);
        weatherCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        widgetHeader.addView(weatherCard, weatherCardParams);
        eventRule = new View(this);
        eventRule.setBackgroundColor(light ? LiquidTheme.border(this) : 0x55CDD0D4);
        widgetHeader.addView(eventRule, new LinearLayout.LayoutParams(dp(1), dp(48)));
        LinearLayout.LayoutParams eventCardParams = new LinearLayout.LayoutParams(0, -1, 32);
        eventCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        widgetHeader.addView(homeCalendarCard, eventCardParams);
        FrameLayout topBar = new FrameLayout(this);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        topBar.setPadding(dp(20), dp(16), dp(20), 0);
        topBar.addView(widgetHeader, new FrameLayout.LayoutParams(-1, topCardHeight));
        FrameLayout.LayoutParams musicParams = new FrameLayout.LayoutParams(weatherWidth, -2, Gravity.END | Gravity.TOP);
        musicParams.topMargin = topCardHeight + dp(8);
        topBar.addView(weatherStack, musicParams);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(40), 0, dp(20), dp(32));
        homeContent.setClipChildren(false);
        homeContent.setClipToPadding(false);
        homeContent.setBackgroundColor(Color.TRANSPARENT);
        root.addView(homeContent, new LinearLayout.LayoutParams(-1, -2));

        View appsSpacer = new View(this);
        homeContent.addView(appsSpacer, new LinearLayout.LayoutParams(-1, dp(34)));

        HorizontalScrollView appScroll = new HorizontalScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                android.graphics.Rect focusBounds = new android.graphics.Rect(rect);
                focusBounds.inset(-dp(12), 0);
                return super.computeScrollDeltaToGetChildRectOnScreen(focusBounds);
            }
        };
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Keep focus clearance inside the scroll content: the viewport clips its child bounds.
        appScroll.setClipChildren(true);
        appScroll.setClipToPadding(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        appScroll.addView(appRow);
        homeContent.addView(appScroll, new LinearLayout.LayoutParams(-1, dp(92)));

        applyWallpaper();
        updateTaskProgress();
        selectTab(activeTab, false);
    }

    private void buildGamesTab() {
        LinearLayout gamesHeading = new LinearLayout(this);
        gamesHeading.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        gamesHeading.setClipChildren(false);
        gamesHeading.setClipToPadding(false);
        Button seeAllGames = actionButton(AppLanguage.get(R.string.ui_import_games));
        seeAllGames.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        seeAllGames.setOnClickListener(v -> openGameLibrary());
        linkTabEntry(TAB_GAMES, seeAllGames);
        LinearLayout.LayoutParams seeAllParams = new LinearLayout.LayoutParams(dp(172), dp(46));
        seeAllParams.setMargins(dp(4), dp(4), dp(12), dp(4));
        gamesHeading.addView(seeAllGames, seeAllParams);
        LinearLayout.LayoutParams gamesHeadingParams = new LinearLayout.LayoutParams(-1, dp(56));
        gamesHeadingParams.topMargin = dp(14);
        gamesContent.addView(gamesHeading, gamesHeadingParams);
        gamesEmpty = label(AppLanguage.get(R.string.ui_no_games_yet_import_your_own_nes_files_to_get_started), 17, MUTED, false);
        gamesEmpty.setPadding(dp(8), dp(20), 0, dp(20));
        gamesContent.addView(gamesEmpty);
        gameGrid = new RecyclerView(this);
        int columns = Math.max(1, Math.min(5, (getResources().getDisplayMetrics().widthPixels
                - gamesContent.getPaddingLeft() - gamesContent.getPaddingRight()) / dp(162)));
        gameGrid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, columns));
        gameGrid.setItemAnimator(null);
        gameGrid.setClipChildren(false); gameGrid.setClipToPadding(false);
        gameGrid.setNestedScrollingEnabled(false);
        gameAdapter = new GameCardAdapter(this,
                dp(224), 0, 0, dp(12), dp(12), game -> startActivity(new Intent(this,
                        GameDetailActivity.class).putExtra("gameId", game.id)));
        gameGrid.setAdapter(gameAdapter);
        gamesContent.addView(gameGrid, new LinearLayout.LayoutParams(-1, dp(Math.max(236,
                getResources().getConfiguration().screenHeightDp - 326))));
    }

    private void buildLiveTab() {

        if (liveContent.getChildCount() > 0) liveContent.getChildAt(0).setVisibility(View.GONE);
        LinearLayout liveHeading = new LinearLayout(this);
        liveHeading.setOrientation(LinearLayout.HORIZONTAL);
        liveHeading.setGravity(Gravity.CENTER_VERTICAL);
        liveHeading.setClipChildren(false);
        liveHeading.setClipToPadding(false);
        guideSection = liveHeading;
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveTitle = label(AppLanguage.get(R.string.ui_live_tv), 32, LiquidTheme.text(this), true);
        liveTitle.setTypeface(LiquidTheme.font(this));
        titleGroup.addView(liveTitle);
        guideEmpty = label(AppLanguage.get(R.string.ui_explore_the_schedule_see_what_s_on_now_and_set_reminders), 13, MUTED, false);
        titleGroup.addView(guideEmpty);
        liveHeading.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));

        guideToggle = actionButton(AppLanguage.get(R.string.ui_open_tv_guide));
        guideToggle.setContentDescription(AppLanguage.get(R.string.ui_open_full_screen_tv_guide));
        guideToggle.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        guideToggle.setOnClickListener(view -> openTvGuide());
        linkTabEntry(TAB_TV, guideToggle);
        LinearLayout.LayoutParams guideToggleParams = new LinearLayout.LayoutParams(dp(195), dp(44));
        guideToggleParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        liveHeading.addView(guideToggle, guideToggleParams);

        LinearLayout.LayoutParams liveHeadingParams = new LinearLayout.LayoutParams(-1, -2);
        liveHeadingParams.bottomMargin = dp(4);
        liveContent.addView(liveHeading, liveHeadingParams);
        liveProgress = new TaskProgress(this, false);
        liveContent.addView(liveProgress, new LinearLayout.LayoutParams(-1, -2));
        updateTaskProgress();

        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        channelRows.setClipChildren(false);
        channelRows.setClipToPadding(false);
        liveContent.addView(channelRows, new LinearLayout.LayoutParams(-1, -2));
        channelCategoryScroll = new HorizontalScrollView(this);
        channelCategoryScroll.setHorizontalScrollBarEnabled(false);
        channelCategoryScroll.setClipChildren(false);
        channelCategoryScroll.setClipToPadding(false);
        channelCategoryScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        channelCategoryTabs = new LinearLayout(this);
        channelCategoryTabs.setOrientation(LinearLayout.HORIZONTAL);
        channelCategoryTabs.setGravity(Gravity.CENTER_VERTICAL);
        channelCategoryTabs.setClipChildren(false);
        channelCategoryTabs.setClipToPadding(false);
        channelCategoryScroll.addView(channelCategoryTabs, new HorizontalScrollView.LayoutParams(-2, -1));
        channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(50)));
        channelsEmpty = label(AppLanguage.get(R.string.ui_add_a_playlist_in_settings_to_browse_live_tv), 15, MUTED, false);
        channelsEmpty.setPadding(0, dp(16), 0, dp(20));
        status = label(AppLanguage.get(R.string.ui_add_an_m3u_and_optional_xmltv_url_in_settings_to_get_started), 15, MUTED, false);
        status.setPadding(0, dp(12), 0, 0);
        liveContent.addView(status);
        if (liveStatus != null) status.setText(liveStatus);
        status.setVisibility(liveStatusVisible ? View.VISIBLE : View.GONE);

    }

    private void buildSavedTab() {
        youtubeSection = new LinearLayout(this);
        youtubeSection.setOrientation(LinearLayout.VERTICAL);
        youtubeSection.setClipChildren(false);
        youtubeSection.setClipToPadding(false);
        LinearLayout youtubeTitleRow = new LinearLayout(this);
        youtubeTitleRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        youtubeTitleRow.setClipChildren(false);
        youtubeTitleRow.setClipToPadding(false);
        Button addYouTube = actionButton(AppLanguage.get(R.string.ui_add_shortcut));
        addYouTube.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        addYouTube.setOnClickListener(v -> openYouTubeSettings());
        linkTabEntry(TAB_SAVED, addYouTube);
        LinearLayout.LayoutParams addYouTubeParams = new LinearLayout.LayoutParams(dp(175), dp(46));
        addYouTubeParams.setMargins(dp(4), dp(4), dp(12), dp(4));
        youtubeTitleRow.addView(addYouTube, addYouTubeParams);

        youtubeTitleRow.setPadding(0, dp(4), 0, 0);
        LinearLayout.LayoutParams youtubeSectionParams = new LinearLayout.LayoutParams(-1, -2);
        youtubeSectionParams.topMargin = dp(16);
        youtubeSection.addView(youtubeTitleRow, new LinearLayout.LayoutParams(-1, dp(56)));
        youtubeRow = new RecyclerView(this);
        youtubeRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        youtubeRow.setHorizontalScrollBarEnabled(false);
        youtubeRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        youtubeRow.setClipChildren(false); youtubeRow.setClipToPadding(false);
        youtubeRow.setItemAnimator(null);
        savedCardAdapter = new SavedCardAdapter();
        youtubeRow.setAdapter(savedCardAdapter);
        youtubeSection.addView(youtubeRow, new LinearLayout.LayoutParams(-1, dp(238)));
        savedContent.addView(youtubeSection, youtubeSectionParams);
        youtubeEmpty = label(AppLanguage.get(R.string.ui_no_saved_youtube_shortcuts_yet_add_a_video_or_playlist_in_settin), 17, MUTED, false);
        youtubeEmpty.setPadding(dp(8), dp(16), 0, dp(16));
        savedContent.addView(youtubeEmpty);
        renderYouTubeCards();

    }

    private void refreshNews() {
        String feeds = preferences.getString(NewsRepository.FEEDS, NewsRepository.DEFAULTS);
        ioWorker.execute(() -> {
            try {
                List<NewsRepository.Story> stories = NewsRepository.fetch(feeds);
                runOnUiThread(() -> {
                    if (isFinishing() || newsContent == null) return;
                    while (newsContent.getChildCount() > 2) newsContent.removeViewAt(2);
                    if (!stories.isEmpty()) preferences.edit().putString(NewsRepository.CACHE, NewsRepository.encode(stories)).apply();
                    for (NewsRepository.Story story : stories) {
                        LinearLayout card = new LinearLayout(this);
                        card.setGravity(Gravity.CENTER_VERTICAL);
                        card.setPadding(dp(16), dp(12), dp(20), dp(12));
                        ImageView image = new ImageView(this);
                        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        image.setBackgroundColor(0xFF20303C);
                        image.setTag(story.image);
                        card.addView(image, new LinearLayout.LayoutParams(dp(168), dp(94)));
                        LinearLayout copy = new LinearLayout(this);
                        copy.setOrientation(LinearLayout.VERTICAL);
                        copy.setGravity(Gravity.CENTER_VERTICAL);
                        TextView source = label(story.source, 12, LiquidTheme.secondaryText(this), false);
                        source.setPadding(0, 0, 0, dp(6));
                        copy.addView(source);
                        TextView title = label(story.title, 19, LiquidTheme.text(this), true);
                        title.setMaxLines(3);
                        title.setEllipsize(TextUtils.TruncateAt.END);
                        copy.addView(title);
                        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
                        titleParams.setMargins(dp(18), 0, 0, 0);
                        card.addView(copy, titleParams);
                        card.setFocusable(true); card.setClickable(true);
                        card.setBackground(SettingsUi.navigationStates(this, true));
                        card.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
                        card.setOnClickListener(v -> {
                            try { startActivity(new Intent(this, NewsFeedActivity.class).putExtra("url", story.link).putExtra("title", story.title)); }
                            catch (Exception ignored) { Toast.makeText(this, AppLanguage.get(R.string.news_open_error), Toast.LENGTH_SHORT).show(); }
                        });
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(122));
                        cardParams.bottomMargin = dp(12);
                        newsContent.addView(card, cardParams);
                        if (story.image != null) ioWorker.execute(() -> {
                            try {
                                java.net.HttpURLConnection c = (java.net.HttpURLConnection)new java.net.URL(story.image).openConnection();
                                c.setConnectTimeout(7000); c.setReadTimeout(9000);
                                android.graphics.Bitmap bitmap;
                                try {
                                    if (c.getContentLengthLong() > 2L * 1024 * 1024) return;
                                    try (java.io.InputStream in = c.getInputStream();
                                         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                                        byte[] buffer = new byte[8192]; int count, total = 0;
                                        while ((count = in.read(buffer)) != -1) {
                                            if ((total += count) > 2 * 1024 * 1024) return;
                                            out.write(buffer, 0, count);
                                        }
                                        byte[] bytes = out.toByteArray();
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inJustDecodeBounds = true;
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                                        if (options.outWidth <= 0 || options.outHeight <= 0) return;
                                        int targetWidth = dp(168), targetHeight = dp(94);
                                        options.inSampleSize = 1;
                                        while (options.outWidth / options.inSampleSize > targetWidth
                                                || options.outHeight / options.inSampleSize > targetHeight) {
                                            options.inSampleSize *= 2;
                                        }
                                        options.inJustDecodeBounds = false;
                                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                                    }
                                } finally { c.disconnect(); }
                                if (bitmap != null) runOnUiThread(() -> {
                                    if (image.isAttachedToWindow() && story.image.equals(image.getTag())) image.setImageBitmap(bitmap);
                                });
                            } catch (Exception ignored) { }
                        });
                    }
                    if (stories.isEmpty()) {
                        List<NewsRepository.Story> cached = NewsRepository.decode(preferences.getString(NewsRepository.CACHE, ""));
                        for (NewsRepository.Story story : cached) {
                            Button item = actionButton(story.source + " · " + story.title);
                            item.setOnClickListener(v -> { try { startActivity(new Intent(this,NewsFeedActivity.class).putExtra("url",story.link)); } catch (Exception ignored) { } });
                            newsContent.addView(item, new LinearLayout.LayoutParams(-1, dp(68)));
                        }
                        newsContent.addView(label(cached.isEmpty() ? AppLanguage.get(R.string.news_empty) : AppLanguage.get(R.string.news_offline), 16, MUTED, false));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (newsContent == null || isFinishing()) return;
                    List<NewsRepository.Story> cached=NewsRepository.decode(preferences.getString(NewsRepository.CACHE, ""));
                    while(newsContent.getChildCount()>2)newsContent.removeViewAt(2);
                    for(NewsRepository.Story story:cached){Button item=actionButton(story.source+" · "+story.title);item.setOnClickListener(v->{try{startActivity(new Intent(this,NewsFeedActivity.class).putExtra("url",story.link).putExtra("title",story.title));}catch(Exception ignored){}});newsContent.addView(item,new LinearLayout.LayoutParams(-1,dp(68)));}
                    newsContent.addView(label(cached.isEmpty()?AppLanguage.get(R.string.news_refresh_error):AppLanguage.get(R.string.news_offline),16,MUTED,false));
                });
            }
        });
    }

    private void ensureTabBuilt(int tab) {
        if (tab == TAB_TV) {
            if (channelRows == null) buildLiveTab();
            renderChannels();
        } else if (tab == TAB_GAMES) {
            if (gameGrid == null) buildGamesTab();
            renderGames();
        } else if (tab == TAB_EVENTS) {
            if (calendarMonthGrid == null) buildEventsTab();
            updateCalendarEventDisplay();
        } else if (tab == TAB_SAVED) {
            if (youtubeRow == null) buildSavedTab();
            renderYouTubeCards();
        } else if (tab == TAB_ACTIVITIES) {
            if (activitiesPanel == null) {
                activitiesPanel = new ActivitiesPanel(this);
                activitiesContent.addView(activitiesPanel);
                linkTabEntry(TAB_ACTIVITIES, activitiesPanel.refreshButton);
            }
            activitiesPanel.refresh();
        } else if (tab == TAB_NEWS && newsContent.getChildCount() == 1) {
            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            View heading = newsContent.getChildAt(0);
            newsContent.removeView(heading);
            toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            Button refresh = actionButton(AppLanguage.get(R.string.news_refresh));
            refresh.setOnClickListener(v -> refreshNews());
            toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(190), dp(44)));
            Button feeds = actionButton(AppLanguage.get(R.string.news_manage_feeds));
            feeds.setOnClickListener(v -> editNewsFeeds());
            LinearLayout.LayoutParams feedsParams = new LinearLayout.LayoutParams(dp(190), dp(44));
            feedsParams.leftMargin = dp(12);
            toolbar.addView(feeds, feedsParams);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, dp(72));
            headerParams.topMargin = dp(4);
            newsContent.addView(toolbar, headerParams);
            linkTabEntry(TAB_NEWS, refresh);
            TextView info = label(AppLanguage.get(R.string.news_latest), 12, MUTED, true);
            info.setPadding(dp(4), dp(18), 0, dp(12));
            newsContent.addView(info);
            refreshNews();
        }
    }

    private void editNewsFeeds() {
        EditText input = new EditText(this);
        input.setSingleLine(false); input.setMinLines(3);
        input.setText(preferences.getString(NewsRepository.FEEDS, NewsRepository.DEFAULTS));
        new AlertDialog.Builder(this).setTitle(AppLanguage.get(R.string.news_feed_urls)).setView(input)
                .setNegativeButton(AppLanguage.get(R.string.ui_cancel), null)
                .setPositiveButton(AppLanguage.get(R.string.ui_save), (dialog, which) -> {
                    preferences.edit().putString(NewsRepository.FEEDS, input.getText().toString().trim()).apply();
                    refreshNews();
                }).show();
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void openTvGuide() {
        startActivityForResult(new Intent(this, GuideActivity.class).putExtra("category", selectedChannelCategory), 7410);
    }

    private LinearLayout addTabContentPage(FrameLayout viewport, int tab) {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setClipChildren(true);
        page.setClipToPadding(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        if (tab == TAB_NEWS || tab == TAB_ACTIVITIES) {
            page.setPadding(0, 0, 0, dp(12));
            page.setClipToPadding(true);
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int topPad = tab == TAB_TV ? dp(24) : dp(54);
        int bottomPad = tab == TAB_TV ? dp(20) : dp(34);
        content.setPadding(dp(72), topPad, dp(72), bottomPad);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        TextView title = label(TAB_TITLES[tab], 42, LiquidTheme.text(this), true);
        title.setTypeface(LiquidTheme.font(this));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(72));
        titleParams.topMargin = dp(4);
        if (tab != TAB_ACTIVITIES) content.addView(title, titleParams);
        page.addView(content);
        page.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (activeTab == tab) browsingShade.setAlpha(Math.min(.94f,
                    (LiquidTheme.isLight(this) ? .88f : .65f) + (float) y / dp(600) * .2f));
            glassBackdrop.invalidate();
        });
        tabPages[tab] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return content;
    }

    private void linkTabEntry(int tab, View entry) {
        if (entry == null || tab < TAB_HOME || tab > TAB_ACTIVITIES || tabButtons[tab] == null) return;
        if (entry.getId() == View.NO_ID) entry.setId(View.generateViewId());
        tabButtons[tab].setNextFocusUpId(entry.getId());
        tabEntryFocus[tab] = entry;
    }

    private void linkTopControl(int tab, View control) {
        if (control == null || tab < TAB_HOME || tab > TAB_ACTIVITIES) return;
        if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                event.startTracking();
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_STAR || event.getKeyCode() == KeyEvent.KEYCODE_BOOKMARK
                    || event.getKeyCode() == KeyEvent.KEYCODE_PROG_YELLOW || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y) {
                View focused = getCurrentFocus();
                if (focused != null && focused.performLongClick()) return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode())
                && tabsInitialized && settingsScreen == null) {
            View focused = getCurrentFocus();
            int direction = focusDirection(event.getKeyCode());
            if (activeTab == TAB_EVENTS && focused != null
                    && handleCalendarDirection(focused, direction)) return true;
            if (focused != null && tabRow != null && isWithinPage(focused, tabRow)) {
                if (direction == View.FOCUS_UP) {
                    if (activeTab == TAB_TV) {
                        ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                        if (channelRow != null && channelRow.requestFocus()) return true;
                        Button categoryTab = channelTabMap.get(selectedChannelCategory);
                        if (categoryTab != null && categoryTab.requestFocus()) return true;
                        if (guideToggle != null && guideToggle.requestFocus()) return true;
                    }
                    View entry = tabEntryFocus[activeTab];
                    if (entry != null && entry.isShown() && entry.isEnabled() && entry.requestFocus()) return true;
                    return true;
                }
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    View next = focused.focusSearch(direction);
                    boolean tabSibling = next != null && isWithinPage(next, tabRow);
                    boolean settingsEntry = direction == (isRtl() ? View.FOCUS_LEFT : View.FOCUS_RIGHT) && next != null
                            && isWithinPage(next, systemShortcuts);
                    if ((tabSibling || settingsEntry)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
                return true;
            }
            if (focused != null && systemShortcuts != null && isWithinPage(focused, systemShortcuts)) {
                if (direction == View.FOCUS_UP) {
                    View entry = tabEntryFocus[activeTab];
                    if (entry != null && entry.isShown() && entry.isEnabled() && entry.requestFocus()) return true;
                    return true;
                }
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    View next = focused.focusSearch(direction);
                    boolean shortcutSibling = next != null && next.getParent() == systemShortcuts;
                    boolean tabReturn = direction == (isRtl() ? View.FOCUS_RIGHT : View.FOCUS_LEFT) && next != null
                            && isWithinPage(next, tabRow);
                    if ((shortcutSibling || tabReturn)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
                return true;
            }
            View page = tabPages[activeTab];
            if (focused != null && page != null && isWithinPage(focused, page)) {
                if (activeTab == TAB_GAMES && gameGrid != null && isWithinPage(focused, gameGrid)
                        && (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN)) {
                    View item = gameGrid.findContainingItemView(focused);
                    int position = item == null ? RecyclerView.NO_POSITION : gameGrid.getChildAdapterPosition(item);
                    int columns = ((androidx.recyclerview.widget.GridLayoutManager)gameGrid.getLayoutManager()).getSpanCount();
                    if (pendingGameFocus != RecyclerView.NO_POSITION) position = pendingGameFocus;
                    boolean anotherRow = position != RecyclerView.NO_POSITION && (direction == View.FOCUS_DOWN
                            ? position / columns < (gameAdapter.getItemCount() - 1) / columns : position >= columns);
                    if (anotherRow) {
                        focusGamePosition(Math.min(gameAdapter.getItemCount() - 1,
                                position + (direction == View.FOCUS_DOWN ? columns : -columns)));
                        return true;
                    }
                }
                if (activeTab == TAB_TV) {
                    if (focused == guideToggle && direction == View.FOCUS_DOWN) {
                        Button categoryTab = channelTabMap.get(selectedChannelCategory);
                        if (categoryTab == null && channelCategoryTabs != null && channelCategoryTabs.getChildCount() > 0)
                            categoryTab = (Button) channelCategoryTabs.getChildAt(0);
                        if (categoryTab != null && categoryTab.requestFocus()) return true;
                    }
                    if (channelCategoryTabs != null && isWithinPage(focused, channelCategoryTabs)) {
                        if (direction == View.FOCUS_UP) {
                            if (guideToggle != null && guideToggle.requestFocus()) return true;
                        } else if (direction == View.FOCUS_DOWN) {
                            ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                            if (channelRow != null && channelRow.requestFocus()) return true;
                        }
                    }
                    ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                    if (channelRow != null && isWithinPage(focused, channelRow.container)) {
                        if (direction == View.FOCUS_UP) {
                            Button categoryTab = channelTabMap.get(selectedChannelCategory);
                            if (categoryTab == null && channelCategoryTabs != null && channelCategoryTabs.getChildCount() > 0)
                                categoryTab = (Button) channelCategoryTabs.getChildAt(0);
                            if (categoryTab != null && categoryTab.requestFocus()) return true;
                        } else if (direction == View.FOCUS_DOWN) {
                            Button destination = navigationButton(TAB_TV);
                            if (destination != null && destination.requestFocus()) return true;
                        }
                    }
                }
                View next = focused.focusSearch(direction);
                if (next != null && next.isFocusable() && next.isEnabled()
                        && isInFocusDirection(focused, next, direction)) {
                    if (isWithinPage(next, page) && next.requestFocus()) return true;
                    if (direction == View.FOCUS_DOWN && isWithinPage(next, tabRow)) {
                        if (scrollPageWithinDirection(focused, page, direction)) return true;
                        Button destination = navigationButton(activeTab);
                        if (destination != null && destination.requestFocus()) return true;
                    }
                }
                if (scrollPageWithinDirection(focused, page, direction)) return true;
                if (direction == View.FOCUS_DOWN) {
                    Button destination = navigationButton(activeTab);
                    if (destination != null && destination.requestFocus()) return true;
                }
                return true;
            }
            if (focused != null && focused.getParent() instanceof ViewGroup) {
                ViewGroup siblingGroup = (ViewGroup) focused.getParent();
                View next = focused.focusSearch(direction);
                if (next != null && next.getParent() == siblingGroup
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            View focused = getCurrentFocus();
            if (focused != null && focused.performLongClick()) return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private View calendarDateView(LocalDate day) {
        if (calendarMonthGrid == null) return null;
        for (int i = 7; i < calendarMonthGrid.getChildCount(); i++) {
            View child = calendarMonthGrid.getChildAt(i);
            if (day.equals(child.getTag())) return child;
        }
        return null;
    }

    private boolean focusCalendarDate(LocalDate day) {
        View destination = calendarDateView(day);
        return destination != null && destination.requestFocus();
    }

    private boolean handleCalendarDirection(View focused, int direction) {
        if (focused instanceof CalendarDayButton && focused.getTag() instanceof LocalDate) {
            LocalDate day = (LocalDate) focused.getTag();
            int delta = direction == View.FOCUS_LEFT ? -1 : direction == View.FOCUS_RIGHT ? 1
                    : direction == View.FOCUS_UP ? -7 : 7;
            if (isRtl() && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) delta = -delta;
            LocalDate target = day.plusDays(delta);
            if (YearMonth.from(target).equals(displayedMonth)) {
                focusCalendarDate(target);
            } else if (direction == View.FOCUS_UP) {
                previousMonthButton.requestFocus();
            } else if (direction == View.FOCUS_DOWN) {
                navigationButton(TAB_EVENTS).requestFocus();
            } else if (CalendarDates.inRange(YearMonth.from(target), YearMonth.now())) {
                // Move naturally across month boundaries without relying on geometry.
                // Keep focus off the old grid while its date views are rebuilt.
                previousMonthButton.requestFocus();
                displayedMonth = YearMonth.from(target);
                selectedCalendarDay = target;
                renderMonthCalendar(cachedCalendarEvents());
                focusCalendarDate(target);
            }
            return true;
        }
        if (direction == View.FOCUS_UP
                && (isWithinPage(focused, tabRow) || isWithinPage(focused, systemShortcuts))) {
            focusCalendarDate(selectedCalendarDay);
            return true;
        }
        if (direction == View.FOCUS_DOWN && isWithinPage(focused, eventsContent)
                && !isWithinPage(focused, calendarMonthGrid)) {
            focusCalendarDate(selectedCalendarDay);
            return true;
        }
        return false;
    }

    private boolean isDpadDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            default: return View.FOCUS_RIGHT;
        }
    }

    private boolean isInFocusDirection(View from, View to, int direction) {
        int[] fromLocation = new int[2];
        int[] toLocation = new int[2];
        from.getLocationOnScreen(fromLocation);
        to.getLocationOnScreen(toLocation);
        float fromX = fromLocation[0] + from.getWidth() / 2f;
        float fromY = fromLocation[1] + from.getHeight() / 2f;
        float toX = toLocation[0] + to.getWidth() / 2f;
        float toY = toLocation[1] + to.getHeight() / 2f;
        switch (direction) {
            case View.FOCUS_UP: return toY < fromY;
            case View.FOCUS_DOWN: return toY > fromY;
            case View.FOCUS_LEFT: return toX < fromX;
            default: return toX > fromX;
        }
    }

    private boolean scrollPageWithinDirection(View child, View page, int direction) {
        for (View current = child; current != null; ) {
            boolean canScroll = direction == View.FOCUS_UP ? current.canScrollVertically(-1)
                    : direction == View.FOCUS_DOWN ? current.canScrollVertically(1)
                    : direction == View.FOCUS_LEFT ? current.canScrollHorizontally(-1)
                    : current.canScrollHorizontally(1);
            if (canScroll) {
                int distance = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN
                        ? Math.max(1, current.getHeight() / 2) : Math.max(1, current.getWidth() / 2);
                current.scrollBy(direction == View.FOCUS_LEFT ? -distance
                                : direction == View.FOCUS_RIGHT ? distance : 0,
                        direction == View.FOCUS_UP ? -distance
                                : direction == View.FOCUS_DOWN ? distance : 0);
                return true;
            }
            if (current == page) break;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean isWithinPage(View child, View page) {
        for (View current = child; current != null; ) {
            if (current == page) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean canScrollWithinPage(View child, View page, int direction) {
        for (View current = child; current != null; ) {
            boolean canScroll = direction == View.FOCUS_UP ? current.canScrollVertically(-1)
                    : direction == View.FOCUS_DOWN ? current.canScrollVertically(1)
                    : direction == View.FOCUS_LEFT ? current.canScrollHorizontally(-1)
                    : current.canScrollHorizontally(1);
            if (canScroll) return true;
            if (current == page) break;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void selectTab(int tab, boolean focusTabButton) {
        // Hiding a focused page can synchronously focus a navigation button.
        // Its listener must not start another switch partway through this one.
        if (switchingTabs) return;
        if (tab < TAB_HOME || tab > TAB_ACTIVITIES || tabPages[TAB_HOME] == null) return;
        final int selectedTab = (tab == TAB_TV && isNavigationHidden(tab))
                || (!tabsInitialized && isNavigationHidden(tab))
                ? TAB_HOME : tab;
        View currentFocus = getCurrentFocus();
        boolean navigatingTabs = false;
        for (Button button : tabButtons) {
            if (button == currentFocus) { navigatingTabs = true; break; }
        }
        final boolean keepNavigationFocus = navigatingTabs;
        boolean animatePage = tabsInitialized && activeTab != selectedTab;
        final int previousTab = activeTab;
        View oldPage = tabPages[activeTab];
        if (tabsInitialized && oldPage != null && oldPage.getVisibility() == View.VISIBLE) {
            tabScroll[activeTab] = activeTab == TAB_HOME ? 0 : oldPage.getScrollY();
            View focused = oldPage.findFocus();
            if (focused != null) tabFocus[activeTab] = focused;
        }
        if (activeTab != selectedTab) { gameFocusGeneration++; pendingGameFocus = RecyclerView.NO_POSITION; }
        activeTab = selectedTab;
        tabsInitialized = true;
        switchingTabs = true;
        try {
            ensureTabBuilt(selectedTab);
            if (selectedTab == TAB_HOME) { updateClock(); updateHomeCalendarWidget(); }
            for (int i = 0; i < tabPages.length; i++) {
                if (tabPages[i] != null) {
                    if (i != selectedTab) Motion.reset(tabPages[i]);
                    tabPages[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE);
                }
                if (tabButtons[i] != null) {
                    if (i != previousTab && i != selectedTab) continue;
                    boolean selected = i == selectedTab;
                    tabButtons[i].setSelected(selected);
                    navigationGlyphs[i].setGlyphColor(selected
                            ? (LiquidTheme.isLight(this) ? Color.WHITE : 0xFF071417)
                            : (LiquidTheme.isLight(this) ? 0xFF24403D : 0xD4FFFFFF));
                }
            }
            if (!keepNavigationFocus && !focusTabButton) {
                View destination = tabFocus[selectedTab];
                if (destination == null || !destination.isAttachedToWindow() || !destination.isShown())
                    destination = tabEntryFocus[selectedTab];
                if (destination != null) destination.requestFocus();
            }
        } finally {
            switchingTabs = false;
        }
        browsingShade.setAlpha(selectedTab == TAB_HOME ? 0f
                : (LiquidTheme.isLight(this) ? .88f : .65f));
        View selectedPage = tabPages[selectedTab];
        if (selectedPage != null) {
            if (animatePage) Motion.reveal(selectedPage);
            selectedPage.post(() -> {
                if (activeTab != selectedTab) return;
                if (selectedTab != TAB_HOME) selectedPage.scrollTo(0, tabScroll[selectedTab]);
                View previous = tabFocus[selectedTab];
                if (focusTabButton) navigationButton(selectedTab).requestFocus();
                else if (!keepNavigationFocus && previous != null && previous.isAttachedToWindow() && previous.isShown()) previous.requestFocus();
            });
        }
    }

    private boolean isNavigationHidden(int tab) {
        String key = NavigationPreferences.hiddenKey(tab);
        return key != null && preferences.getBoolean(key, false);
    }

    private Button navigationButton(int tab) {
        return isNavigationHidden(tab) ? tabButtons[TAB_HOME] : tabButtons[tab];
    }

    private List<Integer> navigationOrder() {
        return NavigationPreferences.order(preferences.getString(NavigationPreferences.ORDER, null),
                preferences.getInt(PREF_LIVE_TV_POSITION, 0));
    }

    private void rebuildNavigationButtons() {
        if (tabRow == null) return;
        tabRow.removeAllViews();
        tabRow.addView(tabButtons[TAB_HOME], new LinearLayout.LayoutParams(-2, dp(44)));
        for (int tab : navigationOrder()) {
            if (isNavigationHidden(tab)) continue;
            tabRow.addView(tabButtons[tab], new LinearLayout.LayoutParams(-2, dp(44)));
        }
    }

    private void openYouTubeSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.YOUTUBE;
        settingsScreen.show();
    }

    private void openCalendarSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.CALENDAR;
        settingsScreen.show();
    }

    private void buildEventsTab() {
        // Events is a dedicated month view, so let its own month header lead.
        if (eventsContent.getChildCount() > 0) eventsContent.getChildAt(0).setVisibility(View.GONE);
        eventsContent.setPadding(dp(28), dp(20), dp(28), dp(12));
        LinearLayout monthHeading = new LinearLayout(this);
        monthHeading.setGravity(Gravity.CENTER_VERTICAL);
        monthHeading.setClipChildren(false);
        monthHeading.setClipToPadding(false);
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(-1, dp(80));
        eventsContent.addView(monthHeading, monthParams);
        boolean light = LiquidTheme.isLight(this);
        Button backButton = actionButton("‹");
        backButton.setTextSize(30);
        alignCalendarArrow(backButton);
        backButton.setContentDescription(AppLanguage.get(R.string.ui_back_to_home));
        backButton.setBackground(calendarGlassShape(
                light ? LiquidTheme.surface(this) : 0xC91A1D26,
                light ? LiquidTheme.border(this) : 0xAAFFFFFF, 28));
        backButton.setOnClickListener(v -> selectTab(TAB_HOME, true));
        linkTabEntry(TAB_EVENTS, backButton);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        backParams.setMargins(dp(2), dp(2), dp(14), dp(2));
        monthHeading.addView(backButton, backParams);
        monthTitle = label(AppLanguage.get(R.string.ui_calendar), 32, Color.WHITE, true);
        monthTitle.setTextColor(LiquidTheme.text(this));
        monthHeading.addView(monthTitle, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout eventSummary = new LinearLayout(this);
        eventSummary.setOrientation(LinearLayout.VERTICAL);
        eventSummary.setGravity(Gravity.CENTER_VERTICAL);
        eventSummary.setPadding(dp(20), 0, dp(20), 0);
        calendarHeaderDate = label("", 10, LiquidTheme.accent(this), true);
        calendarHeaderDate.setLetterSpacing(.1f);
        LinearLayout eventsHeading = new LinearLayout(this);
        eventsHeading.setGravity(Gravity.CENTER_VERTICAL);
        eventsHeading.addView(calendarHeaderDate, new LinearLayout.LayoutParams(0, -2, 1));
        eventsProgress = new TaskProgress(this, true);
        eventsHeading.addView(eventsProgress);
        eventSummary.addView(eventsHeading, new LinearLayout.LayoutParams(-1, -2));
        updateTaskProgress();
        calendarHeaderEvents = label("", 14, LiquidTheme.text(this), true);
        calendarHeaderEvents.setMaxLines(3);
        calendarHeaderEvents.setEllipsize(TextUtils.TruncateAt.END);
        calendarHeaderEvents.setPadding(0, dp(4), 0, 0);
        eventSummary.addView(calendarHeaderEvents);
        monthHeading.addView(eventSummary, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout monthSwitcher = new LinearLayout(this);
        monthSwitcher.setGravity(Gravity.CENTER_VERTICAL);
        monthSwitcher.setClipChildren(false);
        monthSwitcher.setClipToPadding(false);
        monthSwitcher.setBackground(calendarGlassShape(
                light ? LiquidTheme.surface(this) : 0xC91A1D26,
                light ? LiquidTheme.border(this) : 0xAAFFFFFF, 30));
        monthHeading.addView(monthSwitcher, new LinearLayout.LayoutParams(dp(200), dp(52)));
        previousMonthButton = actionButton(isRtl() ? "›" : "‹");
        previousMonthButton.setTextSize(28);
        alignCalendarArrow(previousMonthButton);
        previousMonthButton.setContentDescription(AppLanguage.get(R.string.ui_previous_month));
        styleCalendarHeaderControl(previousMonthButton);
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        prevParams.setMargins(dp(3), dp(3), dp(1), dp(3));
        monthSwitcher.addView(previousMonthButton, prevParams);
        Button todayButton = actionButton(AppLanguage.get(R.string.ui_today_24345a1));
        todayButton.setTextSize(14);
        styleCalendarHeaderControl(todayButton);
        todayButton.setOnClickListener(v -> {
            displayedMonth = YearMonth.now();
            selectedCalendarDay = LocalDate.now();
            renderMonthCalendar(cachedCalendarEvents());
        });
        LinearLayout.LayoutParams todayParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        todayParams.setMargins(dp(1), dp(3), dp(1), dp(3));
        monthSwitcher.addView(todayButton, todayParams);
        nextMonthButton = actionButton(isRtl() ? "‹" : "›");
        nextMonthButton.setTextSize(28);
        alignCalendarArrow(nextMonthButton);
        nextMonthButton.setContentDescription(AppLanguage.get(R.string.ui_next_month));
        styleCalendarHeaderControl(nextMonthButton);
        nextMonthButton.setId(View.generateViewId());
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        nextParams.setMargins(dp(1), dp(3), dp(3), dp(3));
        monthSwitcher.addView(nextMonthButton, nextParams);
        previousMonthButton.setOnClickListener(v -> {
            YearMonth candidate = displayedMonth.minusMonths(1);
            if (!CalendarDates.inRange(candidate, YearMonth.now())) return;
            displayedMonth = candidate;
            selectedCalendarDay = displayedMonth.atDay(1);
            renderMonthCalendar(cachedCalendarEvents());
        });
        nextMonthButton.setOnClickListener(v -> {
            YearMonth candidate = displayedMonth.plusMonths(1);
            if (!CalendarDates.inRange(candidate, YearMonth.now())) return;
            displayedMonth = candidate;
            selectedCalendarDay = displayedMonth.atDay(1);
            renderMonthCalendar(cachedCalendarEvents());
        });

        calendarMonthGrid = new GridLayout(this) {
            private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void dispatchDraw(Canvas canvas) {
                rulePaint.setColor(LiquidTheme.isLight(MainActivity.this) ? 0x55708B88 : 0x558FAEAC);
                rulePaint.setStrokeWidth(dp(1));
                if (getChildCount() > 7) {
                    int top = getChildAt(7).getTop();
                    for (int i = 7; i < getChildCount(); i += 7) {
                        int y = getChildAt(i).getTop();
                        canvas.drawLine(0, y, getWidth(), y, rulePaint);
                    }
                    for (int i = 8; i < Math.min(14, getChildCount()); i++) {
                        int x = getChildAt(i).getLeft();
                        canvas.drawLine(x, top, x, getHeight(), rulePaint);
                    }
                }
                super.dispatchDraw(canvas);
            }
        };
        calendarMonthGrid.setColumnCount(7);
        calendarMonthGrid.setClipChildren(false);
        calendarMonthGrid.setClipToPadding(false);
        eventsContent.addView(calendarMonthGrid, new LinearLayout.LayoutParams(-1, -2));

    }

    private void renderMonthCalendar(List<PublicCalendarRepository.Event> events) {
        if (calendarMonthGrid == null) return;
        boolean light = LiquidTheme.isLight(this);
        renderedMonth = displayedMonth;
        renderedToday = LocalDate.now();
        renderedCalendarStamp = preferences.getLong("publicCalendarUpdatedAt", 0L);
        renderedCalendarSource = preferences.getString("publicCalendarEvents", "[]");
        String monthName = displayedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", AppLanguage.locale()));
        SpannableString headingText = new SpannableString(monthName + " " + displayedMonth.getYear());
        headingText.setSpan(new ForegroundColorSpan(light ? LiquidTheme.secondaryText(this) : 0xFFAFC6D2), monthName.length() + 1,
                headingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        monthTitle.setText(headingText);
        previousMonthButton.setEnabled(CalendarDates.inRange(displayedMonth.minusMonths(1), YearMonth.now()));
        nextMonthButton.setEnabled(CalendarDates.inRange(displayedMonth.plusMonths(1), YearMonth.now()));
        calendarMonthGrid.removeAllViews();
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        int cellWidth = Math.max(58, (widthDp - 56) / 7);
        for (int i = 0; i < 7; i++) {
            DayOfWeek weekday = DayOfWeek.SUNDAY.plus(i);
            TextView heading = label(weekday.getDisplayName(TextStyle.SHORT, AppLanguage.locale()),
                    13, light ? LiquidTheme.secondaryText(this) : 0xFFC6D6DB, false);
            heading.setGravity(Gravity.CENTER);
            heading.setLetterSpacing(.1f);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(cellWidth);
            params.height = dp(34);
            calendarMonthGrid.addView(heading, params);
        }
        int offset = displayedMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int cells = ((offset + displayedMonth.lengthOfMonth() + 6) / 7) * 7;
        ZoneId zone = ZoneId.systemDefault();
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density);
        int rows = cells / 7;
        int fittedHeight = (Math.max(300, screenHeightDp - 205) - 34) / rows - 7;
        int cellHeight = Math.max(58, Math.min(94, Math.min(cellWidth, fittedHeight)));
        for (int i = 0; i < cells; i++) {
            int dayNumber = i - offset + 1;
            View cell;
            if (dayNumber < 1 || dayNumber > displayedMonth.lengthOfMonth()) {
                TextView overflow = label(String.valueOf(dayNumber < 1
                        ? displayedMonth.minusMonths(1).lengthOfMonth() + dayNumber
                        : dayNumber - displayedMonth.lengthOfMonth()), 20,
                        light ? 0x554D6968 : 0xFF78818A, false);
                overflow.setGravity(Gravity.TOP | Gravity.START);
                overflow.setPadding(dp(13), dp(8), 0, 0);
                overflow.setBackgroundColor(Color.TRANSPARENT);
                cell = overflow;
            } else {
                LocalDate day = displayedMonth.atDay(dayNumber);
                ArrayList<PublicCalendarRepository.Event> dayEvents = new ArrayList<>();
                for (PublicCalendarRepository.Event event : events)
                    if (CalendarDates.includes(event, day, zone)) dayEvents.add(event);
                ArrayList<String> eventTitles = new ArrayList<>();
                for (PublicCalendarRepository.Event event : dayEvents) eventTitles.add(event.title);
                CalendarDayButton dateButton = new CalendarDayButton(this);
                dateButton.setContent(dayNumber, eventTitles, day.equals(LocalDate.now()));
                dateButton.setId(View.generateViewId());
                dateButton.setTag(day);
                StringBuilder dayDescription = new StringBuilder(day.format(
                        java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", AppLanguage.locale())))
                        .append(". ");
                for (PublicCalendarRepository.Event event : dayEvents)
                    dayDescription.append(". ").append(event.title);
                dateButton.setContentDescription(dayDescription);
                styleCalendarDateButton(dateButton, day.equals(selectedCalendarDay), day.equals(LocalDate.now()),
                        day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY);
                dateButton.setOnFocusChangeListener((view, focused) -> {
                    if (focused) selectCalendarDay(day);
                });
                dateButton.setOnHoverListener((view, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER)
                        updateCalendarHeader(day);
                    else if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_EXIT)
                        updateCalendarHeader(selectedCalendarDay);
                    return false;
                });
                dateButton.setOnClickListener(v -> {
                    selectCalendarDay(day);
                    v.requestFocus();
                });
                cell = dateButton;
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(cellWidth);
            params.height = dp(cellHeight);
            params.setMargins(0, 0, 0, 0);
            calendarMonthGrid.addView(cell, params);
        }
        updateCalendarDaySelection();
        updateCalendarDetails(events);
        updateCalendarHeader(selectedCalendarDay);
    }

    private void updateCalendarHeader(LocalDate day) {
        if (calendarHeaderDate == null) return;
        calendarHeaderDate.setText(day.format(java.time.format.DateTimeFormatter.ofPattern(
                "EEEE, d MMM", AppLanguage.locale())).toUpperCase(AppLanguage.locale()));
        ArrayList<String> titles = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (PublicCalendarRepository.Event event : cachedCalendarEvents()) {
            if (CalendarDates.includes(event, day, zone)) titles.add(event.title);
        }
        calendarHeaderEvents.setText(titles.isEmpty() ? AppLanguage.get(R.string.ui_no_events_planned) : TextUtils.join(" · ", titles));
    }

    private void selectCalendarDay(LocalDate day) {
        if (!day.equals(selectedCalendarDay)) {
            selectedCalendarDay = day;
            updateCalendarDaySelection();
        }
        updateCalendarDetails(cachedCalendarEvents());
        updateCalendarHeader(day);
    }

    private void updateCalendarDaySelection() {
        if (calendarMonthGrid == null) return;
        for (int i = 0; i < calendarMonthGrid.getChildCount(); i++) {
            View child = calendarMonthGrid.getChildAt(i);
            if (child instanceof Button && child.getTag() instanceof LocalDate) {
                LocalDate day = (LocalDate) child.getTag();
                styleCalendarDateButton((Button) child, selectedCalendarDay.equals(day), day.equals(LocalDate.now()),
                        day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY);
            }
        }
    }

    private void styleCalendarDateButton(Button button, boolean selected, boolean today, boolean weekend) {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, calendarDayHighlight(true));
        states.addState(new int[]{android.R.attr.state_pressed}, calendarDayHighlight(true));
        states.addState(new int[]{}, selected ? calendarDayHighlight(false)
                : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        button.setBackground(states);
        if (button instanceof CalendarDayButton) ((CalendarDayButton) button).setToday(today);
        button.animate().cancel();
        button.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationZ(selected ? dp(14) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 150 : 0)
                .start();
    }

    private Drawable calendarDayHighlight(boolean focused) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                paint.setColor(LiquidTheme.isLight(MainActivity.this) ? 0x22087D91 : 0x227BE7F4);
                canvas.drawRect(bounds, paint);
                paint.setColor(LiquidTheme.accent(MainActivity.this));
                canvas.drawRect(bounds.left + dp(10), bounds.bottom - dp(focused ? 3 : 2),
                        bounds.right - dp(10), bounds.bottom, paint);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    private Drawable calendarGlassShape(int fill, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(1), border);
        return shape;
    }

    private void alignCalendarArrow(Button arrow) {
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        // The chevron glyph sits below its font's visual center on Android TV.
        arrow.setPadding(0, 0, 0, dp(5));
    }

    private void styleCalendarHeaderControl(Button button) {
        boolean light = LiquidTheme.isLight(this);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{-android.R.attr.state_enabled},
                calendarGlassShape(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        states.addState(new int[]{android.R.attr.state_pressed},
                calendarGlassShape(light ? 0xFF087D91 : 0xFFB6FFF0, light ? 0x887C9E9A : 0xFFFFFFFF, 24));
        states.addState(new int[]{android.R.attr.state_focused},
                calendarGlassShape(light ? 0xFF087D91 : 0xFF7BE7F4, light ? 0x887C9E9A : 0xCCFFFFFF, 24));
        states.addState(new int[]{},
                calendarGlassShape(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        button.setBackground(states);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_focused}, new int[]{}
        }, light ? new int[]{0x55132B2B, Color.WHITE, Color.WHITE, LiquidTheme.text(this)}
                 : new int[]{0x55FFFFFF, 0xFF071417, 0xFF071417, Color.WHITE}));
        button.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
    }

private void updateCalendarDetails(List<PublicCalendarRepository.Event> events) {
    if (calendarDetailDate == null || selectedCalendarDay == null) return;

    CalendarDates.MoonPhase moon =
            CalendarDates.moonPhase(selectedCalendarDay);

    calendarDetailDate.setText(
            String.valueOf(selectedCalendarDay.getDayOfMonth())
    );

    calendarDetailDay.setText(
            selectedCalendarDay.getDayOfWeek().getDisplayName(
                    TextStyle.FULL,
                    AppLanguage.locale()
            )
            + "  ·  "
            + selectedCalendarDay.getMonth().getDisplayName(
                    TextStyle.FULL,
                    AppLanguage.locale()
            )
    );

    calendarDetailMoon.setText(moon.symbol);
    calendarDetailMoonName.setText(moon.name.isEmpty() ? "" : AppLanguage.get("New moon".equals(moon.name) ? R.string.moon_new : R.string.moon_full));
    calendarDetailIllumination.setText(
            AppLanguage.get(R.string.ui_lunar_illumination_1_s, moon.illumination)
    );

    // ---------------------------------------------------------
    // Find ALL events for selected day
    // ---------------------------------------------------------

    ArrayList<String> titles = new ArrayList<>();
    ZoneId zone = ZoneId.systemDefault();

    for (PublicCalendarRepository.Event event : events) {
        if (CalendarDates.includes(event, selectedCalendarDay, zone)) {
            titles.add(event.title);
        }
    }

    // ---------------------------------------------------------
    // Display events
    // ---------------------------------------------------------

    if (titles.isEmpty()) {

        calendarDetailEvent.setVisibility(View.GONE);

    } else {

        StringBuilder text = new StringBuilder(" \n");

        for (String title : titles) {
            text.append("\n ").append(title);
        }
         text.append("\n ");

        calendarDetailEvent.setText(text.toString());
        calendarDetailEvent.setVisibility(View.VISIBLE);
    }
}

    private void addCalendarEventCard(LinearLayout parent, PublicCalendarRepository.Event event) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.setBackground(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 15));
        card.addView(label(event.title, 17, LiquidTheme.text(this), true));
        DateFormat format = preferences.getBoolean("24h", false)
                ? new java.text.SimpleDateFormat("HH:mm", AppLanguage.locale())
                : new java.text.SimpleDateFormat("h:mm a", AppLanguage.locale());
        String timing = event.allDay ? AppLanguage.get(R.string.ui_all_day) : format.format(new Date(event.startMs))
                + " – " + format.format(new Date(event.endMs));
        card.addView(label(timing, 13, LiquidTheme.secondaryText(this), false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(card, params);
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
    private final class CalendarDayButton extends Button {

    private int dayNumber;
    private final ArrayList<String> eventTitles = new ArrayList<>();
    private boolean today;

    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    CalendarDayButton(Context context) {
        super(context);

        setText("");
        setAllCaps(false);

        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);

        setMinWidth(0);
        setMinimumWidth(0);
        setMinHeight(0);
        setMinimumHeight(0);

        // Compact TV calendar cell.
        setPadding(dp(12), dp(6), dp(10), dp(2));

        configurePaints();
    }

    private void configurePaints() {

        // Large date number.
        datePaint.setTextSize(sp(20));
        datePaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // MON / TUE / WED...
        weekdayPaint.setTextSize(sp(9));
        weekdayPaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // Moon phase.
        moonPaint.setTextSize(sp(9));
        moonPaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // Holiday/event.
        eventPaint.setTextSize(sp(8));
        eventPaint.setTypeface(LiquidTheme.font(MainActivity.this));
        chipTextPaint.setTextSize(sp(9));
        chipTextPaint.setTypeface(LiquidTheme.font(MainActivity.this));
        chipTextPaint.setColor(Color.WHITE);

        todayPaint.setStyle(Paint.Style.FILL);
    }

    public void setContent(int dayNumber, List<String> events, boolean today) {
        this.dayNumber = dayNumber;
        this.eventTitles.clear();
        if (events != null) for (String event : events) {
            if (event != null && !event.trim().isEmpty() && this.eventTitles.size() < 3)
                this.eventTitles.add(event.trim());
        }
        this.today = today;

        invalidate();
    }

    public void setToday(boolean today) {
        if (this.today == today) {
            return;
        }

        this.today = today;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        /*
         * Important:
         * Don't call super.onDraw().
         *
         * Button has no normal text. We draw all content ourselves.
         */

        int save = canvas.save();

        /*
         * Absolutely prevent event/moon text from escaping
         * outside the rounded calendar tile.
         */
        canvas.clipRect(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom()
        );

        final int primary =
                LiquidTheme.text(MainActivity.this);

        final int secondary =
                LiquidTheme.secondaryText(MainActivity.this);

        final int eventColor =
                0xFFFFA0AE;

        datePaint.setColor(primary);
        weekdayPaint.setColor(secondary);
        moonPaint.setColor(secondary);
        eventPaint.setColor(eventColor);

        final float left = getPaddingLeft();
        final float right =
                getWidth() - getPaddingRight();

        /*
         * =========================================================
         * ROW 1
         *
         * 29  THU                              ●
         * =========================================================
         */

        Paint.FontMetrics dateFm =
                datePaint.getFontMetrics();

        float row1Baseline =
                getPaddingTop() - dateFm.ascent;

        canvas.drawText(
                String.valueOf(dayNumber),
                left,
                row1Baseline,
                datePaint
        );

        /*
         * TODAY DOT
         */

        if (today) {

            todayPaint.setColor(
                    LiquidTheme.isLight(MainActivity.this)
                            ? 0xFFD32F2F
                            : 0xFFFFE0E0
            );

            float radius = dp(3);

            canvas.drawCircle(
                    right - radius,
                    getPaddingTop() + radius,
                    radius,
                    todayPaint
            );
        }

        float chipTop = row1Baseline + dp(3);
        float chipHeight = dp(13);
        for (int i = 0; i < eventTitles.size(); i++) {
            if (chipTop + chipHeight > getHeight() - getPaddingBottom()) break;
            String eventTitle = eventTitles.get(i);
            int chipColor = calendarEventColor(eventTitle);
            chipPaint.setColor(chipColor);
            canvas.drawRoundRect(left, chipTop, right, chipTop + chipHeight, dp(7), dp(7), chipPaint);
            todayPaint.setColor(calendarEventDotColor(eventTitle));
            canvas.drawCircle(left + dp(6), chipTop + chipHeight / 2f, dp(3), todayPaint);
            String title = ellipsize(eventTitle, chipTextPaint, right - left - dp(20));
            Paint.FontMetrics chipMetrics = chipTextPaint.getFontMetrics();
            float baseline = chipTop + (chipHeight - chipMetrics.bottom + chipMetrics.top) / 2f - chipMetrics.top;
            canvas.drawText(title, left + dp(12), baseline, chipTextPaint);
            chipTop += chipHeight + dp(2);
        }

        canvas.restoreToCount(save);
    }

    private int calendarEventColor(String title) {
        String name = title.toLowerCase(Locale.ROOT);
        if (name.contains("ekadashi")) return 0xBB1252AE;
        if (name.contains("purnima")) return 0xAA166E4B;
        if (name.contains("lunch") || name.contains("holiday")) return 0xAA795A1C;
        if (name.contains("navratri") || name.contains("dusshera")) return 0xAA84244F;
        return 0xAA45418D;
    }

    private int calendarEventDotColor(String title) {
        String name = title.toLowerCase(Locale.ROOT);
        if (name.contains("ekadashi")) return 0xFF38AAFF;
        if (name.contains("purnima")) return 0xFF27DF7F;
        if (name.contains("lunch") || name.contains("holiday")) return 0xFFFFD438;
        if (name.contains("navratri") || name.contains("dusshera")) return 0xFFFF69AE;
        return 0xFFAD83FF;
    }

    /**
     * Ellipsizes text according to actual Paint measurements.
     *
     * Example:
     *
     * "Waning gibbous" -> "Waning gib…"
     * "Mahatma Gandhi Jayanti" -> "Mahatma Gand…"
     */
    private String ellipsize(
            String text,
            Paint paint,
            float availableWidth) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        if (availableWidth <= 0) {
            return "";
        }

        if (paint.measureText(text) <= availableWidth) {
            return text;
        }

        final String ellipsis = "…";

        float ellipsisWidth =
                paint.measureText(ellipsis);

        int end = text.length();

        while (end > 0) {

            float width =
                    paint.measureText(
                            text,
                            0,
                            end
                    );

            if (width + ellipsisWidth <= availableWidth) {
                break;
            }

            end--;
        }

        if (end <= 0) {
            return ellipsis;
        }

        return text
                .substring(0, end)
                .trim()
                + ellipsis;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private float sp(float value) {
        return value *
                getResources()
                        .getDisplayMetrics()
                        .scaledDensity;
    }
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

    static final class RegionTheme {
        final boolean isLight;
        final int primaryText;
        final int secondaryText;
        final int accentColor;
        final int dividerColor;
        final int shadowColor;
        final float shadowRadius;

        RegionTheme(boolean isLight, int primaryText, int secondaryText, int accentColor, int dividerColor, int shadowColor, float shadowRadius) {
            this.isLight = isLight;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.accentColor = accentColor;
            this.dividerColor = dividerColor;
            this.shadowColor = shadowColor;
            this.shadowRadius = shadowRadius;
        }
    }

    static final class HeaderWidgetThemes {
        final RegionTheme clock;
        final RegionTheme weather;
        final RegionTheme calendar;

        HeaderWidgetThemes(RegionTheme clock, RegionTheme weather, RegionTheme calendar) {
            this.clock = clock;
            this.weather = weather;
            this.calendar = calendar;
        }
    }

    private RegionTheme lightRegionTheme() {
        return new RegionTheme(true, 0xFF101820, 0xFF3C4B5A, 0xFF087D91, 0x38000000, 0x33FFFFFF, 2f);
    }

    private RegionTheme darkRegionTheme() {
        return new RegionTheme(false, Color.WHITE, 0xFFCCD0D6, 0xFF81B8F2, 0x55CDD0D4, 0xAA000000, 3.5f);
    }

    private RegionTheme analyzeRegionLuminance(android.graphics.Bitmap bitmap, float leftFrac, float topFrac, float rightFrac, float bottomFrac) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return LiquidTheme.isLight(this) ? lightRegionTheme() : darkRegionTheme();
        }

        int bmW = bitmap.getWidth();
        int bmH = bitmap.getHeight();

        int startX = Math.max(0, Math.min(bmW - 1, Math.round(leftFrac * bmW)));
        int endX = Math.max(startX + 1, Math.min(bmW, Math.round(rightFrac * bmW)));
        int startY = Math.max(0, Math.min(bmH - 1, Math.round(topFrac * bmH)));
        int endY = Math.max(startY + 1, Math.min(bmH, Math.round(bottomFrac * bmH)));

        int stepX = Math.max(1, (endX - startX) / 16);
        int stepY = Math.max(1, (endY - startY) / 16);

        double totalLuminance = 0;
        double sumSqLuminance = 0;
        int count = 0;

        for (int y = startY; y < endY; y += stepY) {
            for (int x = startX; x < endX; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                totalLuminance += lum;
                sumSqLuminance += lum * lum;
                count++;
            }
        }

        if (count == 0) return LiquidTheme.isLight(this) ? lightRegionTheme() : darkRegionTheme();

        double avgLum = totalLuminance / count;
        double variance = (sumSqLuminance / count) - (avgLum * avgLum);
        boolean isComplex = variance > 0.025;

        boolean isLightBackground = avgLum > 0.52;

        if (isLightBackground) {
            int primary = 0xFF101820;
            int secondary = 0xFF3C4B5A;
            int accent = 0xFF087D91;
            int divider = 0x38000000;
            int shadow = isComplex ? 0x55FFFFFF : 0x22FFFFFF;
            float shadowRadius = isComplex ? 4f : 2f;
            return new RegionTheme(true, primary, secondary, accent, divider, shadow, shadowRadius);
        } else {
            int primary = Color.WHITE;
            int secondary = 0xFFCCD0D6;
            int accent = 0xFF7BE7F4;
            int divider = 0x55CDD0D4;
            int shadow = isComplex ? 0xEE000000 : 0xAA000000;
            float shadowRadius = isComplex ? 5f : 3.5f;
            return new RegionTheme(false, primary, secondary, accent, divider, shadow, shadowRadius);
        }
    }

    private HeaderWidgetThemes analyzeHeaderWidgetThemes(android.graphics.Bitmap bitmap) {
        if (!LiquidTheme.isLight(this) || bitmap == null || bitmap.isRecycled()) {
            RegionTheme fallback = LiquidTheme.isLight(this) ? lightRegionTheme() : darkRegionTheme();
            return new HeaderWidgetThemes(fallback, fallback, fallback);
        }

        float clockLeft = 0.02f, clockRight = 0.32f;
        float weatherLeft = 0.32f, weatherRight = 0.68f;
        float calendarLeft = 0.68f, calendarRight = 0.98f;
        float topFrac = 0.0f, bottomFrac = 0.30f;

        if (widgetHeader != null && widgetHeader.getWidth() > 0) {
            float hW = widgetHeader.getWidth();
            if (timeCard != null && timeCard.getWidth() > 0) {
                clockLeft = Math.max(0f, (float) timeCard.getLeft() / hW);
                clockRight = Math.min(1f, (float) timeCard.getRight() / hW);
            }
            if (weatherCard != null && weatherCard.getWidth() > 0) {
                weatherLeft = Math.max(0f, (float) weatherCard.getLeft() / hW);
                weatherRight = Math.min(1f, (float) weatherCard.getRight() / hW);
            }
            if (homeCalendarCard != null && homeCalendarCard.getWidth() > 0) {
                calendarLeft = Math.max(0f, (float) homeCalendarCard.getLeft() / hW);
                calendarRight = Math.min(1f, (float) homeCalendarCard.getRight() / hW);
            }
        }

        RegionTheme clockTheme = analyzeRegionLuminance(bitmap, clockLeft, topFrac, clockRight, bottomFrac);
        RegionTheme weatherTheme = analyzeRegionLuminance(bitmap, weatherLeft, topFrac, weatherRight, bottomFrac);
        RegionTheme calendarTheme = analyzeRegionLuminance(bitmap, calendarLeft, topFrac, calendarRight, bottomFrac);

        return new HeaderWidgetThemes(clockTheme, weatherTheme, calendarTheme);
    }

    private void updateWidgetThemesForBitmap(android.graphics.Bitmap bitmap, String key) {
        if (bitmap == null || bitmap.isRecycled()) {
            RegionTheme fallback = LiquidTheme.isLight(this) ? lightRegionTheme() : darkRegionTheme();
            applyOrAnimateWidgetThemes(new HeaderWidgetThemes(fallback, fallback, fallback));
            return;
        }

        if (key != null) {
            HeaderWidgetThemes cached = widgetThemeCache.get(key);
            if (cached != null) {
                applyOrAnimateWidgetThemes(cached);
                return;
            }
        }

        lifecycleWork.execute(ioWorker, () -> {
            if (bitmap.isRecycled()) return;
            HeaderWidgetThemes themes = analyzeHeaderWidgetThemes(bitmap);
            if (key != null) widgetThemeCache.put(key, themes);
            lifecycleWork.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                applyOrAnimateWidgetThemes(themes);
            });
        });
    }

    private void applyOrAnimateWidgetThemes(HeaderWidgetThemes themes) {
        if (themes == null) return;
        currentWidgetThemes = themes;

        if (clock == null || weatherTemperature == null || homeCalendarTitle == null) return;

        if (currentWidgetThemeAnimator != null) {
            currentWidgetThemeAnimator.cancel();
            currentWidgetThemeAnimator = null;
        }

        if (!ValueAnimator.areAnimatorsEnabled()) {
            applyWidgetThemesInstantly(themes);
            return;
        }

        int startClockPrimary = clock.getCurrentTextColor();
        int startClockSecondary = date.getCurrentTextColor();
        int startClockPeriod = clockPeriod.getCurrentTextColor();
        int startTimeRule = timeRule != null && timeRule.getBackground() instanceof ColorDrawable
                ? ((ColorDrawable) timeRule.getBackground()).getColor() : 0;

        int startWeatherPrimary = weatherTemperature.getCurrentTextColor();
        int startWeatherSecondary = weatherCondition.getCurrentTextColor();
        int startWeatherSymbol = weatherSymbol.getCurrentTextColor();
        int startEventRule = eventRule != null && eventRule.getBackground() instanceof ColorDrawable
                ? ((ColorDrawable) eventRule.getBackground()).getColor() : 0;

        int startCalendarPrimary = homeCalendarTitle.getCurrentTextColor();
        int startCalendarSecondary = homeCalendarEyebrow.getCurrentTextColor();
        int startCalendarGlyph = calendarEventGlyph != null ? calendarEventGlyph.getGlyphColor() : 0;
        int startCalendarDivider = calendarDivider != null && calendarDivider.getBackground() instanceof ColorDrawable
                ? ((ColorDrawable) calendarDivider.getBackground()).getColor() : 0;

        ArgbEvaluator evaluator = new ArgbEvaluator();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(350);
        animator.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();

            // Clock region
            int clockPrimary = (int) evaluator.evaluate(f, startClockPrimary, themes.clock.primaryText);
            int clockSecondary = (int) evaluator.evaluate(f, startClockSecondary, themes.clock.secondaryText);
            int clockPeriodCol = (int) evaluator.evaluate(f, startClockPeriod, themes.clock.secondaryText);
            clock.setTextColor(clockPrimary);
            date.setTextColor(clockSecondary);
            clockPeriod.setTextColor(clockPeriodCol);
            if (timeRule != null) timeRule.setBackgroundColor((int) evaluator.evaluate(f, startTimeRule, themes.clock.dividerColor));

            // Weather region
            int weatherPrimary = (int) evaluator.evaluate(f, startWeatherPrimary, themes.weather.primaryText);
            int weatherSecondary = (int) evaluator.evaluate(f, startWeatherSecondary, themes.weather.secondaryText);
            int weatherSymCol = (int) evaluator.evaluate(f, startWeatherSymbol, themes.weather.isLight ? 0xFFE67E22 : 0xFFFFBD38);
            weatherTemperature.setTextColor(weatherPrimary);
            weatherCondition.setTextColor(weatherSecondary);
            weatherDetails.setTextColor(weatherSecondary);
            weatherLocation.setTextColor(weatherSecondary);
            weatherMetrics.setTextColor(weatherPrimary);
            weatherSymbol.setTextColor(weatherSymCol);
            if (eventRule != null) eventRule.setBackgroundColor((int) evaluator.evaluate(f, startEventRule, themes.weather.dividerColor));

            // Calendar region
            int calendarPrimary = (int) evaluator.evaluate(f, startCalendarPrimary, themes.calendar.primaryText);
            int calendarSecondary = (int) evaluator.evaluate(f, startCalendarSecondary, themes.calendar.secondaryText);
            int calendarAccent = (int) evaluator.evaluate(f, startCalendarGlyph, themes.calendar.accentColor);
            int calendarDiv = (int) evaluator.evaluate(f, startCalendarDivider, themes.calendar.dividerColor);
            homeCalendarTitle.setTextColor(calendarPrimary);
            homeCalendarEyebrow.setTextColor(calendarSecondary);
            homeCalendarSubtitle.setTextColor(calendarSecondary);
            if (calendarEventGlyph != null) {
                calendarEventGlyph.setGlyphColor(calendarAccent);
                if (calendarIcon != null) calendarIcon.invalidate();
            }
            if (calendarDivider != null) calendarDivider.setBackgroundColor(calendarDiv);
        });

        applyShadowLayer(clock, themes.clock);
        applyShadowLayer(clockPeriod, themes.clock);
        applyShadowLayer(date, themes.clock);

        applyShadowLayer(weatherTemperature, themes.weather);
        applyShadowLayer(weatherCondition, themes.weather);
        applyShadowLayer(weatherDetails, themes.weather);
        applyShadowLayer(weatherLocation, themes.weather);
        applyShadowLayer(weatherMetrics, themes.weather);

        applyShadowLayer(homeCalendarTitle, themes.calendar);
        applyShadowLayer(homeCalendarEyebrow, themes.calendar);
        applyShadowLayer(homeCalendarSubtitle, themes.calendar);

        currentWidgetThemeAnimator = animator;
        animator.start();
    }

    private void applyShadowLayer(TextView tv, RegionTheme theme) {
        if (tv == null || theme == null) return;
        tv.setShadowLayer(dp(Math.round(theme.shadowRadius)), 0, dp(1), theme.shadowColor);
    }

    private void applyWidgetThemesInstantly(HeaderWidgetThemes themes) {
        if (themes == null) return;

        // Clock
        if (clock != null) clock.setTextColor(themes.clock.primaryText);
        if (date != null) date.setTextColor(themes.clock.secondaryText);
        if (clockPeriod != null) clockPeriod.setTextColor(themes.clock.secondaryText);
        if (timeRule != null) timeRule.setBackgroundColor(themes.clock.dividerColor);

        // Weather
        if (weatherTemperature != null) weatherTemperature.setTextColor(themes.weather.primaryText);
        if (weatherCondition != null) weatherCondition.setTextColor(themes.weather.secondaryText);
        if (weatherDetails != null) weatherDetails.setTextColor(themes.weather.secondaryText);
        if (weatherLocation != null) weatherLocation.setTextColor(themes.weather.secondaryText);
        if (weatherMetrics != null) weatherMetrics.setTextColor(themes.weather.primaryText);
        if (weatherSymbol != null) weatherSymbol.setTextColor(themes.weather.isLight ? 0xFFE67E22 : 0xFFFFBD38);
        if (eventRule != null) eventRule.setBackgroundColor(themes.weather.dividerColor);

        // Calendar
        if (homeCalendarTitle != null) homeCalendarTitle.setTextColor(themes.calendar.primaryText);
        if (homeCalendarEyebrow != null) homeCalendarEyebrow.setTextColor(themes.calendar.secondaryText);
        if (homeCalendarSubtitle != null) homeCalendarSubtitle.setTextColor(themes.calendar.secondaryText);
        if (calendarEventGlyph != null) {
            calendarEventGlyph.setGlyphColor(themes.calendar.accentColor);
            if (calendarIcon != null) calendarIcon.invalidate();
        }
        if (calendarDivider != null) calendarDivider.setBackgroundColor(themes.calendar.dividerColor);

        applyShadowLayer(clock, themes.clock);
        applyShadowLayer(clockPeriod, themes.clock);
        applyShadowLayer(date, themes.clock);

        applyShadowLayer(weatherTemperature, themes.weather);
        applyShadowLayer(weatherCondition, themes.weather);
        applyShadowLayer(weatherDetails, themes.weather);
        applyShadowLayer(weatherLocation, themes.weather);
        applyShadowLayer(weatherMetrics, themes.weather);

        applyShadowLayer(homeCalendarTitle, themes.calendar);
        applyShadowLayer(homeCalendarEyebrow, themes.calendar);
        applyShadowLayer(homeCalendarSubtitle, themes.calendar);
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
        v.setTypeface(LiquidTheme.font(this));
        return v;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTypeface(LiquidTheme.font(this));
        b.setTextSize(14);
        b.setTextColor(LiquidTheme.text(this));
        b.setAllCaps(false);
        b.setFocusable(true);
        styleFocusableTile(b);
        return b;
    }

    private void stylePrimaryButton(Button button) {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 18));
        button.setBackground(states);
        button.setTextColor(LiquidTheme.text(this));
        button.setTypeface(LiquidTheme.font(this));
        button.setLetterSpacing(.035f);
        button.setOnFocusChangeListener(LiquidTheme::animateFocus);
    }

    private void updateClock() {
        if (clock == null) return;
        boolean twentyFour = preferences.getBoolean("24h", false);
        Date now = new Date();
        DateFormat time = android.text.format.DateFormat.getTimeFormat(this);
        if (twentyFour) time = new java.text.SimpleDateFormat("HH:mm", AppLanguage.locale());
        else time = new java.text.SimpleDateFormat("h:mm a", AppLanguage.locale());
        String formattedTime = time.format(now);
        String period = "";
        int suffixStart = twentyFour ? -1 : formattedTime.lastIndexOf(' ');
        if (suffixStart >= 0) {
            period = formattedTime.substring(suffixStart + 1).toUpperCase(AppLanguage.locale());
            formattedTime = formattedTime.substring(0, suffixStart);
        }
        SpannableString styledTime = new SpannableString(formattedTime);
        int separator = formattedTime.indexOf(':');
        if (separator >= 0) {
            int minuteEnd = Math.min(formattedTime.length(), separator + 3);
            styledTime.setSpan(new ForegroundColorSpan(0xFFFFA94D), separator + 1, minuteEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        clock.setText(formattedTime);
        clockPeriod.setText(period);
        clockPeriod.setVisibility(twentyFour ? View.GONE : View.VISIBLE);
        clockDivider.setVisibility(View.GONE);
        date.setVisibility(preferences.getBoolean("showDate", true)
                ? View.VISIBLE : View.GONE);
        date.setText(new java.text.SimpleDateFormat("EEE, dd MMM yyyy", AppLanguage.locale()).format(now).toUpperCase(AppLanguage.locale()));
    }

    private void updateNowPlaying() {
        if (nowPlayingSection == null) return;
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        ComponentName listener = new ComponentName(this, PlaybackListenerService.class);
        boolean granted = enabled != null && enabled.contains(listener.flattenToString());
        activeMediaController = null;
        if (granted) {
            try {
                MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                MediaController pausedController = null;
                if (manager != null) for (MediaController controller : manager.getActiveSessions(listener)) {
                    MediaMetadata metadata = controller.getMetadata();
                    PlaybackState state = controller.getPlaybackState();
                    if (metadata == null || state == null) continue;
                    if (state.getState() == PlaybackState.STATE_PLAYING) {
                        activeMediaController = controller;
                        break;
                    }
                    if (state.getState() == PlaybackState.STATE_PAUSED && pausedController == null)
                        pausedController = controller;
                }
                if (activeMediaController == null) activeMediaController = pausedController;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        MediaController controller = activeMediaController;
        boolean hasMedia = controller != null;
        nowPlayingSection.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
        if (appRow != null) {
            int upId = (hasMedia && musicToggle != null) ? musicToggle.getId() : View.NO_ID;
            for (int i = 0; i < appRow.getChildCount(); i++) {
                appRow.getChildAt(i).setNextFocusUpId(upId);
            }
        }
        if (hasMedia && musicToggle != null && tabEntryFocus[TAB_HOME] != null) {
            musicToggle.setNextFocusDownId(tabEntryFocus[TAB_HOME].getId());
        }
        if (controller == null) return;
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        musicTitle.setText(TextUtils.isEmpty(title) ? controller.getPackageName() : title);
        musicArtist.setText(TextUtils.isEmpty(artist) ? AppLanguage.get(R.string.ui_playing_on_your_tv) : artist);
        android.graphics.Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (artwork == null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (artwork != null) musicArtwork.setImageBitmap(artwork);
        else musicArtwork.setImageResource(android.R.drawable.ic_media_play);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += Math.max(0, (long) (elapsed * state.getPlaybackSpeed()));
        }
        musicProgress.setProgress(duration > 0
                ? (int) Math.min(1000, Math.max(0, position * 1000 / duration)) : 0);
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        musicToggle.setText(playing ? "Ⅱ" : "▶");
        musicToggle.setContentDescription(playing ? AppLanguage.get(R.string.ui_pause_playback) : AppLanguage.get(R.string.ui_resume_playback));
    }

    private void toggleMediaPlayback() {
        if (activeMediaController == null || activeMediaController.getPlaybackState() == null) return;
        if (activeMediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
            activeMediaController.getTransportControls().pause();
        else activeMediaController.getTransportControls().play();
        handler.postDelayed(this::updateNowPlaying, 250);
    }

    private void openMusicAccessSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (android.content.ActivityNotFoundException missing) {
            Toast.makeText(this, AppLanguage.get(R.string.ui_open_android_settings_and_allow_sva_now_playing_notification_acc),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasWeatherLocation() {
        return WeatherRepository.validCoordinates(Double.longBitsToDouble(preferences.getLong("weatherLatitude", Double.doubleToRawLongBits(Double.NaN))),
                Double.longBitsToDouble(preferences.getLong("weatherLongitude", Double.doubleToRawLongBits(Double.NaN))));
    }

    private WeatherRepository.Location savedWeatherLocation() {
        double latitude = Double.longBitsToDouble(preferences.getLong("weatherLatitude", Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(preferences.getLong("weatherLongitude", Double.doubleToRawLongBits(Double.NaN)));
        if (!WeatherRepository.validCoordinates(latitude, longitude)) return null;
        return new WeatherRepository.Location(preferences.getString("weatherLocationLabel", AppLanguage.get(R.string.ui_weather)), latitude, longitude);
    }

    private WeatherRepository.Forecast cachedWeather() {
        try { return WeatherRepository.Forecast.fromJson(new JSONObject(preferences.getString("weatherCache", ""))); }
        catch (Exception ignored) { return null; }
    }

    private void renderWeather() {
        if (weatherPanel == null) return;
        boolean light = LiquidTheme.isLight(this);
        if (weatherForecast == null) {
            weatherSymbol.setText("☀");
            weatherTemperature.setText(hasWeatherLocation() ? AppLanguage.get(R.string.ui_weather_unavailable) : AppLanguage.get(R.string.ui_weather_not_set));
            weatherTemperature.setTextSize(compactNavigation ? 14 : 16);
            weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : 0xFFF5FAF8);
            weatherCondition.setText(hasWeatherLocation() ? AppLanguage.get(R.string.ui_refresh_in_settings) : AppLanguage.get(R.string.ui_configure_in_settings));
            weatherCondition.setVisibility(View.VISIBLE);
            weatherLocation.setText("");
            weatherMetrics.setText("");
            weatherDetails.setText("");
            weatherDetails.setVisibility(View.GONE);
            weatherPanel.setContentDescription(weatherTemperature.getText() + ". " + weatherCondition.getText());
            return;
        }
        boolean fahrenheit = preferences.getBoolean("weatherFahrenheit", false);
        weatherSymbol.setText(WeatherRepository.symbol(weatherForecast.code, weatherForecast.day));
        double temperature = fahrenheit ? WeatherRepository.fahrenheit(weatherForecast.temperature) : weatherForecast.temperature;
        weatherTemperature.setTextSize(compactNavigation ? 28 : 32);
        weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        String temperatureText = AppLanguage.number(Math.round(temperature)) + (fahrenheit ? "°F" : "°C");
        SpannableString styledTemperature = new SpannableString(temperatureText);
        int degree = temperatureText.indexOf('°');
        if (degree >= 0) styledTemperature.setSpan(new ForegroundColorSpan(0xFFFFA94D), degree,
                temperatureText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        weatherTemperature.setText(temperatureText);
        boolean mph = preferences.getBoolean("weatherMph", false);
        double wind = mph ? WeatherRepository.milesPerHour(weatherForecast.windSpeed) : weatherForecast.windSpeed;
        double apparent = fahrenheit ? WeatherRepository.fahrenheit(weatherForecast.apparentTemperature)
                : weatherForecast.apparentTemperature;
        weatherCondition.setText(WeatherRepository.condition(weatherForecast.code));
        weatherCondition.setVisibility(View.VISIBLE);
        weatherDetails.setText(AppLanguage.get(R.string.ui_feels_like_1_s_2_s, AppLanguage.number(Math.round(apparent)), (fahrenheit ? "°F" : "°C")));
        weatherDetails.setVisibility(View.VISIBLE);
        weatherLocation.setText("⌖  " + AppLanguage.bidi(weatherForecast.location));
        String windUnit = mph ? (" " + AppLanguage.get(R.string.ui_mph).trim()) : " km/h";
        weatherMetrics.setText("≋  " + AppLanguage.number(Math.round(wind)) + windUnit
                + (weatherForecast.humidity >= 0 ? "    💧 " + weatherForecast.humidity + "%" : ""));
        weatherPanel.setContentDescription(AppLanguage.get(R.string.ui_1_s_2_s_3_s_wind_4_s_5_s, weatherTemperature.getText(), weatherCondition.getText(), weatherForecast.location, Math.round(wind), windUnit));
        if (currentWidgetThemes != null) {
            applyWidgetThemesInstantly(currentWidgetThemes);
        }
    }

    private void showWeatherForecast() {
        if (weatherForecast == null) {
            openWeatherSettings();
            return;
        }
        boolean fahrenheit = preferences.getBoolean("weatherFahrenheit", false);
        StringBuilder details = new StringBuilder();
        for (WeatherRepository.Day day : weatherForecast.days) {
            String weekday = day.date;
            try { weekday = new java.text.SimpleDateFormat("EEE", AppLanguage.locale()).format(
                    new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)); } catch (Exception ignored) { }
            double low = fahrenheit ? WeatherRepository.fahrenheit(day.low) : day.low;
            double high = fahrenheit ? WeatherRepository.fahrenheit(day.high) : day.high;
            if (details.length() > 0) details.append("\n\n");
            details.append(weekday).append("  ·  ").append(Math.round(low)).append("° / ")
                    .append(Math.round(high)).append("°  ·  ").append(WeatherRepository.condition(day.code));
        }
        AlertDialog dialog = new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,
                R.style.SvaDeviceAlertDialogTheme))
                .setTitle(weatherForecast.location)
                .setMessage(details.length() > 0 ? details.toString() : AppLanguage.get(R.string.ui_forecast_unavailable))
                .setPositiveButton(AppLanguage.get(R.string.ui_close), null)
                .create();
        Motion.dialog(dialog); dialog.show();
        styleDialog(dialog);
    }

    private void maybeRefreshWeather(boolean manual) {
        WeatherRepository.Location location = savedWeatherLocation();
        if (location == null || weatherRefreshing) return;
        long interval = scheduleValue(SchedulePreferences.WEATHER, SchedulePreferences.WEATHER_VALUES,
                SchedulePreferences.DEFAULT_WEATHER);
        if (!manual) {
            if (interval == SchedulePreferences.MANUAL) return;
            if (interval == SchedulePreferences.EVERY_LAUNCH) {
                if (weatherRefreshedThisLaunch) return;
                weatherRefreshedThisLaunch = true;
            } else {
                long last = Math.max(preferences.getLong("weatherUpdatedAt", 0), preferences.getLong("weatherAttemptedAt", 0));
                if (System.currentTimeMillis() - last < interval) return;
            }
        }
        refreshWeather(location);
    }

    private void refreshWeather(WeatherRepository.Location location) {
        weatherRefreshing = true; updateTaskProgress();
        preferences.edit().putLong("weatherAttemptedAt", System.currentTimeMillis()).apply();
        int generation = ++weatherRequestGeneration;
        if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_refreshing_weather));
        lifecycleWork.execute(ioWorker, () -> {
            WeatherRepository.Forecast forecast = null; String error = null;
            try { forecast = WeatherRepository.fetch(location, System.currentTimeMillis()); }
            catch (Exception failure) { error = failure.getMessage() == null ? AppLanguage.get(R.string.ui_could_not_refresh_weather) : failure.getMessage(); }
            WeatherRepository.Forecast result = forecast; String resultError = error;
            lifecycleWork.post(() -> {
                if (generation != weatherRequestGeneration || isFinishing() || isDestroyed()) return;
                weatherRefreshing = false; updateTaskProgress();
                if (result != null) {
                    weatherForecast = result;
                    try { preferences.edit().putString("weatherCache", result.toJson().toString())
                            .putLong("weatherUpdatedAt", result.fetchedAt).apply(); } catch (Exception ignored) { }
                    renderWeather();
                    if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_weather_refreshed));
                } else if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_refresh_failed_1_s, resultError));
                scheduleWeatherRefresh();
            });
        });
    }

    private void scheduleWeatherRefresh() {
        if (weatherRefreshTick == null) return;
        handler.removeCallbacks(weatherRefreshTick);
        if (!screenActive || !hasWeatherLocation()) return;
        long interval = scheduleValue(SchedulePreferences.WEATHER, SchedulePreferences.WEATHER_VALUES,
                SchedulePreferences.DEFAULT_WEATHER);
        if (interval <= 0) return;
        long last = Math.max(preferences.getLong("weatherUpdatedAt", 0), preferences.getLong("weatherAttemptedAt", 0));
        handler.postDelayed(weatherRefreshTick, Math.max(1000L, interval - Math.max(0L, System.currentTimeMillis() - last)));
    }

    private void openWeatherSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.WEATHER;
        settingsScreen.show();
    }

    private void saveWeatherLocation(String mode, WeatherRepository.Location location) {
        weatherRequestGeneration++;
        weatherRefreshing = false; updateTaskProgress();
        WeatherRepository.Location previous = savedWeatherLocation();
        boolean changed = previous == null || Math.abs(previous.latitude - location.latitude) > .0001
                || Math.abs(previous.longitude - location.longitude) > .0001;
        SharedPreferences.Editor editor = preferences.edit().putString("weatherLocationMode", mode).putString("weatherLocationLabel", location.label)
                .putLong("weatherLatitude", Double.doubleToRawLongBits(location.latitude))
                .putLong("weatherLongitude", Double.doubleToRawLongBits(location.longitude));
        if (changed) editor.remove("weatherCache").remove("weatherUpdatedAt");
        editor.apply();
        if (changed) weatherForecast = null;
        weatherRefreshedThisLaunch = true; renderWeather(); refreshWeather(location);
        if (settingsScreen != null) settingsScreen.renderSection();
    }

    private void requestDeviceWeatherLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingDeviceWeatherLocation = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_WEATHER_LOCATION);
            return;
        }
        cancelWeatherLocation();
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_location_is_unavailable_on_this_tv)); return; }
        String provider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_turn_on_android_location_services_first)); return; }
        if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_finding_this_tv_s_location));
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                weatherLocationCancellation = new CancellationSignal();
                manager.getCurrentLocation(provider, weatherLocationCancellation, getMainExecutor(), this::acceptWeatherLocation);
            } else {
                weatherLocationListener = location -> acceptWeatherLocation(location);
                manager.requestSingleUpdate(provider, weatherLocationListener, Looper.getMainLooper());
            }
            weatherLocationTimeout = () -> {
                cancelWeatherLocation();
                if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_location_timed_out_try_city_search_instead));
            };
            handler.postDelayed(weatherLocationTimeout, 10000);
        } catch (SecurityException error) {
            if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_location_permission_was_not_granted));
        }
    }

    private void acceptWeatherLocation(android.location.Location location) {
        cancelWeatherLocation();
        if (location == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_could_not_determine_this_tv_s_location)); return; }
        double latitude = location.getLatitude(), longitude = location.getLongitude();
        lifecycleWork.execute(ioWorker, () -> {
            String label = AppLanguage.get(R.string.ui_current_location);
            // Geocoder requires Google Play Services. Many budget/OEM TV devices ship
            // without GMS; isPresent() returns false and getFromLocation() throws
            // IOException("Service not available"). Guard to avoid the silent failure.
            if (Geocoder.isPresent()) {
                try {
                    List<Address> addresses = new Geocoder(this, AppLanguage.locale()).getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String locality = address.getLocality() == null ? address.getSubAdminArea() : address.getLocality();
                        if (locality != null && !locality.trim().isEmpty()) label = locality;
                    }
                } catch (Exception ignored) { }
            }
            String result = label;
            lifecycleWork.post(() -> saveWeatherLocation("device", new WeatherRepository.Location(result, latitude, longitude)));
        });
    }

    private void cancelWeatherLocation() {
        if (weatherLocationTimeout != null) handler.removeCallbacks(weatherLocationTimeout);
        weatherLocationTimeout = null;
        if (weatherLocationCancellation != null) weatherLocationCancellation.cancel();
        weatherLocationCancellation = null;
        if (weatherLocationListener != null) {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager != null) try { manager.removeUpdates(weatherLocationListener); } catch (Exception ignored) { }
        }
        weatherLocationListener = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ActivitiesRepository.REQUEST_PERMISSION) {
            if (activitiesPanel != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) activitiesPanel.refresh();
            return;
        }
        if (requestCode != REQUEST_WEATHER_LOCATION) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (pendingDeviceWeatherLocation && granted) requestDeviceWeatherLocation();
        else if (!granted && settingsScreen != null) settingsScreen.setWeatherFeedback(AppLanguage.get(R.string.ui_location_denied_search_for_a_city_instead));
        pendingDeviceWeatherLocation = false;
    }

    private List<PublicCalendarRepository.Event> cachedCalendarEvents() {
        String source = preferences.getString("publicCalendarEvents", "[]");
        if (source.equals(parsedCalendarSource)) return parsedCalendarEvents;
        ArrayList<PublicCalendarRepository.Event> events = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(source);
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                long start = value.optLong("start", 0L), end = value.optLong("end", 0L);
                if (end > start) events.add(new PublicCalendarRepository.Event(
                        value.optString("title", AppLanguage.get(R.string.ui_calendar_event)), start, end,
                        value.optBoolean("allDay", false)));
            }
        } catch (Exception ignored) { }
        Collections.sort(events, (a, b) -> Long.compare(a.startMs, b.startMs));
        parsedCalendarSource = source;
        parsedCalendarEvents = Collections.unmodifiableList(events);
        return parsedCalendarEvents;
    }

    private void updateHomeCalendarWidget() {
        if (homeCalendarTitle == null || homeCalendarSubtitle == null) return;
        LocalDate today = LocalDate.now();
        PublicCalendarRepository.Event todayEvent = null;
        PublicCalendarRepository.Event nextEvent = null;
        for (PublicCalendarRepository.Event event : cachedCalendarEvents()) {
            if (CalendarDates.includes(event, today, ZoneId.systemDefault())) {
                todayEvent = event;
                break;
            }
            if (event.startMs > System.currentTimeMillis()) { nextEvent = event; break; }
        }
        if (todayEvent != null) {
            homeCalendarEyebrow.setText(AppLanguage.get(R.string.ui_today));
            homeCalendarTitle.setText(todayEvent.title);
            homeCalendarSubtitle.setText(todayEvent.allDay ? AppLanguage.get(R.string.ui_all_day)
                    : new java.text.SimpleDateFormat("h:mm a", AppLanguage.locale()).format(new Date(todayEvent.startMs)));
        } else if (nextEvent != null) {
            homeCalendarEyebrow.setText(AppLanguage.get(R.string.ui_up_next));
            homeCalendarTitle.setText(nextEvent.title);
            homeCalendarSubtitle.setText(new java.text.SimpleDateFormat("EEE · h:mm a", AppLanguage.locale())
                    .format(new Date(nextEvent.startMs)));
        } else {
            homeCalendarEyebrow.setText(AppLanguage.get(R.string.ui_today));
            homeCalendarTitle.setText(AppLanguage.get(R.string.ui_no_events_today));
            homeCalendarSubtitle.setText(preferences.getString("publicCalendarUrl", "").isEmpty()
                    ? AppLanguage.get(R.string.ui_add_a_calendar_in_settings) : AppLanguage.get(R.string.ui_your_calendar_is_clear));
        }
    }

    private String wrapHomeCalendarTitle(String value) {
        String clean = value == null ? AppLanguage.get(R.string.ui_calendar_event) : value.trim().replaceAll("\\s+", " ");
        String[] words = clean.split(" ");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder();
        int lineLength = 0;
        for (String word : words) {
            if (lineLength > 0 && lineLength + 1 + word.length() > 24) {
                result.append(line).append('\n');
                line.setLength(0);
                lineLength = 0;
            }
            if (lineLength > 0) { line.append(' '); lineLength++; }
            line.append(word);
            lineLength += word.length();
        }
        if (line.length() > 0) result.append(line);
        return result.toString();
    }

    private CalendarFeedCache calendarFeedCache() {
        try {
            return CalendarFeedCache.load(preferences.getString(CalendarFeedCache.KEY, ""),
                    preferences.getString("publicCalendarUrl", ""), preferences.getString("publicCalendarEvents", "[]"),
                    preferences.getLong("publicCalendarUpdatedAt", 0L));
        } catch (IllegalArgumentException invalidSavedUrls) {
            return CalendarFeedCache.load("", "", "[]", 0L);
        }
    }

    private void saveCalendarCache(CalendarFeedCache cache, List<String> urls, boolean refreshed) {
        try {
            List<PublicCalendarRepository.Event> events = cache.merged(urls, System.currentTimeMillis());
            android.content.SharedPreferences.Editor edit = preferences.edit()
                    .putString(CalendarFeedCache.KEY, cache.encode())
                    .putString("publicCalendarEvents", CalendarFeedCache.encodeEvents(events).toString())
                    .putInt("publicCalendarCacheVersion", CALENDAR_CACHE_VERSION);
            // Also invalidate the rendered month when sources are removed or reordered.
            renderedCalendarStamp = -1;
            if (refreshed) edit.putLong("publicCalendarUpdatedAt", System.currentTimeMillis());
            edit.apply();
            updateCalendarEventDisplay();
        } catch (Exception failure) { throw new IllegalStateException(AppLanguage.get(R.string.ui_could_not_save_calendar_cache), failure); }
    }

    private void cancelCalendarRefresh() {
        calendarRequestGeneration++;
        calendarRefreshing = false; updateTaskProgress();
        calendarRefreshingUrl = "";
    }

    private void updateCalendarEventDisplay() {
        updateHomeCalendarWidget();
        if (calendarMonthGrid == null || activeTab != TAB_EVENTS) return;
        List<PublicCalendarRepository.Event> cached = cachedCalendarEvents();
        LocalDate today = LocalDate.now();
        long stamp = preferences.getLong("publicCalendarUpdatedAt", 0L);
        if (today.equals(renderedToday) && displayedMonth.equals(renderedMonth)
                && stamp == renderedCalendarStamp && java.util.Objects.equals(parsedCalendarSource, renderedCalendarSource)) return;
        if (todayItems != null) todayItems.removeAllViews();
        int todayCount = 0;
        for (PublicCalendarRepository.Event event : cached) {
            if (CalendarDates.includes(event, today, ZoneId.systemDefault())) {
                if (todayItems != null) addCalendarEventCard(todayItems, event);
                todayCount++;
            }
        }
        boolean hasTodayEvents = todayCount > 0;
        if (todayHeading != null) todayHeading.setVisibility(View.VISIBLE);
        if (todayItems != null) todayItems.setVisibility(hasTodayEvents ? View.VISIBLE : View.GONE);
        if (todayEmpty != null) todayEmpty.setVisibility(hasTodayEvents ? View.GONE : View.VISIBLE);
        renderMonthCalendar(cached);
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
        if (url.isEmpty() || url.equals(calendarRefreshingUrl)) return;
        final List<String> urls;
        final CalendarFeedCache cache;
        try {
            urls = PublicCalendarRepository.normalizeUrls(url);
            cache = calendarFeedCache();
        } catch (IllegalArgumentException failure) {
            if (settingsScreen != null) settingsScreen.onCalendarRefreshResult(failure.getMessage());
            return;
        }
        if (urls.isEmpty()) return;
        calendarRefreshingUrl = url;
        calendarRefreshing = true; updateTaskProgress();
        preferences.edit().putLong("publicCalendarAttemptedAt", System.currentTimeMillis())
                .putInt("publicCalendarAttemptedVersion", CALENDAR_CACHE_VERSION).apply();
        int generation = ++calendarRequestGeneration;
        lifecycleWork.execute(calendarWorker, () -> {
            CalendarFeedCache.RefreshResult result = cache.refresh(urls, System.currentTimeMillis(), PublicCalendarRepository::download);
            lifecycleWork.post(() -> {
                if (generation != calendarRequestGeneration) return;
                calendarRefreshing = false; updateTaskProgress();
                calendarRefreshingUrl = "";
                if (isFinishing() || !url.equals(preferences.getString("publicCalendarUrl", ""))) return;
                saveCalendarCache(cache, urls, result.succeeded > 0);
                String message = result.message(cache.merged(urls, System.currentTimeMillis()).size());
                if (settingsScreen != null) settingsScreen.onCalendarRefreshResult(message);
                if (manual && !result.errors.isEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

    private void renderApps() {
        if (appRow == null) return;
        if (appsLoading) { appsRefreshPending = true; return; }
        appsLoading = true;
        if (!appsLoaded) {
            // Preserve the existing row and its focus during background refreshes.
            if (appRow.getChildCount() == 0) {
                TextView loading = label(AppLanguage.get(R.string.ui_loading_apps), 15, MUTED, false);
                loading.setGravity(Gravity.CENTER_VERTICAL);
                appRow.addView(loading, new LinearLayout.LayoutParams(dp(180), dp(72)));
            }
        }
        lifecycleWork.execute(ioWorker, () -> {
            List<AppEntry> loaded = installedApps();
            List<String> selected = selectedPackages(loaded);
            if (selected.isEmpty() && !preferences.getBoolean("customApps", false)) {
                for (AppEntry entry : loaded) selected.add(entry.packageName);
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
            lifecycleWork.post(() -> showApps(withIcons));
        });
    }

    private void showApps(List<AppEntry> all) {
        if (appRow == null || isFinishing()) return;
        appsLoading = false;
        if (appsRefreshPending) {
            appsRefreshPending = false;
            renderApps();
            return;
        }
        appsLoaded = true;
        View previous = appRow.findFocus();
        if (previous != null && previous.getTag() instanceof String) focusedPackage = (String) previous.getTag();
        appRow.removeAllViews();
        boolean light = LiquidTheme.isLight(this);
        int tileWidth = compactNavigation ? 148 : 167;
        List<String> selected = selectedPackages(all);
        if (selected.isEmpty() && !preferences.getBoolean("customApps", false)) {
            for (AppEntry entry : all) selected.add(entry.packageName);
        }
        int displayed = 0;
        for (String packageName : selected) {
            AppEntry entry = findApp(all, packageName);
            if (entry == null) continue;
            LinearLayout tile = new LinearLayout(this);
            tile.setId(View.generateViewId());
            tile.setGravity(Gravity.CENTER);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setFocusable(true);
            tile.setClickable(true);
            StateListDrawable appTileStates = new Motion.States();
            String brand = (entry.packageName + " " + entry.label).toLowerCase(Locale.ROOT);
            boolean isBranded = brand.contains("netflix") || brand.contains("prime") || brand.contains("disney")
                    || brand.contains("youtube") || brand.contains("apple");
            int cardColor = brand.contains("netflix") ? 0xED071316
                    : brand.contains("prime") ? 0xF00678B9
                    : brand.contains("disney") ? 0xF00D1A55
                    : brand.contains("youtube") ? 0xF4FAFAFA
                    : brand.contains("apple") ? 0xF4FAFAFA
                    : (light ? LiquidTheme.surface(this) : 0xCE102328);
            boolean whiteCard = brand.contains("youtube") || brand.contains("apple");
            int focusColor = isBranded ? cardColor : (light ? LiquidTheme.raisedSurface(this) : cardColor);
            int unfocusedBg = isBranded ? cardColor : (light ? LiquidTheme.surface(this) : 0xB5101C20);
            int unfocusedBorder = light ? LiquidTheme.border(this) : 0x66FFFFFF;
            int focusedBorder = LiquidTheme.accent(this);
            appTileStates.addState(new int[]{android.R.attr.state_focused},
                    roundedShape(focusColor, focusedBorder, 2, 16));
            appTileStates.addState(new int[]{android.R.attr.state_pressed},
                    roundedShape(focusColor, focusedBorder, 2, 16));
            appTileStates.addState(new int[]{},
                    roundedShape(unfocusedBg, unfocusedBorder, 1, 16));
            tile.setBackground(appTileStates);
            tile.setFocusableInTouchMode(false);
            tile.setElevation(dp(2));
            tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
            Motion.interaction(tile, false);
            tile.setPadding(dp(10), dp(4), dp(8), dp(4));
            tile.setTag(packageName);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            int nameColor;
            if (isBranded) {
                nameColor = whiteCard ? 0xFF101820 : Color.WHITE;
            } else {
                nameColor = light ? LiquidTheme.text(this) : Color.WHITE;
            }
            TextView name = label(entry.label, compactNavigation ? 12 : 13, nameColor, true);
            name.setTextColor(nameColor);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams appNameParams = new LinearLayout.LayoutParams(0, -1, 1);
            appNameParams.setMarginStart(dp(8));
            tile.addView(name, appNameParams);
            tile.setOnClickListener(v -> launchApp(entry.packageName));
            if (nowPlayingSection != null && nowPlayingSection.getVisibility() == View.VISIBLE && musicToggle != null) {
                tile.setNextFocusUpId(musicToggle.getId());
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(tileWidth), dp(64));
            lp.setMargins(0, 0, dp(10), 0);
            appRow.addView(tile, lp);
            if (displayed == 0) {
                tabEntryFocus[TAB_HOME] = tile;
                if (musicToggle != null) musicToggle.setNextFocusDownId(tile.getId());
            }
            displayed++;
            if (previous != null && initialAppFocus && activeTab == TAB_HOME && settingsScreen == null
                    && packageName.equals(focusedPackage)) tile.requestFocus();
        }
        if (displayed > 0) {
            tabButtons[TAB_HOME].setNextFocusUpId(appRow.getChildAt(0).getId());
            if (previous != null && activeTab == TAB_HOME && settingsScreen == null && appRow.findFocus() == null)
                appRow.getChildAt(0).requestFocus();
        }
        if (!initialAppFocus && activeTab == TAB_HOME && displayed > 0) {
            initialAppFocus = true;
            appRow.post(() -> {
                if (activeTab != TAB_HOME || settingsScreen != null) return;
                View destination = null;
                for (int i = 0; i < appRow.getChildCount(); i++) {
                    View child = appRow.getChildAt(i);
                    if (focusedPackage != null && focusedPackage.equals(child.getTag())) { destination = child; break; }
                    if (destination == null && child.isFocusable()) destination = child;
                }
                if (destination != null) destination.requestFocus();
            });
        }
        if (selected.isEmpty()) {
            TextView empty = label(AppLanguage.get(R.string.ui_no_apps_selected), 15, MUTED, false);
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
        if (youtubeRow == null || activeTab != TAB_SAVED) return;
        String source = preferences.getString("youtubeCards", "[]");
        if (source.equals(renderedSavedSource)) return;
        renderedSavedSource = source;
        List<YouTubeCard> cards = savedYouTubeCards();
        youtubeRow.setVisibility(cards.isEmpty() ? View.GONE : View.VISIBLE);
        youtubeEmpty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
        retrySavedArtwork = !preferences.getBoolean("youtubeArtworkFeedFallbackTried", false);
        if (retrySavedArtwork) preferences.edit().putBoolean("youtubeArtworkFeedFallbackTried", true).apply();
        savedCardAdapter.submit(cards);
        if (savedListState != null && !cards.isEmpty()) {
            youtubeRow.getLayoutManager().onRestoreInstanceState(savedListState);
            savedListState = null;
        }
    }

    private View createYouTubeTile(YouTubeCard card) {
        boolean hasThumbnail = card.thumbnailUrl != null && !card.thumbnailUrl.isEmpty();
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        tile.setPadding(dp(8), dp(8), dp(8), dp(7));
        StateListDrawable states = new Motion.States();
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
        Motion.interaction(tile, false);

        FrameLayout poster = new FrameLayout(this);
        GradientDrawable posterBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                light
                        ? new int[]{0xFFE0EBF0, 0xFFC5D9E2, 0xFFB2CAD6}
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
        TextView fallback = label(card.displayTitle(), 20, light ? 0xFF1A353D : Color.WHITE, true);
        fallback.setTextColor(light ? 0xFF1A353D : Color.WHITE);
        fallback.setGravity(Gravity.BOTTOM | Gravity.START);
        fallback.setMaxLines(3);
        fallback.setEllipsize(android.text.TextUtils.TruncateAt.END);
        fallback.setPadding(dp(15), dp(34), dp(15), dp(14));
        if (!light) fallback.setShadowLayer(dp(8), 0, dp(2), Color.BLACK);
        fallback.setContentDescription(AppLanguage.get(R.string.ui_artwork_1_s, card.displayTitle()));
        poster.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        TextView type = label(YouTubeCard.VIDEO.equals(card.type) ? AppLanguage.get(R.string.ui_video) : AppLanguage.get(R.string.ui_playlist),
                9, light ? LiquidTheme.accent(this) : Color.WHITE, true);
        type.setTextColor(light ? LiquidTheme.accent(this) : Color.WHITE);
        type.setLetterSpacing(.10f);
        type.setPadding(dp(9), dp(5), dp(9), dp(5));
        type.setBackground(roundedShape(light ? 0xD0FFFFFF : 0xDB121212, LiquidTheme.accent(this), 1, 5));
        FrameLayout.LayoutParams typeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        typeParams.setMargins(dp(8), dp(8), 0, 0);
        poster.addView(type, typeParams);
        tile.addView(poster, new LinearLayout.LayoutParams(dp(224), dp(hasThumbnail ? 126 : 178)));
        if (hasThumbnail) {
            TextView title = label(card.displayTitle(), 14, LiquidTheme.text(this), true);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            title.setPadding(dp(3), dp(6), dp(3), 0);
            tile.addView(title, new LinearLayout.LayoutParams(dp(224), dp(52)));
        }
        tile.setContentDescription((YouTubeCard.VIDEO.equals(card.type) ? AppLanguage.get(R.string.ui_play_video) : AppLanguage.get(R.string.ui_open_playlist)).trim() + " " + AppLanguage.bidi(card.displayTitle()));
        tile.setOnClickListener(view -> launchYouTubeCard(card));
        bindYouTubeTile(tile, card);
        linkTopControl(TAB_SAVED, tile);
        return tile;
    }

    private void bindYouTubeTile(LinearLayout tile, YouTubeCard card) {
        FrameLayout poster = (FrameLayout)tile.getChildAt(0);
        ImageView thumbnail = (ImageView)poster.getChildAt(0);
        TextView fallback = (TextView)poster.getChildAt(1);
        thumbnail.setTag(null); thumbnail.setImageDrawable(null);
        fallback.setText(card.displayTitle()); fallback.setVisibility(View.VISIBLE);
        fallback.setContentDescription(AppLanguage.get(R.string.ui_artwork_1_s, card.displayTitle()));
        ((TextView)poster.getChildAt(2)).setText(AppLanguage.get(YouTubeCard.VIDEO.equals(card.type)
                ? R.string.ui_video : R.string.ui_playlist));
        if (tile.getChildCount() > 1) ((TextView)tile.getChildAt(1)).setText(card.displayTitle());
        tile.setContentDescription((YouTubeCard.VIDEO.equals(card.type) ? AppLanguage.get(R.string.ui_play_video)
                : AppLanguage.get(R.string.ui_open_playlist)).trim() + " " + AppLanguage.bidi(card.displayTitle()));
        tile.setOnClickListener(view -> launchYouTubeCard(card));
        boolean retryMissingArtwork = retrySavedArtwork;
        loadYouTubeThumbnail(card, thumbnail, fallback);
        long refreshAfter = card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()
                ? 60L * 60L * 1000L : 24L * 60L * 60L * 1000L;
        if (card.metadataCheckedAt == 0L
                || System.currentTimeMillis() - card.metadataCheckedAt > refreshAfter
                || (retryMissingArtwork && (card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()))) {
            fetchYouTubeMetadata(card.url);
        }
    }

    private final class SavedCardAdapter extends RecyclerView.Adapter<SavedCardAdapter.Holder> {
        private List<YouTubeCard> cards = Collections.emptyList();
        void submit(List<YouTubeCard> next) {
            List<YouTubeCard> previous = cards;
            androidx.recyclerview.widget.DiffUtil.DiffResult diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                    new androidx.recyclerview.widget.DiffUtil.Callback() {
                public int getOldListSize() { return previous.size(); }
                public int getNewListSize() { return next.size(); }
                public boolean areItemsTheSame(int a, int b) { return previous.get(a).url.equals(next.get(b).url); }
                public boolean areContentsTheSame(int a, int b) {
                    YouTubeCard old = previous.get(a), card = next.get(b);
                    return java.util.Objects.equals(old.title, card.title)
                            && java.util.Objects.equals(old.thumbnailUrl, card.thumbnailUrl)
                            && old.customTitle == card.customTitle && old.customThumbnail == card.customThumbnail;
                }
            });
            cards = next;
            diff.dispatchUpdatesTo(this);
        }
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout host = new FrameLayout(MainActivity.this);
            host.setClipChildren(false); host.setClipToPadding(false);
            host.setPadding(dp(10), dp(10), dp(14), dp(10));
            host.setLayoutParams(new RecyclerView.LayoutParams(dp(264), dp(218)));
            return new Holder(host);
        }
        public void onBindViewHolder(Holder holder, int position) {
            if (holder.host.getChildCount() == 0) {
                View tile = createYouTubeTile(cards.get(position));
                holder.host.addView(tile, new FrameLayout.LayoutParams(dp(240), dp(198)));
            } else bindYouTubeTile((LinearLayout)holder.host.getChildAt(0), cards.get(position));
        }
        @Override public int getItemViewType(int position) {
            String thumbnail = cards.get(position).thumbnailUrl;
            return thumbnail == null || thumbnail.isEmpty() ? 0 : 1;
        }
        @Override public void onViewRecycled(Holder holder) {
            if (holder.host.getChildCount() > 0) {
                ImageView image = (ImageView)((FrameLayout)((LinearLayout)holder.host.getChildAt(0)).getChildAt(0)).getChildAt(0);
                image.setTag(null); image.setImageDrawable(null);
            }
        }
        public int getItemCount() { return cards.size(); }
        final class Holder extends RecyclerView.ViewHolder {
            final FrameLayout host;
            Holder(FrameLayout host) { super(host); this.host = host; }
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
            else Toast.makeText(this, AppLanguage.get(R.string.ui_no_app_can_open_this_youtube_link), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, AppLanguage.get(R.string.ui_could_not_open_this_youtube_link), Toast.LENGTH_LONG).show();
        }
    }

    private void fetchYouTubeMetadata(String url) {
        synchronized (youtubeMetadataInFlight) {
            if (!youtubeMetadataInFlight.add(url)) return;
        }
        lifecycleWork.execute(youtubeWorker, () -> {
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
            lifecycleWork.post(() -> {
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
                            return String.format(java.util.Locale.ROOT, "https://i.ytimg.com/vi/%1$s/hqdefault.jpg", videoId);
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
        String request = card.url + "|" + card.thumbnailUrl;
        image.setTag(request);
        android.graphics.Bitmap cached = youtubeThumbnailCache.get(card.thumbnailUrl);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            fallback.setVisibility(View.GONE);
            return;
        }
        lifecycleWork.execute(youtubeWorker, () -> {
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
            if (!lifecycleWork.active()) return;
            youtubeThumbnailCache.put(card.thumbnailUrl, bitmap);
            android.graphics.Bitmap result = bitmap;
            lifecycleWork.post(() -> {
                if (request.equals(image.getTag())) {
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
        icon.setColorFilter(LiquidTheme.isLight(this) ? 0xFF24403D : 0xD4FFFFFF);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(compactNavigation ? 8 : 10), dp(compactNavigation ? 8 : 10),
                dp(compactNavigation ? 8 : 10), dp(compactNavigation ? 8 : 10));
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        boolean light = LiquidTheme.isLight(this);
        int focusFill = light ? 0x22087D91 : 0x24FFFFFF;
        int focusStroke = light ? LiquidTheme.accent(this) : 0x4DFFFFFF;
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(focusFill, focusStroke, 1, 22));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(focusFill, focusStroke, 1, 22));
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        icon.setBackground(states);
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        icon.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(compactNavigation ? 34 : 40), dp(compactNavigation ? 34 : 40));
        lp.setMargins(dp(2), 0, dp(2), 0);
        container.addView(icon, lp);
    }

    private void openTvInputs() {
        List<android.media.tv.TvInputInfo> inputs = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();
        try {
            android.media.tv.TvInputManager manager = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (manager != null) for (android.media.tv.TvInputInfo input : manager.getTvInputList()) {
                if (input.isHidden(this)) continue;
                CharSequence name = input.loadCustomLabel(this);
                if (TextUtils.isEmpty(name)) name = input.loadLabel(this);
                inputs.add(input);
                labels.add(TextUtils.isEmpty(name) ? AppLanguage.get(R.string.ui_tv_input_4b66ee8) : name);
            }
        } catch (SecurityException unavailable) {
            // Some TV manufacturers restrict input discovery to their system apps.
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,
                LiquidTheme.alertDialogTheme(this))).setTitle(AppLanguage.get(R.string.ui_tv_input));
        if (inputs.isEmpty()) {
            builder.setMessage(AppLanguage.get(R.string.ui_no_tv_inputs_are_available_to_sva_on_this_device_use_the_input_s))
                    .setPositiveButton("OK", null);
        } else {
            builder.setItems(labels.toArray(new CharSequence[0]), (ignored, index) -> {
                android.media.tv.TvInputInfo input = inputs.get(index);
                Uri uri = input.isPassthroughInput()
                        ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.getId())
                        : android.media.tv.TvContract.buildChannelsUriForInput(input.getId());
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, AppLanguage.get(R.string.ui_this_tv_cannot_open_that_source_from_sva_use_the_input_source_bu), Toast.LENGTH_LONG).show();
                }
            }).setNegativeButton(AppLanguage.get(R.string.ui_cancel), null);
        }
        AlertDialog dialog = builder.create();
        Motion.dialog(dialog); dialog.show(); styleDialog(dialog);
    }

    private void openNetworkSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        try { startActivity(intent); }
        catch (Exception ignored) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
            catch (Exception ignoredAgain) { }
        }
    }

    private void openDateSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS)); }
        catch (Exception ignored) { openAndroidSettings(); }
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
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{android.R.attr.state_pressed}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{}, tileShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1));
        tile.setBackground(states);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
        Motion.interaction(tile, false);
    }

    private Drawable headerSectionBackground() {
        boolean light = LiquidTheme.isLight(this);
        StateListDrawable states = new Motion.States();
        Drawable focused = light
                ? calendarGlassShape(0x25087D91, LiquidTheme.accent(this), 14)
                : roundedShape(0x443D5B61, 0xFF7BE7F4, 1, 14);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private Drawable topWidgetBackground() {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused},
                roundedShape(0xE0173032, 0xFF7BE7F4, 2, 18));
        states.addState(new int[]{android.R.attr.state_pressed},
                roundedShape(0xE0173032, 0xFF7BE7F4, 2, 18));
        states.addState(new int[]{},
                roundedShape(0xD0151719, 0x66FFFFFF, 1, 18));
        return states;
    }

    private void launchApp(String packageName) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) try { startActivity(launch); } catch (Exception ignored) { }
    }

    private void focusGamePosition(int position) {
        pendingGameFocus = position;
        int generation = ++gameFocusGeneration;
        gameGrid.scrollToPosition(position);
        gameGrid.post(new Runnable() {
            int attempts;
            @Override public void run() {
                if (generation != gameFocusGeneration || activeTab != TAB_GAMES || !lifecycleWork.active()) return;
                RecyclerView.ViewHolder holder = gameGrid.findViewHolderForAdapterPosition(position);
                if (holder != null && holder.itemView.requestFocus()) {
                    pendingGameFocus = RecyclerView.NO_POSITION;
                } else if (++attempts < 8) gameGrid.postOnAnimation(this);
                else pendingGameFocus = RecyclerView.NO_POSITION;
            }
        });
    }

    private void renderGames() {
        if (gameGrid == null || activeTab != TAB_GAMES) return;
        int generation = ++gameLoadGeneration;
        lifecycleWork.execute(ioWorker, () -> {
            List<GameCardAdapter.Item> games = GameCardAdapter.load(this);
            lifecycleWork.post(() -> {
                if (generation != gameLoadGeneration) return;
                gamesEmpty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
                gameAdapter.submit(games);
                if (gamesListState != null && !games.isEmpty()) {
                    gameGrid.getLayoutManager().onRestoreInstanceState(gamesListState);
                    gamesListState = null;
                }
            });
        });
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

    private void updateTaskProgress() {
        String liveMessage = AppLanguage.get(channelsRefreshing ? R.string.ui_refreshing_channels : R.string.ui_refreshing_programme_guide);
        if (liveProgress != null) liveProgress.setBusy(channelsRefreshing || guideRefreshing, liveMessage);
        if (weatherProgress != null) weatherProgress.setBusy(weatherRefreshing, AppLanguage.get(R.string.ui_refreshing_weather));
        String calendarMessage = AppLanguage.get(R.string.ui_refreshing_calendar_feeds);
        if (homeCalendarProgress != null) homeCalendarProgress.setBusy(calendarRefreshing, calendarMessage);
        if (eventsProgress != null) eventsProgress.setBusy(calendarRefreshing, calendarMessage);
        if (settingsScreen != null) settingsScreen.updateTaskProgress();
    }

    private void setLiveStatus(String value) {
        liveStatus = value;
        if (status != null) status.setText(value);
    }
    private void setLiveStatusVisible(boolean visible) {
        liveStatusVisible = visible;
        if (status != null) status.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void renderChannels() {
        if (channelRows == null || activeTab != TAB_TV) return;
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        List<IptvRepository.Channel> favorites = FavoriteChannels.filterFavorites(channels, hiddenChannelKeys, favoriteChannelKeys);
        LinkedHashMap<String, List<IptvRepository.Channel>> grouped = new LinkedHashMap<>();
        if (!favorites.isEmpty()) {
            grouped.put(FavoriteChannels.CATEGORY_FAVORITES, favorites);
        }
        for (IptvRepository.Channel channel : channels) {
            if (HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            String category = channel.group.trim().isEmpty() ? AppLanguage.get(R.string.ui_uncategorized) : channel.group.trim();
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(channel);
        }
        if (grouped.isEmpty()) {
            channelsEmpty.setText(channels.isEmpty()
                    ? AppLanguage.get(R.string.ui_add_a_playlist_in_settings_to_browse_live_tv)
                    : AppLanguage.get(R.string.ui_all_channels_are_hidden_restore_channels_in_settings));
            if (!renderedCategories.isEmpty() || channelRows.getChildCount() != 1 || channelRows.getChildAt(0) != channelsEmpty) {
                channelRows.removeAllViews();
                channelRowMap.clear();
                channelTabMap.clear();
                channelRows.addView(channelsEmpty);
                renderedCategories.clear();
                selectedChannelCategory = null;
            }
            renderGuide();
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
            channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(50)));
            ChannelRow row = createChannelRow();
            channelRowMap.put(selectedChannelCategory, row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(6));
            channelRows.addView(row.container, params);
            for (String category : renderedCategories) {
                Button tab = createChannelCategoryTab(category);
                channelTabMap.put(category, tab);
                LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(44));
                tabParams.setMargins(dp(2), dp(2), dp(8), dp(2));
                channelCategoryTabs.addView(tab, tabParams);
                linkTopControl(TAB_TV, tab);
                if (channelCategoryTabs.getChildCount() == 1) linkTabEntry(TAB_TV, tab);
            }
        }
        if (!renderedCategories.contains(selectedChannelCategory)) selectedChannelCategory = renderedCategories.get(0);
        for (Map.Entry<String, List<IptvRepository.Channel>> category : grouped.entrySet()) {
            Button tab = channelTabMap.get(category.getKey());
            String prefix = FavoriteChannels.CATEGORY_FAVORITES.equals(category.getKey()) ? "★ " : "";
            if (tab != null) tab.setText(prefix + AppLanguage.category(category.getKey()) + "  ·  " + category.getValue().size());
            ChannelRow row = channelRowMap.get(selectedChannelCategory);
            if (row != null && category.getKey().equals(selectedChannelCategory)) {
                queueChannelRowUpdate(row, selectedChannelCategory, category.getValue());
            }
        }
        updateChannelCategoryTabStyles();
        renderGuide();
    }

    private void refreshCachedProgrammeDisplay() {
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        for (ChannelRow row : channelRowMap.values()) {
            row.list.post(() -> {
                if (channelRowMap.containsValue(row)) row.adapter.refreshVisibleProgrammeLabels(row.list);
            });
        }
    }

    private void renderGuide() {
        if (guideEmpty == null) return;
        guideEmpty.setText(channels.isEmpty() ? AppLanguage.get(R.string.ui_add_a_playlist_and_programme_guide_in_settings)
                : AppLanguage.get(R.string.ui_explore_the_schedule_see_what_s_on_now_and_set_reminders));
    }

    private Button createChannelCategoryTab(String category) {
        String label = FavoriteChannels.CATEGORY_FAVORITES.equals(category) ? "★ " + AppLanguage.category(category) : category;
        Button tab = actionButton(label);
        tab.setTextSize(14);
        tab.setPadding(dp(18), 0, dp(18), 0);
        tab.setMinWidth(dp(72));
        tab.setOnFocusChangeListener((view, focused) -> {
            LiquidTheme.animateFocus(view, focused);
            if (focused) selectChannelCategory(category, view);
        });
        tab.setOnClickListener(view -> selectChannelCategory(category, view));
        return tab;
    }

    private void selectChannelCategory(String category, View view) {
        if (category.equals(selectedChannelCategory)) return;
        selectedChannelCategory = category;
        updateChannelCategoryTabStyles();
        ChannelRow row = channelRowMap.isEmpty() ? null : channelRowMap.values().iterator().next();
        if (row != null) {
            channelRowMap.clear();
            channelRowMap.put(selectedChannelCategory, row);
            queueChannelRowUpdate(row, category, channelsForCategory(category));
        }
        renderGuide();
        channelCategoryScroll.smoothScrollTo(Math.max(0, view.getLeft() - dp(24)), 0);
    }

    private void queueChannelRowUpdate(ChannelRow row, String category, List<IptvRepository.Channel> items) {
        int generation = ++row.pendingUpdateGeneration;
        List<IptvRepository.Channel> snapshot = new ArrayList<>(items);
        Set<String> favorites = new HashSet<>(favoriteChannelKeys);
        row.list.post(() -> {
            if (row.pendingUpdateGeneration != generation
                    || !category.equals(selectedChannelCategory)
                    || channelRowMap.get(category) != row) return;
            row.adapter.setChannels(snapshot, favorites);
        });
    }

    private List<IptvRepository.Channel> channelsForCategory(String category) {
        if (FavoriteChannels.CATEGORY_FAVORITES.equals(category)) {
            return FavoriteChannels.filterFavorites(channels, hiddenChannelKeys, favoriteChannelKeys);
        }
        ArrayList<IptvRepository.Channel> selected = new ArrayList<>();
        for (IptvRepository.Channel channel : channels) {
            if (HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            String channelCategory = channel.group.trim().isEmpty() ? AppLanguage.get(R.string.ui_uncategorized) : channel.group.trim();
            if (category.equals(channelCategory)) selected.add(channel);
        }
        return selected;
    }

    private void updateChannelCategoryTabStyles() {
        for (Map.Entry<String, Button> entry : channelTabMap.entrySet()) {
            boolean selected = entry.getKey().equals(selectedChannelCategory);
            Button tab = entry.getValue();
            android.graphics.drawable.StateListDrawable states = new Motion.States();
            states.addState(new int[]{android.R.attr.state_focused}, roundedShape(
                    LiquidTheme.raisedSurface(this),
                    LiquidTheme.accent(this), 2, 8));
            states.addState(new int[]{}, selected
                    ? roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 24)
                    : roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 8));
            tab.setBackground(states);
            tab.setTextColor(LiquidTheme.text(this));
            tab.setTypeface(LiquidTheme.font(this));
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
        ChannelAdapter adapter = new ChannelAdapter(this, logoLoader, glassBackdrop, this::playChannel, this::toggleFavoriteChannel);
        list.setAdapter(adapter);
        container.addView(list, new LinearLayout.LayoutParams(-1, dp(250)));
        return new ChannelRow(container, list, adapter);
    }

    private void loadBackgroundFolder(Uri treeUri, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        albumLoading = false; loadingAlbumUrl = ""; updateTaskProgress();
        List<BackgroundPhoto> cached = cachedBackgroundFolder(treeUri.toString());
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        else if (userSelected && cached.isEmpty()) setBackgroundPhotos(Collections.emptyList());
        lifecycleWork.execute(ioWorker, () -> {
            FolderScanResult scan = enumeratePhotos(treeUri);
            lifecycleWork.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                if (!scan.success) {
                    if (userSelected) Toast.makeText(this,
                            AppLanguage.get(R.string.ui_could_not_read_that_folder_keeping_the_saved_slideshow), Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundFolder(treeUri.toString(), scan.photos);
                if (!sameBackgroundPhotos(scan.photos)) setBackgroundPhotos(scan.photos);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected && scan.photos.isEmpty()) {
                    Toast.makeText(this, AppLanguage.get(R.string.ui_no_images_were_found_in_that_folder), Toast.LENGTH_LONG).show();
                } else if (userSelected) {
                    Toast.makeText(this, AppLanguage.get(R.string.ui_background_slideshow_updated_1_s_photos, scan.photos.size()), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadBackgroundAlbum(String shareUrl, boolean userSelected) {
        if (albumLoading && shareUrl.equals(loadingAlbumUrl)) return;
        int generation = ++backgroundFolderGeneration;
        albumLoading = true;
        loadingAlbumUrl = shareUrl;
        updateTaskProgress();
        List<BackgroundPhoto> cached = cachedBackgroundAlbum(shareUrl);
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        preferences.edit().putLong("googlePhotosAttemptedAt", System.currentTimeMillis()).apply();
        lifecycleWork.execute(ioWorker, () -> {
            List<BackgroundPhoto> photos = new ArrayList<>();
            String failure = null;
            try {
                List<String> urls = GooglePhotosAlbumRepository.fetchImageUrls(shareUrl);
                if (urls.isEmpty()) throw new IllegalStateException(AppLanguage.get(R.string.ui_no_photos_were_found_check_that_link_sharing_is_on));
                for (int i = 0; i < urls.size(); i++) photos.add(new BackgroundPhoto(Uri.parse(urls.get(i)), AppLanguage.get(R.string.ui_google_photos_1_s, (i + 1))));
            } catch (Exception e) {
                failure = e.getMessage() == null ? AppLanguage.get(R.string.ui_could_not_load_that_google_photos_album) : e.getMessage();
            }
            List<BackgroundPhoto> result = photos;
            String error = failure;
            lifecycleWork.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                albumLoading = false; loadingAlbumUrl = ""; updateTaskProgress();
                if (error != null || result.isEmpty()) {
                    if (userSelected) Toast.makeText(this, error == null ? AppLanguage.get(R.string.ui_no_public_photos_were_found_in_that_album) : error, Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundAlbum(shareUrl, result);
                preferences.edit().putString("wallpaperAlbum", shareUrl).remove("wallpaperFolder")
                        .putLong("googlePhotosUpdatedAt", System.currentTimeMillis()).apply();
                if (!sameBackgroundPhotos(result)) setBackgroundPhotos(result);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected) Toast.makeText(this, AppLanguage.get(R.string.ui_google_photos_slideshow_updated_1_s_photos, result.size()), Toast.LENGTH_SHORT).show();
                scheduleGooglePhotosRefresh();
            });
        });
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
                if (!url.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(url), AppLanguage.get(R.string.ui_google_photos_1_s, (i + 1))));
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
                if (!uri.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(uri), value.optString("name", AppLanguage.get(R.string.ui_photo))));
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
        updateWidgetThemesForBitmap(bitmap, url);
        slideA.setTag(uri);
        slideA.setVisibility(View.VISIBLE);
        slideA.setAlpha(1f);
        slideAActive = false;
        backgroundIndex = backgroundPhotos.size() > 1 ? 1 : 0;
    }

    private FolderScanResult enumeratePhotos(Uri treeUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            boolean success;
            if ("file".equals(treeUri.getScheme())) {
                success = collectLocalPhotos(new java.io.File(treeUri.getPath()), "", visited, photos, 0);
            } else {
                success = collectPhotos(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", visited, photos, 0);
            }
            Collections.sort(photos, (left, right) -> left.name.compareToIgnoreCase(right.name));
            return new FolderScanResult(photos, success);
        } catch (Exception ignored) { return new FolderScanResult(photos, false); }
    }

    private boolean collectLocalPhotos(java.io.File folder, String parentPath, Set<String> visited,
                                       List<BackgroundPhoto> photos, int depth) {
        if (depth > 16 || !visited.add(folder.getAbsolutePath())) return true;
        java.io.File[] children = folder.listFiles();
        if (children == null) return false;
        for (java.io.File child : children) {
            if (child.isDirectory()) {
                collectLocalPhotos(child, parentPath + child.getName() + "/", visited, photos, depth + 1);
            } else if (isImageDocument(null, child.getName())) {
                photos.add(new BackgroundPhoto(Uri.fromFile(child), parentPath + child.getName()));
            }
        }
        return true;
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
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap bitmap = decodeWallpaper(photo.uri,
                    Math.min(getResources().getDisplayMetrics().widthPixels, 1280),
                    Math.min(dp(heroHeightDp()), 720));
            lifecycleWork.post(() -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                backgroundIndex = (index + 1) % backgroundPhotos.size();
                if (bitmap == null) {
                    scheduleNextBackgroundPhoto();
                    return;
                }
                glassBackdrop.prepare(bitmap, true, () -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                incoming.setImageBitmap(bitmap);
                updateWidgetThemesForBitmap(bitmap, photo.uri.toString());
                incoming.setTag(photo.uri);
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
        });
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
        final RecyclerView list;
        final ChannelAdapter adapter;
        int pendingUpdateGeneration;
        ChannelRow(LinearLayout container, RecyclerView list, ChannelAdapter adapter) {
            this.container = container;
            this.list = list;
            this.adapter = adapter;
        }

        boolean requestFocus() {
            if (list != null) {
                View focused = list.getFocusedChild();
                if (focused != null && focused.requestFocus()) return true;
                if (list.getChildCount() > 0) {
                    View first = list.getChildAt(0);
                    if (first != null && first.requestFocus()) return true;
                }
                if (list.requestFocus()) return true;
            }
            return container != null && container.requestFocus();
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void playChannel(IptvRepository.Channel channel) {
        PlaybackLauncher.watch(this, channel, channels);
    }

    private boolean toggleFavoriteChannel(IptvRepository.Channel channel) {
        if (channel == null) return false;
        boolean added = FavoriteChannels.toggle(favoriteChannelKeys, channel);
        FavoriteChannels.save(preferences, favoriteChannelKeys);
        Toast.makeText(this, AppLanguage.get(added ? R.string.ui_favourite_added : R.string.ui_favourite_removed, AppLanguage.bidi(channel.name)), Toast.LENGTH_SHORT).show();
        renderChannels();
        return true;
    }

    private void refreshChannels() {
        if (channelsRefreshing) return;
        channelsRefreshing = true; updateTaskProgress();
        setLiveStatusVisible(true);
        setLiveStatus(AppLanguage.get(R.string.ui_refreshing_channels));
        if (settingsScreen != null) settingsScreen.onRefreshResult(AppLanguage.get(R.string.ui_refreshing_channels));
        iptv.refreshChannels((loaded, message) -> lifecycleWork.post(() -> {
            channelsRefreshing = false;
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            if (!loaded.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                    .reconcile(channels, hiddenChannelKeys);
            openPendingReminder();
            setLiveStatus(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
            if (!channels.isEmpty() && !iptv.guideUrl().isEmpty() && iptv.isGuideStale(channels)) refreshGuide(false);
            updateTaskProgress();
            scheduleLiveRefreshCheck();
        }));
    }

    private void refreshGuide(boolean userRequested) {
        if (guideRefreshing) return;
        guideRefreshing = true; updateTaskProgress();
        String refreshing = userRequested ? AppLanguage.get(R.string.ui_refreshing_programme_guide) : AppLanguage.get(R.string.ui_updating_today_s_programme_guide);
        setLiveStatusVisible(true);
        setLiveStatus(refreshing);
        if (settingsScreen != null) settingsScreen.onRefreshResult(refreshing);
        iptv.refreshGuide(new ArrayList<>(channels), (loaded, message) -> lifecycleWork.post(() -> {
            guideRefreshing = false; updateTaskProgress();
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            if (!loaded.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                    .reconcile(channels, hiddenChannelKeys);
            setLiveStatus(message);
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

    private volatile boolean restoringBackup;
    private SettingsScreen settingsScreen;

    private void styleDialog(AlertDialog dialog) {
        Motion.dialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 26));
            window.setDimAmount(.35f);
            // Ensure the window resizes for the soft keyboard so OEM TV firmwares
            // (e.g. TCL P615) cannot steal focus away from EditText inputs inside
            // these dialogs. Harmless on dialogs that contain no text fields.
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) {
                styleFocusableTile(button);
                button.setTextColor(LiquidTheme.text(this));
                button.setAllCaps(false);
                ViewGroup.LayoutParams lp = button.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(6), dp(4), dp(6), dp(4));
                }
                if (button.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) button.getParent();
                    parent.setClipChildren(false);
                    parent.setClipToPadding(false);
                }
            }
        }
    }

    private boolean isRtl() { return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    private void showSettings() {
        if (settingsScreen != null) return;
        settingsScreen = new SettingsScreen();
        settingsScreen.show();
    }

    private final class SettingsScreen {
        private static final int LIVE = 0, CHANNELS = 1, APPS = 2, APPEARANCE = 3, CLOCK = 4, DRM = 5, HOME = 6, BACKUP = 7, YOUTUBE = 8, CALENDAR = 9, SCHEDULES = 10, GAMES = 11, WEATHER = 12, NAVIGATION = 13, LANGUAGE = 14;
        private final String[] sections = {AppLanguage.get(R.string.ui_live_tv), AppLanguage.get(R.string.ui_channels_18e03e2), AppLanguage.get(R.string.ui_apps), AppLanguage.get(R.string.ui_appearance), AppLanguage.get(R.string.ui_clock), AppLanguage.get(R.string.ui_playback_drm), AppLanguage.get(R.string.ui_home_button), AppLanguage.get(R.string.ui_backup_restore), "YouTube", AppLanguage.get(R.string.ui_calendar), AppLanguage.get(R.string.ui_schedules), AppLanguage.get(R.string.ui_games), AppLanguage.get(R.string.ui_weather), AppLanguage.get(R.string.ui_navigation), AppLanguage.get(R.string.ui_language)};
        private final int[] NAV_ORDER = {
            // Group 0: Content (5 items)
            LIVE, CHANNELS, YOUTUBE, CALENDAR, GAMES,
            // Group 1: Home & Display (5 items)
            APPS, APPEARANCE, CLOCK, WEATHER, NAVIGATION,
            // Group 2: System & Advanced (4 items)
            SCHEDULES, HOME, DRM, BACKUP, LANGUAGE
        };
        private final String[] GROUP_LABELS = {AppLanguage.get(R.string.ui_content), AppLanguage.get(R.string.ui_home_display), AppLanguage.get(R.string.ui_system)};
        private final String[] descriptions = {
                AppLanguage.get(R.string.ui_playlist_and_programme_guide), AppLanguage.get(R.string.ui_choose_which_channels_appear_in_live_tv),
                AppLanguage.get(R.string.ui_choose_and_order_your_home_shortcuts), AppLanguage.get(R.string.ui_photo_backdrop_and_wallpaper),
                AppLanguage.get(R.string.ui_time_and_date_on_home), AppLanguage.get(R.string.ui_default_widevine_license_settings),
                AppLanguage.get(R.string.ui_open_sva_when_the_tv_home_button_opens_its_built_in_launcher),
                AppLanguage.get(R.string.ui_save_or_restore_launcher_settings_and_channels), AppLanguage.get(R.string.ui_save_videos_and_playlists_in_saved),
                AppLanguage.get(R.string.ui_show_events_from_icalendar_feeds), AppLanguage.get(R.string.ui_choose_when_content_updates_automatically),
                AppLanguage.get(R.string.ui_import_and_manage_your_local_nes_library), AppLanguage.get(R.string.ui_current_conditions_and_a_three_day_forecast),
                AppLanguage.get(R.string.ui_choose_which_categories_appear_and_their_order), AppLanguage.get(R.string.ui_choose_the_language_used_throughout_sva)
        };
        private final boolean compactSettings = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density < 1100;
        private final Dialog dialog = new Dialog(MainActivity.this, R.style.SvaFullScreenDialogTheme);
        private final Button[] tabs = new Button[sections.length];
        private final FrameLayout detail = new FrameLayout(MainActivity.this) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                return restoringBackup || super.dispatchTouchEvent(event);
            }
        };
        private LinearLayout rail;
        private int section = LIVE;
        private int renderedSection = -1;
        private final Map<Integer, Integer> sectionScroll = new HashMap<>();
        private final Map<Integer, int[]> sectionFocus = new HashMap<>();
        private final Map<Integer, android.os.Parcelable> listState = new HashMap<>();
        private View firstControl;
        private EditText playlistField, guideField, licenseField, headersField, calendarUrlField;
        private EditText youtubeUrlField, youtubeTitleField, youtubeArtField;
        private Button youtubeAddButton;
        private TaskProgress taskProgress, backupExportProgress;
        private Button backupExportButton, backupImportButton;
        private TextView liveFeedback, cacheSummary, guideSummary, youtubeFeedback, calendarFeedback, calendarSummary, weatherFeedback;
        private LinearLayout youtubeSavedList, weatherSearchResults;
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
            shell.setPadding(dp(compactSettings ? 24 : 40), dp(20), dp(compactSettings ? 24 : 40), dp(24));
            // Settings use a quiet, solid surface independently of the Home wallpaper.
            shell.setBackgroundColor(SettingsUi.background(MainActivity.this));

            // ── Header ────────────────────────────────────────────────────────────
            LinearLayout heading = new LinearLayout(MainActivity.this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setMinimumHeight(dp(60));
            shell.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout headingCopy = new LinearLayout(MainActivity.this);
            headingCopy.setOrientation(LinearLayout.VERTICAL);
            headingCopy.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(headingCopy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView eyebrow = label("Sva Launcher", 14, SettingsUi.muted(MainActivity.this), false);

            LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(-2, -2);
            eyeParams.bottomMargin = dp(2);
            headingCopy.addView(eyebrow, eyeParams);
            TextView title = label(AppLanguage.get(R.string.ui_settings), 28, SettingsUi.text(MainActivity.this), true);
            headingCopy.addView(title);
            // Thin separator below header
            View accentLine = new View(MainActivity.this);
            accentLine.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(-1, dp(1));
            accentParams.bottomMargin = dp(12);
            shell.addView(accentLine, accentParams);

            // ── Body (rail + detail) ──────────────────────────────────────────────
            LinearLayout body = new LinearLayout(MainActivity.this);
            shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

            // Fixed outer frame for navigation rail
            FrameLayout railFrame = new FrameLayout(MainActivity.this);
            railFrame.setBackgroundColor(SettingsUi.background(MainActivity.this));

            ScrollView railScroll = new ScrollView(MainActivity.this);
            railScroll.setFillViewport(true);
            railScroll.setSmoothScrollingEnabled(true);
            railScroll.setVerticalScrollBarEnabled(false);
            railScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            railScroll.setClipToPadding(false);
            railScroll.setPadding(dp(8), dp(8), dp(8), dp(8));

            rail = new LinearLayout(MainActivity.this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setClipChildren(false);
            rail.setClipToPadding(false);
            railScroll.addView(rail, new FrameLayout.LayoutParams(-1, -2));
            railFrame.addView(railScroll, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(compactSettings ? 220 : 260), -1);
            railParams.setMarginEnd(dp(12));
            body.addView(railFrame, railParams);

            // Build grouped rail with NAV_ORDER
            int lastGroup = -1;
            for (int i = 0; i < NAV_ORDER.length; i++) {
                final int target = NAV_ORDER[i];
                int group = (i < 5) ? 0 : (i < 10 ? 1 : 2);
                if (group != lastGroup) {
                    if (lastGroup >= 0) {
                        View div = new View(MainActivity.this);
                        div.setBackgroundColor(LiquidTheme.border(MainActivity.this));
                        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(-1, dp(1));
                        divParams.setMargins(dp(6), dp(6), dp(6), dp(8));
                        rail.addView(div, divParams);
                    }
                    TextView groupLabel = label(GROUP_LABELS[group],
                            14, SettingsUi.muted(MainActivity.this), false);

                    LinearLayout.LayoutParams glParams = new LinearLayout.LayoutParams(-1, dp(36));
                    glParams.setMargins(dp(10), lastGroup < 0 ? dp(2) : 0, dp(8), dp(2));
                    rail.addView(groupLabel, glParams);
                    lastGroup = group;
                }
                Button tab = new Button(MainActivity.this);
                tab.setText(sections[target]);
                tab.setCompoundDrawablesRelativeWithIntrinsicBounds(SettingsUi.icon(MainActivity.this, target), null, null, null);
                tab.setCompoundDrawablePadding(dp(12));
                tab.setTypeface(LiquidTheme.font(MainActivity.this));
                tab.setTextSize(18);
                tab.setAllCaps(false);
                tab.setMinWidth(0);
                tab.setMinimumWidth(0);
                tab.setMinHeight(0);
                tab.setMinimumHeight(0);
                tab.setSingleLine(false);
                tab.setMaxLines(2);
                tab.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                tab.setPadding(dp(14), 0, dp(10), 0);
                tab.setFocusable(true);
                tab.setId(View.generateViewId());
                tab.setOnFocusChangeListener(null);
                tab.setStateListAnimator(null);
                tab.setElevation(0);
                tab.setOnClickListener(v -> {
                    captureDrafts();
                    sectionScroll.put(target, 0);
                    sectionFocus.remove(target);
                    section = target;
                    renderSection();
                    if (firstControl != null) firstControl.requestFocus();
                });
                tabs[target] = tab;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(64));
                params.setMargins(dp(6), dp(1), dp(6), dp(3));
                rail.addView(tab, params);
            }
            detail.setBackgroundColor(SettingsUi.background(MainActivity.this));
            body.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));

            dialog.setContentView(shell);
            dialog.setCancelable(false);
            dialog.setOnKeyListener((ignored, keyCode, event) -> {
                View focused = dialog.getCurrentFocus();
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        if (focused instanceof EditText) {
                            android.view.inputmethod.InputMethodManager keyboard =
                                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                            focused.clearFocus();
                            tabs[section].requestFocus();
                        } else requestClose();
                    }
                    return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN || focused == null) return false;
                if (!isDpadDirection(keyCode)) return false;
                int direction = focusDirection(keyCode);
                // Keep cursor movement within an input, but let the remote leave
                // its first/last line even when Android's text editor consumes arrows.
                if (focused instanceof EditText) {
                    EditText input = (EditText) focused;
                    android.text.Layout layout = input.getLayout();
                    int cursor = direction == View.FOCUS_UP
                            ? Math.min(input.getSelectionStart(), input.getSelectionEnd())
                            : Math.max(input.getSelectionStart(), input.getSelectionEnd());
                    if (layout != null && cursor >= 0
                            && (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN)) {
                        int line = layout.getLineForOffset(cursor);
                        boolean boundary = direction == View.FOCUS_UP ? line == 0
                                : line == layout.getLineCount() - 1;
                        if (boundary) {
                            View ordered = formFocusNeighbor(input, direction);
                            if (ordered != null && ordered.requestFocus()) return true;
                            View next = input.focusSearch(direction);
                            if (next != null && isWithinPage(next, detail) && next != input
                                    && next.requestFocus()) return true;
                            if (direction == View.FOCUS_UP) {
                                tabs[section].requestFocus();
                                return true;
                            }
                        }
                    }
                    return false;
                }
                if (rail != null && isWithinPage(focused, rail)) {
                    if (direction == View.FOCUS_UP && focused == tabs[NAV_ORDER[0]]) return true;
                    if (direction == (isRtl() ? View.FOCUS_LEFT : View.FOCUS_RIGHT)) {
                        if (focused == tabs[section] && firstControl != null && firstControl.requestFocus()) return true;
                        return true;
                    }
                    if (direction == (isRtl() ? View.FOCUS_RIGHT : View.FOCUS_LEFT)) return true;
                    View next = focused.focusSearch(direction);
                    if (next != null && isWithinPage(next, rail)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                    if (next != null) return true;
                    if (canScrollWithinPage(focused, rail, direction)) return false;
                    return true;
                }
                if (isWithinPage(focused, detail) && !(focused instanceof EditText)) {
                    if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                        View ordered = formFocusNeighbor(focused, direction);
                        if (ordered != null && ordered.requestFocus()) return true;
                    }
                    if (direction == View.FOCUS_UP && focused.getParent() instanceof LinearLayout
                            && "settings-save-actions".equals(((View) focused.getParent()).getTag())) {
                        ViewGroup page = (ViewGroup) focused.getParent().getParent();
                        for (int i = 0; i < page.getChildCount(); i++) {
                            if (!(page.getChildAt(i) instanceof ScrollView)) continue;
                            ArrayList<View> controls = page.getChildAt(i).getFocusables(View.FOCUS_BACKWARD);
                            for (int j = controls.size() - 1; j >= 0; j--) {
                                View control = controls.get(j);
                                if (!(control instanceof ScrollView) && control.isEnabled()
                                        && control.requestFocus()) return true;
                            }
                        }
                    }
                    // Traverse horizontal actions before returning to the section rail.
                    if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)
                            && focused.getParent() instanceof LinearLayout
                            && ("settings-save-actions".equals(((View) focused.getParent()).getTag())
                            || "settings-app-actions".equals(((View) focused.getParent()).getTag())
                            || "settings-card-actions".equals(((View) focused.getParent()).getTag()))) {
                        LinearLayout actions = (LinearLayout) focused.getParent();
                        int step = direction == View.FOCUS_LEFT ? -1 : 1;
                        if (isRtl()) step = -step;
                        for (int index = actions.indexOfChild(focused) + step;
                             index >= 0 && index < actions.getChildCount(); index += step) {
                            View target = actions.getChildAt(index);
                            if (target.getVisibility() == View.VISIBLE && target.isEnabled()
                                    && target.isFocusable() && target.requestFocus()) return true;
                        }
                        if (direction == (isRtl() ? View.FOCUS_LEFT : View.FOCUS_RIGHT)) return true;
                    }
                    if (direction == (isRtl() ? View.FOCUS_RIGHT : View.FOCUS_LEFT) && !(focused instanceof EditText)) {
                        tabs[section].requestFocus(); return true;
                    }
                    View next = focused.focusSearch(direction);
                    if (next != null && isWithinPage(next, detail)) {
                        if (next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                        return true;
                    }
                    if (direction == (isRtl() ? View.FOCUS_RIGHT : View.FOCUS_LEFT) && tabs[section].requestFocus()) return true;
                    if (direction == View.FOCUS_UP && !canScrollWithinPage(focused, detail, direction)) {
                        tabs[section].requestFocus(); return true;
                    }
                    if (next != null) return true;
                    if (canScrollWithinPage(focused, detail, direction)) return false;
                    return true;
                }
                return false;
            });
            dialog.setOnDismissListener(ignored -> {
                if (channelAdapter != null) channelAdapter.cancelSearch();
                if (settingsScreen == this) settingsScreen = null;
                if (appsChanged || !appsLoaded) renderApps();
                Set<String> freshHidden = HiddenChannels.load(preferences);
                Set<String> freshFavorites = FavoriteChannels.load(preferences);
                if (channelsChanged || !freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
                    hiddenChannelKeys.clear();
                    hiddenChannelKeys.addAll(freshHidden);
                    favoriteChannelKeys.clear();
                    favoriteChannelKeys.addAll(freshFavorites);
                    renderChannels();
                    ((SvaApplication) getApplication()).guideReminders().reconcile(channels, hiddenChannelKeys);
                }
                if (youtubeChanged) renderYouTubeCards();
                if (screenActive && !backgroundPhotos.isEmpty()) {
                    if (slideA.getVisibility() == View.VISIBLE || slideB.getVisibility() == View.VISIBLE) {
                        scheduleNextBackgroundPhoto();
                    } else showNextBackgroundPhoto();
                }
            });
            renderSection();
            Motion.dialog(dialog); dialog.show();
            BackNavigation.install(dialog, this::requestClose);
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
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            tabs[section].requestFocus();
            if (section == LANGUAGE && firstControl != null) firstControl.requestFocus();
        }

        private View formFocusNeighbor(View focused, int direction) {
            // Search the form itself: searching the window lets the rail beat controls
            // above/below the ScrollView's current viewport.
            for (android.view.ViewParent parent = focused.getParent(); parent instanceof View;
                 parent = parent.getParent()) {
                if (parent == detail) break;
                if (!(parent instanceof ScrollView)) continue;
                ScrollView scroll = (ScrollView)parent;
                if (!isWithinPage(scroll, detail) || scroll.getChildCount() == 0
                        || !(scroll.getChildAt(0) instanceof ViewGroup)) return null;
                View next = android.view.FocusFinder.getInstance().findNextFocus(
                        (ViewGroup)scroll.getChildAt(0), focused, direction);
                if (next != focused && next != null && next.isEnabled() && next.isFocusable()) return next;
                return direction == View.FOCUS_UP ? tabs[section] : null;
            }
            return null;
        }

        private void captureDrafts() {
            rememberLayout();
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

        private void saveDrafts(Bundle state) {
            state.putString("settingsPlaylist", playlistField == null ? draftPlaylist : playlistField.getText().toString());
            state.putString("settingsGuide", guideField == null ? draftGuide : guideField.getText().toString());
            state.putString("settingsLicense", licenseField == null ? draftLicense : licenseField.getText().toString());
            state.putString("settingsHeaders", headersField == null ? draftHeaders : headersField.getText().toString());
            state.putString("settingsCalendar", calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString());
            state.putString("settingsYoutubeUrl", youtubeUrlField == null ? draftYoutubeUrl : youtubeUrlField.getText().toString());
            state.putString("settingsYoutubeTitle", youtubeTitleField == null ? draftYoutubeTitle : youtubeTitleField.getText().toString());
            state.putString("settingsYoutubeArt", youtubeArtField == null ? draftYoutubeArt : youtubeArtField.getText().toString());
            rememberLayout();
            for (int i = 0; i < sections.length; i++) {
                state.putInt("settingsScroll" + i, sectionScroll.getOrDefault(i, 0));
                state.putIntArray("settingsFocus" + i, sectionFocus.get(i));
                state.putParcelable("settingsList" + i, listState.get(i));
            }
        }

        private void restoreDrafts(Bundle state) {
            draftPlaylist = state.getString("settingsPlaylist", draftPlaylist);
            draftGuide = state.getString("settingsGuide", draftGuide);
            draftLicense = state.getString("settingsLicense", draftLicense);
            draftHeaders = state.getString("settingsHeaders", draftHeaders);
            draftCalendarUrl = state.getString("settingsCalendar", draftCalendarUrl);
            draftYoutubeUrl = state.getString("settingsYoutubeUrl", "");
            draftYoutubeTitle = state.getString("settingsYoutubeTitle", "");
            draftYoutubeArt = state.getString("settingsYoutubeArt", "");
            for (int i = 0; i < sections.length; i++) {
                sectionScroll.put(i, state.getInt("settingsScroll" + i, 0));
                if (state.getIntArray("settingsFocus" + i) != null) sectionFocus.put(i, state.getIntArray("settingsFocus" + i));
                if (state.getParcelable("settingsList" + i) != null) listState.put(i, state.getParcelable("settingsList" + i));
            }
        }

        private View findContainer(View root, Class<?> type) {
            if (type.isInstance(root)) return root;
            if (root instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) root;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = findContainer(group.getChildAt(i), type);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void rememberLayout() {
            if (renderedSection < 0) return;
            View scroll = findContainer(detail, ScrollView.class);
            if (scroll != null) sectionScroll.put(renderedSection, scroll.getScrollY());
            View list = findContainer(detail, RecyclerView.class);
            if (list instanceof RecyclerView && ((RecyclerView) list).getLayoutManager() != null)
                listState.put(renderedSection, ((RecyclerView) list).getLayoutManager().onSaveInstanceState());
            View focused = detail.findFocus();
            if (focused == null) return;
            ArrayList<Integer> reversed = new ArrayList<>();
            for (View child = focused; child != detail && child.getParent() instanceof ViewGroup; ) {
                ViewGroup parent = (ViewGroup)child.getParent();
                reversed.add(parent.indexOfChild(child)); child = parent;
            }
            int[] path = new int[reversed.size()];
            for (int i = 0; i < path.length; i++) path[i] = reversed.get(path.length - 1 - i);
            sectionFocus.put(renderedSection, path);
        }

        private void restoreLayout(boolean restoreFocus) {
            final int target = section;
            detail.post(() -> {
                if (settingsScreen != this || renderedSection != target) return;
                View scroll = findContainer(detail, ScrollView.class);
                if (scroll != null) scroll.scrollTo(0, sectionScroll.getOrDefault(target, 0));
                View list = findContainer(detail, RecyclerView.class);
                if (list instanceof RecyclerView && ((RecyclerView)list).getLayoutManager() != null && listState.containsKey(target))
                    ((RecyclerView)list).getLayoutManager().onRestoreInstanceState(listState.get(target));
                if (!restoreFocus) return;
                View focused = detail;
                int[] path = sectionFocus.get(target);
                if (path != null) for (int index : path) {
                    if (!(focused instanceof ViewGroup) || index >= ((ViewGroup)focused).getChildCount()) { focused = null; break; }
                    focused = ((ViewGroup)focused).getChildAt(index);
                }
                if (focused == null || focused == detail || !focused.requestFocus()) {
                    if (firstControl != null) firstControl.requestFocus();
                }
            });
        }

        void resetDrafts() {
            draftPlaylist = iptv.playlistUrl();
            draftGuide = iptv.guideUrl();
            draftLicense = iptv.drmLicenseUrl();
            draftHeaders = iptv.drmHeaderText();
            draftCalendarUrl = preferences.getString("publicCalendarUrl", "");
            draftYoutubeUrl = "";
            draftYoutubeTitle = "";
            draftYoutubeArt = "";
            if (playlistField != null) playlistField.setText(draftPlaylist);
            if (guideField != null) guideField.setText(draftGuide);
            if (licenseField != null) licenseField.setText(draftLicense);
            if (headersField != null) headersField.setText(draftHeaders);
            if (calendarUrlField != null) calendarUrlField.setText(draftCalendarUrl);
            if (youtubeUrlField != null) youtubeUrlField.setText("");
            if (youtubeTitleField != null) youtubeTitleField.setText("");
            if (youtubeArtField != null) youtubeArtField.setText("");
            appsChanged = false;
            channelsChanged = false;
            youtubeChanged = false;
        }

        private boolean hasUnsavedEdits() {
            if (restoringBackup) return false;
            captureDrafts();
            return !draftPlaylist.trim().equals(iptv.playlistUrl().trim())
                    || !draftGuide.trim().equals(iptv.guideUrl().trim())
                    || !draftLicense.trim().equals(iptv.drmLicenseUrl().trim())
                    || !draftHeaders.trim().equals(iptv.drmHeaderText().trim())
                    || !draftCalendarUrl.trim().equals(preferences.getString("publicCalendarUrl", "").trim());
        }

        private void requestClose() {
            if (restoringBackup) { dialog.dismiss(); return; }
            if (!hasUnsavedEdits()) { dialog.dismiss(); return; }
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle(AppLanguage.get(R.string.ui_discard_unsaved_edits))
                    .setMessage(AppLanguage.get(R.string.ui_playlist_drm_or_calendar_changes_have_not_been_saved))
                    .setPositiveButton(AppLanguage.get(R.string.ui_discard), (ignored, which) -> dialog.dismiss())
                    .setNegativeButton(AppLanguage.get(R.string.ui_keep_editing), null).create();
            Motion.dialog(prompt); prompt.show();
            styleDialog(prompt);
        }

        private void renderSection() {
            if (channelAdapter != null) channelAdapter.cancelSearch();
            boolean animateSection = renderedSection >= 0 && renderedSection != section;
            boolean restoreFocus = detail.findFocus() != null;
            rememberLayout();
            renderedSection = section;
            firstControl = null;
            detail.removeAllViews();
            taskProgress = backupExportProgress = null;
            backupExportButton = backupImportButton = null;
            for (int i = 0; i < tabs.length; i++) {
                boolean selected = i == section;
                tabs[i].setTextColor(selected ? SettingsUi.text(MainActivity.this) : SettingsUi.muted(MainActivity.this));
                tabs[i].setSelected(selected);
                tabs[i].setBackground(SettingsUi.navigationStates(MainActivity.this, selected));
            }
            // Detail page
            LinearLayout page = new LinearLayout(MainActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(16), dp(8), dp(16), dp(4));
            detail.addView(page, new FrameLayout.LayoutParams(-1, -1));

            // Section header: glyph badge + title + subtitle
            LinearLayout headerRow = new LinearLayout(MainActivity.this);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
            headerParams.bottomMargin = dp(10);
            page.addView(headerRow, headerParams);

            LinearLayout headerCopy = new LinearLayout(MainActivity.this);
            headerCopy.setOrientation(LinearLayout.VERTICAL);
            headerCopy.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.addView(headerCopy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = label(sections[section], 28, SettingsUi.text(MainActivity.this), true);
            title.setTypeface(LiquidTheme.font(MainActivity.this));
            headerCopy.addView(title);
            TextView subtitle = label(descriptions[section], 14, SettingsUi.muted(MainActivity.this), false);
            subtitle.setMaxLines(2);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.topMargin = dp(2);
            headerCopy.addView(subtitle, subParams);

            // Thin separator below header
            View separator = new View(MainActivity.this);
            separator.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(-1, dp(1));
            sepParams.bottomMargin = dp(10);
            page.addView(separator, sepParams);

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
                case GAMES: renderGameSettings(page); break;
                case WEATHER: renderWeatherSettings(page); break;
                case NAVIGATION: renderNavigationSettings(page); break;
                case LANGUAGE: renderLanguageSettings(page); break;
                default: break;
            }
            if (restoringBackup && taskProgress == null) addTaskProgress(headerCopy);
            updateTaskProgress();
            if (firstControl != null && firstControl.isEnabled()) {
                firstControl.setId(View.generateViewId());
                if (isRtl()) {
                    tabs[section].setNextFocusLeftId(firstControl.getId());
                    if (!(firstControl instanceof EditText)) firstControl.setNextFocusRightId(tabs[section].getId());
                } else {
                    tabs[section].setNextFocusRightId(firstControl.getId());
                    if (!(firstControl instanceof EditText)) firstControl.setNextFocusLeftId(tabs[section].getId());
                }
                firstControl.setNextFocusUpId(tabs[section].getId());
            }
            restoreLayout(restoreFocus);
            if (animateSection) Motion.reveal(page);
        }

        private void renderGameSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_library));
            addFormHint(form, AppLanguage.get(R.string.ui_sva_supports_user_provided_nes_files_only_games_are_copied_into));
            addFormButton(form, AppLanguage.get(R.string.ui_open_game_library_and_import), v -> openGameLibrary());
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_controllers));
            addFormButton(form, AppLanguage.get(R.string.ui_configure_game_controllers), v -> startActivity(new Intent(MainActivity.this, ControllerSettingsActivity.class)));
            addFormHint(form, AppLanguage.get(R.string.ui_nestopia_ue_is_embedded_in_sva_on_a_tv_remote_use_the_arrow_butt));
            addFormHint(form, AppLanguage.get(R.string.ui_licenses));
        }

        private void renderLanguageSettings(LinearLayout page) {
            addFormHint(page, AppLanguage.get(R.string.ui_follow_your_tv_language_or_choose_a_different_language_for_sva_t));
            int selected = java.util.Arrays.asList(AppLanguage.TAGS).indexOf(AppLanguage.selected(MainActivity.this));
            View choice = SettingsUi.choice(MainActivity.this, AppLanguage.get(R.string.ui_language), AppLanguage.labels(MainActivity.this),
                    Math.max(0, selected), index -> {
                        String tag = AppLanguage.TAGS[index];
                        if (tag.equals(AppLanguage.selected(MainActivity.this))) return;
                        captureDrafts();

                        AppLanguage.select(MainActivity.this, tag);
                        // Older TVs do not dispatch a locale configuration change for an app override.
                        if (android.os.Build.VERSION.SDK_INT < 33) recreate();
                    });
            page.addView(choice);
            firstControl = choice;
        }

        private void renderNavigationSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            LinearLayout orderRows = new LinearLayout(MainActivity.this);
            orderRows.setOrientation(LinearLayout.VERTICAL);
            orderRows.setClipChildren(false);
            orderRows.setClipToPadding(false);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_visible_categories));
            addFormHint(form, AppLanguage.get(R.string.ui_choose_which_categories_appear_in_the_navigation_bar_your_games));
            int[] visibilityLabels = {R.string.ui_show_live_tv_in_navigation, R.string.ui_show_games_in_navigation,
                    R.string.ui_show_events_in_navigation, R.string.ui_show_saved_in_navigation,
                    R.string.ui_show_news_in_navigation, R.string.ui_show_activities};
            for (int tab : navigationOrder()) {
                String key = NavigationPreferences.hiddenKey(tab);
                View toggle = addToggle(form, AppLanguage.get(visibilityLabels[tab - 1]), !isNavigationHidden(tab), enabled -> {
                    preferences.edit().putBoolean(key, !enabled).apply();
                    rebuildNavigationButtons();
                    renderNavigationOrder(orderRows, -1);
                    if (!enabled && activeTab == tab) selectTab(TAB_HOME, true);
                });
                toggle.setTag("navigation-visible-" + tab);
            }
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_item_order));
            addFormHint(form, AppLanguage.get(R.string.ui_home_stays_first_select_a_category_to_change_its_position_hidden));
            form.addView(orderRows);
            renderNavigationOrder(orderRows, -1);
            addFormButton(form, AppLanguage.get(R.string.ui_reset_item_order), v -> {
                preferences.edit().remove(NavigationPreferences.ORDER).remove(PREF_LIVE_TV_POSITION).apply();
                rebuildNavigationButtons();
                renderNavigationOrder(orderRows, -1);
            });
        }

        private void renderNavigationOrder(LinearLayout container, int focusTab) {
            container.removeAllViews();
            List<Integer> ordered = navigationOrder();
            String[] positions = new String[ordered.size()];
            for (int i = 0; i < positions.length; i++) positions[i] = getString(R.string.ui_navigation_position, i + 1);
            for (int position = 0; position < ordered.size(); position++) {
                final int tab = ordered.get(position);
                View row = SettingsUi.choice(MainActivity.this, TAB_TITLES[tab]
                        + (isNavigationHidden(tab) ? AppLanguage.get(R.string.ui_hidden_ce2680d) : ""), positions, position, destination -> {
                    List<Integer> updated = navigationOrder();
                    updated.remove(Integer.valueOf(tab)); updated.add(destination, tab);
                    preferences.edit().putString(NavigationPreferences.ORDER, NavigationPreferences.encode(updated)).apply();
                    rebuildNavigationButtons(); renderNavigationOrder(container, tab);
                });
                row.setTag("navigation-order-" + tab);
                SettingsUi.add(container, row);
                if (focusTab == tab) row.post(row::requestFocus);
            }
        }

        private void renderWeatherSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            WeatherRepository.Location saved = savedWeatherLocation();
            // — Location ——————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_location));
            addFormHint(form, saved == null ? AppLanguage.get(R.string.ui_choose_a_city_or_use_this_tv_s_location_to_show_weather_on_home)
                    : AppLanguage.get(R.string.ui_weather_location_1_s, saved.label));
            EditText search = addInput(form, AppLanguage.get(R.string.ui_search_city_or_postal_code), AppLanguage.get(R.string.ui_pune_india), "", false);
            weatherSearchResults = new LinearLayout(MainActivity.this);
            weatherSearchResults.setOrientation(LinearLayout.VERTICAL);
            weatherSearchResults.setClipChildren(false);
            weatherSearchResults.setClipToPadding(false);
            addFormButton(form, AppLanguage.get(R.string.ui_search), v -> {
                String query = search.getText().toString().trim();
                if (query.length() < 2) { setWeatherFeedback(AppLanguage.get(R.string.ui_enter_at_least_two_characters)); return; }
                setWeatherFeedback(AppLanguage.get(R.string.ui_searching_locations)); weatherSearchResults.removeAllViews();
                lifecycleWork.execute(ioWorker, () -> {
                    List<WeatherRepository.Location> results = null; String error = null;
                    try { results = WeatherRepository.search(query, AppLanguage.locale()); }
                    catch (Exception failure) { error = failure.getMessage() == null ? AppLanguage.get(R.string.ui_location_search_failed) : failure.getMessage(); }
                    List<WeatherRepository.Location> found = results; String failure = error;
                    lifecycleWork.post(() -> {
                        if (settingsScreen != this || section != WEATHER) return;
                        weatherSearchResults.removeAllViews();
                        if (failure != null) { setWeatherFeedback(AppLanguage.get(R.string.ui_search_failed_1_s, failure)); return; }
                        if (found == null || found.isEmpty()) { setWeatherFeedback(AppLanguage.get(R.string.ui_no_matching_places_found)); return; }
                        setWeatherFeedback(AppLanguage.get(R.string.ui_choose_a_location));
                        for (WeatherRepository.Location location : found) {
                            Button option = addFormButton(weatherSearchResults, location.label, null);
                            option.setOnClickListener(ignored -> saveWeatherLocation("city", location));
                        }
                    });
                });
            });
            form.addView(weatherSearchResults);
            addFormButton(form, AppLanguage.get(R.string.ui_use_device_location), v -> requestDeviceWeatherLocation());
            if (saved != null) addFormButton(form, AppLanguage.get(R.string.ui_remove_weather), true, v -> {
                preferences.edit().remove("weatherLocationMode").remove("weatherLocationLabel")
                        .remove("weatherLatitude").remove("weatherLongitude").remove("weatherCache")
                        .remove("weatherUpdatedAt").remove("weatherAttemptedAt").apply();
                weatherForecast = null; weatherRequestGeneration++; weatherRefreshing = false; MainActivity.this.updateTaskProgress();
                renderWeather(); renderSection();
            });
            // — Units ——————————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_units));
            addChoice(form, AppLanguage.get(R.string.ui_temperature), new String[]{AppLanguage.get(R.string.ui_celsius), AppLanguage.get(R.string.ui_fahrenheit)},
                    preferences.getBoolean("weatherFahrenheit", false) ? 1 : 0, index -> {
                        preferences.edit().putBoolean("weatherFahrenheit", index == 1).apply(); renderWeather();
                    });
            addChoice(form, AppLanguage.get(R.string.ui_wind_speed), new String[]{"km/h", "mph"},
                    preferences.getBoolean("weatherMph", false) ? 1 : 0, index -> {
                        preferences.edit().putBoolean("weatherMph", index == 1).apply(); renderWeather();
                    });
            addFormButton(form, AppLanguage.get(R.string.ui_refresh_now), v -> {
                if (!hasWeatherLocation()) { setWeatherFeedback(AppLanguage.get(R.string.ui_choose_a_location_first)); return; }
                if ("device".equals(preferences.getString("weatherLocationMode", ""))) requestDeviceWeatherLocation();
                else maybeRefreshWeather(true);
            });
            weatherFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            weatherFeedback.setPadding(dp(2), dp(6), dp(2), dp(6));
            form.addView(weatherFeedback);
            addTaskProgress(form);
            addFormHint(form, AppLanguage.get(R.string.ui_weather_by_open_meteo_locations_by_geonames_device_location_requ));
        }

        private void setWeatherFeedback(String message) {
            if (weatherFeedback != null) weatherFeedback.setText(message == null ? "" : message);
        }

        private void renderHomeOverride(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_override_status));
            boolean enabled = isLauncherOverrideEnabled();
            addFormHint(form, enabled
                    ? AppLanguage.get(R.string.ui_sva_launcher_override_is_enabled_on_this_tv)
                    : AppLanguage.get(R.string.ui_sva_launcher_override_is_currently_disabled));
            addFormHint(form, AppLanguage.get(R.string.ui_some_tvs_keep_their_built_in_launcher_as_home_even_when_another));
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_settings_shortcuts));
            TextView accessibilityFeedback = SettingsUi.text(MainActivity.this, "", 14, true);
            accessibilityFeedback.setTag("accessibility-shortcut-feedback");
            accessibilityFeedback.setVisibility(View.GONE);
            addFormButton(form, AppLanguage.get(R.string.ui_open_accessibility_settings), v -> AccessibilitySettingsShortcut.open(
                    action -> startActivity(new Intent(action)), accessibilityFeedback));
            SettingsUi.add(form, accessibilityFeedback);
            addFormButton(form, AppLanguage.get(R.string.ui_try_setting_sva_as_the_home_app), v -> {
                try {
                    startActivity(new Intent("android.settings.HOME_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) { Toast.makeText(MainActivity.this, AppLanguage.get(R.string.ui_could_not_open_home_settings), Toast.LENGTH_LONG).show(); }
                }
            });
        }

        private void addTaskProgress(LinearLayout parent) {
            taskProgress = new TaskProgress(MainActivity.this, false);
            parent.addView(taskProgress, new LinearLayout.LayoutParams(-1, -2));
        }

        private void updateTaskProgress() {
            boolean busy = false;
            String message = "";
            if (section == LIVE || section == CHANNELS) {
                busy = channelsRefreshing || guideRefreshing;
                message = AppLanguage.get(channelsRefreshing ? R.string.ui_refreshing_channels : R.string.ui_refreshing_programme_guide);
            } else if (section == WEATHER) {
                busy = weatherRefreshing; message = AppLanguage.get(R.string.ui_refreshing_weather);
            } else if (section == CALENDAR) {
                busy = calendarRefreshing; message = AppLanguage.get(R.string.ui_refreshing_calendar_feeds);
            } else if (section == APPEARANCE) {
                busy = albumLoading; message = AppLanguage.get(R.string.ui_loading_public_album);
            } else if (section == BACKUP) {
                busy = restoringBackup; message = AppLanguage.get(R.string.ui_restoring_launcher_backup);
            }
            if (restoringBackup) {
                busy = true; message = AppLanguage.get(R.string.ui_restoring_launcher_backup);
            }
            if (taskProgress != null) taskProgress.setBusy(busy, message);
            detail.setDescendantFocusability(restoringBackup ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (backupExportProgress != null) backupExportProgress.setBusy(exportingBackup, AppLanguage.get(R.string.ui_creating_launcher_backup));
            if (backupExportButton != null) backupExportButton.setEnabled(!exportingBackup && !restoringBackup);
            if (backupImportButton != null) backupImportButton.setEnabled(!exportingBackup && !restoringBackup);
            if (restoringBackup) {
                disableSettingsEdits(detail);
                View focused = dialog.getCurrentFocus();
                if (focused != null && isWithinPage(focused, detail)) tabs[section].requestFocus();
            }
        }

        private void disableSettingsEdits(View view) {
            if (view instanceof EditText || view.isClickable()) view.setEnabled(false);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) disableSettingsEdits(group.getChildAt(i));
            }
        }

        private void renderBackup(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_create_backup));
            addFormHint(form, AppLanguage.get(R.string.ui_create_a_json_backup_of_launcher_settings_app_and_channel_choice));
            backupExportButton = addFormButton(form, AppLanguage.get(R.string.ui_create_backup_file), v -> openBackupExportPicker());
            backupExportProgress = new TaskProgress(MainActivity.this, false);
            form.addView(backupExportProgress, new LinearLayout.LayoutParams(-1, -2));
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_restore_from_backup));
            addFormHint(form, AppLanguage.get(R.string.ui_restoring_replaces_the_current_launcher_settings_and_channel_pre));
            backupImportButton = addFormButton(form, AppLanguage.get(R.string.ui_restore_from_backup), v -> openBackupImportPicker());
            addTaskProgress(form);
        }

        private void renderSchedules(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_refresh_intervals));
            addFormHint(form, AppLanguage.get(R.string.ui_schedules_run_while_sva_is_open_overdue_content_is_checked_when));
            addScheduleButton(form, AppLanguage.get(R.string.ui_photo_slideshow), SchedulePreferences.SLIDESHOW,
                    SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW, true, false,
                    () -> {
                        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
                        scheduleNextBackgroundPhoto();
                    });
            addScheduleButton(form, AppLanguage.get(R.string.ui_iptv_playlist), SchedulePreferences.PLAYLIST,
                    SchedulePreferences.PLAYLIST_VALUES, SchedulePreferences.DEFAULT_PLAYLIST, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, AppLanguage.get(R.string.ui_programme_guide), SchedulePreferences.GUIDE,
                    SchedulePreferences.GUIDE_VALUES, SchedulePreferences.DEFAULT_GUIDE, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, AppLanguage.get(R.string.ui_public_calendar), SchedulePreferences.CALENDAR,
                    SchedulePreferences.CALENDAR_VALUES, SchedulePreferences.DEFAULT_CALENDAR, false, false,
                    () -> { maybeRefreshPublicCalendar(false); scheduleCalendarRefresh(); });
            addScheduleButton(form, AppLanguage.get(R.string.ui_google_photos_album), SchedulePreferences.GOOGLE_PHOTOS,
                    SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS, false, true,
                    () -> { maybeRefreshGooglePhotos(false); scheduleGooglePhotosRefresh(); });
            addScheduleButton(form, AppLanguage.get(R.string.ui_weather), SchedulePreferences.WEATHER,
                    SchedulePreferences.WEATHER_VALUES, SchedulePreferences.DEFAULT_WEATHER, false, true,
                    () -> { weatherRefreshedThisLaunch = false; maybeRefreshWeather(false); scheduleWeatherRefresh(); });
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_defaults));
            addFormHint(form, AppLanguage.get(R.string.ui_paused_keeps_the_current_photo_visible_every_launch_refreshes_go));
            addFormButton(form, AppLanguage.get(R.string.ui_restore_schedule_defaults), v -> {
                preferences.edit()
                        .putLong(SchedulePreferences.SLIDESHOW, SchedulePreferences.DEFAULT_SLIDESHOW)
                        .putLong(SchedulePreferences.PLAYLIST, SchedulePreferences.DEFAULT_PLAYLIST)
                        .putLong(SchedulePreferences.GUIDE, SchedulePreferences.DEFAULT_GUIDE)
                        .putLong(SchedulePreferences.CALENDAR, SchedulePreferences.DEFAULT_CALENDAR)
                        .putLong(SchedulePreferences.GOOGLE_PHOTOS, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS)
                        .putLong(SchedulePreferences.WEATHER, SchedulePreferences.DEFAULT_WEATHER)
                        .apply();
                maybeRefreshLiveData();
                scheduleLiveRefreshCheck();
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
                scheduleGooglePhotosRefresh();
                weatherRefreshedThisLaunch = false;
                maybeRefreshWeather(false);
                scheduleWeatherRefresh();
                scheduleNextBackgroundPhoto();
                renderSection();
            });
        }

        private void addScheduleButton(LinearLayout form, String title, String key, long[] values,
                                       long fallback, boolean slideshow, boolean googlePhotos,
                                       Runnable apply) {
            long current = scheduleValue(key, values, fallback);
            String[] labels = new String[values.length];
            int selected = 0;
            for (int i = 0; i < values.length; i++) {
                labels[i] = AppLanguage.duration(values[i], slideshow, googlePhotos);
                if (values[i] == current) selected = i;
            }
            addChoice(form, title, labels, selected, index -> {
                preferences.edit().putLong(key, values[index]).apply(); apply.run();
            });
        }

        private void renderYouTubeSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_add_shortcut));
            addFormHint(form, AppLanguage.get(R.string.ui_paste_a_public_youtube_video_or_playlist_link_to_add_a_card_to_s));
            youtubeUrlField = addInput(form, AppLanguage.get(R.string.ui_video_or_playlist_link), "https://www.youtube.com/watch?v=…", draftYoutubeUrl, false);
            youtubeTitleField = addInput(form, AppLanguage.get(R.string.ui_card_title_optional), AppLanguage.get(R.string.ui_use_the_youtube_title), draftYoutubeTitle, false);
            youtubeArtField = addInput(form, AppLanguage.get(R.string.ui_artwork_video_link_optional), AppLanguage.get(R.string.ui_use_for_playlist_artwork), draftYoutubeArt, false);
            youtubeAddButton = addFormButton(form, AppLanguage.get(R.string.ui_add_to_saved), view -> {
                String value = youtubeUrlField == null ? draftYoutubeUrl : youtubeUrlField.getText().toString();
                String customTitle = youtubeTitleField == null ? draftYoutubeTitle : youtubeTitleField.getText().toString().trim();
                String artworkVideo = youtubeArtField == null ? draftYoutubeArt : youtubeArtField.getText().toString().trim();
                YouTubeCard parsed = YouTubeCard.parse(value);
                if (parsed == null) {
                    if (youtubeFeedback != null) youtubeFeedback.setText(AppLanguage.get(R.string.ui_enter_a_valid_youtube_video_or_playlist_link));
                    return;
                }
                List<YouTubeCard> cards = savedYouTubeCards();
                for (YouTubeCard card : cards) {
                    if (card.url.equals(parsed.url)) {
                        if (youtubeFeedback != null) youtubeFeedback.setText(AppLanguage.get(R.string.ui_that_link_is_already_in_saved));
                        return;
                    }
                }
                if (!customTitle.isEmpty()) {
                    parsed.title = customTitle;
                    parsed.customTitle = true;
                }
                if (!artworkVideo.isEmpty() && !parsed.useArtworkFromVideo(artworkVideo)) {
                    if (youtubeFeedback != null) youtubeFeedback.setText(AppLanguage.get(R.string.ui_artwork_must_be_a_youtube_video_link));
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
                if (youtubeFeedback != null) youtubeFeedback.setText(AppLanguage.get(R.string.ui_added_to_saved));
                renderYouTubeSavedList();
                if (youtubeUrlField != null) youtubeUrlField.requestFocus();
            });
            youtubeFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            youtubeFeedback.setPadding(0, dp(4), 0, dp(8));
            form.addView(youtubeFeedback);
            TextView savedTitle = label(AppLanguage.get(R.string.ui_saved_shortcuts), 18, SettingsUi.text(MainActivity.this), true);
            savedTitle.setLetterSpacing(.12f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(36));
            titleParams.topMargin = dp(10);
            form.addView(savedTitle, titleParams);
            youtubeSavedList = new LinearLayout(MainActivity.this);
            youtubeSavedList.setOrientation(LinearLayout.VERTICAL);
            youtubeSavedList.setClipChildren(false);
            youtubeSavedList.setClipToPadding(false);
            form.addView(youtubeSavedList);
            renderYouTubeSavedList();
        }

        private void renderYouTubeSavedList() {
            if (youtubeSavedList == null) return;
            youtubeSavedList.removeAllViews();
            List<YouTubeCard> cards = savedYouTubeCards();
            if (cards.isEmpty()) {
                if (youtubeAddButton != null) youtubeAddButton.setNextFocusDownId(View.NO_ID);
                youtubeSavedList.addView(label(AppLanguage.get(R.string.ui_no_youtube_cards_saved_yet), 14,
                        SettingsUi.muted(MainActivity.this), false));
                return;
            }
            Button firstEditTitle = null;
            for (YouTubeCard card : cards) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                        LiquidTheme.border(MainActivity.this), 1, 13));
                LinearLayout copy = new LinearLayout(MainActivity.this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView title = label(card.title, 15, SettingsUi.text(MainActivity.this), true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(title);
                TextView detail = label((YouTubeCard.VIDEO.equals(card.type) ? AppLanguage.get(R.string.ui_video) : AppLanguage.get(R.string.ui_playlist))
                        + ((card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()) ? ("  ·  " + AppLanguage.get(R.string.ui_add_art).replace("·", "").trim()) : "")
                        + "  ·  " + card.url, 11, SettingsUi.muted(MainActivity.this), false);
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                copy.addView(detail);
                row.addView(copy, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout cardActions = new LinearLayout(MainActivity.this);
                cardActions.setTag("settings-card-actions");
                cardActions.setClipChildren(false);
                cardActions.setClipToPadding(false);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams cardActionParams = new LinearLayout.LayoutParams(-1, -2);
                cardActionParams.topMargin = dp(10);
                row.addView(cardActions, cardActionParams);
                Button remove = settingsButton(AppLanguage.get(R.string.ui_remove));
                remove.setContentDescription(AppLanguage.get(R.string.ui_remove_1_s_from_saved, card.displayTitle()));
                Button editTitle = settingsButton(AppLanguage.get(R.string.ui_title));
                editTitle.setContentDescription(AppLanguage.get(R.string.ui_edit_title_for_1_s, card.displayTitle()));
                editTitle.setId(View.generateViewId());
                if (firstEditTitle == null) {
                    firstEditTitle = editTitle;
                }
                Button editArt = settingsButton(AppLanguage.get(R.string.ui_art));
                editArt.setContentDescription(AppLanguage.get(R.string.ui_edit_artwork_for_1_s, card.displayTitle()));
                LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                editParams.setMargins(dp(2), dp(2), dp(3), dp(2));
                cardActions.addView(editTitle, editParams);
                LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                artParams.setMargins(dp(3), dp(2), dp(3), dp(2));
                cardActions.addView(editArt, artParams);
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                removeParams.setMargins(dp(3), dp(2), dp(2), dp(2));
                cardActions.addView(remove, removeParams);
                editTitle.setOnClickListener(view -> editYouTubeTitle(card));
                editArt.setOnClickListener(view -> editYouTubeArtwork(card));
                remove.setOnClickListener(view -> {
                    List<YouTubeCard> latest = savedYouTubeCards();
                    ArrayList<YouTubeCard> retained = new ArrayList<>();
                    for (YouTubeCard saved : latest) if (!saved.url.equals(card.url)) retained.add(saved);
                    saveYouTubeCards(retained);
                    youtubeChanged = true;
                    if (youtubeFeedback != null) youtubeFeedback.setText(AppLanguage.get(R.string.ui_removed_from_saved));
                    renderYouTubeSavedList();
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(dp(6), dp(2), dp(6), dp(8));
                youtubeSavedList.addView(row, rowParams);
            }
            if (youtubeAddButton != null && firstEditTitle != null) {
                if (youtubeAddButton.getId() == View.NO_ID) youtubeAddButton.setId(View.generateViewId());
                youtubeAddButton.setNextFocusDownId(firstEditTitle.getId());
                firstEditTitle.setNextFocusUpId(youtubeAddButton.getId());
            }
        }

        private void editYouTubeArtwork(YouTubeCard selected) {
            EditText artworkInput = new EditText(MainActivity.this);
            artworkInput.setSingleLine(true);
            artworkInput.setHint(AppLanguage.get(R.string.ui_youtube_video_link));
            artworkInput.setTextColor(SettingsUi.text(MainActivity.this));
            artworkInput.setHintTextColor(SettingsUi.muted(MainActivity.this));
            artworkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            artworkInput.setPadding(dp(24), dp(12), dp(24), dp(12));
            artworkInput.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog artworkDialog = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle(AppLanguage.get(R.string.ui_card_artwork))
                    .setMessage(AppLanguage.get(R.string.ui_paste_a_youtube_video_link_to_use_its_thumbnail_leave_empty_to_r))
                    .setView(artworkInput)
                    .setNegativeButton(AppLanguage.get(R.string.ui_cancel), null)
                    .setPositiveButton(AppLanguage.get(R.string.ui_save), null)
                    .create();
            artworkDialog.setOnShowListener(ignored -> artworkDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String videoLink = artworkInput.getText().toString().trim();
                        YouTubeCard image = videoLink.isEmpty() ? null : YouTubeCard.parse(videoLink);
                        if (!videoLink.isEmpty() && (image == null || !YouTubeCard.VIDEO.equals(image.type))) {
                            artworkInput.setError(AppLanguage.get(R.string.ui_enter_a_youtube_video_link));
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
            Motion.dialog(artworkDialog); artworkDialog.show();
            styleDialog(artworkDialog);
        }

        private void editYouTubeTitle(YouTubeCard selected) {
            EditText titleInput = new EditText(MainActivity.this);
            titleInput.setSingleLine(true);
            titleInput.setHint(AppLanguage.get(R.string.ui_use_the_youtube_title));
            titleInput.setText(selected.customTitle ? selected.title : "");
            titleInput.setTextColor(SettingsUi.text(MainActivity.this));
            titleInput.setHintTextColor(SettingsUi.muted(MainActivity.this));
            titleInput.setSelectAllOnFocus(true);
            titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            int inset = dp(24);
            titleInput.setPadding(inset, dp(12), inset, dp(12));
            titleInput.setBackground(roundedShape(
                    LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle(AppLanguage.get(R.string.ui_card_title))
                    .setMessage(AppLanguage.get(R.string.ui_clear_the_title_to_use_the_title_provided_by_youtube_when_availa))
                    .setView(titleInput)
                    .setNegativeButton(AppLanguage.get(R.string.ui_cancel), null)
                    .setPositiveButton(AppLanguage.get(R.string.ui_save), (dialog, which) -> {
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
                    }).create();
            Motion.dialog(prompt); prompt.show();
            styleDialog(prompt);
        }

        private void renderCalendarSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_calendar_feeds));
            addFormHint(form, AppLanguage.get(R.string.ui_add_icalendar_feed_urls_one_per_line_google_calendar_published_c));
            calendarUrlField = addInput(form, AppLanguage.get(R.string.ui_calendar_feeds_one_url_per_line), "https://example.com/calendar.ics", draftCalendarUrl, true);
            calendarSummary = label(calendarSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            calendarSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            calendarSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams csParams = new LinearLayout.LayoutParams(-1, -2);
            csParams.setMargins(0, dp(4), 0, dp(6));
            form.addView(calendarSummary, csParams);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_actions));
            addFormButton(form, AppLanguage.get(R.string.ui_save_calendar_and_refresh), view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString();
                final List<String> urls;
                try { urls = PublicCalendarRepository.normalizeUrls(entered); }
                catch (IllegalArgumentException failure) {
                    if (calendarFeedback != null) calendarFeedback.setText(failure.getMessage());
                    return;
                }
                String normalized = String.join("\n", urls);
                CalendarFeedCache cache = calendarFeedCache();
                cancelCalendarRefresh();
                cache.retain(urls);
                if (urls.isEmpty()) {
                    preferences.edit().remove("publicCalendarUrl").remove("publicCalendarEvents")
                            .remove(CalendarFeedCache.KEY).remove("publicCalendarUpdatedAt").remove("publicCalendarAttemptedAt")
                            .remove("publicCalendarCacheVersion").remove("publicCalendarAttemptedVersion").apply();
                    renderedCalendarStamp = -1;
                    draftCalendarUrl = "";
                    if (calendarUrlField != null) calendarUrlField.setText("");
                    if (calendarFeedback != null) calendarFeedback.setText(AppLanguage.get(R.string.ui_calendars_removed));
                    updateCalendarEventDisplay();
                    scheduleCalendarRefresh();
                    if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                    return;
                }
                preferences.edit().putString("publicCalendarUrl", normalized).apply();
                saveCalendarCache(cache, urls, false);
                draftCalendarUrl = normalized;
                if (calendarUrlField != null) calendarUrlField.setText(normalized);
                if (calendarFeedback != null) calendarFeedback.setText(AppLanguage.get(R.string.ui_refreshing_calendar_feeds));
                if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                refreshPublicCalendar(normalized, true);
            });
            addFormButton(form, AppLanguage.get(R.string.ui_refresh_saved_calendar_now), view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString();
                if (!entered.equals(preferences.getString("publicCalendarUrl", ""))) {
                    if (calendarFeedback != null) calendarFeedback.setText(AppLanguage.get(R.string.ui_save_the_calendar_feeds_before_refreshing));
                } else if (entered.isEmpty()) {
                    if (calendarFeedback != null) calendarFeedback.setText(AppLanguage.get(R.string.ui_add_a_calendar_feed_first));
                } else {
                    if (calendarFeedback != null) calendarFeedback.setText(AppLanguage.get(R.string.ui_refreshing_calendar_feeds));
                    refreshPublicCalendar(entered, true);
                }
            });
            calendarFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            calendarFeedback.setPadding(dp(2), dp(4), dp(2), dp(4));
            form.addView(calendarFeedback);
            addTaskProgress(form);
            addFormHint(form, AppLanguage.get(R.string.ui_the_on_device_schedule_is_checked_when_sva_opens_change_its_auto));
        }

        private String calendarSummaryText() {
            long updatedAt = preferences.getLong("publicCalendarUpdatedAt", 0L);
            JSONArray events;
            try { events = new JSONArray(preferences.getString("publicCalendarEvents", "[]")); }
            catch (Exception ignored) { events = new JSONArray(); }
            String updated = updatedAt == 0L ? AppLanguage.get(R.string.ui_not_refreshed_yet)
                    : AppLanguage.get(R.string.ui_updated_1_s, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, AppLanguage.locale())
                    .format(new Date(updatedAt)));
            int sources;
            try { sources = PublicCalendarRepository.normalizeUrls(preferences.getString("publicCalendarUrl", "")).size(); }
            catch (IllegalArgumentException invalidSavedUrls) { return AppLanguage.get(R.string.ui_calendar_feeds_need_correcting_1_s_cached_events, events.length()); }
            return AppLanguage.get(R.string.ui_calendar_cache_1_s_sources_2_s_events_3_s, sources, events.length(), updated);
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
            scroll.setPadding(dp(8), dp(4), dp(8), dp(6));
            scroll.setClipToPadding(false);
            scroll.setSmoothScrollingEnabled(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout form = new LinearLayout(MainActivity.this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setClipChildren(false);
            form.setClipToPadding(false);
            form.setPadding(0, 0, 0, dp(8));
            scroll.addView(form);
            return form;
        }

        private EditText addInput(LinearLayout form, String labelText, String hintText, String value, boolean multiline) {
            TextView title = label(labelText, 14, SettingsUi.muted(MainActivity.this), false);

            title.setMinHeight(dp(24));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.setMargins(dp(10), dp(10), dp(10), 0);
            form.addView(title, labelParams);
            EditText field = new EditText(MainActivity.this);
            field.setText(value);
            field.setHint(hintText);
            field.setTextColor(SettingsUi.text(MainActivity.this));
            field.setHintTextColor(SettingsUi.muted(MainActivity.this));
            field.setTextSize(14);
            field.setTypeface(LiquidTheme.font(MainActivity.this));
            field.setSelectAllOnFocus(!multiline);
            field.setInputType(multiline
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            if (!multiline) {
                field.setSingleLine(true);
                field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                field.setOnEditorActionListener((view, actionId, event) -> {
                    if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
                    android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
                    field.clearFocus();
                    return true;
                });
            }
            field.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
            field.setPadding(dp(16), dp(10), dp(16), dp(10));
            SettingsUi.styleInput(field, multiline);
            field.setOnClickListener(v -> {
                field.requestFocus();
                showSettingsKeyboard(field);
            });
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
            fieldParams.setMargins(dp(8), dp(4), dp(8), dp(12));
            form.addView(field, fieldParams);
            if (firstControl == null) firstControl = field;
            return field;
        }

        private void showSettingsKeyboard(EditText field) {
            field.post(() -> {
                if (!field.hasFocus() || !field.isAttachedToWindow()) return;
                android.view.inputmethod.InputMethodManager keyboard =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(field,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        }

        private Button addFormButton(LinearLayout form, String text, View.OnClickListener listener) {
            return addFormButton(form, text, false, listener);
        }

        private Button addFormButton(LinearLayout form, String text, boolean destructive, View.OnClickListener listener) {
            Button button = settingsButton(text);
            if (destructive) button.setTextColor(LiquidTheme.isLight(MainActivity.this) ? 0xFFAA2635 : 0xFFFFA8AB);
            button.setOnClickListener(listener);
            button.setMinHeight(dp(50));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(8), dp(6), dp(8), dp(6));
            form.addView(button, params);
            if (firstControl == null) firstControl = button;
            return button;
        }

        private void addFormSectionHeader(LinearLayout form, String label) {
            SettingsUi.heading(form, label);
        }

        private Button settingsButton(String text) {
            Button button = SettingsUi.action(MainActivity.this, text, null);
            if (text.equals("↑") || text.equals("↓")) {
                button.setPadding(dp(4), 0, dp(4), 0);
                button.setGravity(Gravity.CENTER);
                button.setContentDescription(text.equals("↑") ? AppLanguage.get(R.string.ui_move_up) : AppLanguage.get(R.string.ui_move_down));
            }
            return button;
        }

        private void addFormHint(LinearLayout form, String value) {
            SettingsUi.hint(form, value);
        }

        private View addToggle(LinearLayout form, String title, boolean checked, java.util.function.Consumer<Boolean> save) {
            View row = SettingsUi.toggle(MainActivity.this, title, checked, save);
            SettingsUi.add(form, row);
            if (firstControl == null) firstControl = row;
            return row;
        }

        private void addChoice(LinearLayout form, String title, String[] options, int selected, java.util.function.IntConsumer save) {
            View row = SettingsUi.choice(MainActivity.this, title, options, selected, save);
            SettingsUi.add(form, row);
            if (firstControl == null) firstControl = row;
        }

        private void addSaveActions(LinearLayout page, View.OnClickListener save,
                                    View.OnClickListener discard, View.OnClickListener refresh) {
            LinearLayout actions = new LinearLayout(MainActivity.this);
            actions.setTag("settings-save-actions");
            actions.setClipChildren(false);
            actions.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            rowParams.setMargins(dp(8), dp(10), dp(8), dp(4));
            page.addView(actions, rowParams);
            Button saveButton = settingsButton(AppLanguage.get(R.string.ui_save_changes));
            saveButton.setTextColor(LiquidTheme.accent(MainActivity.this));
            saveButton.setMinHeight(dp(50));
            saveButton.setOnClickListener(save);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
            saveParams.setMargins(dp(2), dp(3), dp(4), dp(3));
            actions.addView(saveButton, saveParams);
            Button discardButton = settingsButton(AppLanguage.get(R.string.ui_discard_edits));
            discardButton.setOnClickListener(discard);
            discardButton.setMinHeight(dp(50));
            LinearLayout.LayoutParams discardParams = new LinearLayout.LayoutParams(0, -2, 1);
            discardParams.setMargins(dp(6), dp(3), dp(4), dp(3));
            actions.addView(discardButton, discardParams);
            if (refresh != null) {
                Button refreshButton = settingsButton(AppLanguage.get(R.string.ui_refresh_channels));
                refreshButton.setOnClickListener(refresh);
                refreshButton.setMinHeight(dp(50));
                LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, -2, 1);
                refreshParams.setMargins(dp(6), dp(3), dp(2), dp(3));
                actions.addView(refreshButton, refreshParams);
            }
            // Geometry can prefer the sidebar or the current editor when the
            // form scrolls. Give vertical remote navigation a stable route.
            ArrayList<View> controls = new ArrayList<>();
            for (int i = 0; i < page.getChildCount(); i++) {
                View child = page.getChildAt(i);
                if (!(child instanceof ScrollView)) continue;
                for (View control : child.getFocusables(View.FOCUS_FORWARD)) {
                    if (!(control instanceof ScrollView) && control.isEnabled()) controls.add(control);
                }
            }
            controls.add(saveButton);
            for (View control : controls) {
                if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
            }
            for (int i = 1; i < controls.size(); i++) {
                controls.get(i - 1).setNextFocusDownId(controls.get(i).getId());
                controls.get(i).setNextFocusUpId(controls.get(i - 1).getId());
            }
        }

        private void renderLive(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_playlist_guide_urls));
            playlistField = addInput(form, AppLanguage.get(R.string.ui_m3u_playlists_one_url_per_line), "https://example.com/playlist.m3u", draftPlaylist, true);
            guideField = addInput(form, AppLanguage.get(R.string.ui_programme_guides_one_url_per_line), "https://www.epgschedule.com/data/index-lite.json", draftGuide, true);
            addFormHint(form, AppLanguage.get(R.string.ui_channels_from_all_playlists_are_combined_guide_schedules_from_al));
            addFormHint(form, AppLanguage.get(R.string.ui_xmltv_matches_by_tvg_id_json_guides_match_channel_ids_or_a_uniqu));
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_cache_guide_status));
            cacheSummary = label(cacheSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            cacheSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            cacheSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams csParams = new LinearLayout.LayoutParams(-1, -2);
            csParams.setMargins(0, dp(4), 0, dp(4));
            form.addView(cacheSummary, csParams);

            guideSummary = label(guideSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            guideSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            guideSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams gsParams = new LinearLayout.LayoutParams(-1, -2);
            gsParams.setMargins(0, dp(4), 0, dp(6));
            form.addView(guideSummary, gsParams);

            addFormButton(form, AppLanguage.get(R.string.ui_refresh_programme_guide_now), v -> {
                String enteredGuide = guideField == null ? draftGuide : guideField.getText().toString().trim();
                if (enteredGuide.isEmpty() || !enteredGuide.equals(iptv.guideUrl())) {
                    if (liveFeedback != null) liveFeedback.setText(AppLanguage.get(R.string.ui_save_the_programme_guide_url_before_refreshing_it));
                    return;
                }
                refreshGuide(true);
            });
            addFormHint(form, AppLanguage.get(R.string.ui_playlist_and_programme_guide_refresh_cadences_are_configured_ind));
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
                        ? AppLanguage.get(R.string.ui_settings_saved_automatic_refresh_uses_the_cadences_under_schedul)
                        : AppLanguage.get(R.string.ui_no_changes_to_save));
            }, v -> {
                draftPlaylist = iptv.playlistUrl(); draftGuide = iptv.guideUrl(); renderSection();
            }, v -> {
                String enteredPlaylist = playlistField == null
                        ? draftPlaylist : playlistField.getText().toString().trim();
                if (enteredPlaylist.isEmpty() || !enteredPlaylist.equals(iptv.playlistUrl())) {
                    if (liveFeedback != null) liveFeedback.setText(AppLanguage.get(R.string.ui_save_the_playlist_url_before_refreshing_channels));
                    return;
                }
                refreshChannels();
            });
            liveFeedback = label(liveStatus == null ? AppLanguage.get(R.string.ui_add_an_m3u_and_optional_xmltv_url_in_settings_to_get_started) : liveStatus, 14, SettingsUi.muted(MainActivity.this), false);
            liveFeedback.setPadding(dp(2), dp(4), dp(2), dp(2));
            page.addView(liveFeedback);
            addTaskProgress(page);
        }

        private void renderDrm(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_widevine_drm));
            licenseField = addInput(form, AppLanguage.get(R.string.ui_widevine_license_url), AppLanguage.get(R.string.ui_optional_default_license_endpoint), draftLicense, false);
            headersField = addInput(form, AppLanguage.get(R.string.ui_license_request_headers), AppLanguage.get(R.string.ui_one_per_line_name_value), draftHeaders, true);
            addFormHint(form, AppLanguage.get(R.string.ui_channel_specific_kodiprop_settings_in_the_playlist_take_priority));
            addSaveActions(page, v -> {
                captureDrafts();
                iptv.saveDrmSettings(draftLicense, draftHeaders);
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText();
                renderSection();
                Toast.makeText(MainActivity.this, AppLanguage.get(R.string.ui_playback_settings_saved), Toast.LENGTH_SHORT).show();
            }, v -> {
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText(); renderSection();
            }, null);
        }

        private void renderClock(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_clock_display));
            addFormHint(form, AppLanguage.get(R.string.ui_changes_appear_on_the_home_clock_immediately));
            addToggle(form, AppLanguage.get(R.string.ui_24_hour_time), preferences.getBoolean("24h", false), enabled -> {
                preferences.edit().putBoolean("24h", enabled).apply(); updateClock();
            });
            addToggle(form, AppLanguage.get(R.string.ui_show_date), preferences.getBoolean("showDate", true), enabled -> {
                preferences.edit().putBoolean("showDate", enabled).apply(); updateClock();
            });
        }

        private void renderAppearance(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            // — Theme ——————————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_theme));
            addFormHint(form, AppLanguage.get(R.string.ui_follow_your_tv_s_theme_or_choose_a_light_or_dark_appearance));
            String mode = preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.DEFAULT_MODE);
            String[] modes = {LiquidTheme.SYSTEM, LiquidTheme.LIGHT, LiquidTheme.DARK};
            addChoice(form, AppLanguage.get(R.string.ui_appearance), new String[]{AppLanguage.get(R.string.ui_system_default), AppLanguage.get(R.string.ui_light), AppLanguage.get(R.string.ui_dark)},
                    LiquidTheme.LIGHT.equals(mode) ? 1 : LiquidTheme.DARK.equals(mode) ? 2 : 0,
                    index -> changeLiquidGlassMode(modes[index]));

            // — Now Playing ————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_now_playing));
            addFormHint(form, AppLanguage.get(R.string.ui_appears_on_home_only_while_media_is_playing_allow_notification_a));
            addFormButton(form, AppLanguage.get(R.string.ui_manage_now_playing_access), v -> openMusicAccessSettings());
            // — Screensaver ————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_screensaver));
            addFormButton(form, AppLanguage.get(R.string.ui_sva_memories_screensaver_settings), v ->
                    startActivity(new Intent(MainActivity.this, ScreensaverSettingsActivity.class)));
            // — Slideshow ——————————————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_slideshow));
            addFormHint(form, AppLanguage.get(R.string.ui_a_photo_folder_or_google_photos_album_plays_as_a_slideshow_it_ta));
            boolean hasFolder = !preferences.getString("wallpaperFolder", "").isEmpty();
            addFormHint(form, hasFolder
                    ? AppLanguage.get(R.string.ui_photo_folder_1_s_images, backgroundPhotos.size())
                    : AppLanguage.get(R.string.ui_no_photo_folder_selected));
            addFormButton(form, AppLanguage.get(R.string.ui_choose_photo_folder), v -> openWallpaperFolderPicker());
            if (hasFolder) addFormButton(form, AppLanguage.get(R.string.ui_clear_photo_folder), true, v -> {
                backgroundFolderGeneration++;
                albumLoading = false; loadingAlbumUrl = ""; updateTaskProgress();
                preferences.edit().remove("wallpaperFolder")
                        .remove(PHOTO_FOLDER_CACHE_URI)
                        .remove(PHOTO_FOLDER_CACHE_IMAGES).apply();
                String album = preferences.getString("wallpaperAlbum", "");
                if (!album.isEmpty()) loadBackgroundAlbum(album, false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            String albumUrl = preferences.getString("wallpaperAlbum", "");
            addFormHint(form, albumUrl.isEmpty()
                    ? AppLanguage.get(R.string.ui_no_google_photos_album_selected)
                    : AppLanguage.get(R.string.ui_google_photos_album_1_s_images, backgroundPhotos.size()));
            addTaskProgress(form);
            EditText albumField = addInput(form, AppLanguage.get(R.string.ui_google_photos_public_album_link), AppLanguage.get(R.string.ui_paste_a_link_shared_with_anyone), albumUrl, false);
            addFormButton(form, AppLanguage.get(R.string.ui_use_google_photos_album), v -> {
                String link = albumField.getText().toString().trim();
                if (link.isEmpty()) {
                    Toast.makeText(MainActivity.this, AppLanguage.get(R.string.ui_paste_a_google_photos_shared_album_link_first), Toast.LENGTH_SHORT).show();
                    return;
                }
                loadBackgroundAlbum(link, true);
            });
            if (!albumUrl.isEmpty()) addFormButton(form, AppLanguage.get(R.string.ui_clear_google_photos_album), true, v -> {
                backgroundFolderGeneration++;
                albumLoading = false; loadingAlbumUrl = ""; updateTaskProgress();
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
            // — Single wallpaper ———————————————————————————————————————————————
            addFormSectionHeader(form, AppLanguage.get(R.string.ui_single_wallpaper));
            boolean hasWallpaper = preferences.contains("wallpaper");
            addFormHint(form, hasWallpaper ? AppLanguage.get(R.string.ui_single_wallpaper_is_set) : AppLanguage.get(R.string.ui_no_single_wallpaper_using_the_default_landscape));
            addFormButton(form, AppLanguage.get(R.string.ui_choose_single_wallpaper), v ->
                    startActivityForResult(TvFilePickerActivity.intent(MainActivity.this, TvFilePickerActivity.MODE_IMAGE), PICK_WALLPAPER));
            if (hasWallpaper) addFormButton(form, AppLanguage.get(R.string.ui_clear_single_wallpaper), true, v -> {
                preferences.edit().remove("wallpaper").apply();
                applyWallpaper();
                renderSection();
            });
        }

        private void changeLiquidGlassMode(String mode) {
            if (mode.equals(preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.DEFAULT_MODE))) return;
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
            if (settingsScreen == this && section == CHANNELS && channelAdapter != null) channelAdapter.refresh();
        }

        private String cacheSummaryText() {
            long updatedAt = iptv.lastUpdatedAt();
            String updated = updatedAt == 0 ? AppLanguage.get(R.string.ui_not_refreshed_yet)
                    : AppLanguage.get(R.string.ui_updated_1_s, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, AppLanguage.locale())
                    .format(new Date(updatedAt)));
            return AppLanguage.get(R.string.ui_on_device_channel_cache_1_s_2_s_3_s, "", AppLanguage.quantity(R.plurals.channel_count, channels.size()), updated);
        }

        private String guideSummaryText() {
            long updatedAt = iptv.lastGuideUpdatedAt();
            String updated = updatedAt == 0 ? AppLanguage.get(R.string.ui_not_refreshed_yet)
                    : AppLanguage.get(R.string.ui_last_refreshed_1_s, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, AppLanguage.locale())
                    .format(new Date(updatedAt)));
            return AppLanguage.get(R.string.ui_programme_guide_daily_refresh_1_s, updated);
        }

        private void renderChannelSettings(LinearLayout page) {
            addTaskProgress(page);
            EditText search = addInput(page, AppLanguage.get(R.string.ui_find_a_channel), AppLanguage.get(R.string.ui_search_by_channel_name), "", false);
            search.setInputType(InputType.TYPE_CLASS_TEXT);
            TextView count = label("", 14, SettingsUi.muted(MainActivity.this), false);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
            countParams.bottomMargin = dp(8);
            page.addView(count, countParams);
            RecyclerView list = new RecyclerView(MainActivity.this);
            list.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            list.setClipChildren(true);
            list.setClipToPadding(false);
            list.setPadding(dp(4), dp(4), dp(4), dp(4));
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
            addFormHint(page, AppLanguage.get(R.string.ui_choose_up_to_five_apps_for_home_use_arrows_to_change_their_order));
            appList = new RecyclerView(MainActivity.this);
            appList.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            appList.setClipChildren(true);
            appList.setClipToPadding(false);
            appList.setPadding(dp(4), dp(4), dp(4), dp(4));
            appList.setItemViewCacheSize(6);
            appList.setOverScrollMode(View.OVER_SCROLL_NEVER);
            appAdapter = new AppSettingsAdapter(appList);
            appList.setAdapter(appAdapter);
            page.addView(appList, new LinearLayout.LayoutParams(-1, 0, 1));
            if (availableApps != null) appAdapter.setApps(availableApps);
            else if (!loadingApps) {
                loadingApps = true;
                lifecycleWork.execute(ioWorker, () -> {
                    List<AppEntry> loaded = installedApps();
                    lifecycleWork.post(() -> {
                        if (settingsScreen != this) return;
                        availableApps = loaded;
                        loadingApps = false;
                        if (section == APPS && appAdapter != null) appAdapter.setApps(loaded);
                    });
                });
            }
            firstControl = appList;
        }

        private final class ChannelSettingsAdapter extends RecyclerView.Adapter<ChannelSettingsAdapter.Holder> {
            private final List<IptvRepository.Channel> visible = new ArrayList<>();
            private final TextView count;
            private String query = "";

            ChannelSettingsAdapter(TextView count) { this.count = count; }

            private volatile int searchGeneration;
            private Runnable pendingSearch;

            void cancelSearch() {
                searchGeneration++;
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
            }
            void setQuery(String value) {
                query = value == null ? "" : value.trim().toLowerCase(AppLanguage.locale());
                cancelSearch();
                pendingSearch = this::refresh;
                if (query.isEmpty()) refresh();
                else handler.postDelayed(pendingSearch, 150);
            }
            void refresh() {
                cancelSearch();
                int generation = searchGeneration;
                String requestedQuery = query;
                Locale locale = AppLanguage.locale();
                List<IptvRepository.Channel> snapshot = new ArrayList<>(channels);
                lifecycleWork.execute(ioWorker, () -> {
                    if (generation != searchGeneration) return;
                    List<IptvRepository.Channel> matched = new ArrayList<>();
                    for (IptvRepository.Channel channel : snapshot) {
                        if (generation != searchGeneration) return;
                        if (requestedQuery.isEmpty() || channel.name.toLowerCase(locale).contains(requestedQuery)) matched.add(channel);
                    }
                    lifecycleWork.post(() -> {
                        if (generation != searchGeneration || settingsScreen != SettingsScreen.this
                                || section != CHANNELS || channelAdapter != this) return;
                        visible.clear(); visible.addAll(matched);
                        updateCountLabel(); notifyDataSetChanged();
                    });
                });
            }

            private int shownChannelCount() {
                int shown = 0;
                for (IptvRepository.Channel channel : channels) {
                    if (!HiddenChannels.isHidden(hiddenChannelKeys, channel)) shown++;
                }
                return shown;
            }

            private int favoriteChannelCount() {
                int count = 0;
                for (IptvRepository.Channel channel : channels) {
                    if (favoriteChannelKeys.contains(channel.stableKey())) count++;
                }
                return count;
            }

            private void updateCountLabel() {
                count.setText(AppLanguage.get(R.string.ui_1_s_of_2_s_channels_3_s_shown_4_s_favourites_press_ok_to_toggle, visible.size(), channels.size(), shownChannelCount(), favoriteChannelCount()));
            }

            @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(4), dp(18), dp(4));
                row.setFocusable(true); row.setClickable(true);
                row.setBackground(SettingsUi.states(MainActivity.this, false));
                Motion.interaction(row, true);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(dp(6), dp(1), dp(6), dp(7));
                row.setLayoutParams(params);
                LinearLayout text = new LinearLayout(MainActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                TextView name = label("", 16, SettingsUi.text(MainActivity.this), true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.addView(name);
                TextView group = label("", 14, SettingsUi.muted(MainActivity.this), false);
                group.setSingleLine(true);
                text.addView(group);
                TextView state = label("", 14, SettingsUi.text(MainActivity.this), true);
                row.addView(state);
                return new Holder(row, name, group, state);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                IptvRepository.Channel channel = visible.get(position);
                boolean isFav = favoriteChannelKeys.contains(channel.stableKey());
                holder.name.setText((isFav ? "★  " : "") + channel.name);
                holder.group.setText(channel.group.isEmpty() ? AppLanguage.get(R.string.ui_uncategorized) : channel.group);
                boolean shown = !HiddenChannels.isHidden(hiddenChannelKeys, channel);
                holder.state.setText((isFav ? "★ " : "") + (shown ? AppLanguage.get(R.string.ui_shown) : AppLanguage.get(R.string.ui_hidden)));
                holder.state.setTextColor(shown ? SettingsUi.text(MainActivity.this)
                        : SettingsUi.muted(MainActivity.this));
                holder.itemView.setOnClickListener(v -> {
                    HiddenChannels.toggle(hiddenChannelKeys, channel);
                    HiddenChannels.save(preferences, hiddenChannelKeys);
                    channelsChanged = true;
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
                    updateCountLabel();
                });
                holder.itemView.setOnLongClickListener(v -> {
                    boolean added = FavoriteChannels.toggle(favoriteChannelKeys, channel);
                    FavoriteChannels.save(preferences, favoriteChannelKeys);
                    channelsChanged = true;
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
                    updateCountLabel();
                    Toast.makeText(MainActivity.this, AppLanguage.get(added ? R.string.ui_favourite_added : R.string.ui_favourite_removed, AppLanguage.bidi(channel.name)), Toast.LENGTH_SHORT).show();
                    return true;
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
                        for (AppEntry entry : apps) selectedOrder.add(entry.packageName);
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
                row.setTag("settings-app-actions");
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(dp(6), dp(1), dp(6), dp(7));
                row.setLayoutParams(params);
                Button toggle = settingsButton("");
                toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                toggle.setPadding(dp(18), 0, dp(12), 0);
                LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(0, dp(60), 1);
                toggleParams.setMargins(dp(2), dp(2), dp(4), dp(2));
                row.addView(toggle, toggleParams);
                Button up = settingsButton("↑");
                LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                upParams.setMargins(dp(4), dp(2), 0, dp(2));
                row.addView(up, upParams);
                Button down = settingsButton("↓");
                LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                downParams.setMargins(dp(6), dp(2), dp(4), dp(2));
                row.addView(down, downParams);
                return new Holder(row, toggle, up, down);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                AppEntry entry = rows.get(position);
                int selected = selectedOrder.indexOf(entry.packageName);
                holder.toggle.setText((selected >= 0 ? "✓  " : "+  ") + entry.label);
                holder.toggle.setTextColor(SettingsUi.text(MainActivity.this));
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
        if (requestCode == 7410) {
            Set<String> freshHidden = HiddenChannels.load(preferences);
            Set<String> freshFavorites = FavoriteChannels.load(preferences);
            if (!freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
                hiddenChannelKeys.clear();
                hiddenChannelKeys.addAll(freshHidden);
                favoriteChannelKeys.clear();
                favoriteChannelKeys.addAll(freshFavorites);
                renderChannels();
            }
            if (guideToggle != null) guideToggle.post(() -> guideToggle.requestFocus());
            return;
        }
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
            try { getContentResolver().takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
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
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap decoded = uriValue == null ? null : decodeWallpaper(Uri.parse(uriValue), targetWidth, targetHeight);
            if (decoded == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                decoded = BitmapFactory.decodeResource(getResources(), R.drawable.mountain_wallpaper, options);
            }
            final android.graphics.Bitmap bitmap = decoded;
            lifecycleWork.post(() -> {
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
                    updateWidgetThemesForBitmap(bitmap, uriValue == null ? "default-wallpaper" : uriValue);
                });
            });
        });
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

    private final class NavigationGlyph extends Drawable {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path strokePath = new Path();
        private final Path fillPath = new Path();
        private int glyphColor = 0xFFEAF4F5;

        NavigationGlyph(int kind) {
            this.kind = kind;
            paint.setStrokeWidth(2.1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            initPaths();
        }

        private void initPaths() {
            switch (kind) {
                case TAB_HOME:
                    strokePath.moveTo(2, 11); strokePath.lineTo(12, 3); strokePath.lineTo(22, 11);
                    strokePath.moveTo(5, 10); strokePath.lineTo(5, 21); strokePath.lineTo(10, 21); strokePath.lineTo(10, 15);
                    strokePath.lineTo(14, 15); strokePath.lineTo(14, 21); strokePath.lineTo(19, 21); strokePath.lineTo(19, 10);
                    break;
                case TAB_TV:
                    strokePath.addRoundRect(new RectF(3, 8, 21, 19), 2, 2, Path.Direction.CW);
                    strokePath.moveTo(8, 4); strokePath.lineTo(12, 8); strokePath.lineTo(16, 4);
                    strokePath.moveTo(9, 22); strokePath.lineTo(15, 22); strokePath.moveTo(12, 19); strokePath.lineTo(12, 22);
                    break;
                case TAB_GAMES:
                    strokePath.moveTo(7, 8); strokePath.lineTo(17, 8);
                    strokePath.cubicTo(20, 8, 21, 10, 22, 16); strokePath.cubicTo(23, 20, 20, 21, 17, 18);
                    strokePath.lineTo(15, 16); strokePath.lineTo(9, 16); strokePath.lineTo(7, 18);
                    strokePath.cubicTo(4, 21, 1, 20, 2, 16); strokePath.cubicTo(3, 10, 4, 8, 7, 8);
                    strokePath.moveTo(6, 11); strokePath.lineTo(6, 15); strokePath.moveTo(4, 13); strokePath.lineTo(8, 13);
                    fillPath.addCircle(17, 12, 1, Path.Direction.CW);
                    fillPath.addCircle(19, 14, 1, Path.Direction.CW);
                    break;
                case TAB_ACTIVITIES:
                    for (int x = 3; x <= 13; x += 10) for (int y = 3; y <= 13; y += 10)
                        strokePath.addRoundRect(new RectF(x, y, x + 8, y + 8), 1, 1, Path.Direction.CW);
                    break;
                case TAB_NEWS:
                    strokePath.moveTo(4, 3); strokePath.lineTo(20, 3); strokePath.lineTo(20, 21); strokePath.lineTo(12, 17);
                    strokePath.lineTo(4, 21); strokePath.close();
                    strokePath.moveTo(8, 8); strokePath.lineTo(16, 8);
                    strokePath.moveTo(8, 12); strokePath.lineTo(16, 12);
                    break;
                case TAB_EVENTS:
                    strokePath.addRoundRect(new RectF(3, 5, 21, 21), 2, 2, Path.Direction.CW);
                    strokePath.moveTo(7, 3); strokePath.lineTo(7, 8); strokePath.moveTo(17, 3); strokePath.lineTo(17, 8);
                    strokePath.moveTo(3, 10); strokePath.lineTo(21, 10);
                    fillPath.addCircle(8, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(12, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(16, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(8, 18, 1, Path.Direction.CW);
                    break;
                default:
                    strokePath.moveTo(6, 3); strokePath.lineTo(18, 3); strokePath.lineTo(18, 21); strokePath.lineTo(12, 17);
                    strokePath.lineTo(6, 21); strokePath.close();
                    break;
            }
        }

        void setGlyphColor(int color) {
            if (glyphColor == color) return;
            glyphColor = color;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            paint.setColor(glyphColor);
            if (!strokePath.isEmpty()) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(strokePath, paint);
            }
            if (!fillPath.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(fillPath, paint);
            }
            canvas.restoreToCount(save);
        }

        int getGlyphColor() { return glyphColor; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
