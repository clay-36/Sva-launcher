package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.app.ActivityManager;
import java.security.MessageDigest;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/** Recommendation rows and local visibility/order controls for the Activities tab. */
final class ActivitiesPanel extends LinearLayout {
    static final String ORDER = "activitiesChannelOrder", HIDDEN = "activitiesHiddenChannels";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final LinearLayout rows;
    private final TextView status;
    final Button refreshButton;
    private final ExecutorService queries = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor images = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File artworkCache;
    private static final long MAX_ARTWORK_CACHE_BYTES = 32L * 1024 * 1024;
    private final LruCache<String, Bitmap> artworkMemory;
    private List<ActivitiesRepository.Channel> channels = new ArrayList<>();
    private boolean closed;
    private int generation;

    ActivitiesPanel(Activity activity) {
        super(activity); this.activity = activity; prefs = activity.getSharedPreferences("launcher", 0);
        setOrientation(VERTICAL);
        artworkCache = new File(activity.getCacheDir(), "activities-artwork"); artworkCache.mkdirs();
        trimArtworkCache();
        ActivityManager am = (ActivityManager)activity.getSystemService(Activity.ACTIVITY_SERVICE);
        int cacheKb=Math.max(2048,Math.min(12*1024,((am==null?128:am.getMemoryClass())*1024)/12));
        artworkMemory=new LruCache<String,Bitmap>(cacheKb){@Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}};
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setMinimumHeight(dp(72));
        TextView heading = SettingsUi.text(activity, activity.getString(R.string.ui_activities), 42, false);
        heading.setTypeface(LiquidTheme.font(activity));
        heading.setMaxLines(1);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        refreshButton = SettingsUi.action(activity, activity.getString(R.string.activities_refresh), this::refresh);
        toolbar.addView(refreshButton);
        LinearLayout.LayoutParams customizeParams = new LinearLayout.LayoutParams(-2, -2);
        customizeParams.setMarginStart(dp(10));
        toolbar.addView(SettingsUi.action(activity, activity.getString(R.string.activities_customize), this::customize), customizeParams);
        LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(-1, -2);
        toolbarParams.topMargin = dp(4);
        addView(toolbar, toolbarParams);
        status = SettingsUi.text(activity, "", 15, true); status.setPadding(0, dp(12), 0, dp(12)); addView(status);
        rows = new LinearLayout(activity); rows.setOrientation(VERTICAL); addView(rows);
    }

    private int dp(int value) { return SettingsUi.dp(activity, value); }
    void refresh() {
        if (closed) return;
        if (activity.checkSelfPermission(ActivitiesRepository.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            status.setText(activity.getString(R.string.activities_allow));
            activity.requestPermissions(new String[]{ActivitiesRepository.PERMISSION}, ActivitiesRepository.REQUEST_PERMISSION);
            return;
        }
        final int request = ++generation;
        status.setText(activity.getString(R.string.activities_loading)); refreshButton.setEnabled(false);
        queries.execute(() -> {
            try {
                List<ActivitiesRepository.Channel> loaded = ActivitiesRepository.load(activity.getContentResolver());
                main.post(() -> {
                    if (closed || request != generation) return;
                    channels = loaded; refreshButton.setEnabled(true); render();
                });
            } catch (RuntimeException e) {
                android.util.Log.w("SvaActivities", "Cannot load shared recommendation channels", e);
                main.post(() -> { if (!closed && request == generation) {
                    refreshButton.setEnabled(true); status.setText(activity.getString(R.string.activities_error));
                }});
            }
        });
    }

    private List<ActivitiesRepository.Channel> ordered() {
        List<String> order = Arrays.asList(prefs.getString(ORDER, "").split("\\n"));
        List<ActivitiesRepository.Channel> result = new ArrayList<>(channels);
        result.sort(Comparator.comparingInt(c -> { int index = order.indexOf(c.key); return index < 0 ? Integer.MAX_VALUE : index; }));
        return result;
    }
    private void render() {
        rows.removeAllViews();
        Set<String> hidden = prefs.getStringSet(HIDDEN, Collections.emptySet());
        int shown = 0;
        boolean hasVisibleChannels = false;
        for (ActivitiesRepository.Channel channel : ordered()) {
            if (hidden.contains(channel.key)) continue;
            hasVisibleChannels = true;
            if (channel.programs.isEmpty()) continue;
            shown++;
            String app = channel.publisher;
            try { app = activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(channel.publisher, 0)).toString(); } catch (Exception ignored) { }
            TextView heading = SettingsUi.text(activity, app + " · " + channel.title, 20, false);
            heading.setPadding(0, dp(18), 0, dp(10)); rows.addView(heading);
            RecyclerView list = new RecyclerView(activity);
            list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
            list.setNestedScrollingEnabled(false); list.setItemAnimator(null);
            list.setClipChildren(false); list.setClipToPadding(false);
            list.setPadding(dp(10), dp(10), dp(10), dp(10));
            list.setOverScrollMode(View.OVER_SCROLL_NEVER);
            list.setAdapter(new Cards(channel));
            rows.addView(list, new LinearLayout.LayoutParams(-1, dp(192)));
        }
        status.setText(channels.isEmpty() ? activity.getString(R.string.activities_empty)
                : shown == 0 ? activity.getString(hasVisibleChannels ? R.string.activities_no_items : R.string.activities_hidden)
                : activity.getString(R.string.activities_description));
    }
    private void customize() {
        if (channels.isEmpty()) { Toast.makeText(activity, activity.getString(R.string.activities_refresh_first), Toast.LENGTH_SHORT).show(); return; }
        List<ActivitiesRepository.Channel> order = ordered();
        String[] titles = new String[order.size()]; boolean[] checked = new boolean[order.size()];
        Set<String> hidden = new HashSet<>(prefs.getStringSet(HIDDEN, Collections.emptySet()));
        for (int i = 0; i < order.size(); i++) { titles[i] = order.get(i).title + " · " + order.get(i).publisher; checked[i] = !hidden.contains(order.get(i).key); }
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.activities_visible))
                .setMultiChoiceItems(titles, checked, (dialog, which, value) -> { if (value) hidden.remove(order.get(which).key); else hidden.add(order.get(which).key); })
                .setPositiveButton(activity.getString(R.string.activities_save), (d, w) -> { prefs.edit().putStringSet(HIDDEN, hidden).apply(); render(); })
                .setNeutralButton(activity.getString(R.string.activities_reorder), (d, w) -> reorder())
                .setNegativeButton(activity.getString(R.string.activities_cancel), null).show();
    }
    private void reorder() {
        List<ActivitiesRepository.Channel> order = ordered();
        String[] titles = new String[order.size()]; for (int i=0;i<order.size();i++) titles[i]=order.get(i).title;
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.activities_move)).setItems(titles, (d, index) -> {
            String[] positions = new String[order.size()]; for(int i=0;i<positions.length;i++)positions[i]=activity.getString(R.string.activities_position, i+1);
            new AlertDialog.Builder(activity).setTitle(order.get(index).title).setItems(positions, (dialog, position) -> {
                ActivitiesRepository.Channel selected=order.remove(index); order.add(position,selected);
                List<String> keys=new ArrayList<>();for(ActivitiesRepository.Channel c:order)keys.add(c.key);
                prefs.edit().putString(ORDER,String.join("\n",keys)).apply(); render();
            }).show();
        }).show();
    }
    private void open(ActivitiesRepository.Program program, String publisher) {
        try {
            Intent intent = Intent.parseUri(program.intent, Intent.URI_INTENT_SCHEME);
            intent.setSelector(null);
            if (intent.getComponent() != null && !publisher.equals(intent.getComponent().getPackageName())) throw new SecurityException("Publisher mismatch");
            intent.setPackage(publisher);
            intent.addCategory(Intent.CATEGORY_BROWSABLE); intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) { Toast.makeText(activity, activity.getString(R.string.activities_unavailable), Toast.LENGTH_SHORT).show(); }
    }
    private final class Cards extends RecyclerView.Adapter<Card> {
        private final ActivitiesRepository.Channel channel;
        Cards(ActivitiesRepository.Channel channel) { this.channel = channel; }
        @Override public int getItemCount() { return channel.programs.size(); }
        @Override public Card onCreateViewHolder(android.view.ViewGroup parent, int type) { return new Card(); }
        @Override public void onBindViewHolder(Card holder, int position) {
            ActivitiesRepository.Program program=channel.programs.get(position);
            holder.title.setText(program.title == null ? channel.title : program.title);
            holder.image.animate().cancel();
            holder.image.setAlpha(program.artwork == null ? 1f : 0f);
            holder.image.setImageDrawable(null); holder.image.setTag(program.artwork);
            holder.itemView.setOnClickListener(v -> open(program, channel.publisher));
            if (program.artwork != null) images.execute(() -> {
                Bitmap bitmap=artwork(program.artwork);
                main.post(() -> {
                    if(closed || !program.artwork.equals(holder.image.getTag())) return;
                    if(bitmap==null) { holder.image.setAlpha(1f); return; }
                    holder.image.setImageBitmap(bitmap);
                    holder.image.animate().alpha(1f).setDuration(Motion.enabled() ? Motion.FADE : 0)
                            .setInterpolator(Motion.EASING).start();
                });
            });
        }
    }
    private final class Card extends RecyclerView.ViewHolder {
        final ImageView image; final TextView title;
        Card() {
            super(new LinearLayout(activity)); LinearLayout box=(LinearLayout)itemView;
            box.setOrientation(VERTICAL);box.setPadding(dp(8),dp(8),dp(8),dp(8));box.setFocusable(true);box.setClickable(true);
            box.setFocusableInTouchMode(false);
            box.setBackground(SettingsUi.states(activity,false));
            box.setElevation(dp(2));
            box.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
            Motion.interaction(box,true);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(dp(240),dp(170));lp.rightMargin=dp(12);box.setLayoutParams(lp);
            image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(image,new LinearLayout.LayoutParams(-1,dp(110)));
            title=SettingsUi.text(activity,"",15,false);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(title);
        }
    }
    private Bitmap artwork(String address) {
        HttpURLConnection connection=null;
        try {
            Bitmap memory=artworkMemory.get(address);if(memory!=null)return memory;
            File cache=diskFile(address);Bitmap stored=decodeArtwork(cache);if(stored!=null){cache.setLastModified(System.currentTimeMillis());trimArtworkCache();artworkMemory.put(address,stored);return stored;}
            Uri uri=Uri.parse(address);
            if("content".equals(uri.getScheme())||"android.resource".equals(uri.getScheme())){
                try(InputStream input=activity.getContentResolver().openInputStream(uri)){
                    return input==null?null:cacheArtwork(address,cache,input,false);
                }
            }
            if(!"https".equals(uri.getScheme()))return null;
            connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            try(InputStream input=connection.getInputStream()){
                return cacheArtwork(address,cache,input,true);
            }
        }catch(Exception ignored){return null;}finally{
            if(connection!=null)connection.disconnect();
        }
    }
    private Bitmap cacheArtwork(String address,File cache,InputStream input,boolean keepOnDisk)throws IOException{
        File temporary=null;
        try{
            temporary=File.createTempFile("art-", ".tmp", artworkCache);
            try(FileOutputStream out=new FileOutputStream(temporary)){
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted()||(total+=count)>2*1024*1024)return null;
                    out.write(buffer,0,count);
                }
            }
            Bitmap bitmap=decodeArtwork(temporary);
            if(bitmap!=null){
                if(keepOnDisk&&temporary.renameTo(cache)){temporary=null;trimArtworkCache();}
                artworkMemory.put(address,bitmap);
            }
            return bitmap;
        }finally{if(temporary!=null)temporary.delete();}
    }
    private Bitmap decodeArtwork(File file){
        if(!file.isFile())return null;BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        if(options.outWidth<=0||options.outHeight<=0)return null;
        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>360)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;return BitmapFactory.decodeFile(file.getAbsolutePath(),options);
    }
    private File diskFile(String address)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(Locale.ROOT,"%02x",b));return new File(artworkCache,key+".img");}
    private synchronized void trimArtworkCache(){
        File[] files=artworkCache.listFiles(f->f.isFile()&&f.getName().endsWith(".img"));
        if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        long total=0;for(File file:files)total+=file.length();
        for(File file:files){if(total<=MAX_ARTWORK_CACHE_BYTES)break;long length=file.length();if(file.delete())total-=length;}
    }
    void close() { closed=true;generation++;queries.shutdownNow();images.shutdownNow();main.removeCallbacksAndMessages(null);artworkMemory.evictAll(); }
}
