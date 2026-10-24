package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Only visible game cards own views and artwork requests. Load snapshots on a worker. */
final class GameCardAdapter extends RecyclerView.Adapter<GameCardAdapter.Holder> {
    static final class Item {
        final GameRecord game;
        final long artworkVersion;
        final File artwork;
        Item(GameRecord source, File artwork) {
            game = new GameRecord(source.id, source.title, source.romFile, source.artworkFile,
                    source.importedAt, source.lastPlayedAt);
            this.artwork = artwork;
            this.artworkVersion = artwork.lastModified();
        }
    }
    static List<Item> load(Context context) {
        GameRepository repository = new GameRepository(context);
        List<Item> result = new ArrayList<>();
        for (GameRecord game : repository.games())
            result.add(new Item(game, repository.artworkFile(game)));
        return result;
    }
    private final Activity activity;
    private final Consumer<GameRecord> click;
    private final int height, left, top, right, bottom;
    private List<Item> items = new ArrayList<>();

    GameCardAdapter(Activity activity, int height, int left, int top, int right, int bottom,
                    Consumer<GameRecord> click) {
        this.activity = activity; this.height = height;
        this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.click = click;
    }
    void submit(List<Item> next) {
        List<Item> previous = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return previous.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int a, int b) { return previous.get(a).game.id.equals(next.get(b).game.id); }
            public boolean areContentsTheSame(int a, int b) {
                Item old = previous.get(a), item = next.get(b);
                return old.game.title.equals(item.game.title)
                        && Objects.equals(old.game.artworkFile, item.game.artworkFile)
                        && old.artworkVersion == item.artworkVersion;
            }
        });
        items = new ArrayList<>(next);
        diff.dispatchUpdatesTo(this);
    }
    int position(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).game.id.equals(id)) return i;
        return 0;
    }
    @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
        FrameLayout host = new FrameLayout(activity);
        host.setClipChildren(false); host.setClipToPadding(false);
        host.setLayoutParams(new RecyclerView.LayoutParams(-1, height + top + bottom));
        host.setPadding(left, top, right, bottom);
        return new Holder(host);
    }
    @Override public void onBindViewHolder(Holder holder, int position) {
        Item item = items.get(position);
        GameRecord game = item.game;
        if (holder.host.getChildCount() == 0) {
            LinearLayout card = GameUi.card(activity, game, view -> click.accept(game), item.artwork, item.artworkVersion);
            holder.host.addView(card, new FrameLayout.LayoutParams(-1, height));
        } else {
            GameUi.bindCard(activity, (LinearLayout)holder.host.getChildAt(0), game,
                    view -> click.accept(game), item.artwork, item.artworkVersion);
        }
    }
    @Override public void onViewRecycled(Holder holder) {
        if (holder.host.getChildCount() > 0) {
            android.widget.ImageView image = (android.widget.ImageView)((LinearLayout)holder.host.getChildAt(0)).getChildAt(0);
            image.setTag(null); image.setImageDrawable(null);
        }
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout host;
        Holder(FrameLayout host) { super(host); this.host = host; }
    }
}
