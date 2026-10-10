package com.sva.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** A remote-friendly local storage browser. The system picker remains available for cloud providers. */
public final class TvFilePickerActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_NES_FILES = "nes_files";
    static final String MODE_NES_FOLDER = "nes_folder";
    static final String MODE_IMAGE = "image";
    static final String MODE_IMAGE_FOLDER = "image_folder";
    static final String MODE_JSON = "json";
    static final String MODE_SAVE_JSON = "save_json";
    private static final int EXTERNAL_PICK = 1;
    private static final int STORAGE_SETTINGS = 2;
    private static final int STORAGE_PERMISSION = 3;

    private String mode;
    private File directory;
    private final LinkedHashSet<File> selected = new LinkedHashSet<>();
    private LinearLayout rows;
    private TextView path;
    private TextView instruction;
    private Button action;
    private Button system;
    private EditText filename;

    static Intent intent(Activity activity, String mode) {
        return new Intent(activity, TvFilePickerActivity.class).putExtra(EXTRA_MODE, mode);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!isSupportedMode()) { finish(); return; }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (state != null) {
            String saved = state.getString("directory");
            if (saved != null) directory = new File(saved);
            ArrayList<String> paths = state.getStringArrayList("selected");
            if (paths != null) for (String value : paths) selected.add(new File(value));
        }
        build();
        showDirectory();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("directory", directory == null ? null : directory.getAbsolutePath());
        ArrayList<String> paths = new ArrayList<>();
        for (File file : selected) paths.add(file.getAbsolutePath());
        state.putStringArrayList("selected", paths);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (rows != null) showDirectory();
    }

    private boolean isSupportedMode() {
        return MODE_NES_FILES.equals(mode) || MODE_NES_FOLDER.equals(mode)
                || MODE_IMAGE.equals(mode) || MODE_IMAGE_FOLDER.equals(mode)
                || MODE_JSON.equals(mode) || MODE_SAVE_JSON.equals(mode);
    }

    private boolean isFolderMode() { return MODE_NES_FOLDER.equals(mode) || MODE_IMAGE_FOLDER.equals(mode); }
    private boolean isMultiMode() { return MODE_NES_FILES.equals(mode); }
    private boolean isSaveMode() { return MODE_SAVE_JSON.equals(mode); }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(GameUi.dp(this, 52), GameUi.dp(this, 28), GameUi.dp(this, 52), GameUi.dp(this, 28));
        page.setBackground(LiquidTheme.scenicBackground(this));
        TextView title = GameUi.text(this, title(), 30, true);
        page.addView(title);
        path = GameUi.text(this, "Storage", 15, false);
        path.setTextColor(LiquidTheme.secondaryText(this));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 38));
        pathParams.topMargin = GameUi.dp(this, 8);
        page.addView(path, pathParams);
        instruction = GameUi.text(this, "", 15, false);
        page.addView(instruction);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = GameUi.dp(this, 14);
        page.addView(scroll, scrollParams);
        if (isSaveMode()) {
            filename = new EditText(this);
            filename.setSingleLine(true);
            filename.setText("sva-launcher-backup.json");
            filename.setSelectAllOnFocus(true);
            filename.setTextColor(LiquidTheme.text(this));
            filename.setHintTextColor(LiquidTheme.secondaryText(this));
            filename.setHint("Backup filename");
            page.addView(filename, new LinearLayout.LayoutParams(GameUi.dp(this, 400), GameUi.dp(this, 54)));
        }
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        Button back = GameUi.button(this, "Back");
        back.setOnClickListener(v -> up());
        footer.addView(back, new LinearLayout.LayoutParams(0, GameUi.dp(this, 54), 1f));
        action = GameUi.button(this, "Select");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, GameUi.dp(this, 54), 1.5f);
        actionParams.leftMargin = GameUi.dp(this, 12);
        footer.addView(action, actionParams);
        action.setOnClickListener(v -> confirm());
        system = GameUi.button(this, "Other locations");
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(0, GameUi.dp(this, 54), 1.3f);
        systemParams.leftMargin = GameUi.dp(this, 12);
        footer.addView(system, systemParams);
        system.setOnClickListener(v -> openSystemPicker());
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 66));
        footerParams.topMargin = GameUi.dp(this, 14);
        page.addView(footer, footerParams);
        setContentView(page);
    }

    private String title() {
        if (MODE_NES_FILES.equals(mode)) return "Add NES games";
        if (MODE_NES_FOLDER.equals(mode)) return "Add games from folder";
        if (MODE_IMAGE_FOLDER.equals(mode)) return "Choose photo folder";
        if (MODE_IMAGE.equals(mode)) return "Choose image";
        if (MODE_JSON.equals(mode)) return "Restore backup";
        return "Save backup";
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && (!isSaveMode() || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())), STORAGE_SETTINGS);
            } catch (Exception e) {
                try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), STORAGE_SETTINGS); }
                catch (Exception ignored) { Toast.makeText(this, "Storage settings are unavailable.", Toast.LENGTH_LONG).show(); }
            }
        } else {
            requestPermissions(isSaveMode()
                    ? new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == STORAGE_PERMISSION) showDirectory();
    }

    private List<File> roots() {
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        roots.add(Environment.getExternalStorageDirectory());
        StorageManager manager = getSystemService(StorageManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= 30) {
            for (StorageVolume volume : manager.getStorageVolumes()) {
                File root = volume.getDirectory();
                if (root != null) roots.add(root);
            }
        }
        File[] appDirs = getExternalFilesDirs(null);
        if (appDirs != null) for (File appDir : appDirs) {
            if (appDir == null) continue;
            File root = appDir;
            for (int i = 0; i < 4 && root.getParentFile() != null; i++) root = root.getParentFile();
            roots.add(root);
        }
        ArrayList<File> available = new ArrayList<>();
        for (File root : roots) if (root.isDirectory()) available.add(root);
        return available;
    }

    private void showDirectory() {
        rows.removeAllViews();
        if (!hasAccess()) {
            path.setText("Storage access needed");
            instruction.setText("Allow Sva to browse local files, or choose Other locations for a provider.");
            row("Allow storage access", this::requestAccess);
            action.setEnabled(false);
            rows.getChildAt(0).requestFocus();
            return;
        }
        if (directory != null && !directory.isDirectory()) directory = null;
        if (directory == null) {
            path.setText("Storage locations");
            instruction.setText("Choose internal storage or a connected USB drive.");
            List<File> roots = roots();
            for (File root : roots) row(root.equals(Environment.getExternalStorageDirectory())
                    ? "Internal storage" : "USB · " + root.getName(), () -> navigate(root));
            if (roots.isEmpty()) row("No local storage found", null);
            action.setEnabled(false);
            if (!roots.isEmpty()) rows.getChildAt(0).requestFocus();
            return;
        }
        path.setText(directory.getAbsolutePath());
        instruction.setText(isMultiMode() ? selected.size() + " selected · press OK on files, then Add selected"
                : isFolderMode() ? "Open a folder, then choose Use this folder" : isSaveMode()
                ? "Enter a filename and save in this folder" : "Select a file with OK");
        File[] children = directory.listFiles();
        if (children == null) {
            row("This folder cannot be read", null);
        } else {
            Arrays.sort(children, Comparator.comparing(File::isDirectory).reversed()
                    .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            int shown = 0;
            for (File child : children) {
                if (child.isDirectory()) {
                    if (child.canRead()) { row("📁  " + child.getName(), () -> navigate(child)); shown++; }
                } else if (accepts(child)) {
                    row((selected.contains(child) ? "✓  " : "      ") + child.getName(), () -> chooseFile(child));
                    shown++;
                }
            }
            if (shown == 0) row("No matching files here", null);
        }
        action.setText(isMultiMode() ? "Add selected (" + selected.size() + ")"
                : isFolderMode() ? "Use this folder" : isSaveMode() ? "Save here" : "Select a file");
        action.setEnabled(isFolderMode() || isSaveMode() || (isMultiMode() && !selected.isEmpty()));
        if (rows.getChildCount() > 0 && rows.getChildAt(0).isFocusable()) rows.getChildAt(0).requestFocus();
        else action.requestFocus();
    }

    private boolean accepts(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (isSaveMode() || isFolderMode()) return false;
        if (isMultiMode()) return name.endsWith(".nes");
        if (MODE_JSON.equals(mode)) return name.endsWith(".json");
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".gif")
                || name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".avif");
    }

    private void row(String title, Runnable click) {
        Button button = GameUi.button(this, title);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setPadding(GameUi.dp(this, 20), 0, GameUi.dp(this, 20), 0);
        button.setEnabled(click != null);
        if (click != null) button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 58));
        params.bottomMargin = GameUi.dp(this, 8);
        rows.addView(button, params);
    }

    private void navigate(File next) { directory = next; showDirectory(); }

    private void chooseFile(File file) {
        if (isMultiMode()) {
            int focusedRow = rows.indexOfChild(getCurrentFocus());
            if (!selected.add(file)) selected.remove(file);
            showDirectory();
            if (focusedRow >= 0 && focusedRow < rows.getChildCount())
                rows.getChildAt(focusedRow).requestFocus();
        } else finishWithFiles(Arrays.asList(file));
    }

    private void confirm() {
        if (isMultiMode()) {
            if (!selected.isEmpty()) finishWithFiles(new ArrayList<>(selected));
        } else if (isFolderMode()) {
            if (directory != null) finishWithFiles(Arrays.asList(directory));
        } else if (isSaveMode() && directory != null) {
            String name = filename.getText().toString().trim();
            if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
                filename.setError("Enter a filename without slashes"); return;
            }
            if (!name.toLowerCase(Locale.ROOT).endsWith(".json")) name += ".json";
            File target = new File(directory, name);
            if (target.exists()) {
                new AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this))
                        .setTitle("Replace " + name + "?")
                        .setPositiveButton("Replace", (dialog, which) -> finishWithFiles(Arrays.asList(target)))
                        .setNegativeButton("Cancel", null).show();
            } else {
                try {
                    if (!target.createNewFile()) throw new IOException("Could not create file");
                    finishWithFiles(Arrays.asList(target));
                } catch (IOException e) { Toast.makeText(this, "Could not create a file here.", Toast.LENGTH_LONG).show(); }
            }
        }
    }

    private void finishWithFiles(List<File> files) {
        Intent result = new Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (files.size() == 1) result.setData(Uri.fromFile(files.get(0)));
        else {
            ClipData clips = ClipData.newRawUri("Selected files", Uri.fromFile(files.get(0)));
            for (int i = 1; i < files.size(); i++) clips.addItem(new ClipData.Item(Uri.fromFile(files.get(i))));
            result.setClipData(clips);
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private void openSystemPicker() {
        Intent picker;
        if (isFolderMode()) picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        else if (isSaveMode()) picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, filename.getText().toString().trim());
        else picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MODE_IMAGE.equals(mode) ? "image/*" : MODE_JSON.equals(mode) ? "application/json" : "*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiMode());
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(picker, EXTERNAL_PICK); }
        catch (Exception e) { Toast.makeText(this, "No other file provider is available.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == EXTERNAL_PICK && result == RESULT_OK && data != null) {
            if (data.getData() != null && !isSaveMode()) {
                try {
                    getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
            }
            setResult(RESULT_OK, data);
            finish();
        } else if (request == STORAGE_SETTINGS) showDirectory();
    }

    private void up() {
        if (directory == null) { finish(); return; }
        for (File root : roots()) if (root.equals(directory)) { directory = null; showDirectory(); return; }
        directory = directory.getParentFile();
        showDirectory();
    }

    @Override public void onBackPressed() { up(); }
}
