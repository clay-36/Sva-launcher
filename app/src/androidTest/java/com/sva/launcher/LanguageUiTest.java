package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real Resources/Activity checks: no network or translated content fixtures are required. */
public final class LanguageUiTest extends InstrumentationTestCase {
    public void testEveryLanguageFormatsEveryResourceAndPlural() throws Exception {
        Context base = getInstrumentation().getTargetContext();
        Pattern argument = Pattern.compile("%(\\d+)\\$[sd]");
        for (String tag : AppLanguage.TAGS) {
            if (tag.isEmpty()) continue;
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocales(LocaleList.forLanguageTags(tag));
            Context localized = base.createConfigurationContext(config);
            for (Field field : R.string.class.getFields()) {
                int id = field.getInt(null);
                String template = localized.getString(id);
                if (field.getName().startsWith("ui_"))
                    assertFalse(tag + ": empty label " + field.getName(), template.trim().isEmpty());
                assertFalse(tag + ": unresolved token in " + field.getName(), template.contains("__SVA"));
                Matcher matcher = argument.matcher(template);
                int count = 0;
                while (matcher.find()) count = Math.max(count, Integer.parseInt(matcher.group(1)));
                if (count > 0) {
                    Object[] values = new Object[count];
                    java.util.Arrays.fill(values, 3);
                    assertFalse(localized.getString(id, values).isEmpty());
                }
            }
            for (Field field : R.plurals.class.getFields()) {
                for (int count : new int[]{0, 1, 2, 3, 5, 11, 21, 101}) {
                    assertFalse(localized.getResources().getQuantityString(field.getInt(null), count, count).isEmpty());
                }
            }
            String language = localized.getString(R.string.ui_language);
            if (!"en".equals(tag)) assertFalse(tag + " has no translation", "Language".equals(language));
        }
    }

