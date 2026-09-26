package com.deo.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    interface OnChannelClick { void onClick(IptvRepository.Channel channel); }


    private final Activity activity;
    private final ChannelLogoLoader logos;
    private final OnChannelClick listener;
    private final List<IptvRepository.Channel> items = new ArrayList<>();

    ChannelAdapter(Activity activity, ChannelLogoLoader logos, OnChannelClick listener) {
        this.activity = activity;
        this.logos = logos;
        this.listener = listener;
        setHasStableIds(false);
    }

    void setChannels(List<IptvRepository.Channel> channels) {
        List<IptvRepository.Channel> next = new ArrayList<>(channels);
        if (hasSameContentsInOrder(next)) return;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return items.get(oldPosition).sameRenderIdentity(next.get(newPosition));
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                IptvRepository.Channel oldChannel = items.get(oldPosition);
                IptvRepository.Channel newChannel = next.get(newPosition);
                return oldChannel.name.equals(newChannel.name)
                        && oldChannel.group.equals(newChannel.group)
                        && oldChannel.logoUrl.equals(newChannel.logoUrl)
                        && oldChannel.now.equals(newChannel.now)
                        && oldChannel.next.equals(newChannel.next)
                        && oldChannel.nowStartMs == newChannel.nowStartMs
                        && oldChannel.nowEndMs == newChannel.nowEndMs
                        && oldChannel.streamUrl.equals(newChannel.streamUrl)
                        && oldChannel.drmType.equals(newChannel.drmType)
                        && oldChannel.drmLicenseUrl.equals(newChannel.drmLicenseUrl)
                        && oldChannel.drmHeaders.equals(newChannel.drmHeaders);
            }
        }, false);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    void refreshProgrammeLabels() {
        if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), "programme-time");
    }

    private boolean hasSameContentsInOrder(List<IptvRepository.Channel> next) {
        if (items.size() != next.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            IptvRepository.Channel oldChannel = items.get(i);
            IptvRepository.Channel newChannel = next.get(i);
            if (!oldChannel.sameRenderIdentity(newChannel)
                    || !oldChannel.name.equals(newChannel.name)
                    || !oldChannel.group.equals(newChannel.group)
                    || !oldChannel.logoUrl.equals(newChannel.logoUrl)
                    || !oldChannel.now.equals(newChannel.now)
                    || !oldChannel.next.equals(newChannel.next)
                    || oldChannel.nowStartMs != newChannel.nowStartMs
                    || oldChannel.nowEndMs != newChannel.nowEndMs
                    || !oldChannel.streamUrl.equals(newChannel.streamUrl)
                    || !oldChannel.drmType.equals(newChannel.drmType)
                    || !oldChannel.drmLicenseUrl.equals(newChannel.drmLicenseUrl)
                    || !oldChannel.drmHeaders.equals(newChannel.drmHeaders)) return false;
        }
        return true;
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        float density = activity.getResources().getDisplayMetrics().density;
        boolean light = LiquidTheme.isLight(activity);
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(10, density), dp(10, density), dp(10, density), dp(9, density));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        RecyclerView.LayoutParams tileParams = new RecyclerView.LayoutParams(dp(164, density), dp(226, density));
        tileParams.setMargins(0, 0, dp(14, density), 0);
        tile.setLayoutParams(tileParams);

        StateListDrawable states = new StateListDrawable();
        int accent = LiquidTheme.accent(activity);
        states.addState(new int[]{android.R.attr.state_focused}, shape(
                LiquidTheme.raisedSurface(activity), accent, 3, density));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(
                LiquidTheme.raisedSurface(activity), accent, 3, density));
        states.addState(new int[]{}, shape(
                LiquidTheme.surface(activity), LiquidTheme.border(activity), 1, density));
        tile.setBackground(states);
        tile.setElevation(dp(2, density));
        tile.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                .translationZ(focused ? dp(7, density) : 0).setDuration(150).start());

        FrameLayout logoBox = new FrameLayout(activity);
        GradientDrawable logoBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                light ? new int[]{0xFFFFFFFF, 0xFFE8E8E8} : new int[]{0xFF2A2A2A, 0xFF101010});
        logoBackground.setCornerRadius(dp(8, density));
        logoBackground.setStroke(dp(1, density), LiquidTheme.border(activity));
        logoBox.setBackground(logoBackground);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(146, density), dp(104, density));
        logoParams.setMargins(0, 0, 0, dp(9, density));
        tile.addView(logoBox, logoParams);

        TextView fallback = new TextView(activity);
        fallback.setGravity(Gravity.CENTER);
        fallback.setTextColor(LiquidTheme.text(activity));
        fallback.setTextSize(27);
        fallback.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        logoBox.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1);
        imageParams.setMargins(dp(4, density), dp(4, density), dp(4, density), dp(4, density));
        logoBox.addView(image, imageParams);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        tile.addView(text, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView name = new TextView(activity);
        name.setTextColor(LiquidTheme.text(activity));
        name.setTextSize(16);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        text.addView(name);
        TextView now = new TextView(activity);
        now.setTextColor(LiquidTheme.text(activity));
        now.setTextSize(12);
        now.setGravity(Gravity.CENTER);
        now.setMaxLines(2);
        now.setPadding(0, dp(3, density), 0, 0);
        text.addView(now);
        TextView next = new TextView(activity);
        next.setTextColor(LiquidTheme.secondaryText(activity));
        next.setTextSize(11);
        next.setGravity(Gravity.CENTER);
        next.setMaxLines(2);
        text.addView(next);

        return new Holder(tile, fallback, image, name, now, next);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        IptvRepository.Channel channel = items.get(position);
        holder.name.setText(channel.name);
        holder.now.setText(channel.now.isEmpty() ? channel.group : "ON NOW  ·  " + channel.now);
        holder.next.setText(channel.next.isEmpty() ? "Select to watch" : "Next · " + channel.next);
        holder.fallback.setText(channel.name.isEmpty() ? "TV" : channel.name.substring(0, 1).toUpperCase(Locale.getDefault()));
        logos.load(channel.logoUrl, holder.logo, holder.fallback);
        holder.itemView.setOnClickListener(view -> listener.onClick(channel));
    }

    @Override public int getItemCount() { return items.size(); }

    private static GradientDrawable shape(int color, int border, int width, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(10, density));
        if (width > 0) drawable.setStroke(dp(width, density), border);
        return drawable;
    }

    private static GradientDrawable gradientShape(int startColor, int endColor, int border, int width, float density) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(17, density));
        if (width > 0) drawable.setStroke(dp(width, density), border);
        return drawable;
    }

    private static int dp(int value, float density) { return Math.round(value * density); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView fallback, name, now, next;
        final ImageView logo;
        Holder(View itemView, TextView fallback, ImageView logo, TextView name, TextView now, TextView next) {
            super(itemView);
            this.fallback = fallback;
            this.logo = logo;
            this.name = name;
            this.now = now;
            this.next = next;
        }
    }
}
