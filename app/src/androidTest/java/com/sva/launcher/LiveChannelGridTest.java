package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.*;
import java.util.*;

/** Remote navigation and bounded view creation with a provider-sized catalogue. */
public final class LiveChannelGridTest extends InstrumentationTestCase {
    private MainActivity activity;
    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void onMain(Runnable run) { getInstrumentation().runOnMainSync(run); }
    private void key(int key) {
        onMain(() -> {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        });
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(60);
    }
    private int focusedPosition(RecyclerView list) {
        int[] result = {-1};
        onMain(() -> {
            View item = list.findContainingItemView(activity.getCurrentFocus());
            if (item != null) result[0] = list.getChildAdapterPosition(item);
        });
        return result[0];
    }
    public void testLargeCategoryRecyclesAndHeaderScrollsWithChannels() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        activity = (MainActivity)TestActivities.start(getInstrumentation(), new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        SystemClock.sleep(600); getInstrumentation().waitForIdleSync();
        try {
            onMain(() -> {
                try {
                    List<IptvRepository.Channel> channels = (List<IptvRepository.Channel>)field(activity, "channels");
                    channels.clear();
                    for (int i = 0; i < 5000; i++) channels.add(new IptvRepository.Channel("Grid channel " + i,
                            "http://127.0.0.1:1/" + i + ".ts", "grid-" + i, i < 4992 ? "Large group" : "Small group",
                            "", "", "", Collections.emptyMap()));
                    ((Set<?>)field(activity, "hiddenChannelKeys")).clear();
                    ((Set<?>)field(activity, "favoriteChannelKeys")).clear();
                    Field tv = MainActivity.class.getDeclaredField("TAB_TV"); tv.setAccessible(true);
                    Method select = MainActivity.class.getDeclaredMethod("selectTab", int.class, boolean.class); select.setAccessible(true);
                    select.invoke(activity, tv.getInt(null), false);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            getInstrumentation().waitForIdleSync(); SystemClock.sleep(200);
            Object row = field(activity, "liveChannelGrid");
            RecyclerView list = (RecyclerView)field(row, "list");
            assertEquals(4993, list.getAdapter().getItemCount());
            assertTrue("Offscreen channels must be recycled", list.getChildCount() < 32);
            onMain(() -> {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(1);
                assertNotNull(holder); assertTrue(holder.itemView.requestFocus());
            });
            key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals(2, focusedPosition(list));
            key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(6, focusedPosition(list));
            key(KeyEvent.KEYCODE_DPAD_UP); assertEquals(2, focusedPosition(list));
            for (int i = 0; i < 12; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertEquals(50, focusedPosition(list));
            assertTrue(list.getChildCount() < 32);
            View header = (View)field(activity, "liveContent");
            android.graphics.Rect visible = new android.graphics.Rect();
            assertFalse("Header must scroll away", header.isAttachedToWindow() && header.getGlobalVisibleRect(visible));
            for (int i = 0; i < 12; i++) key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertTrue("Up from first row must return to a category", activity.getCurrentFocus() instanceof android.widget.Button);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            getInstrumentation().waitForIdleSync(); SystemClock.sleep(150);
            assertEquals(9, list.getAdapter().getItemCount());
            key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(1, focusedPosition(list));
        } finally { onMain(activity::finish); activity = null; }
    }
}
