package com.sva.launcher;

import android.app.Activity;
import android.content.Context;

/** Context wrapping is required for standard Activities on Android 8–12. */
public abstract class LocalizedActivity extends Activity {
    private String languageStamp;
    @Override protected void attachBaseContext(Context base) {
        // A locale override in the base context would mask LocaleManager's configuration
        // changes and can cause duplicate recreation on Android 13 and newer.
        super.attachBaseContext(android.os.Build.VERSION.SDK_INT >= 33 ? base : AppLanguage.wrap(base));
    }
    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        languageStamp = AppLanguage.context().getResources().getConfiguration().getLocales().toLanguageTags();
        getWindow().getDecorView().setLayoutDirection(AppLanguage.context().getResources().getConfiguration().getLayoutDirection());
    }
    @Override protected void onResume() {
        super.onResume();
        String current = AppLanguage.context().getResources().getConfiguration().getLocales().toLanguageTags();
        if (languageStamp != null && !languageStamp.equals(current)) {
            languageStamp = current;
            recreate();
        }
    }
}
