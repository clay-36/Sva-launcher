package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A recycled channel list and a single logical time window, designed for a TV remote. */
@UnstableApi
public final class GuideActivity extends MotionActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LifecycleWork lifecycleWork = new LifecycleWork(this, handler);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<IptvRepository.Channel, List<GuideTimeline.Slot>> slotCache = new HashMap<>();
    private List<IptvRepository.Channel> all = new ArrayList<>(), channels = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Set<String> favoriteKeys = new HashSet<>();
    private String category, selectedKey;
    private long origin, horizon, windowStart, anchor, navigationTime;
    private double renderedWindowStart;
    private long renderFrom, renderTo;
    private android.animation.ValueAnimator timelinePan;
    private LinearLayout details;
    private String detailIdentity;
    private int selectedRow;
    private boolean channelFocused, loaded, destroyed, selectingFocus;
    private ChannelLogoLoader logos;
    private GuideReminders reminders;
    private RecyclerView list;
    private Rows adapter;
    private LinearLayout toolbar;
    private Button backButton, categoryButton, nowButton;
    private TextView clock, title, metadata, actionHint, empty;
    private ProgressBar progress;
    private AlertDialog actionDialog;
    private Ruler ruler;
    private int loadGeneration, focusGeneration;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            clock.setText(date(System.currentTimeMillis(), "EEE, d MMM") + "  ·  " + time(System.currentTimeMillis()));
            updateDetails(); ruler.invalidate();
            // Update visible badges without rebuilding cells or disturbing the user's focus.
            for (int i = 0; i < list.getChildCount(); i++) {
                Row row = (Row)list.getChildViewHolder(list.getChildAt(i));
                for (int j = 0; j < row.cells.getChildCount(); j++) {
                    View child = row.cells.getChildAt(j);
                    if (child.getTag() instanceof Target) updateTile((TextView)child, (Target)child.getTag());
                }
                row.cells.invalidate();
            }
            handler.postDelayed(this, 60_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        origin = GuideTimeline.floor(System.currentTimeMillis());
        horizon = origin + GuideTimeline.HORIZON;
        windowStart = origin; anchor = System.currentTimeMillis();
        navigationTime = anchor;
        category = getIntent().getStringExtra("category");
        if (state != null) {
            category = state.getString("category"); selectedKey = state.getString("channel");
            windowStart = Math.max(origin, Math.min(horizon - GuideTimeline.WINDOW, state.getLong("window", origin)));
            anchor = Math.max(origin, Math.min(horizon - 1, state.getLong("anchor", anchor)));
            navigationTime = Math.max(origin, Math.min(horizon - 1, state.getLong("navigationTime", anchor)));
            channelFocused = state.getBoolean("channelFocused");
        }
        renderedWindowStart = windowStart; updateRenderBounds();
        logos = new ChannelLogoLoader(this);
        reminders = ((SvaApplication)getApplication()).guideReminders();
        build();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setBackground(LiquidTheme.scenicBackground(this));
        page.setPadding(dp(32), dp(20), dp(32), dp(18));
        setContentView(page);
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setClipChildren(false); toolbar.setClipToPadding(false);
        backButton = button("‹", "Back to Live TV"); backButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(48), dp(42));
        backParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        toolbar.addView(backButton, backParams);
        TextView heading = text("TV guide", 26, LiquidTheme.text(this));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.leftMargin = dp(16); toolbar.addView(heading, headingParams);
        categoryButton = button("All channels  ▾", "Choose channel category");
        categoryButton.setOnClickListener(v -> chooseCategory());
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(dp(190), dp(42));
        categoryParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        toolbar.addView(categoryButton, categoryParams);
        nowButton = button("Now", "Return to current programme"); nowButton.setOnClickListener(v -> jumpNow());
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(dp(76), dp(42));
        nowParams.setMargins(dp(10), dp(2), dp(4), dp(2));
        toolbar.addView(nowButton, nowParams);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
        clock = text("", 12, LiquidTheme.secondaryText(this)); clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        page.addView(clock, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout detail = new LinearLayout(this); details = detail; detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(20), dp(10), dp(20), dp(10));
        detail.setBackground(shape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 16));
        metadata = text("YOUR TV SCHEDULE", 11, LiquidTheme.accent(this)); metadata.setSingleLine(true);
        detail.addView(metadata, new LinearLayout.LayoutParams(-1, dp(19)));
        title = text("Loading your channels…", 23, LiquidTheme.text(this)); title.setSingleLine(true);
        detail.addView(title, new LinearLayout.LayoutParams(-1, dp(33)));
        LinearLayout lower = new LinearLayout(this); lower.setGravity(Gravity.CENTER_VERTICAL);
        actionHint = text("", 12, LiquidTheme.secondaryText(this)); actionHint.setSingleLine(true);
        lower.addView(actionHint, new LinearLayout.LayoutParams(0, dp(24), 1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(LiquidTheme.accent(this)));
        lower.addView(progress, new LinearLayout.LayoutParams(dp(140), dp(4)));
        detail.addView(lower, new LinearLayout.LayoutParams(-1, dp(24)));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, dp(104));
        detailParams.setMargins(0, dp(8), 0, dp(12)); page.addView(detail, detailParams);

        ruler = new Ruler(); page.addView(ruler, new LinearLayout.LayoutParams(-1, dp(34)));
        FrameLayout grid = new FrameLayout(this);
        grid.setBackground(shape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 14));
        list = new GuideList(); list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null); list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setPadding(dp(4), dp(4), dp(4), dp(4)); list.setClipToPadding(false);
        list.setFocusable(false);
        adapter = new Rows(); list.setAdapter(adapter); grid.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = text("Loading channels…", 17, LiquidTheme.secondaryText(this)); empty.setGravity(Gravity.CENTER);
        grid.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView help = text("↑↓ Channels     ←→ Schedule     OK Watch / reminders     Back Live TV", 11, LiquidTheme.secondaryText(this));
        help.setGravity(Gravity.CENTER_VERTICAL); page.addView(help, new LinearLayout.LayoutParams(-1, dp(28)));
    }

    @Override protected void onResume() {
        super.onResume();
        load(); handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(tick); finishPan(); list.stopScroll(); Motion.cancelTree(getWindow().getDecorView()); super.onPause();
    }
    @Override protected void onDestroy() {
        lifecycleWork.close(); destroyed = true; loadGeneration++; focusGeneration++;
        if (actionDialog != null) actionDialog.dismiss();
        handler.removeCallbacksAndMessages(null); worker.shutdownNow(); logos.close(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("category", category); state.putString("channel", selectedKey);
        state.putLong("window", windowStart); state.putLong("anchor", anchor);
        state.putLong("navigationTime", navigationTime);
        state.putBoolean("channelFocused", channelFocused); super.onSaveInstanceState(state);
    }

    private void load() {
        int generation = ++loadGeneration;
        boolean focusGrid = !loaded || (getCurrentFocus() != null && getCurrentFocus().getTag() instanceof Target);
        lifecycleWork.execute(worker, () -> {
            List<IptvRepository.Channel> cached = new IptvRepository(this).cachedChannels();
            Set<String> hidden = HiddenChannels.load(getSharedPreferences("launcher", 0));
            Set<String> favorites = FavoriteChannels.load(getSharedPreferences("launcher", 0));
            List<IptvRepository.Channel> visible = HiddenChannels.filterVisible(cached, hidden);
            lifecycleWork.post(() -> {
                if (destroyed || generation != loadGeneration) return;
                all = visible;
                favoriteKeys.clear();
                favoriteKeys.addAll(favorites);
                categories.clear();
                boolean hasFavorites = false;
                for (IptvRepository.Channel channel : all) {
                    if (favoriteKeys.contains(channel.stableKey())) {
                        hasFavorites = true;
                        break;
                    }
                }
                if (hasFavorites) categories.add(FavoriteChannels.CATEGORY_FAVORITES);
                LinkedHashSet<String> groups = new LinkedHashSet<>();
                for (IptvRepository.Channel channel : all) groups.add(group(channel));
                categories.addAll(groups);
                if (!categories.contains(category)) category = null;
                loaded = true; filter(focusGrid);
            });
        });
    }

    private void updateFavoriteCategoryList() {
        boolean hasFavorites = false;
        for (IptvRepository.Channel channel : all) {
            if (favoriteKeys.contains(channel.stableKey())) {
                hasFavorites = true;
                break;
            }
        }
        if (hasFavorites) {
            if (!categories.contains(FavoriteChannels.CATEGORY_FAVORITES)) {
                categories.add(0, FavoriteChannels.CATEGORY_FAVORITES);
            }
        } else {
            categories.remove(FavoriteChannels.CATEGORY_FAVORITES);
            if (FavoriteChannels.CATEGORY_FAVORITES.equals(category)) {
                category = null;
                filter(false);
            }
        }
    }

    private static String group(IptvRepository.Channel channel) { return channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim(); }
    private static String key(IptvRepository.Channel channel) { return channel.stableKey() + "\n" + channel.streamUrl; }
    private void filter(boolean focusGrid) {
        slotCache.clear();
        channels = new ArrayList<>();
        for (IptvRepository.Channel channel : all) {
            if (category == null) channels.add(channel);
            else if (FavoriteChannels.CATEGORY_FAVORITES.equals(category)) {
                if (favoriteKeys.contains(channel.stableKey())) channels.add(channel);
            } else if (category.equals(group(channel))) channels.add(channel);
        }
        selectedRow = 0;
        for (int i = 0; i < channels.size(); i++) if (key(channels.get(i)).equals(selectedKey)) { selectedRow = i; break; }
        if (!channels.isEmpty()) selectedKey = key(channels.get(selectedRow));
        String label = category == null ? "All channels" : (FavoriteChannels.CATEGORY_FAVORITES.equals(category) ? "★ " + category : category);
        categoryButton.setText(label + "  ▾");
        categoryButton.setContentDescription("Category: " + label);
        empty.setVisibility(channels.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("No visible channels. Add a playlist or restore hidden channels in Settings.");
        list.setVisibility(channels.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged(); ruler.invalidate(); updateDetails();
        if (focusGrid && !channels.isEmpty()) focusSelection();
        else if (channels.isEmpty()) categoryButton.requestFocus();
    }

    private void chooseCategory() {
        List<String> labels = new ArrayList<>();
        labels.add("All channels");
        for (String cat : categories) {
            labels.add(FavoriteChannels.CATEGORY_FAVORITES.equals(cat) ? "★ " + cat : cat);
        }
        int checked = category == null ? 0 : categories.indexOf(category) + 1;
        AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, LiquidTheme.alertDialogTheme(this)))
                .setTitle("Channel category").setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    category = which == 0 ? null : categories.get(which - 1); selectedKey = null;
                    channelFocused = false; d.dismiss(); filter(true); Motion.reveal(list);
                }).setNegativeButton("Cancel", null).create();
        showDialog(dialog);
    }

    private List<GuideTimeline.Slot> slots(int row) {
        if (row < 0 || row >= channels.size()) return Collections.emptyList();
        IptvRepository.Channel c = channels.get(row);
        List<GuideTimeline.Slot> cached = slotCache.get(c);
        if (cached == null) {
            cached = GuideTimeline.slots(c, origin, horizon);
            slotCache.put(c, cached);
        }
        return cached;
    }
    private GuideTimeline.Slot selectedSlot() {
        if (channels.isEmpty() || selectedRow < 0 || selectedRow >= channels.size()) return null;
        List<GuideTimeline.Slot> schedule = slots(selectedRow);
        if (schedule.isEmpty()) return null;
        return schedule.get(GuideTimeline.at(schedule, anchor));
    }
    private void jumpNow() {
        slotCache.clear();
        origin = GuideTimeline.floor(System.currentTimeMillis()); horizon = origin + GuideTimeline.HORIZON;
        finishPan(); windowStart = origin; renderedWindowStart = origin; updateRenderBounds();
        anchor = System.currentTimeMillis(); channelFocused = false;
        navigationTime = anchor;
        adapter.notifyDataSetChanged(); ruler.invalidate(); updateDetails(); focusSelection(); Motion.reveal(list);
    }
    private void updateRenderBounds() {
        renderFrom = Math.min((long)renderedWindowStart, windowStart) - GuideTimeline.HALF_HOUR;
        renderTo = Math.max((long)renderedWindowStart, windowStart) + GuideTimeline.WINDOW + GuideTimeline.HALF_HOUR;
    }
    private float position(long time) { return (float)((time - renderedWindowStart) / GuideTimeline.WINDOW); }
    private void finishPan() {
        if (timelinePan != null) { timelinePan.cancel(); timelinePan = null; }
        renderedWindowStart = windowStart; updateRenderBounds();
        if (list != null) layoutTimeline();
    }
    private void layoutTimeline() {
        for (int i = 0; i < list.getChildCount(); i++) {
            Row row = (Row)list.getChildViewHolder(list.getChildAt(i)); row.cells.requestLayout(); row.cells.invalidate();
        }
        ruler.invalidate();
    }
    private void panTo(long next) {
        if (timelinePan != null) { timelinePan.cancel(); timelinePan = null; }
        double previous = renderedWindowStart; windowStart = next;
        boolean smooth = Motion.enabled() && Math.abs(next - previous) <= 2 * GuideTimeline.HALF_HOUR;
        if (!smooth) renderedWindowStart = next;
        updateRenderBounds(); adapter.notifyDataSetChanged(); ruler.invalidate();
        if (!smooth) { Motion.reveal(list); return; }
        android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofFloat(0f, 1f);
        timelinePan = animation; animation.setDuration(Motion.SCREEN); animation.setInterpolator(Motion.EASING);
        animation.addUpdateListener(a -> {
            if (timelinePan != a) return;
            renderedWindowStart = previous + (next - previous) * (Float)a.getAnimatedValue(); layoutTimeline();
        });
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (timelinePan != a) return;
                timelinePan = null; renderedWindowStart = windowStart; updateRenderBounds(); layoutTimeline();
            }
        }); animation.start();
    }
    private void ensureWindow(GuideTimeline.Slot slot) {
        if (slot == null) return;
        long next = windowStart;
        if (slot.end <= windowStart || anchor < windowStart) next = GuideTimeline.floor(anchor);
        else if (slot.start >= windowStart + GuideTimeline.WINDOW || anchor >= windowStart + GuideTimeline.WINDOW
                || (slot.programme != null && slot.start > windowStart && slot.end > windowStart + GuideTimeline.WINDOW)) {
            next = slot.end - slot.start <= GuideTimeline.WINDOW
                    ? GuideTimeline.floor(slot.end - GuideTimeline.WINDOW + GuideTimeline.HALF_HOUR - 1)
                    : GuideTimeline.floor(anchor);
            next = Math.min(next, GuideTimeline.floor(anchor));
        }
        next = Math.max(origin, Math.min(horizon - GuideTimeline.WINDOW, next));
        if (next != windowStart) panTo(next);
    }
    private final class GuideList extends RecyclerView {
        GuideList() { super(GuideActivity.this); }
        @Override public boolean requestChildRectangleOnScreen(View child, android.graphics.Rect rect, boolean immediate) {
            return !selectingFocus && super.requestChildRectangleOnScreen(child, rect, immediate);
        }
    }
    private void focusSelection() { focusSelection(false); }
    private void focusSelection(boolean smooth) {
        if (channels.isEmpty()) return;
        GuideTimeline.Slot slot = selectedSlot(); ensureWindow(slot);
        int generation = ++focusGeneration;
        View focused = getCurrentFocus();
        Target previous = focused != null && focused.getTag() instanceof Target ? (Target)focused.getTag() : null;
        list.stopScroll();
        if (smooth && Motion.enabled() && previous != null && Math.abs(previous.row - selectedRow) <= 1) {
            Row row = (Row)list.findViewHolderForAdapterPosition(selectedRow);
            int dy = row == null ? (selectedRow > previous.row ? dp(64) : -dp(64))
                    : row.itemView.getBottom() > list.getHeight() - list.getPaddingBottom()
                    ? row.itemView.getBottom() - list.getHeight() + list.getPaddingBottom()
                    : Math.min(0, row.itemView.getTop() - list.getPaddingTop());
            if (dy != 0) list.smoothScrollBy(0, dy, Motion.EASING, Motion.SCROLL);
        } else list.scrollToPosition(selectedRow);
        handler.post(new Runnable() {
            int attempts;
            @Override public void run() {
                if (destroyed || generation != focusGeneration) return;
                Row row = (Row)list.findViewHolderForAdapterPosition(selectedRow);
                View target = null;
                if (row != null) {
                    if (channelFocused) target = row.channel;
                    else for (int i = 0; i < row.cells.getChildCount(); i++) {
                        View child = row.cells.getChildAt(i); Target t = (Target)child.getTag();
                        if (t.slot != null && t.slot.start <= anchor && t.slot.end > anchor) { target = child; break; }
                    }
                }
                if (target != null) {
                    selectingFocus = true; target.requestFocus(); selectingFocus = false;
                }
                else if (++attempts < 30) handler.postDelayed(this, 20);
            }
        });
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
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN
                && code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return super.dispatchKeyEvent(event);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof Target) {
            if (channels.isEmpty()) return true;
            if (code == KeyEvent.KEYCODE_DPAD_UP && selectedRow == 0) { focusGeneration++; categoryButton.requestFocus(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                selectedRow = Math.max(0, Math.min(channels.size() - 1, selectedRow + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                selectedKey = key(channels.get(selectedRow));
                if (!channelFocused) {
                    List<GuideTimeline.Slot> schedule = slots(selectedRow);
                    GuideTimeline.Slot target = schedule.get(GuideTimeline.nearestProgramme(schedule, navigationTime));
                    anchor = Math.max(target.start, Math.min(target.end - 1, navigationTime));
                }
            } else if (channelFocused) {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) channelFocused = false;
            } else {
                List<GuideTimeline.Slot> schedule = slots(selectedRow); int index = GuideTimeline.at(schedule, anchor);
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) channelFocused = true;
                else {
                    int next = Math.max(0, Math.min(schedule.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
                    anchor = Math.max(schedule.get(next).start, origin);
                    navigationTime = anchor;
                }
            }
            updateDetails(); focusSelection(true); return true;
        }
        if (focused == backButton || focused == categoryButton || focused == nowButton) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { focusSelection(); return true; }
            Button[] controls = {backButton, categoryButton, nowButton};
            int index = focused == backButton ? 0 : focused == categoryButton ? 1 : 2;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)
                controls[Math.max(0, Math.min(2, index + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)))].requestFocus();
            return true;
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

    private GuideReminders.Reminder reminder(IptvRepository.Channel channel, IptvRepository.Programme p) {
        return new GuideReminders.Reminder(channel.stableKey(), channel.streamUrl, channel.name, p.title, p.startMs, p.endMs);
    }
    private boolean reminded(IptvRepository.Channel channel, IptvRepository.Programme p) { return reminders.has(reminder(channel, p)); }
    private String range(IptvRepository.Programme p) { return date(p.startMs, "EEE, d MMM") + "  ·  " + time(p.startMs) + "–" + time(p.endMs); }
    private void updateDetails() {
        if (title == null) return;
        GuideTimeline.Slot slot = selectedSlot();
        String identity = selectedKey + ":" + channelFocused + ":" + (slot == null ? "empty"
                : slot.programme == null ? "gap" : slot.programme.startMs + ":" + slot.programme.title);
        if (detailIdentity != null && !detailIdentity.equals(identity)) Motion.crossfade(details, this::updateDetailsContent);
        else updateDetailsContent();
        detailIdentity = identity;
    }
    private void updateDetailsContent() {
        if (channels.isEmpty()) {
            title.setText(loaded ? "Your guide is ready when you are" : "Loading your channels…");
            metadata.setText("TV GUIDE"); actionHint.setText("Configure Live TV sources in Settings"); progress.setVisibility(View.GONE); return;
        }
        IptvRepository.Channel channel = channels.get(selectedRow);
        GuideTimeline.Slot slot = selectedSlot(); IptvRepository.Programme p = channelFocused ? null : slot.programme;
        long now = System.currentTimeMillis();
        if (p == null) {
            metadata.setText(channel.name + "  ·  " + (channelFocused ? "LIVE TV" : "SCHEDULE UNAVAILABLE"));
            title.setText(channelFocused ? channel.name : "No programme information");
            actionHint.setText("Press OK to watch this channel"); progress.setVisibility(View.GONE);
        } else {
            boolean live = p.startMs <= now && p.endMs > now;
            metadata.setText((live ? "● LIVE NOW" : p.endMs <= now ? "ENDED" : "UPCOMING") + "  ·  " + channel.name);
            title.setText(p.title);
            actionHint.setText(range(p) + (p.startMs <= now ? "  ·  OK to watch channel" : reminded(channel, p) ? "  ·  Reminder set" : "  ·  OK for reminders"));
            progress.setVisibility(live ? View.VISIBLE : View.GONE);
            if (live) progress.setProgress((int)Math.max(0, Math.min(1000, (now - p.startMs) * 1000 / Math.max(1, p.endMs - p.startMs))));
        }
    }
    private void activate(Target target) {
        if (target == null || target.row < 0 || target.row >= channels.size()) return;
        IptvRepository.Channel channel = channels.get(target.row);
        IptvRepository.Programme p = target.slot == null ? null : target.slot.programme;
        if (p == null || p.startMs <= System.currentTimeMillis()) { PlaybackLauncher.watch(this, channel, all); return; }
        boolean saved = reminded(channel, p);
        AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, LiquidTheme.alertDialogTheme(this)))
                .setTitle(p.title).setMessage(channel.name + "\n" + range(p) + "\n\nReminders appear five minutes before the show while Sva is open.")
                .setPositiveButton(saved ? "Remove reminder" : "Remind me", (d, which) -> {
                    if (saved) reminders.remove(reminder(channel, p));
                    else if (!reminders.add(reminder(channel, p))) Toast.makeText(this, "This programme has already started.", Toast.LENGTH_SHORT).show();
                    updateDetails(); refreshBadges();
                }).setNeutralButton("Watch channel", (d, which) -> PlaybackLauncher.watch(this, channel, all))
                .setNegativeButton("Cancel", null).create();
        showDialog(dialog);
    }
    private void showDialog(AlertDialog dialog) {
        actionDialog = dialog;
        dialog.setOnDismissListener(d -> { if (actionDialog == dialog) actionDialog = null; });
        Motion.dialog(dialog); dialog.show();
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) { button.setTypeface(LiquidTheme.font(this)); button.setTextColor(LiquidTheme.accent(this)); }
        }
    }
    private void refreshBadges() {
        for (int i = 0; i < list.getChildCount(); i++) {
            Row row = (Row)list.getChildViewHolder(list.getChildAt(i));
            for (int j = 0; j < row.cells.getChildCount(); j++) {
                TextView tile = (TextView)row.cells.getChildAt(j); updateTile(tile, (Target)tile.getTag());
            }
        }
    }

    private static final class Target {
        final int row; final GuideTimeline.Slot slot;
        Target(int row, GuideTimeline.Slot slot) { this.row = row; this.slot = slot; }
    }
    private final class Rows extends RecyclerView.Adapter<Row> {
        @Override public int getItemCount() { return channels.size(); }
        @Override public Row onCreateViewHolder(ViewGroup parent, int type) { return new Row(); }
        @Override public void onBindViewHolder(Row row, int position) { row.bind(position); }
    }
    private final class Row extends RecyclerView.ViewHolder {
        final LinearLayout channel;
        final ImageView logo;
        final TextView fallback, name;
        final Cells cells;
        Row() {
            super(new LinearLayout(GuideActivity.this));
            LinearLayout row = (LinearLayout)itemView;
            row.setClipChildren(false); row.setClipToPadding(false);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(2), dp(3), dp(2), dp(3));
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(64)));
            channel = new LinearLayout(GuideActivity.this); channel.setGravity(Gravity.CENTER_VERTICAL);
            channel.setPadding(dp(8), dp(4), dp(8), dp(4)); style(channel);
            FrameLayout logoBox = new FrameLayout(GuideActivity.this);
            logoBox.setBackground(shape(LiquidTheme.raisedSurface(GuideActivity.this), 0, 0, 9));
            fallback = text("", 16, LiquidTheme.secondaryText(GuideActivity.this)); fallback.setGravity(Gravity.CENTER);
            logoBox.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(GuideActivity.this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setPadding(dp(3), dp(3), dp(3), dp(3));
            logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1)); channel.addView(logoBox, new LinearLayout.LayoutParams(dp(36), dp(36)));
            name = text("", 12, LiquidTheme.text(GuideActivity.this)); name.setMaxLines(2);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.leftMargin = dp(8); channel.addView(name, nameParams);
            row.addView(channel, new LinearLayout.LayoutParams(dp(156), -1));
            cells = new Cells(); LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, -1, 1); cellParams.leftMargin = dp(8); row.addView(cells, cellParams);
        }
        void bind(int position) {
            IptvRepository.Channel c = channels.get(position);
            boolean isFav = favoriteKeys.contains(c.stableKey());
            name.setText((isFav ? "★ " : "") + c.name);
            fallback.setText(c.name.isEmpty() ? "TV" : c.name.substring(0, 1).toUpperCase(Locale.getDefault()));
            logos.load(c.logoUrl, logo, fallback);
            configure(channel, new Target(position, null));
            channel.setContentDescription("Watch " + c.name + (isFav ? ", favourite" : ""));
            int childIndex = 0;
            for (GuideTimeline.Slot slot : slots(position)) {
                if (slot.end <= renderFrom || slot.start >= renderTo) continue;
                TextView tile;
                if (childIndex < cells.getChildCount()) {
                    tile = (TextView)cells.getChildAt(childIndex);
                } else {
                    tile = text("", 13, LiquidTheme.text(GuideActivity.this)); tile.setGravity(Gravity.CENTER_VERTICAL);
                    tile.setMaxLines(2); tile.setPadding(dp(10), dp(4), dp(8), dp(4)); style(tile);
                    cells.addView(tile, new FrameLayout.LayoutParams(1, -1));
                }
                Target target = new Target(position, slot); configure(tile, target); updateTile(tile, target);
                childIndex++;
            }
            if (cells.getChildCount() > childIndex) {
                cells.removeViews(childIndex, cells.getChildCount() - childIndex);
            }
            cells.requestLayout();
        }
    }
    private void configure(View view, Target target) {
        view.setTag(target);
        view.setOnFocusChangeListener((v, focused) -> {
            LiquidTheme.animateContainedFocus(v, focused);
            if (!focused) return;
            selectedRow = target.row; selectedKey = key(channels.get(selectedRow)); channelFocused = target.slot == null;
            if (target.slot != null && (anchor < target.slot.start || anchor >= target.slot.end)) anchor = target.slot.start;
            if (!selectingFocus) navigationTime = anchor;
            updateDetails();
        });
        view.setOnClickListener(v -> activate(target));
        view.setOnLongClickListener(v -> {
            if (target.row >= 0 && target.row < channels.size()) {
                IptvRepository.Channel c = channels.get(target.row);
                boolean added = FavoriteChannels.toggle(favoriteKeys, c);
                FavoriteChannels.save(getSharedPreferences("launcher", 0), favoriteKeys);
                Toast.makeText(GuideActivity.this, (added ? "Added to" : "Removed from") + " favourites: " + c.name, Toast.LENGTH_SHORT).show();
                updateFavoriteCategoryList();
                adapter.notifyItemChanged(target.row);
                if (FavoriteChannels.CATEGORY_FAVORITES.equals(category)) {
                    filter(false);
                }
                return true;
            }
            return false;
        });
    }
    private void updateTile(TextView tile, Target target) {
        IptvRepository.Programme p = target.slot.programme;
        if (p == null) {
            String label = tile.getMeasuredWidth() > 0 && tile.getMeasuredWidth() < dp(70) ? "" : "No schedule";
            if (!label.contentEquals(tile.getText())) tile.setText(label);
            tile.setTextColor(LiquidTheme.secondaryText(this));
            tile.setContentDescription("Schedule unavailable on " + channels.get(target.row).name + ". Press OK to watch.");
        } else {
            long now = System.currentTimeMillis();
            boolean live = p.startMs <= now && p.endMs > now;
            String status = live ? "● LIVE" : reminded(channels.get(target.row), p) ? "◷ REMINDER" : time(p.startMs);
            int width = tile.getMeasuredWidth();
            String label = width > 0 && width < dp(48) ? "" : width > 0 && width < dp(100) ? p.title : status + "\n" + p.title;
            if (!label.contentEquals(tile.getText())) tile.setText(label);
            tile.setContentDescription(p.title + " on " + channels.get(target.row).name + ", " + range(p)
                    + (live ? ", live now. Press OK to watch." : ". Press OK for reminders."));
        }
    }
    private final class Cells extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Cells() {
            super(GuideActivity.this);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
        }
        @Override protected void onMeasure(int width, int height) {
            int w = MeasureSpec.getSize(width), h = MeasureSpec.getSize(height);
            setMeasuredDimension(w, h);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); GuideTimeline.Slot slot = ((Target)child.getTag()).slot;
                int rawLeft = Math.round(position(slot.start) * w), rawRight = Math.round(position(slot.end) * w);
                boolean intersects = rawRight > 0 && rawLeft < w;
                int left = intersects ? Math.max(0, rawLeft) : Math.max(-w, rawLeft);
                int right = intersects ? Math.min(w, rawRight) : Math.min(w * 2, rawRight);
                child.measure(MeasureSpec.makeMeasureSpec(Math.max(1, right - left - dp(4)), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(1, h - dp(4)), MeasureSpec.EXACTLY));
                updateTile((TextView)child, (Target)child.getTag());
            }
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); GuideTimeline.Slot slot = ((Target)child.getTag()).slot;
                int rawLeft = Math.round(position(slot.start) * getWidth()), rawRight = Math.round(position(slot.end) * getWidth());
                int left = (rawRight > 0 && rawLeft < getWidth() ? Math.max(0, rawLeft) : Math.max(-getWidth(), rawLeft)) + dp(2);
                child.layout(left, dp(2), left + child.getMeasuredWidth(), dp(2) + child.getMeasuredHeight());
            }
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            // Buffered cells may sit outside the time window while panning. Keep them
            // inside the schedule viewport so they cannot paint over channel labels.
            int save = canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            super.dispatchDraw(canvas);
            float fraction = position(System.currentTimeMillis());
            if (fraction >= 0 && fraction <= 1) {
                paint.setColor(LiquidTheme.accent(GuideActivity.this)); paint.setStrokeWidth(dp(1)); paint.setAlpha(160);
                canvas.drawLine(fraction * getWidth(), 0, fraction * getWidth(), getHeight(), paint);
            }
            canvas.restoreToCount(save);
        }
    }
    private final class Ruler extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ruler() { super(GuideActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            int left = dp(170), width = getWidth() - left - dp(6);
            paint.setTypeface(LiquidTheme.font(GuideActivity.this)); paint.setTextSize(dp(11)); paint.setColor(LiquidTheme.secondaryText(GuideActivity.this));
            canvas.drawText(date(windowStart, "EEE, d MMM"), dp(8), dp(20), paint);
            int save = canvas.save(); canvas.clipRect(left, 0, getWidth(), getHeight());
            long first = GuideTimeline.floor((long)renderedWindowStart);
            for (int i = 0; i < 8; i++) {
                long stamp = first + i * GuideTimeline.HALF_HOUR;
                float x = left + width * position(stamp);
                canvas.drawText(time(stamp), x + dp(3), dp(20), paint);
            }
            canvas.restoreToCount(save);
            float now = position(System.currentTimeMillis());
            if (now >= 0 && now <= 1) {
                paint.setColor(LiquidTheme.accent(GuideActivity.this)); canvas.drawCircle(left + now * width, dp(30), dp(3), paint);
            }
        }
    }
    private Button button(String label, String description) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(label); button.setTextSize(13);
        button.setTypeface(LiquidTheme.font(this)); button.setTextColor(LiquidTheme.text(this)); button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END); button.setPadding(dp(10), 0, dp(10), 0); style(button); button.setContentDescription(description); return button;
    }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(LiquidTheme.font(this)); text.setEllipsize(TextUtils.TruncateAt.END); return text;
    }
    private void style(View view) {
        view.setFocusable(true); view.setClickable(true);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, shape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 10));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 10));
        states.addState(new int[]{}, shape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 10));
        view.setBackground(states);
    }
    private GradientDrawable shape(int color, int stroke, int width, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (width > 0) drawable.setStroke(dp(width), stroke); return drawable;
    }
    private String date(long timestamp, String pattern) { return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(timestamp)); }
    private String time(long timestamp) { return date(timestamp, getSharedPreferences("launcher", 0).getBoolean("24h", false) ? "HH:mm" : "h:mm a"); }
    private int dp(int value) { return LiquidTheme.dp(this, value); }
}
