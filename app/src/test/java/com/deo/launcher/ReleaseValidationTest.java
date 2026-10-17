package com.sva.launcher;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class ReleaseValidationTest {
    private static final String ID = String.join("", java.util.Collections.nCopies(64,"a"));

    @Test public void catalogueRejectsTraversalAndUnexpectedFilenames() throws Exception {
        JSONObject valid = new JSONObject().put("id",ID).put("title","Test").put("romFile",ID+".nes");
        assertNotNull(GameRecord.fromJson(valid));
        valid.put("romFile","../settings.xml"); assertNull(GameRecord.fromJson(valid));
        valid.put("romFile",ID+".nes").put("artworkFile","../other.img"); assertNull(GameRecord.fromJson(valid));
        valid.remove("artworkFile"); valid.put("id","../outside"); assertNull(GameRecord.fromJson(valid));
    }
    @Test public void boundedImportAcceptsTheLimitAndRejectsMore() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] exact = new byte[17]; Arrays.fill(exact,(byte)1);
        GameRepository.copyBounded(new ByteArrayInputStream(exact),out,17);
        assertArrayEquals(exact,out.toByteArray());
        try {
            GameRepository.copyBounded(new ByteArrayInputStream(new byte[18]),new ByteArrayOutputStream(),17);
            fail("Oversized import accepted");
        } catch(IllegalArgumentException expected) { }
    }
    @Test public void unrelatedJsonCannotReplaceLauncherSettings() throws Exception {
        JSONObject fake = new JSONObject().put("version",1).put("launcher",new JSONObject())
                .put("iptv",new JSONObject()).put("channels",new org.json.JSONArray());
        try { LauncherBackup.restore(fake,null,null,null); fail("Unrelated JSON accepted"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("not supported")); }
    }
    @Test public void backupTypesCannotMakeTheNextLaunchCrash() throws Exception {
        JSONObject preferences = new JSONObject().put("wallpaper",new JSONObject().put("type","boolean").put("value",true));
        try { LauncherBackup.decodePreferences(preferences); fail("Wrong preference type accepted"); }
        catch(IllegalArgumentException expected) { }
        preferences = new JSONObject().put("screensaverClock",new JSONObject().put("type","boolean").put("value",true))
                .put("scheduleWeatherMs",new JSONObject().put("type","long").put("value",3600000L));
        assertEquals(Boolean.TRUE,LauncherBackup.decodePreferences(preferences).get("screensaverClock"));
        assertEquals(3600000L,LauncherBackup.decodePreferences(preferences).get("scheduleWeatherMs"));
        preferences = new JSONObject().put("hideGamesNavigation",new JSONObject().put("type","boolean").put("value",true))
                .put("hideEventsNavigation",new JSONObject().put("type","boolean").put("value",false));
        assertEquals(Boolean.TRUE,LauncherBackup.decodePreferences(preferences).get("hideGamesNavigation"));
        assertEquals(Boolean.FALSE,LauncherBackup.decodePreferences(preferences).get("hideEventsNavigation"));
        preferences.put("hideEventsNavigation",new JSONObject().put("type","string").put("value","false"));
        try { LauncherBackup.decodePreferences(preferences); fail("Wrong navigation preference type accepted"); }
        catch(IllegalArgumentException expected) { }
        preferences = new JSONObject().put("navigationOrder",new JSONObject()
                .put("type","string").put("value","saved,events,live_tv,games"));
        assertEquals("saved,events,live_tv,games",LauncherBackup.decodePreferences(preferences).get("navigationOrder"));
        preferences.put("navigationOrder",new JSONObject().put("type","boolean").put("value",true));
        try { LauncherBackup.decodePreferences(preferences); fail("Wrong order preference type accepted"); }
        catch(IllegalArgumentException expected) { }
    }
    @Test public void compressedXmlGuidesHaveADecompressedSizeLimit() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try(java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) {
            byte[] block = new byte[8192];
            for(int i=0;i<16385;i++)gzip.write(block);
        }
        try(InputStream input = IptvRepository.guideStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] block = new byte[8192];
            try { while(input.read(block)!=-1) { } fail("Oversized decompressed guide accepted"); }
            catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("128 MB")); }
        }
    }
}
