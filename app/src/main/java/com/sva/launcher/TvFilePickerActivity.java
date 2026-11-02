package com.sva.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Delegates file access to Android's user-granted Storage Access Framework. */
public final class TvFilePickerActivity extends MotionActivity {
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_LOCAL_FILES = "com.sva.launcher.extra.LOCAL_FILES";
    static final String MODE_NES_FILES = "nes_files";
    static final String MODE_NES_FOLDER = "nes_folder";
    static final String MODE_IMAGE = "image";
    static final String MODE_IMAGE_FOLDER = "image_folder";
    static final String MODE_JSON = "json";
    static final String MODE_SAVE_JSON = "save_json";
    private static final int PICK_DOCUMENT = 1;

    static Intent intent(Activity activity, String mode) {
        return new Intent(activity, TvFilePickerActivity.class).putExtra(EXTRA_MODE, mode);
    }

    static Intent documentIntent(String mode) {
        boolean folder = MODE_NES_FOLDER.equals(mode) || MODE_IMAGE_FOLDER.equals(mode);
        boolean save = MODE_SAVE_JSON.equals(mode);
        Intent intent = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE
                : save ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (MODE_IMAGE.equals(mode) || MODE_IMAGE_FOLDER.equals(mode)) {
            // The wallpaper owner decides whether ongoing access is needed.
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        if (save) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (!folder) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // ROMs and JSON are often reported as application/octet-stream by USB providers.
            intent.setType(MODE_IMAGE.equals(mode) ? "image/*" : "*/*");
            if (save) {
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "sva-launcher-backup.json");
            }
            if (MODE_NES_FILES.equals(mode)) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_NES_FILES.equals(mode) && !MODE_NES_FOLDER.equals(mode)
                && !MODE_IMAGE.equals(mode) && !MODE_IMAGE_FOLDER.equals(mode)
                && !MODE_JSON.equals(mode) && !MODE_SAVE_JSON.equals(mode)) {
            finish(); return;
        }
        if (state != null) return; // The system picker owns the pending selection.
        try {
            startActivityForResult(documentIntent(mode), PICK_DOCUMENT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.ui_document_picker_unavailable, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_DOCUMENT) return;
        if (result == RESULT_OK && data != null) {
            // Forward temporary grants to the caller. ROMs, cover art and backups
            // are consumed or copied immediately; only wallpaper owners persist access.
            setResult(RESULT_OK, data);
        }
        finish();
    }

    // Internal app-private diagnostic fixtures; never used for shared-storage selection.
    static Intent localFilesResult(List<File> files) {
        Intent result = new Intent();
        if (files.size() == 1) result.setData(Uri.fromFile(files.get(0)));
        else {
            // These paths stay within our non-exported picker/import flow. Putting
            // file:// URIs in ClipData makes Activity.finish() throw on modern Android.
            ArrayList<String> paths = new ArrayList<>();
            for (File file : files) paths.add(file.getAbsolutePath());
            result.putStringArrayListExtra(EXTRA_LOCAL_FILES, paths);
        }
        return result;
    }

}
