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
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int PICK_WALLPAPER = 4107;
    private static final int PICK_WALLPAPER_FOLDER = 4108;
    private static final long SLIDESHOW_INTERVAL_MS = 5000L;
    // Translucent surfaces keep the backdrop visible while preserving contrast.
    private static final int PANEL = 0xC91B2731;
    private static final int PANEL_FOCUSED = 0xE03B5660;
    private static final int MUTED = Color.rgb(164, 179, 198);
    private static final int ACCENT = Color.rgb(93, 230, 207);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<IptvRepository.Channel> channels = new ArrayList<>();
    private final Set<String> hiddenChannelKeys = new HashSet<>();
    private IptvRepository iptv;
    private ChannelLogoLoader logoLoader;
    private SharedPreferences preferences;
    private LinearLayout root, homeContent, appRow;
    private FrameLayout heroFrame;
    private ImageView wallpaperImage, slideA, slideB;
    private TextView clock, date, status, channelHeader, greeting, greetingSubtitle;
    private LinearLayout channelRows;
    private TextView channelsEmpty;
    private final LinkedHashMap<String, ChannelRow> channelRowMap = new LinkedHashMap<>();
    private List<String> renderedCategories = new ArrayList<>();
    private Runnable clockTick;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        iptv = new IptvRepository(this);
        logoLoader = new ChannelLogoLoader(this);
        hiddenChannelKeys.addAll(preferences.getStringSet("hiddenChannels", Collections.emptySet()));
        buildHome();
        channels.addAll(iptv.cachedChannels());
        renderChannels();
        String folder = preferences.getString("wallpaperFolder", "");
        String album = preferences.getString("wallpaperAlbum", "");
        if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
        else if (!album.isEmpty()) loadBackgroundAlbum(album, false);
        // Always refresh configured playlist and guide on launcher startup. Cached
        // channels remain rendered above until the network result arrives.
        if (!iptv.playlistUrl().isEmpty()) refreshIptv(false);
        updateClock();
        clockTick = new Runnable() { @Override public void run() { updateClock(); handler.postDelayed(this, 30000); } };
        handler.postDelayed(clockTick, 30000);
        if (folder.isEmpty() && album.isEmpty()) {
            handler.post(() -> openWallpaperFolderPicker());
        }
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

    @Override protected void onResume() {
        super.onResume();
        screenActive = true;
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
        backgroundImageGeneration++;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (clockTick != null) handler.removeCallbacks(clockTick);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        super.onDestroy();
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        page.setBackgroundColor(Color.BLACK);
        page.addView(root);
        setContentView(page);

        heroFrame = new FrameLayout(this);
        heroFrame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF202D37, 0xFF12191F, Color.BLACK}));
        wallpaperImage = backgroundImage();
        slideA = backgroundImage();
        slideB = backgroundImage();
        heroFrame.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideA, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideB, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable shadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x28000000, 0xE8000000, Color.BLACK});
        View shade = new View(this);
        shade.setBackground(shadow);
        heroFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View sideShade = new View(this);
        sideShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x99000000, 0x22000000, 0x00000000}));
        heroFrame.addView(sideShade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(heroFrame, new LinearLayout.LayoutParams(-1, dp(heroHeightDp())));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(40), dp(24), dp(40), dp(12));
        heroFrame.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout timeStack = new LinearLayout(this);
        timeStack.setOrientation(LinearLayout.VERTICAL);
        timeStack.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
        top.addView(timeStack, timeParams);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(28);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setGravity(Gravity.LEFT);
        timeStack.addView(clock);
        date = new TextView(this);
        date.setTextColor(0xFFD4DDE0);
        date.setTextSize(12);
        date.setGravity(Gravity.LEFT);
        timeStack.addView(date);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout systemShortcuts = new LinearLayout(this);
        systemShortcuts.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(-2, dp(48));
        shortcutParams.setMargins(dp(8), 0, 0, 0);
        top.addView(systemShortcuts, shortcutParams);
        addTopIcon(systemShortcuts, "Network", R.drawable.ic_network, this::openNetworkSettings);
        addTopIcon(systemShortcuts, "Android Settings", android.R.drawable.ic_menu_manage, this::openAndroidSettings);
        addTopIcon(systemShortcuts, "Launcher Settings", R.drawable.ic_launcher_settings, this::showSettings);

        LinearLayout heroBottom = new LinearLayout(this);
        heroBottom.setOrientation(LinearLayout.VERTICAL);
        heroBottom.setPadding(dp(40), 0, dp(40), dp(14));
        greeting = label("", 34, Color.WHITE, true);
        heroBottom.addView(greeting);
        greetingSubtitle = label("", 16, 0xFFE2EAEC, false);
        heroBottom.addView(greetingSubtitle);

        heroFrame.addView(heroBottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(40), dp(14), dp(40), dp(24));
        homeContent.setClipChildren(false);
        homeContent.setClipToPadding(false);
        root.addView(homeContent, new LinearLayout.LayoutParams(-1, -2));

        TextView appsTitle = label("YOUR APPS", 13, ACCENT, true);
        appsTitle.setLetterSpacing(.12f);
        LinearLayout.LayoutParams appsTitleParams = new LinearLayout.LayoutParams(-1, dp(30));
        appsTitleParams.setMargins(0, 0, 0, 0);
        homeContent.addView(appsTitle, appsTitleParams);
        HorizontalScrollView appScroll = new HorizontalScrollView(this);
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setClipChildren(false);
        appScroll.setClipToPadding(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        appScroll.addView(appRow);
        homeContent.addView(appScroll, new LinearLayout.LayoutParams(-1, dp(128)));
        applyWallpaper();

        LinearLayout liveHeading = new LinearLayout(this);
        liveHeading.setGravity(Gravity.CENTER_VERTICAL);
        homeContent.addView(liveHeading, new LinearLayout.LayoutParams(-1, dp(56)));
        channelHeader = label("LIVE TV", 18, Color.WHITE, true);
        liveHeading.addView(channelHeader, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("Refresh guide");
        refresh.setOnClickListener(v -> refreshIptv(true));
        liveHeading.addView(refresh, new LinearLayout.LayoutParams(dp(170), dp(48)));

        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        channelRows.setClipChildren(false);
        channelRows.setClipToPadding(false);
        homeContent.addView(channelRows, new LinearLayout.LayoutParams(-1, -2));
        channelsEmpty = label("Add a playlist in Settings to browse live TV.", 15, MUTED, false);
        channelsEmpty.setPadding(0, dp(16), 0, dp(20));
        status = label("Add an M3U and optional XMLTV URL in Settings to get started.", 15, MUTED, false);
        status.setPadding(0, dp(12), 0, 0);
        homeContent.addView(status);
    }

    private int heroHeightDp() {
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return Math.round(screenHeightDp * .70f);
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
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        styleFocusableTile(b);
        return b;
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
        appRow.removeAllViews();
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
            styleFocusableTile(tile);
            tile.setPadding(dp(12), dp(12), dp(12), dp(10));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
            TextView name = label(entry.label, 14, Color.WHITE, true);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            tile.addView(name, new LinearLayout.LayoutParams(dp(128), dp(32)));
            tile.setOnClickListener(v -> launchApp(entry.packageName));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(154), dp(122));
            lp.setMargins(0, 0, dp(16), 0);
            appRow.addView(tile, lp);
        }
        if (selected.isEmpty()) {
            TextView empty = label("No apps selected", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            appRow.addView(empty, new LinearLayout.LayoutParams(dp(240), dp(72)));
        }
    }

    private void addTopIcon(LinearLayout container, String title, int iconResource, Runnable action) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        styleFocusableTile(icon);
        icon.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(6), 0, 0, 0);
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

    private GradientDrawable tileShape(int color, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor);
        return d;
    }

    private void styleFocusableTile(View tile) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, tileShape(PANEL_FOCUSED, ACCENT, 3));
        states.addState(new int[]{android.R.attr.state_pressed}, tileShape(PANEL_FOCUSED, ACCENT, 2));
        states.addState(new int[]{}, tileShape(PANEL, 0x668DA2AC, 1));
        tile.setBackground(states);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        tile.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                .translationZ(focused ? dp(4) : 0).setDuration(110).start());
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
        LinkedHashMap<String, List<IptvRepository.Channel>> grouped = new LinkedHashMap<>();
        for (IptvRepository.Channel channel : channels) {
            if (hiddenChannelKeys.contains(channel.stableKey())) continue;
            String category = channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim();
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(channel);
        }
        int visibleCount = 0;
        for (List<IptvRepository.Channel> list : grouped.values()) visibleCount += list.size();
        if (channelHeader != null) channelHeader.setText(channels.isEmpty() ? "LIVE TV" : "LIVE TV  ·  " + visibleCount + " CHANNELS");
        if (grouped.isEmpty()) {
            channelsEmpty.setText(channels.isEmpty()
                    ? "Add a playlist in Settings to browse live TV."
                    : "All channels are hidden. Restore channels in Settings.");
            if (!renderedCategories.isEmpty() || channelRows.getChildCount() != 1 || channelRows.getChildAt(0) != channelsEmpty) {
                channelRows.removeAllViews();
                channelRowMap.clear();
                channelRows.addView(channelsEmpty);
                renderedCategories.clear();
            }
            return;
        }

        List<String> nextCategories = new ArrayList<>(grouped.keySet());
        if (!nextCategories.equals(renderedCategories)) {
            channelRows.removeAllViews();
            channelRowMap.clear();
            renderedCategories = nextCategories;
            for (String category : renderedCategories) {
                ChannelRow row = createChannelRow(category);
                channelRowMap.put(category, row);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(0, dp(10), 0, dp(14));
                channelRows.addView(row.container, params);
            }
        }
        for (Map.Entry<String, List<IptvRepository.Channel>> category : grouped.entrySet()) {
            ChannelRow row = channelRowMap.get(category.getKey());
            if (row != null) {
                row.count.setText(category.getValue().size() + (category.getValue().size() == 1 ? " channel" : " channels"));
                row.adapter.setChannels(category.getValue());
            }
        }
    }

    private ChannelRow createChannelRow(String category) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, dp(6), 0, dp(10));
        container.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View accent = new View(this);
        accent.setBackgroundColor(ACCENT);
        heading.addView(accent, new LinearLayout.LayoutParams(dp(3), dp(26)));
        TextView title = label(category, 19, Color.WHITE, true);
        title.setMinHeight(dp(32));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, dp(10), 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = label("", 13, MUTED, false);
        count.setPadding(dp(12), dp(6), dp(12), dp(6));
        count.setBackground(tileShape(PANEL, 0x668DA2AC, 1));
        heading.addView(count, new LinearLayout.LayoutParams(-2, -2));
        RecyclerView list = new RecyclerView(this);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(4), dp(4), dp(4), dp(4));
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(4);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ChannelAdapter adapter = new ChannelAdapter(this, logoLoader, this::playChannel);
        list.setAdapter(adapter);
        container.addView(list, new LinearLayout.LayoutParams(-1, dp(126)));
        return new ChannelRow(container, adapter, count);
    }

    private void loadBackgroundFolder(Uri treeUri, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        Thread loader = new Thread(() -> {
            List<BackgroundPhoto> photos = enumeratePhotos(treeUri);
            handler.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                setBackgroundPhotos(photos);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected && photos.isEmpty()) {
                    Toast.makeText(this, "No images were found in that folder.", Toast.LENGTH_LONG).show();
                } else if (userSelected) {
                    Toast.makeText(this, "Background slideshow updated · " + photos.size() + " photos", Toast.LENGTH_SHORT).show();
                }
            });
        }, "background-folder-scan");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadBackgroundAlbum(String shareUrl, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
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
                preferences.edit().putString("wallpaperAlbum", shareUrl).remove("wallpaperFolder").apply();
                setBackgroundPhotos(result);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected) Toast.makeText(this, "Google Photos slideshow updated · " + result.size() + " photos", Toast.LENGTH_SHORT).show();
            });
        }, "google-photos-album");
        loader.setDaemon(true);
        loader.start();
    }

    private List<BackgroundPhoto> enumeratePhotos(Uri treeUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            collectPhotos(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", visited, photos, 0);
            Collections.sort(photos, (left, right) -> left.name.compareToIgnoreCase(right.name));
        } catch (Exception ignored) { }
        return photos;
    }

    private void collectPhotos(Uri treeUri, String documentId, String parentPath, Set<String> visited,
                               List<BackgroundPhoto> photos, int depth) {
        if (depth > 16 || !visited.add(documentId)) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return;
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
        } catch (Exception ignored) { }
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
        handler.postDelayed(slideshowTick, SLIDESHOW_INTERVAL_MS);
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
                incoming.setImageBitmap(bitmap);
                incoming.setVisibility(View.VISIBLE);
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(900).start();
                outgoing.animate().alpha(0f).setDuration(900).withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setImageDrawable(null);
                }).start();
                slideAActive = !slideAActive;
                scheduleNextBackgroundPhoto();
            });
        }, "background-photo-decode").start();
    }

    private static final class BackgroundPhoto {
        final Uri uri;
        final String name;
        BackgroundPhoto(Uri uri, String name) { this.uri = uri; this.name = name; }
    }

    private static final class ChannelRow {
        final LinearLayout container;
        final ChannelAdapter adapter;
        final TextView count;
        ChannelRow(LinearLayout container, ChannelAdapter adapter, TextView count) {
            this.container = container;
            this.adapter = adapter;
            this.count = count;
        }
    }

    private void playChannel(IptvRepository.Channel channel) {
        Intent play = new Intent(this, PlaybackActivity.class);
        play.putExtra("name", channel.name);
        play.putExtra("now", channel.now);
        play.putExtra("nowStartMs", channel.nowStartMs);
        play.putExtra("nowEndMs", channel.nowEndMs);
        play.putExtra("next", channel.next);
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
                if (item.stableKey().equals(channel.stableKey())) selectedIndex = zapList.length();
                zapList.put(entry);
            } catch (Exception ignored) { }
        }
        play.putExtra("zapChannels", zapList.toString());
        play.putExtra("zapIndex", selectedIndex);
        startActivity(play);
    }

    private void refreshIptv(boolean userRequested) {
        status.setText("Refreshing playlist and guide…");
        if (settingsScreen != null) settingsScreen.onRefreshResult("Refreshing playlist and guide…");
        iptv.refresh((loaded, message) -> handler.post(() -> {
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            status.setText(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
        }));
    }

    private SettingsScreen settingsScreen;

    private void showSettings() {
        if (settingsScreen != null) return;
        settingsScreen = new SettingsScreen();
        settingsScreen.show();
    }

    private final class SettingsScreen {
        private static final int LIVE = 0, CHANNELS = 1, APPS = 2, APPEARANCE = 3, CLOCK = 4, DRM = 5, HOME = 6;
        private final String[] sections = {"Live TV", "Channels", "Apps", "Appearance", "Clock", "Playback & DRM", "Home button"};
        private final String[] descriptions = {
                "Playlist and programme guide", "Choose which channels appear on Home",
                "Choose and order your home shortcuts", "Photo backdrop and wallpaper",
                "Time and date on Home", "Default Widevine license settings",
                "Open Duo when the TV Home button opens its built-in launcher"
        };
        private final Dialog dialog = new Dialog(MainActivity.this, android.R.style.Theme_Material_NoActionBar);
        private final Button[] tabs = new Button[sections.length];
        private final FrameLayout detail = new FrameLayout(MainActivity.this);
        private int section = LIVE;
        private View firstControl;
        private EditText playlistField, guideField, licenseField, headersField;
        private TextView liveFeedback;
        private ChannelSettingsAdapter channelAdapter;
        private AppSettingsAdapter appAdapter;
        private RecyclerView appList;
        private List<AppEntry> availableApps;
        private boolean loadingApps, appsChanged, channelsChanged;
        private String draftPlaylist = iptv.playlistUrl();
        private String draftGuide = iptv.guideUrl();
        private String draftLicense = iptv.drmLicenseUrl();
        private String draftHeaders = iptv.drmHeaderText();

        void show() {
            LinearLayout shell = new LinearLayout(MainActivity.this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(32), dp(20), dp(32), dp(24));
            shell.setBackgroundColor(0xFF090D12);

            LinearLayout heading = new LinearLayout(MainActivity.this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            shell.addView(heading, new LinearLayout.LayoutParams(-1, dp(72)));
            TextView title = label("DUO  /  SETTINGS", 20, Color.WHITE, true);
            title.setLetterSpacing(.1f);
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView hint = label("Use your remote to navigate", 14, MUTED, false);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-2, -2);
            hintParams.setMargins(0, 0, dp(20), 0);
            heading.addView(hint, hintParams);
            Button close = actionButton("Close");
            close.setOnClickListener(v -> requestClose());
            heading.addView(close, new LinearLayout.LayoutParams(dp(112), dp(48)));

            LinearLayout body = new LinearLayout(MainActivity.this);
            shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout rail = new LinearLayout(MainActivity.this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setPadding(0, dp(12), dp(16), 0);
            body.addView(rail, new LinearLayout.LayoutParams(dp(232), -1));
            for (int i = 0; i < sections.length; i++) {
                final int target = i;
                Button tab = actionButton(sections[i]);
                tab.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                tab.setPadding(dp(18), 0, dp(12), 0);
                tab.setId(View.generateViewId());
                tab.setOnClickListener(v -> {
                    captureDrafts();
                    section = target;
                    renderSection();
                    tab.requestFocus();
                });
                tabs[i] = tab;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
                params.setMargins(0, 0, 0, dp(8));
                rail.addView(tab, params);
            }
            detail.setBackground(tileShape(0xD9162028, 0x668DA2AC, 1));
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
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            tabs[LIVE].requestFocus();
        }

        private void captureDrafts() {
            if (playlistField != null) draftPlaylist = playlistField.getText().toString();
            if (guideField != null) draftGuide = guideField.getText().toString();
            if (licenseField != null) draftLicense = licenseField.getText().toString();
            if (headersField != null) draftHeaders = headersField.getText().toString();
            playlistField = guideField = licenseField = headersField = null;
            liveFeedback = null;
        }

        private boolean hasUnsavedEdits() {
            captureDrafts();
            return !draftPlaylist.trim().equals(iptv.playlistUrl())
                    || !draftGuide.trim().equals(iptv.guideUrl())
                    || !draftLicense.trim().equals(iptv.drmLicenseUrl())
                    || !draftHeaders.trim().equals(iptv.drmHeaderText());
        }

        private void requestClose() {
            if (!hasUnsavedEdits()) { dialog.dismiss(); return; }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Discard unsaved edits?")
                    .setMessage("Playlist or DRM changes have not been saved.")
                    .setPositiveButton("Discard", (ignored, which) -> dialog.dismiss())
                    .setNegativeButton("Keep editing", null).show();
        }

        private void renderSection() {
            firstControl = null;
            detail.removeAllViews();
            for (int i = 0; i < tabs.length; i++) tabs[i].setTextColor(i == section ? ACCENT : Color.WHITE);
            LinearLayout page = new LinearLayout(MainActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(28), dp(20), dp(28), dp(24));
            detail.addView(page, new FrameLayout.LayoutParams(-1, -1));
            TextView title = label(sections[section], 27, Color.WHITE, true);
            page.addView(title);
            TextView subtitle = label(descriptions[section], 14, MUTED, false);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, dp(42));
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

        void refreshLauncherOverride() {
            if (settingsScreen == this && section == HOME) renderSection();
        }

        private LinearLayout scrollForm(LinearLayout page) {
            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(false);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout form = new LinearLayout(MainActivity.this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(2), dp(8), dp(12), dp(8));
            scroll.addView(form);
            return form;
        }

        private EditText addInput(LinearLayout form, String labelText, String hintText, String value, boolean multiline) {
            TextView title = label(labelText, 15, Color.WHITE, true);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(28));
            labelParams.setMargins(0, dp(8), 0, 0);
            form.addView(title, labelParams);
            EditText field = new EditText(MainActivity.this);
            field.setText(value);
            field.setHint(hintText);
            field.setTextColor(Color.WHITE);
            field.setHintTextColor(MUTED);
            field.setTextSize(16);
            field.setSelectAllOnFocus(!multiline);
            field.setInputType(multiline
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            if (!multiline) field.setSingleLine(true);
            field.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
            field.setPadding(dp(16), dp(8), dp(16), dp(8));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, tileShape(PANEL_FOCUSED, ACCENT, 2));
            states.addState(new int[]{}, tileShape(PANEL, 0x668DA2AC, 1));
            field.setBackground(states);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(multiline ? 102 : 56));
            fieldParams.setMargins(0, 0, 0, dp(10));
            form.addView(field, fieldParams);
            if (firstControl == null) firstControl = field;
            return field;
        }

        private Button addFormButton(LinearLayout form, String text, View.OnClickListener listener) {
            Button button = actionButton(text);
            button.setOnClickListener(listener);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
            params.setMargins(0, dp(8), 0, dp(8));
            form.addView(button, params);
            if (firstControl == null) firstControl = button;
            return button;
        }

        private void addFormHint(LinearLayout form, String value) {
            TextView hint = label(value, 14, MUTED, false);
            hint.setPadding(0, dp(4), 0, dp(8));
            form.addView(hint);
        }

        private void addSaveActions(LinearLayout page, View.OnClickListener save,
                                    View.OnClickListener discard, View.OnClickListener refresh) {
            LinearLayout actions = new LinearLayout(MainActivity.this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(54));
            rowParams.setMargins(0, dp(8), 0, dp(6));
            page.addView(actions, rowParams);
            Button saveButton = actionButton("Save changes");
            saveButton.setOnClickListener(save);
            actions.addView(saveButton, new LinearLayout.LayoutParams(dp(158), -1));
            Button discardButton = actionButton("Discard edits");
            discardButton.setOnClickListener(discard);
            LinearLayout.LayoutParams discardParams = new LinearLayout.LayoutParams(dp(158), -1);
            discardParams.setMargins(dp(12), 0, 0, 0);
            actions.addView(discardButton, discardParams);
            if (refresh != null) {
                Button refreshButton = actionButton("Refresh now");
                refreshButton.setOnClickListener(refresh);
                LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(158), -1);
                refreshParams.setMargins(dp(12), 0, 0, 0);
                actions.addView(refreshButton, refreshParams);
            }
        }

        private void renderLive(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            playlistField = addInput(form, "M3U playlist", "https://example.com/playlist.m3u", draftPlaylist, false);
            guideField = addInput(form, "Programme guide · XML or XML.GZ", "https://example.com/epg.xml.gz", draftGuide, false);
            addFormHint(form, "Guide channels match the playlist by tvg-id. Gzip guides are supported.");
            addSaveActions(page, v -> {
                captureDrafts();
                String playlist = draftPlaylist.trim(), guide = draftGuide.trim();
                boolean changed = !playlist.equals(iptv.playlistUrl()) || !guide.equals(iptv.guideUrl());
                iptv.saveUrls(playlist, guide);
                draftPlaylist = playlist; draftGuide = guide;
                renderSection();
                if (changed && !playlist.isEmpty()) refreshIptv(true);
                else if (liveFeedback != null) liveFeedback.setText(changed ? "Saved. Add a playlist URL to refresh channels." : "No changes to save.");
            }, v -> {
                draftPlaylist = iptv.playlistUrl(); draftGuide = iptv.guideUrl(); renderSection();
            }, v -> {
                if (iptv.playlistUrl().isEmpty()) {
                    if (liveFeedback != null) liveFeedback.setText("Add and save a playlist URL first.");
                } else refreshIptv(true);
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
            addFormHint(form, "A selected photo folder or Google Photos public album cycles images every five seconds and takes priority over a single wallpaper.");
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
                preferences.edit().remove("wallpaperFolder").apply();
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
                preferences.edit().remove("wallpaperAlbum").apply();
                String folder = preferences.getString("wallpaperFolder", "");
                if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            View separator = new View(MainActivity.this);
            separator.setBackgroundColor(0xFF364149);
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

        void refreshAppearance() {
            if (settingsScreen == this && section == APPEARANCE) renderSection();
        }

        void onRefreshResult(String message) {
            if (settingsScreen == this && liveFeedback != null) liveFeedback.setText(message);
            if (settingsScreen == this && section == CHANNELS && channelAdapter != null) channelAdapter.setQuery(channelAdapter.query);
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
                TextView state = label("", 14, ACCENT, true);
                row.addView(state);
                return new Holder(row, name, group, state);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                IptvRepository.Channel channel = visible.get(position);
                holder.name.setText(channel.name);
                holder.group.setText(channel.group.isEmpty() ? "Uncategorized" : channel.group);
                boolean shown = !hiddenChannelKeys.contains(channel.stableKey());
                holder.state.setText(shown ? "Shown" : "Hidden");
                holder.state.setTextColor(shown ? ACCENT : MUTED);
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
                Button toggle = actionButton("");
                toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                toggle.setPadding(dp(18), 0, dp(12), 0);
                row.addView(toggle, new LinearLayout.LayoutParams(0, dp(60), 1));
                Button up = actionButton("↑");
                LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                upParams.setMargins(dp(8), 0, 0, 0);
                row.addView(up, upParams);
                Button down = actionButton("↓");
                LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                downParams.setMargins(dp(8), 0, 0, 0);
                row.addView(down, downParams);
                return new Holder(row, toggle, up, down);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                AppEntry entry = rows.get(position);
                int selected = selectedOrder.indexOf(entry.packageName);
                holder.toggle.setText((selected >= 0 ? "✓  " : "+  ") + entry.label);
                holder.toggle.setTextColor(selected >= 0 ? ACCENT : Color.WHITE);
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
        String uriValue = preferences == null ? null : preferences.getString("wallpaper", null);
        if (uriValue == null) {
            wallpaperImage.setImageResource(R.drawable.default_wallpaper);
            wallpaperImage.setVisibility(backgroundPhotos.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        wallpaperImage.setVisibility(View.GONE);
        int targetWidth = getResources().getDisplayMetrics().widthPixels;
        int targetHeight = dp(heroHeightDp());
        new Thread(() -> {
            android.graphics.Bitmap bitmap = decodeWallpaper(Uri.parse(uriValue), targetWidth, targetHeight);
            handler.post(() -> {
                if (wallpaperImage == null || isFinishing() || !uriValue.equals(preferences.getString("wallpaper", null))) return;
                if (bitmap == null) {
                    preferences.edit().remove("wallpaper").apply();
                    wallpaperImage.setImageResource(R.drawable.default_wallpaper);
                    wallpaperImage.setVisibility(backgroundPhotos.isEmpty() ? View.VISIBLE : View.GONE);
                    return;
                }
                wallpaperImage.setImageBitmap(bitmap);
                wallpaperImage.setVisibility(backgroundPhotos.isEmpty() ? View.VISIBLE : View.GONE);
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
                if (bitmap != null) backgroundBitmapCache.put(key, bitmap);
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
