package com.deo.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Compact, remote-focusable now/next rows for a single playlist category. */
final class ProgrammeGuideAdapter extends RecyclerView.Adapter<ProgrammeGuideAdapter.Holder> {
    interface Actions {
        void watch(IptvRepository.Channel channel);
        void details(IptvRepository.Channel channel, IptvRepository.Programme programme);
    }

    private final Activity activity;
    private final GlassBackdrop backdrop;
    private final ChannelLogoLoader logos;
    private final Actions actions;
    private final GuideReminders reminders;
    private final List<IptvRepository.Channel> channels = new ArrayList<>();

    ProgrammeGuideAdapter(Activity activity, GlassBackdrop backdrop, ChannelLogoLoader logos,
            GuideReminders reminders, Actions actions) {
        this.activity = activity;
        this.backdrop = backdrop;
        this.logos = logos;
        this.reminders = reminders;
        this.actions = actions;
    }

    void setChannels(List<IptvRepository.Channel> updated) {
        channels.clear();
        channels.addAll(updated);
        notifyDataSetChanged();
    }

    void refreshTimes() {
        if (!channels.isEmpty()) notifyItemRangeChanged(0, channels.size());
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(100)));

        LinearLayout channelCell = new LinearLayout(activity);
        channelCell.setGravity(Gravity.CENTER_VERTICAL);
        channelCell.setPadding(dp(10), dp(6), dp(12), dp(6));
        channelCell.setFocusable(true);
        channelCell.setClickable(true);
        styleCell(channelCell);

        FrameLayout logoBox = new FrameLayout(activity);
        GradientDrawable logoBackground = new GradientDrawable();
        logoBackground.setColor(0x24FFFFFF);
        logoBackground.setCornerRadius(dp(12));
        logoBox.setBackground(logoBackground);
        channelCell.addView(logoBox, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView fallback = new TextView(activity);
        fallback.setGravity(Gravity.CENTER);
        fallback.setTextColor(LiquidTheme.text(activity));
        fallback.setTextSize(20);
        fallback.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logoBox.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        ImageView logo = new ImageView(activity);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(-1, -1);
        logoParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        logoBox.addView(logo, logoParams);

        TextView channel = new TextView(activity);
        channel.setTextSize(16);
        channel.setTextColor(LiquidTheme.text(activity));
        channel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        channel.setMaxLines(2);
        channel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams channelNameParams = new LinearLayout.LayoutParams(0, -2, 1);
        channelNameParams.leftMargin = dp(10);
        channelCell.addView(channel, channelNameParams);

        TextView now = cell(14, false);
        TextView next = cell(14, false);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(dp(230), dp(84));
        channelParams.rightMargin = dp(10);
        row.addView(channelCell, channelParams);
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(0, dp(84), 1);
        nowParams.rightMargin = dp(10);
        row.addView(now, nowParams);
        row.addView(next, new LinearLayout.LayoutParams(0, dp(84), 1));
        return new Holder(row, channelCell, fallback, logo, channel, now, next);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        IptvRepository.Channel channel = channels.get(position);
        long time = System.currentTimeMillis();
        IptvRepository.Programme current = current(channel, time);
        IptvRepository.Programme following = next(channel, time);
        holder.channel.setText(channel.name);
        holder.fallback.setText(channel.name.isEmpty() ? "TV"
                : channel.name.substring(0, 1).toUpperCase(Locale.getDefault()));
        logos.load(channel.logoUrl, holder.logo, holder.fallback);
        holder.channelCell.setContentDescription("Watch " + channel.name);
        holder.channelCell.setOnClickListener(view -> actions.watch(channel));

        String currentTitle = current != null ? current.title : channel.now;
        holder.now.setText(currentTitle.isEmpty() ? "NOW\nNo schedule available"
                : "NOW  ·  " + (current == null ? "Time unavailable" : range(current)) + "\n" + currentTitle);
        holder.now.setAlpha(currentTitle.isEmpty() ? .65f : 1f);
        holder.now.setOnClickListener(view -> actions.watch(channel));
        holder.now.setContentDescription("Watch " + channel.name + ". " + currentTitle);

        String followingTitle = following != null ? following.title : channel.next;
        boolean reminded = following != null && reminders.has(new GuideReminders.Reminder(channel.stableKey(),
                channel.streamUrl, channel.name, following.title, following.startMs, following.endMs));
        holder.next.setText(followingTitle.isEmpty() ? "NEXT\nNo upcoming programme"
                : "NEXT  ·  " + (following == null ? "Time unavailable" : range(following))
                + (reminded ? "  ·  REMINDER SET" : "") + "\n" + followingTitle);
        holder.next.setAlpha(following == null ? .65f : 1f);
        holder.next.setFocusable(following != null);
        holder.next.setClickable(following != null);
        holder.next.setOnClickListener(following == null ? null : view -> actions.details(channel, following));
        holder.next.setContentDescription(following == null ? "No timed upcoming programme for " + channel.name
                : "Details and reminder for " + following.title + " on " + channel.name);

        boolean lastRow = position == channels.size() - 1;
        holder.channelCell.setOnKeyListener((view, keyCode, event) ->
                (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && lastRow)
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
        holder.now.setOnKeyListener((view, keyCode, event) ->
                (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && lastRow)
                        || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && following == null));
        holder.next.setOnKeyListener((view, keyCode, event) ->
                (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && lastRow)
                        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    @Override public int getItemCount() { return channels.size(); }

    static IptvRepository.Programme current(IptvRepository.Channel channel, long now) {
        IptvRepository.Programme selected = null;
        for (IptvRepository.Programme programme : channel.programmes) {
            if (programme.startMs <= now && programme.endMs > now
                    && (selected == null || programme.startMs > selected.startMs)) selected = programme;
        }
        return selected;
    }

    static IptvRepository.Programme next(IptvRepository.Channel channel, long now) {
        IptvRepository.Programme selected = null;
        for (IptvRepository.Programme programme : channel.programmes) {
            if (programme.startMs > now && (selected == null || programme.startMs < selected.startMs)) selected = programme;
        }
        return selected;
    }

    private String range(IptvRepository.Programme programme) {
        SharedPreferences preferences = activity.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        DateFormat format = preferences.getBoolean("24h", false)
                ? new SimpleDateFormat("HH:mm", Locale.getDefault())
                : new SimpleDateFormat("h:mm a", Locale.getDefault());
        return format.format(new Date(programme.startMs)) + "–" + format.format(new Date(programme.endMs));
    }

    private TextView cell(int size, boolean bold) {
        TextView text = new TextView(activity);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setTextSize(size);
        text.setTextColor(LiquidTheme.text(activity));
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setMaxLines(3);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setPadding(dp(15), dp(6), dp(15), dp(6));
        text.setFocusable(true);
        text.setClickable(true);
        styleCell(text);
        return text;
    }

    private void styleCell(View view) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                new GlassDrawable(activity, backdrop, LiquidTheme.raisedSurface(activity), LiquidTheme.accent(activity), 2, 16));
        states.addState(new int[]{},
                new GlassDrawable(activity, backdrop, LiquidTheme.surface(activity), LiquidTheme.border(activity), 1, 16));
        view.setBackground(states);
        view.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
    }

    private int dp(int value) { return LiquidTheme.dp(activity, value); }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout channelCell;
        final TextView fallback;
        final ImageView logo;
        final TextView channel, now, next;
        Holder(View itemView, LinearLayout channelCell, TextView fallback, ImageView logo,
                TextView channel, TextView now, TextView next) {
            super(itemView);
            this.channelCell = channelCell;
            this.fallback = fallback;
            this.logo = logo;
            this.channel = channel;
            this.now = now;
            this.next = next;
        }
    }
}
