package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One channel per row, with its cached schedule browsable horizontally. */
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

    static List<IptvRepository.Programme> upcomingProgrammes(IptvRepository.Channel channel, long now) {
        List<IptvRepository.Programme> schedule = new ArrayList<>();
        for (IptvRepository.Programme programme : channel.programmes) {
            if (programme.endMs > now) schedule.add(programme);
        }
        Collections.sort(schedule, Comparator.comparingLong(programme -> programme.startMs));
        return schedule;
    }

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
        boolean light = LiquidTheme.isLight(activity);
        logoBackground.setColor(light ? 0x22087D91 : 0x24FFFFFF);
        logoBackground.setCornerRadius(dp(12));
        if (light) logoBackground.setStroke(dp(1), LiquidTheme.border(activity));
        logoBox.setBackground(logoBackground);
        channelCell.addView(logoBox, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView fallback = new TextView(activity);
        fallback.setGravity(Gravity.CENTER);
        fallback.setTextColor(LiquidTheme.text(activity));
        fallback.setTextSize(20);
        fallback.setTypeface(LiquidTheme.font(activity));
        logoBox.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        ImageView logo = new ImageView(activity);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(-1, -1);
        logoParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        logoBox.addView(logo, logoParams);

        TextView channel = new TextView(activity);
        channel.setTextSize(16);
        channel.setTextColor(LiquidTheme.text(activity));
        channel.setTypeface(LiquidTheme.font(activity));
        channel.setMaxLines(2);
        channel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams channelNameParams = new LinearLayout.LayoutParams(0, -2, 1);
        channelNameParams.leftMargin = dp(10);
        channelCell.addView(channel, channelNameParams);

        HorizontalScrollView programmeScroll = new HorizontalScrollView(activity);
        programmeScroll.setFocusable(false);
        programmeScroll.setHorizontalScrollBarEnabled(false);
        programmeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        programmeScroll.setClipChildren(false);
        programmeScroll.setClipToPadding(false);
        LinearLayout programmeStrip = new LinearLayout(activity);
        programmeStrip.setOrientation(LinearLayout.HORIZONTAL);
        programmeStrip.setClipChildren(false);
        programmeStrip.setClipToPadding(false);
        programmeScroll.addView(programmeStrip, new HorizontalScrollView.LayoutParams(-2, -1));
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(dp(230), dp(84));
        channelParams.rightMargin = dp(10);
        row.addView(channelCell, channelParams);
        row.addView(programmeScroll, new LinearLayout.LayoutParams(0, dp(84), 1));
        return new Holder(row, channelCell, fallback, logo, channel, programmeScroll, programmeStrip);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        IptvRepository.Channel channel = channels.get(position);
        long time = System.currentTimeMillis();
        holder.channel.setText(channel.name);
        holder.fallback.setText(channel.name.isEmpty() ? "TV"
                : channel.name.substring(0, 1).toUpperCase(Locale.getDefault()));
        logos.load(channel.logoUrl, holder.logo, holder.fallback);
        holder.channelCell.setContentDescription("Watch " + channel.name);
        holder.channelCell.setOnClickListener(view -> actions.watch(channel));

        List<IptvRepository.Programme> schedule = upcomingProgrammes(channel, time);
        if (holder.boundChannel != channel || !holder.boundProgrammes.equals(schedule)) {
            holder.programmeStrip.removeAllViews();
            holder.boundChannel = channel;
            holder.boundProgrammes = schedule;
            holder.programmeScroll.scrollTo(0, 0);
            if (schedule.isEmpty()) {
                TextView empty = cell(14, false);
                empty.setText("No upcoming schedule");
                empty.setAlpha(.65f);
                empty.setContentDescription("No upcoming schedule for " + channel.name);
                empty.setOnClickListener(view -> actions.watch(channel));
                holder.programmeStrip.addView(empty, new LinearLayout.LayoutParams(dp(260), dp(84)));
            } else for (IptvRepository.Programme programme : schedule) {
                TextView tile = cell(14, false);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(260), dp(84));
                tileParams.rightMargin = dp(10);
                holder.programmeStrip.addView(tile, tileParams);
            }
        }
        for (int i = 0; i < schedule.size(); i++) {
            IptvRepository.Programme programme = schedule.get(i);
            TextView tile = (TextView) holder.programmeStrip.getChildAt(i);
            String status = programme.startMs <= time && programme.endMs > time ? "ON NOW"
                    : "UPCOMING";
            boolean reminded = programme.startMs > time && reminders.has(new GuideReminders.Reminder(
                    channel.stableKey(), channel.streamUrl, channel.name, programme.title,
                    programme.startMs, programme.endMs));
            String timeRange = range(programme);
            tile.setText(status + "  ·  " + timeRange
                    + (reminded ? "  ·  REMINDER SET" : "") + "\n" + programme.title);
            tile.setContentDescription(status + ": " + programme.title + " on "
                    + channel.name + ", " + timeRange);
            tile.setOnClickListener(view -> actions.details(channel, programme));
        }
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
        DateFormat date = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        return date.format(new Date(programme.startMs)) + "  "
                + format.format(new Date(programme.startMs)) + "–" + format.format(new Date(programme.endMs));
    }

    private TextView cell(int size, boolean bold) {
        TextView text = new TextView(activity);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setTextSize(size);
        text.setTextColor(LiquidTheme.text(activity));
        text.setTypeface(LiquidTheme.font(activity));
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
        final TextView channel;
        final HorizontalScrollView programmeScroll;
        final LinearLayout programmeStrip;
        IptvRepository.Channel boundChannel;
        List<IptvRepository.Programme> boundProgrammes = Collections.emptyList();
        Holder(View itemView, LinearLayout channelCell, TextView fallback, ImageView logo,
                TextView channel, HorizontalScrollView programmeScroll, LinearLayout programmeStrip) {
            super(itemView);
            this.channelCell = channelCell;
            this.fallback = fallback;
            this.logo = logo;
            this.channel = channel;
            this.programmeScroll = programmeScroll;
            this.programmeStrip = programmeStrip;
        }
    }
}
