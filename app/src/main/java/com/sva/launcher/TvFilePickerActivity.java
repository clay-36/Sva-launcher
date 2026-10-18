package com.sva.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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

/** A remote-friendly browser for internal storage and connected drives. */
public final class TvFilePickerActivity extends MotionActivity {
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_LOCAL_FILES = "com.sva.launcher.extra.LOCAL_FILES";
    static final String MODE_NES_FILES = "nes_files";
    static final String MODE_NES_FOLDER = "nes_folder";
    static final String MODE_IMAGE = "image";
    static final String MODE_IMAGE_FOLDER = "image_folder";
    static final String MODE_JSON = "json";
    static final String MODE_SAVE_JSON = "save_json";
    private static final int STORAGE_SETTINGS = 2;
    private static final int STORAGE_PERMISSION = 3;

    private String mode;
    private File directory;
    private final LinkedHashSet<File> selected = new LinkedHashSet<>();
    private LinearLayout rows;
    private TextView path;
    private TextView instruction;
    private Button action;
    private Button back;
    private EditText filename;

    static Intent intent(Activity activity, String mode) {
        return new Intent(activity, TvFilePickerActivity.class).putExtra(EXTRA_MODE, mode);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::up);
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
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(dp(40), dp(24), dp(40), dp(24));
        page.setBackgroundColor(SettingsUi.background(this));
        TextView title = SettingsUi.text(this, title(), 28, false);
        page.addView(title);
        instruction = SettingsUi.text(this, "", 14, true);
        instruction.setMinHeight(dp(38));
        instruction.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(instruction, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout browser = new LinearLayout(this);
        browser.setOrientation(LinearLayout.VERTICAL);
        browser.setPadding(dp(12), dp(8), dp(12), dp(8));
        browser.setBackground(SettingsUi.shape(this, SettingsUi.surface(this), SettingsUi.raised(this), 1, 18));
        path = SettingsUi.text(this, "Storage locations", 15, true);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        path.setGravity(Gravity.CENTER_VERTICAL);
        path.setPadding(dp(12), 0, dp(12), 0);
        browser.addView(path, new LinearLayout.LayoutParams(-1, dp(44)));
        View divider = new View(this); divider.setBackgroundColor(SettingsUi.raised(this));
        browser.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setPadding(0, dp(6), 0, dp(6));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(12), dp(4), dp(12), dp(4));
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        browser.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        if (isSaveMode()) {
            filename = new EditText(this);
            filename.setSingleLine(true);
            filename.setText("sva-launcher-backup.json");
            filename.setSelectAllOnFocus(true);
            filename.setHint("Backup filename");
            filename.setContentDescription("Backup filename");
            SettingsUi.styleInput(filename, false);
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(56));
            inputParams.topMargin = dp(12);
            page.addView(filename, inputParams);
        }
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        footer.setPadding(dp(4), dp(4), dp(4), dp(4));
        back = pickerButton("Back");
        back.setOnClickListener(v -> up());
        footer.addView(back, new LinearLayout.LayoutParams(dp(112), dp(52)));
        action = pickerButton("Select");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        actionParams.leftMargin = dp(12);
        footer.addView(action, actionParams);
        action.setOnClickListener(v -> confirm());
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(64));
        footerParams.topMargin = dp(12);
        page.addView(footer, footerParams);
        setContentView(page);
    }

    private int dp(int value) { return SettingsUi.dp(this, value); }

    private Button pickerButton(String label) {
        Button button = SettingsUi.action(this, label, null);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{SettingsUi.muted(this), SettingsUi.text(this)}));
        return button;
    }

    private void updateAction() {
        action.setText(isMultiMode() ? "Add selected (" + selected.size() + ")"
                : isFolderMode() ? "Use this folder" : isSaveMode() ? "Save here" : "Select a file");
        boolean enabled = hasAccess() && (isMultiMode() ? !selected.isEmpty()
                : directory != null && (isFolderMode() || isSaveMode()));
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : .5f);
        action.setBackground(SettingsUi.states(this, enabled));
        if (!isMultiMode() && !isFolderMode() && !isSaveMode()) action.setVisibility(View.GONE);
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
        path.setContentDescription(null);
        if (directory != null && !directory.isDirectory()) directory = null;
        updateAction();
        if (!hasAccess()) {
            path.setText("Storage access needed");
            instruction.setText("Allow storage access to browse files on this device and connected USB drives.");
            row("Allow storage access", this::requestAccess);
            rows.getChildAt(0).requestFocus();
            return;
        }
        if (directory == null) {
            path.setText("Storage locations");
            instruction.setText("Choose internal storage or a connected USB drive.");
            List<File> roots = roots();
            for (File root : roots) row(root.equals(Environment.getExternalStorageDirectory())
                    ? "Internal storage" : "USB · " + root.getName(), () -> navigate(root));
            if (roots.isEmpty()) row("No local storage found", null);
            if (!roots.isEmpty()) rows.getChildAt(0).requestFocus();
            return;
        }
        path.setText(directory.getAbsolutePath());
        path.setContentDescription("Current folder: " + directory.getAbsolutePath());
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
                    if (child.canRead()) { fileRow(child, true); shown++; }
                } else if (accepts(child)) {
                    fileRow(child, false);
                    shown++;
                }
            }
            if (shown == 0) row("No matching files here", null);
        }
        if (rows.getChildCount() > 0 && rows.getChildAt(0).isFocusable()) rows.getChildAt(0).requestFocus();
        else (action.isEnabled() ? action : back).requestFocus();
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

    private Button row(String title, Runnable click) {
        Button button = pickerButton(title);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setEnabled(click != null);
        button.setFocusable(click != null);
        if (click != null) button.setOnClickListener(v -> click.run());
        else button.setTextColor(SettingsUi.muted(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        rows.addView(button, params);
        return button;
    }

    private void fileRow(File file, boolean folder) {
        Button button = row(file.getName(), folder ? () -> navigate(file) : () -> chooseFile(file));
        button.setTag(file);
        styleFileRow(button, file, folder);
    }

    private void styleFileRow(Button button, File file, boolean folder) {
        boolean checked = !folder && selected.contains(file);
        button.setBackground(SettingsUi.states(this, checked));
        button.setContentDescription(file.getName() + (folder ? ", folder" : checked ? ", selected" : ", file"));
        android.graphics.drawable.Drawable icon = new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas canvas) {
                canvas.save(); canvas.translate(getBounds().left, getBounds().top);
                canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
                paint.setColor(checked ? LiquidTheme.accent(TvFilePickerActivity.this) : SettingsUi.muted(TvFilePickerActivity.this));
                paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
                paint.setStrokeJoin(android.graphics.Paint.Join.ROUND); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                if (checked) {
                    canvas.drawRoundRect(3, 3, 21, 21, 4, 4, paint);
                    canvas.drawLine(7, 12, 10, 15, paint); canvas.drawLine(10, 15, 17, 8, paint);
                } else if (folder) {
                    android.graphics.Path shape = new android.graphics.Path();
                    shape.moveTo(3, 7); shape.lineTo(3, 5); shape.lineTo(10, 5); shape.lineTo(12, 8);
                    shape.lineTo(21, 8); shape.lineTo(21, 19); shape.lineTo(3, 19); shape.close();
                    canvas.drawPath(shape, paint);
                } else {
                    canvas.drawRoundRect(5, 3, 19, 21, 2, 2, paint);
                    canvas.drawLine(9, 10, 15, 10, paint); canvas.drawLine(9, 14, 15, 14, paint);
                }
                canvas.restore();
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        icon.setBounds(0, 0, dp(24), dp(24));
        button.setCompoundDrawablePadding(dp(14));
        button.setCompoundDrawablesRelative(icon, null, null, null);
    }

    private void navigate(File next) { directory = next; showDirectory(); }

    private void chooseFile(File file) {
        if (isMultiMode()) {
            if (!selected.add(file)) selected.remove(file);
            for (int i = 0; i < rows.getChildCount(); i++) {
                View row = rows.getChildAt(i);
                if (row instanceof Button && file.equals(row.getTag())) styleFileRow((Button) row, file, false);
            }
            instruction.setText(selected.size() + " selected · press OK on files, then Add selected");
            updateAction();
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
                AlertDialog prompt = new AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this))
                        .setTitle("Replace " + name + "?")
                        .setPositiveButton("Replace", (dialog, which) -> finishWithFiles(Arrays.asList(target)))
                        .setNegativeButton("Cancel", null).create();
                Motion.dialog(prompt); prompt.show();
            } else {
                try {
                    if (!target.createNewFile()) throw new IOException("Could not create file");
                    finishWithFiles(Arrays.asList(target));
                } catch (IOException e) { Toast.makeText(this, "Could not create a file here.", Toast.LENGTH_LONG).show(); }
            }
        }
    }

    private void finishWithFiles(List<File> files) {
        setResult(RESULT_OK, localFilesResult(files));
        finish();
    }

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

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == STORAGE_SETTINGS) showDirectory();
    }

    private void up() {
        if (directory == null) { finish(); return; }
        for (File root : roots()) if (root.equals(directory)) { directory = null; showDirectory(); return; }
        directory = directory.getParentFile();
        showDirectory();
    }

    // Legacy TV/Android <33 fallback; API 33+ uses BackNavigation.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { up(); }
}
