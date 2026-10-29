package com.sva.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.List;

/** Immersive, manually paged news headlines. */
public final class NewsFeedActivity extends LocalizedActivity {
    private RecyclerView pages;
    private LinearLayoutManager layout;
    private List<NewsRepository.Story> stories;
    private int position;
    private ChannelLogoLoader artwork;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        artwork=new ChannelLogoLoader(this,1440);
        BackNavigation.install(this, this::finish);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        stories=NewsRepository.decode(getSharedPreferences("launcher",0).getString(NewsRepository.CACHE,"[]"));
        if(stories.isEmpty()){finish();return;}
        String link=state==null?getIntent().getStringExtra("url"):state.getString("url");
        for(int i=0;i<stories.size();i++)if(stories.get(i).link.equals(link))position=i;
        pages=new RecyclerView(this);layout=new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false);
        pages.setLayoutManager(layout);pages.setItemAnimator(null);new PagerSnapHelper().attachToRecyclerView(pages);
        pages.setAdapter(new RecyclerView.Adapter<Page>(){
            @Override public void onViewRecycled(Page page){page.photo.setTag(null);page.photo.setImageDrawable(null);}
            public int getItemCount(){return stories.size();}
            public Page onCreateViewHolder(ViewGroup parent,int type){return new Page();}
            public void onBindViewHolder(Page page,int index){
                NewsRepository.Story story=stories.get(index);
                page.title.setText(story.title);page.meta.setText(story.source+(story.publishedAt>0?" · "+android.text.format.DateFormat.format("dd MMM HH:mm",story.publishedAt):"")+"   ·   "+(index+1)+" / "+stories.size()+"    ‹ / ›");
                page.photo.setImageDrawable(null);artwork.load(story.image,page.photo,page.fallback);
                page.read.setOnClickListener(v->startActivity(new Intent(NewsFeedActivity.this,NewsReaderActivity.class).putExtra("url",story.link).putExtra("title",story.title)));
            }
        });
        pages.addOnScrollListener(new RecyclerView.OnScrollListener(){public void onScrollStateChanged(RecyclerView view,int state){if(state==RecyclerView.SCROLL_STATE_IDLE)position=Math.max(0,layout.findFirstVisibleItemPosition());}});
        setContentView(pages);pages.scrollToPosition(position);pages.post(()->pages.requestFocus());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode()==KeyEvent.KEYCODE_ENTER || event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && stories!=null && !stories.isEmpty()){
                NewsRepository.Story story=stories.get(position);
                startActivity(new Intent(this,NewsReaderActivity.class).putExtra("url",story.link).putExtra("title",story.title));
            }
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN && (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){
            position=Math.max(0,Math.min(stories.size()-1,position+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));pages.smoothScrollToPosition(position);return true;
        }return super.dispatchKeyEvent(event);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("url",stories.get(position).link);super.onSaveInstanceState(state);}
    @Override protected void onStop(){if(stories!=null && !stories.isEmpty())getSharedPreferences("launcher",0).edit().putString("newsSelectedStory",stories.get(position).link).apply();super.onStop();}
    @Override protected void onDestroy(){if(artwork!=null)artwork.close();super.onDestroy();}
    private final class Page extends RecyclerView.ViewHolder {
        final TextView title,meta;final Button read;final ImageView photo;final View fallback;
        Page(){
            super(new FrameLayout(NewsFeedActivity.this));FrameLayout root=(FrameLayout)itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-1));
            fallback=new View(NewsFeedActivity.this);fallback.setBackgroundColor(0xff101820);root.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            photo=new ImageView(NewsFeedActivity.this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(photo,new FrameLayout.LayoutParams(-1,-1));
            View shade=new View(NewsFeedActivity.this);shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22000000,0xee000000}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout copy=new LinearLayout(NewsFeedActivity.this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(64,32,64,42);
            meta=new TextView(NewsFeedActivity.this);meta.setTextColor(-1);meta.setTextSize(17);copy.addView(meta);
            title=new TextView(NewsFeedActivity.this);title.setTextColor(-1);title.setTextSize(32);title.setMaxLines(4);copy.addView(title);
            read=new Button(NewsFeedActivity.this);read.setText(getString(R.string.news_read_article));copy.addView(read,new LinearLayout.LayoutParams(-2,-2));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(copy,lp);
        }
    }
}
