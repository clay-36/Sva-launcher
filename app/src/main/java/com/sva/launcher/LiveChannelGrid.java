package com.sva.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** One recycling scroll surface: a full-width heading followed by four-column channels. */
final class LiveChannelGrid extends RecyclerView {
    LiveChannelGrid(Context context, View header, ChannelAdapter channels) {
        super(context);
        GridLayoutManager layout = new GridLayoutManager(context, 4);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position == 0 ? 4 : 1; }
        });
        setLayoutManager(layout);
        setAdapter(new ConcatAdapter(new HeaderAdapter(header), channels));
        setItemAnimator(null);
        setItemViewCacheSize(8);
        setClipChildren(true);
        setClipToPadding(true);
        setPadding(LiquidTheme.dp(context, 72), 0, LiquidTheme.dp(context, 72), LiquidTheme.dp(context, 20));
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }
    private static final class HeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View header;
        HeaderAdapter(View header) { this.header = header; }
        @Override public int getItemCount() { return 1; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            header.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(header) { };
            holder.setIsRecyclable(false);
            return holder;
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { }
    }
}
