package com.sva.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

@UnstableApi
public final class GuideUiTest extends InstrumentationTestCase {
    private GuideActivity activity;
    private Context context;
    private long now;
    private Object field(String name) throws Exception { Field f = GuideActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private Object targetField(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    @Override protected void setUp() throws Exception {
        super.setUp(); context = getInstrumentation().getTargetContext(); now = System.currentTimeMillis();
        context.getSharedPreferences("launcher", 0).edit().clear().commit();
    }
    @Override protected void tearDown() throws Exception {
        if (activity != null) getInstrumentation().runOnMainSync(activity::finish);
        super.tearDown();
    }
    private void seed(int count) throws Exception {
        JSONArray data = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONArray programmes = new JSONArray();
            int shows = count > 100 ? 8 : 96;
            long duration = count > 100 ? 6 * 60 * 60_000L : GuideTimeline.HALF_HOUR;
            if (i != 3) for (int j = 0; j < shows; j++) programmes.put(new JSONObject()
                    .put("title", (j == 0 ? "Morning headlines and live coverage around the world" : "Programme " + j))
                    .put("startMs", now - 10 * 60_000 + j * duration).put("endMs", now - 10 * 60_000 + (j + 1) * duration));
            data.put(new JSONObject().put("name", "Channel " + i + " International HD").put("url", "http://127.0.0.1:1/" + i + ".mpd")
                    .put("id", "guide-test-" + i).put("group", i < 4 ? "News" : "Sports").put("programmes", programmes));
        }
        new IptvRepository(context).restoreCachedChannels(data);
    }
    private void open(String category) throws Exception {
        activity = (GuideActivity)getInstrumentation().startActivitySync(new Intent(context, GuideActivity.class)
                .putExtra("category", category).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitReady();
    }
    private void waitReady() throws Exception {
        long end = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < end) {
            getInstrumentation().waitForIdleSync();
            if ((Boolean)field("loaded") && activity.getCurrentFocus() != null && activity.getCurrentFocus().getTag() != null) return;
            SystemClock.sleep(30);
        }
        fail("Guide did not load/focus a programme");
    }
    private void key(int code) throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        });
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(60); getInstrumentation().waitForIdleSync();
    }
    private void invoke(String method) throws Exception {
        getInstrumentation().runOnMainSync(() -> { try { Method m=GuideActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(activity); } catch(Exception e) {throw new AssertionError(e);} });
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(60);
    }
    public void testRapidGuidePanAndChannelMovementKeepLatestSelection() throws Exception {
        seed(12);open(null);
        getInstrumentation().runOnMainSync(() -> {
            for(int i=0;i<24;i++) {
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
            }
        });
        SystemClock.sleep(Motion.SCREEN+250);getInstrumentation().waitForIdleSync();
        assertNull(field("timelinePan"));
        assertEquals(((Long)field("windowStart")).doubleValue(),((Double)field("renderedWindowStart")).doubleValue(),1);
        Object target=activity.getCurrentFocus().getTag();assertNotNull(target);
        GuideTimeline.Slot slot=(GuideTimeline.Slot)targetField(target,"slot");long anchor=(Long)field("anchor");
        assertTrue(slot.start<=anchor && slot.end>anchor);
        assertEquals(slot.programme.title,((TextView)field("title")).getText().toString());
        getInstrumentation().runOnMainSync(() -> {
            for(int i=0;i<10;i++) {
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));
            }
        });
        SystemClock.sleep(Motion.SCROLL+350);getInstrumentation().waitForIdleSync();
        assertEquals(10,field("selectedRow"));
        assertEquals(10,targetField(activity.getCurrentFocus().getTag(),"row"));
    }
    public void testPanningUpdatesRowsReattachedFromCache() throws Exception {
        seed(12); open(null);
        for (int i = 0; i < 5; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int i = 0; i < 5; i++) key(KeyEvent.KEYCODE_DPAD_UP);
        long initial = (Long)field("windowStart");
        for (int i = 0; i < 16; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue((Long)field("windowStart") > initial);
        for (int i = 0; i < 5; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        SystemClock.sleep(Motion.SCROLL + 250); getInstrumentation().waitForIdleSync();
        assertEquals(5, field("selectedRow"));
        Object target = activity.getCurrentFocus().getTag();
        assertNotNull(target); assertEquals(5, targetField(target, "row"));
        GuideTimeline.Slot slot = (GuideTimeline.Slot)targetField(target, "slot");
        long anchor = (Long)field("anchor");
        assertTrue("Reattached row must contain the selected programme", slot.start <= anchor && slot.end > anchor);
        assertEquals(slot.programme.title, ((TextView)field("title")).getText().toString());
    }
    public void testScheduleCellsCannotPaintOverFixedChannelColumn() throws Exception {
        seed(8); open(null);
        getInstrumentation().runOnMainSync(() -> {
            try {
                RecyclerView list = (RecyclerView)field("list");
                Object row = list.findViewHolderForAdapterPosition(0);
                View rowView = ((RecyclerView.ViewHolder)row).itemView;
                FrameLayout cells = (FrameLayout)targetField(row, "cells");
                View tile = cells.getChildAt(0);
                tile.setStateListAnimator(null); tile.setScaleX(1); tile.setScaleY(1);
                tile.setTranslationX(-GameUi.dp(activity, 30));
                tile.setForeground(new android.graphics.drawable.ColorDrawable(android.graphics.Color.MAGENTA));
                Bitmap capture = Bitmap.createBitmap(rowView.getWidth(), rowView.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    rowView.draw(new android.graphics.Canvas(capture));
                    int y = rowView.getHeight() / 2;
                    assertEquals("Fixture must paint inside the timeline", android.graphics.Color.MAGENTA,
                            capture.getPixel(cells.getLeft() + 3, y));
                    assertTrue("Offscreen schedule cells must not cover the fixed channel column",
                            capture.getPixel(cells.getLeft() - GameUi.dp(activity, 10), y) != android.graphics.Color.MAGENTA);
                } finally { capture.recycle(); }
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    public void testRemoteTimelineFilteringAndUnavailableSchedule() throws Exception {
        seed(12);
        context.getSharedPreferences("launcher", 0).edit().putStringSet("hiddenChannels", Collections.singleton("id:guide-test-2")).commit();
        open("News");
        assertEquals(3, ((List<?>)field("channels")).size());
        long anchor = (Long)field("anchor"); key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1, field("selectedRow")); assertEquals(anchor, ((Long)field("anchor")).longValue());
        key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(2, field("selectedRow"));
        assertEquals("No programme information", ((TextView)field("title")).getText().toString());
        key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP);
        long initialWindow=(Long)field("windowStart");
        for(int i=0;i<10;i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue((Long)field("windowStart")>initialWindow);
        invoke("jumpNow"); assertEquals(field("origin"),field("windowStart"));
        key(KeyEvent.KEYCODE_DPAD_UP); assertSame(field("categoryButton"),activity.getCurrentFocus());
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertSame(field("nowButton"),activity.getCurrentFocus());
        key(KeyEvent.KEYCODE_DPAD_DOWN); assertNotNull(activity.getCurrentFocus().getTag());
    }
    public void testReminderAndSafePlaybackFromGuide() throws Exception {
        seed(8); open("News"); key(KeyEvent.KEYCODE_DPAD_RIGHT);
        Object target=activity.getCurrentFocus().getTag(); Object slot=targetField(target,"slot");
        IptvRepository.Programme programme=(IptvRepository.Programme)targetField(slot,"programme");
        IptvRepository.Channel channel=((List<IptvRepository.Channel>)field("channels")).get(0);
        GuideReminders.Reminder reminder=new GuideReminders.Reminder(channel.stableKey(),channel.streamUrl,channel.name,programme.title,programme.startMs,programme.endMs);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog dialog=(AlertDialog)field("actionDialog"); assertNotNull(dialog);
        getInstrumentation().runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        assertTrue(((SvaApplication)activity.getApplication()).guideReminders().has(reminder));
        invoke("updateDetails");
        assertTrue(((TextView)field("actionHint")).getText().toString().contains("Reminder set"));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog remove=(AlertDialog)field("actionDialog");
        assertEquals("Remove reminder",remove.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        getInstrumentation().runOnMainSync(() -> remove.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        assertFalse(((SvaApplication)activity.getApplication()).guideReminders().has(reminder));
        Intent launch=PlaybackLauncher.intent(activity,channel,(List<IptvRepository.Channel>)field("all"));
        Parcel parcel=Parcel.obtain(); launch.writeToParcel(parcel,0); assertTrue(parcel.dataSize()<2048);parcel.recycle();
        assertTrue(launch.hasExtra(PlaybackSession.EXTRA)); assertFalse(launch.hasExtra("zapChannels"));
        Intent restored=PlaybackSession.restore(context,launch);
        assertEquals(channel.name,restored.getStringExtra("name"));
        assertEquals(8,new JSONArray(restored.getStringExtra("zapChannels")).length());
        long savedAnchor=(Long)field("anchor"),savedWindow=(Long)field("windowStart");
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(PlaybackActivity.class.getName(),null,false);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog watch=(AlertDialog)field("actionDialog");
        getInstrumentation().runOnMainSync(() -> watch.getButton(AlertDialog.BUTTON_NEUTRAL).performClick());
        Activity player=monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);
        assertNotNull(player);assertTrue(player.getIntent().hasExtra(PlaybackSession.EXTRA));
        getInstrumentation().runOnMainSync(player::finish);getInstrumentation().waitForIdleSync();SystemClock.sleep(300);waitReady();
        assertEquals(savedAnchor,((Long)field("anchor")).longValue());assertEquals(savedWindow,((Long)field("windowStart")).longValue());

    }
    public void testLargePlaylistKeepsOnlyVisibleCellsAndRestoresState() throws Exception {
        seed(5000);open(null);
        RecyclerView list=(RecyclerView)field("list");assertEquals(5000,list.getAdapter().getItemCount());
        assertTrue(list.getChildCount()<15);
        int cells=0;
        for(int i=0;i<list.getChildCount();i++) {
            Object row=list.getChildViewHolder(list.getChildAt(i)); FrameLayout strip=(FrameLayout)targetField(row,"cells");
            cells+=strip.getChildCount();
        }
        assertTrue("Should not inflate complete schedules",cells<100);
        key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);
        Bundle state=new Bundle();getInstrumentation().runOnMainSync(() -> activity.onSaveInstanceState(state));
        assertEquals(field("windowStart"),state.getLong("window"));assertEquals(field("anchor"),state.getLong("anchor"));
        assertNotNull(state.getString("channel"));assertFalse(state.containsKey("channels"));
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(GuideActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(activity::recreate);
        Activity recreated=monitor.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(monitor);
        assertNotNull(recreated);activity=(GuideActivity)recreated;waitReady();
        assertEquals(state.getString("channel"),field("selectedKey"));
        assertEquals(state.getLong("window"),((Long)field("windowStart")).longValue());
        assertEquals(state.getLong("anchor"),((Long)field("anchor")).longValue());

    }
    public void testLiveTvEntryReturnsFocusAndEmptyGuideRemainsNavigable() throws Exception {
        seed(8);
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(GuideActivity.class.getName(),null,false);
        try {
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Field tab=MainActivity.class.getDeclaredField("TAB_TV");tab.setAccessible(true);
                    Method select=MainActivity.class.getDeclaredMethod("selectTab",int.class,boolean.class);select.setAccessible(true);select.invoke(main,tab.getInt(null),false);
                    Field button=MainActivity.class.getDeclaredField("guideToggle");button.setAccessible(true);((Button)button.get(main)).performClick();
                }catch(Exception e){throw new AssertionError(e);}
            });
            activity=(GuideActivity)monitor.waitForActivityWithTimeout(5000);assertNotNull(activity);waitReady();
            getInstrumentation().runOnMainSync(activity::finish);activity=null;getInstrumentation().waitForIdleSync();SystemClock.sleep(100);
            Field button=MainActivity.class.getDeclaredField("guideToggle");button.setAccessible(true);
            assertSame(button.get(main),main.getCurrentFocus());
        }finally{getInstrumentation().removeMonitor(monitor);getInstrumentation().runOnMainSync(main::finish);}
        new IptvRepository(context).restoreCachedChannels(new JSONArray());
        activity=(GuideActivity)getInstrumentation().startActivitySync(new Intent(context,GuideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline=SystemClock.uptimeMillis()+5000;
        while(!(Boolean)field("loaded") && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(30);
        getInstrumentation().waitForIdleSync();
        assertEquals("Your guide is ready when you are",((TextView)field("title")).getText().toString());
        assertSame(field("categoryButton"),activity.getCurrentFocus());key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertSame(field("categoryButton"),activity.getCurrentFocus());
    }
    /** Render the actual view hierarchy at 1080p; the TV emulator's physical mode is fixed at 720p. */
    public void test1080pLayoutRendering() throws Exception {
        seed(12);
        for(String mode:new String[]{LiquidTheme.DARK,LiquidTheme.LIGHT}) {
            context.getSharedPreferences("launcher",0).edit().putString(LiquidTheme.PREF_MODE,mode).commit();open(null);
            assertEquals("Theme must follow restored preferences", LiquidTheme.LIGHT.equals(mode), LiquidTheme.isLight(context));
            final Bitmap[] capture=new Bitmap[1];
            getInstrumentation().runOnMainSync(() -> {
                View root=activity.getWindow().getDecorView();
                root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));
                root.layout(0,0,1920,1080);
                assertEquals(1920,root.getWidth());assertEquals(1080,root.getHeight());
                try { assertTrue(((RecyclerView)field("list")).getHeight()>400); } catch(Exception e) {throw new AssertionError(e);}
                capture[0]=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(capture[0]));
            });
            try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"guide-"+mode+"-1080.png"))) {capture[0].compress(Bitmap.CompressFormat.PNG,100,output);}capture[0].recycle();
            getInstrumentation().runOnMainSync(activity::finish);getInstrumentation().waitForIdleSync();activity=null;
        }
    }
    public void testVisualLayoutsAndClockFormats() throws Exception {
        seed(12);
        for(String mode:new String[]{LiquidTheme.DARK,LiquidTheme.LIGHT}) {
            context.getSharedPreferences("launcher",0).edit().putString(LiquidTheme.PREF_MODE,mode).putBoolean("24h",mode.equals(LiquidTheme.LIGHT)).commit();
            open("News");
            assertEquals("Theme must follow restored preferences", LiquidTheme.LIGHT.equals(mode), LiquidTheme.isLight(context));
            getInstrumentation().runOnMainSync(() -> {
                TextView tile = (TextView)activity.getCurrentFocus();
                assertTrue("Guide text must fit vertically instead of clipping the final line",
                        tile.getLayout().getHeight() <= tile.getHeight() - tile.getPaddingTop() - tile.getPaddingBottom());
            });
            View root=activity.getWindow().getDecorView();
            RecyclerView list=(RecyclerView)field("list");
            assertTrue(list.getHeight()>200);assertTrue(list.getBottom()<=root.getHeight());
            Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();
            try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"guide-"+mode+".png"))) {image.compress(Bitmap.CompressFormat.PNG,100,output);}image.recycle();
            getInstrumentation().runOnMainSync(activity::finish);getInstrumentation().waitForIdleSync();activity=null;
        }
    }
}