    public void testSelectionPersistsAndScreensHaveCorrectDirection() throws Exception {
        Context base = getInstrumentation().getTargetContext();
        String previous = AppLanguage.selected(base);
        try {
            for (String tag : new String[]{"en", "de", "hi", "ar", "ur", "zh-Hans", "zh-Hant", "id"}) {
                select(base, tag);
                MainActivity home = start(MainActivity.class);
                try {
                    assertEquals(tag, AppLanguage.selected(base));
                    assertEquals(tag, base.getSharedPreferences("launcher", 0).getString(AppLanguage.KEY, ""));
                    int expected = "ar".equals(tag) || "ur".equals(tag) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
                    assertEquals(expected, home.getResources().getConfiguration().getLayoutDirection());
                    assertEquals(expected, ((View)field(home, "root")).getLayoutDirection());
                    assertEquals(AppLanguage.get(R.string.ui_home), ((String[])field(home, "TAB_TITLES"))[0]);
                    screenshot(tag + "-home");
                    getInstrumentation().runOnMainSync(() -> {
                        try { method(MainActivity.class, "showSettings").invoke(home); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                    Object settings = field(home, "settingsScreen");
                    getInstrumentation().runOnMainSync(() -> {
                        try {
                            Field section = settings.getClass().getDeclaredField("section"); section.setAccessible(true);
                            section.setInt(settings, 14);
                            method(settings.getClass(), "renderSection").invoke(settings);
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    screenshot(tag + "-language");
                    assertNotNull(field(settings, "firstControl"));
                    assertTrue(((View)field(settings, "firstControl")).isFocusable());
                } finally { finish(home); }
                // Independent entry points must not rely on MainActivity initializing their locale.
                for (Class<? extends Activity> screen : java.util.Arrays.asList(GuideActivity.class,
                        GameLibraryActivity.class, ControllerSettingsActivity.class, ScreensaverSettingsActivity.class,
                        ScreensaverPreviewActivity.class)) {
                    Activity activity = start(screen);
                    try {
                        assertEquals(AppLanguage.locale().getLanguage(), activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                        screenshot(tag + "-" + screen.getSimpleName());
                    }
                    finally { finish(activity); }
                }
            }
            select(base, "");
            assertEquals("", AppLanguage.selected(base));
            select(base, "unsupported");
            assertEquals("", AppLanguage.selected(base));
        } finally { select(base, previous); }
    }

    public void testRecreationKeepsUnsavedSettingsAndLanguageSection() throws Exception {
        Context base = getInstrumentation().getTargetContext();
        String previous = AppLanguage.selected(base);
        MainActivity home = null;
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        try {
            select(base, "en");
            home = start(MainActivity.class);
            MainActivity current = home;
            getInstrumentation().runOnMainSync(() -> {
                try { method(MainActivity.class, "showSettings").invoke(current); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            Object settings = field(home, "settingsScreen");
            getInstrumentation().runOnMainSync(() -> {
                try {
                    ((EditText)field(settings, "playlistField")).setText("https://example.invalid/unsaved.m3u");
                    method(settings.getClass(), "captureDrafts").invoke(settings);
                    Field section = settings.getClass().getDeclaredField("section"); section.setAccessible(true); section.setInt(settings, 14);
                    method(settings.getClass(), "renderSection").invoke(settings);
                    ((View)field(settings, "firstControl")).performClick();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            getInstrumentation().waitForIdleSync();
            screenshot("en-language-picker");
            // English is entry 1 and Arabic entry 5, after Follow TV language.
            for (int i = 0; i < 4; i++) getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("ar", AppLanguage.selected(base));
            long deadline = SystemClock.uptimeMillis() + 10000;
            Activity replacement = null;
            while (SystemClock.uptimeMillis() < deadline) {
                Activity candidate = monitor.getLastActivity();
                if (candidate != null && candidate != home && !candidate.isDestroyed()) { replacement = candidate; break; }
                SystemClock.sleep(30);
            }
            assertNotNull("Language change did not recreate Home", replacement);
            home = (MainActivity)replacement;
            getInstrumentation().waitForIdleSync();
            Object restored = field(home, "settingsScreen");
            assertNotNull(restored);
            assertEquals(14, ((Integer)field(restored, "section")).intValue());
            assertEquals("https://example.invalid/unsaved.m3u", field(restored, "draftPlaylist"));
            assertTrue(((View)field(restored, "firstControl")).hasFocus());
        } finally {
            if (home != null) finish(home);
            getInstrumentation().removeMonitor(monitor);
            select(base, previous);
        }
    }

    public void testGuideToolbarArrowsFollowPhysicalDirectionInLtrAndRtl() throws Exception {
        Context base = getInstrumentation().getTargetContext();
        String previous = AppLanguage.selected(base);
        try {
            for (String tag : new String[]{"en", "ar", "ur"}) {
                select(base, tag);
                GuideActivity guide = start(GuideActivity.class);
                try {
                    View category = (View)field(guide, "categoryButton");
                    for (int key : new int[]{android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT}) {
                        getInstrumentation().runOnMainSync(category::requestFocus);
                        getInstrumentation().waitForIdleSync();
                        int[] before = new int[2]; category.getLocationOnScreen(before);
                        getInstrumentation().sendKeyDownUpSync(key);
                        getInstrumentation().waitForIdleSync();
                        View focused = guide.getCurrentFocus();
                        assertNotSame(category, focused);
                        int[] after = new int[2]; focused.getLocationOnScreen(after);
                        assertTrue(tag + ": key " + key + " moved from x=" + before[0] + " to x=" + after[0],
                                key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? after[0] < before[0] : after[0] > before[0]);
                        // At the toolbar edge, repeated arrows stay put; the opposite arrow returns.
                        getInstrumentation().sendKeyDownUpSync(key);
                        getInstrumentation().waitForIdleSync();
                        assertSame(focused, guide.getCurrentFocus());
                        getInstrumentation().sendKeyDownUpSync(key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                                ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                        getInstrumentation().waitForIdleSync();
                        assertTrue(category.hasFocus());
                    }
                } finally { finish(guide); }
            }
        } finally { select(base, previous); }
    }

    private void screenshot(String name) throws Exception {
        if (!"true".equals(((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("screenshots"))) return;
        SystemClock.sleep(250);
        getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File directory = new java.io.File(getInstrumentation().getTargetContext().getCacheDir(), "language-screenshots");
        directory.mkdirs();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        } finally { bitmap.recycle(); }
    }

    private void select(Context base, String tag) {
        getInstrumentation().runOnMainSync(() -> AppLanguage.select(base, tag));
        getInstrumentation().waitForIdleSync();
    }
    private <T extends Activity> T start(Class<T> type) {
        Activity activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().setInTouchMode(false);
        getInstrumentation().waitForIdleSync();
        return type.cast(activity);
    }
    private void finish(Activity activity) {
        getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().waitForIdleSync();
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Method method(Class<?> type, String name) throws Exception {
        Method method = type.getDeclaredMethod(name); method.setAccessible(true); return method;
    }
}
