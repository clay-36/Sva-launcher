package com.sva.launcher;

import android.content.*;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.*;

public final class ActivitiesUiTest extends InstrumentationTestCase {
    public void testPublishedChannelsArtworkAndCustomization() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        ContentResolver resolver=context.getContentResolver();
        SharedPreferences prefs=context.getSharedPreferences("launcher",0);
        String oldOrder=prefs.getString(ActivitiesPanel.ORDER,null), oldLanguage=AppLanguage.selected(context);
        Set<String> oldHidden=new HashSet<>(prefs.getStringSet(ActivitiesPanel.HIDDEN,Collections.emptySet()));
        List<Uri> fixtures=new ArrayList<>(); MainActivity activity=null;
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("com.android.providers.tv.permission.WRITE_EPG_DATA");
        try {
            for(int i=0;i<3;i++) {
                ContentValues c=new ContentValues();c.put("type",TvContract.Channels.TYPE_PREVIEW);c.put("display_name",i==2?"Empty channel":i==0?"Continue exploring":"Weekend picks");
                c.put("input_id","sva.activities.qa");c.put("searchable",1);c.put("internal_provider_id","activities-qa-"+i);
                Uri channel=resolver.insert(TvContract.Channels.CONTENT_URI,c);assertNotNull(channel);fixtures.add(channel);
                if (i == 2) continue;
                ContentValues p=new ContentValues();p.put("channel_id",ContentUris.parseId(channel));p.put("type",0);p.put("title",i==0?"Discover your next adventure":"Recommended for your evening");
                p.put("poster_art_uri","android.resource://com.sva.launcher/drawable/mountain_wallpaper");
                p.put("intent_uri",new Intent(context,MainActivity.class).toUri(Intent.URI_INTENT_SCHEME));p.put("searchable",1);
                assertNotNull(resolver.insert(TvContract.PreviewPrograms.CONTENT_URI,p));
            }
            List<ActivitiesRepository.Channel> loaded=ActivitiesRepository.load(resolver);
            for (ActivitiesRepository.Channel c : loaded) if (c.key.endsWith("activities-qa-2")) assertEquals("Fixture must have no programs", 0, c.programs.size());
            ActivitiesRepository.Channel first=null,second=null;
            for(ActivitiesRepository.Channel c:loaded)if(c.key.endsWith("activities-qa-0"))first=c;else if(c.key.endsWith("activities-qa-1"))second=c;
            assertNotNull(first);assertNotNull(second);assertEquals(1,first.programs.size());
            prefs.edit().remove(ActivitiesPanel.HIDDEN).putString(ActivitiesPanel.ORDER,second.key+"\n"+first.key).commit();
            getInstrumentation().runOnMainSync(()->AppLanguage.select(context,"en"));
            activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity current=activity;
            Method select=MainActivity.class.getDeclaredMethod("selectTab",int.class,boolean.class);select.setAccessible(true);
            getInstrumentation().runOnMainSync(()->{try{select.invoke(current,6,true);}catch(Exception e){throw new RuntimeException(e);}});
            Field panelField=MainActivity.class.getDeclaredField("activitiesPanel");panelField.setAccessible(true);
            ActivitiesPanel panel=(ActivitiesPanel)panelField.get(activity);assertNotNull(panel);
            Field rowsField=ActivitiesPanel.class.getDeclaredField("rows");rowsField.setAccessible(true);LinearLayout rows=(LinearLayout)rowsField.get(panel);
            long deadline=SystemClock.uptimeMillis()+10000;
            while(rows.getChildCount()<4 && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);
            getInstrumentation().waitForIdleSync(); Field statusField=ActivitiesPanel.class.getDeclaredField("status");statusField.setAccessible(true);assertTrue(((TextView)statusField.get(panel)).getText().toString(),rows.getChildCount()>=4);
            StringBuilder labels=new StringBuilder();for(int i=0;i<rows.getChildCount();i++)if(rows.getChildAt(i) instanceof TextView)labels.append(((TextView)rows.getChildAt(i)).getText()).append(";");
            assertFalse("Empty channels must not create rows: "+labels, labels.toString().contains("Empty channel"));
            assertTrue(((TextView)rows.getChildAt(0)).getText().toString().contains("Weekend picks"));
            RecyclerView cards=(RecyclerView)rows.getChildAt(1);
            deadline=SystemClock.uptimeMillis()+5000;
            while(cards.getChildCount()==0 && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);
            assertTrue(cards.getChildCount()>0);
            LinearLayout card=(LinearLayout)cards.getChildAt(0);ImageView image=(ImageView)card.getChildAt(0);
            deadline=SystemClock.uptimeMillis()+5000;
            while(image.getDrawable()==null && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);
            assertNotNull("Content artwork must render",image.getDrawable());
            getInstrumentation().runOnMainSync(()->card.requestFocus());
            SystemClock.sleep(300);
            Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"activities-qa.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
            ActivitiesRepository.Channel hidden=second;
            getInstrumentation().runOnMainSync(()->{
                prefs.edit().putStringSet(ActivitiesPanel.HIDDEN,new HashSet<>(Collections.singleton(hidden.key))).commit();
                try{Method render=ActivitiesPanel.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(panel);}catch(Exception e){throw new RuntimeException(e);}
            });
            assertFalse(((TextView)rows.getChildAt(0)).getText().toString().contains("Weekend picks"));
            assertTrue(prefs.getStringSet(ActivitiesPanel.HIDDEN,Collections.emptySet()).contains(second.key));
            ActivitiesRepository.Channel launchChannel=first;
            getInstrumentation().runOnMainSync(()->{
                try{Method open=ActivitiesPanel.class.getDeclaredMethod("open",ActivitiesRepository.Program.class,String.class);open.setAccessible(true);open.invoke(panel,launchChannel.programs.get(0),launchChannel.publisher);}catch(Exception e){throw new RuntimeException(e);}
            });
            SystemClock.sleep(400);getInstrumentation().waitForIdleSync();
            Field active=MainActivity.class.getDeclaredField("activeTab");active.setAccessible(true);
            assertEquals("Recommendation must launch its publisher destination",0,active.getInt(activity));
        } finally {
            MainActivity current=activity;if(current!=null)getInstrumentation().runOnMainSync(current::finish);
            for(Uri uri:fixtures)resolver.delete(uri,null,null);
            SharedPreferences.Editor editor=prefs.edit().putStringSet(ActivitiesPanel.HIDDEN,oldHidden);
            if(oldOrder==null)editor.remove(ActivitiesPanel.ORDER);else editor.putString(ActivitiesPanel.ORDER,oldOrder);editor.commit();
            getInstrumentation().runOnMainSync(()->AppLanguage.select(context,oldLanguage));
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
