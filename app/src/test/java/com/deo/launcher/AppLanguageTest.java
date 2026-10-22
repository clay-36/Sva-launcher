package com.sva.launcher;

import org.json.JSONObject;
import org.junit.Test;
import java.util.HashSet;
import java.util.Locale;
import static org.junit.Assert.*;

public final class AppLanguageTest {
    @Test public void supportedLanguagesAreUniqueAndUnknownSavedValuesFollowTv() {
        assertEquals(24, AppLanguage.TAGS.length); // 23 languages plus Follow TV
        HashSet<String> unique = new HashSet<>();
        for (String tag : AppLanguage.TAGS) {
            assertTrue(unique.add(tag));
            assertEquals(tag, AppLanguage.sanitize(tag));
        }
        assertEquals("", AppLanguage.sanitize(null));
        assertEquals("", AppLanguage.sanitize("xx"));
        assertEquals("", AppLanguage.sanitize("not-a-locale"));
    }
    @Test public void regionalLocalesResolveToSupportedScriptsAndLanguages() {
        assertEquals("zh-Hant", AppLanguage.supportedTag(Locale.forLanguageTag("zh-TW")));
        assertEquals("zh-Hant", AppLanguage.supportedTag(Locale.forLanguageTag("zh-HK")));
        assertEquals("zh-Hans", AppLanguage.supportedTag(Locale.forLanguageTag("zh-CN")));
        assertEquals("zh-Hant", AppLanguage.supportedTag(Locale.forLanguageTag("zh-Hant")));
        assertEquals("pt", AppLanguage.supportedTag(Locale.forLanguageTag("pt-BR")));
        assertEquals("hi", AppLanguage.supportedTag(Locale.forLanguageTag("hi-IN")));
        assertEquals("id", AppLanguage.supportedTag(new Locale("in", "ID")));
        assertEquals("id", AppLanguage.supportedTag(Locale.forLanguageTag("id-ID")));
        assertEquals("", AppLanguage.supportedTag(Locale.forLanguageTag("it-IT")));
    }
    @Test public void languagePreferenceHasAStringBackupContract() throws Exception {
        JSONObject values = new JSONObject().put(AppLanguage.KEY,
                new JSONObject().put("type", "string").put("value", "ta"));
        assertEquals("ta", LauncherBackup.decodePreferences(values).get(AppLanguage.KEY));
        values.put(AppLanguage.KEY, new JSONObject().put("type", "boolean").put("value", true));
        try { LauncherBackup.decodePreferences(values); fail("Wrong language preference type accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void automaticSelectionUsesSupportedTvPreferencesThenEnglish() {
        assertEquals("en", AppLanguage.firstSupported());
        assertEquals("en", AppLanguage.firstSupported(Locale.ITALIAN));
        assertEquals("ta", AppLanguage.firstSupported(Locale.ITALIAN, Locale.forLanguageTag("ta-IN"), Locale.ENGLISH));
        assertEquals("zh-Hant", AppLanguage.firstSupported(Locale.forLanguageTag("zh-HK"), Locale.ENGLISH));
    }
}
