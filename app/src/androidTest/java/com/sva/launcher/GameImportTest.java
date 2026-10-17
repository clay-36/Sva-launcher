package com.sva.launcher;

import android.content.ClipData;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.test.InstrumentationTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Exercises the picker result boundary and imports only upstream diagnostic fixtures. */
public final class GameImportTest extends InstrumentationTestCase {
    public void testMultipleLocalFilesFinishAndImport() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        File workspace = new File(target.getCacheDir(), "game-import-test-" + System.nanoTime());
        assertTrue(workspace.mkdirs());
        TvFilePickerActivity picker = null;
        try {
            File first = fixture(workspace, "1.line_phase.nes");
            File second = fixture(workspace, "2.frame_phase.nes");
            List<File> selected = Arrays.asList(first, second);
            Intent result = TvFilePickerActivity.localFilesResult(selected);
            assertNull("Local file URIs must not enter ClipData", result.getClipData());
            Parcel parcel = Parcel.obtain();
            try {
                result.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                result = Intent.CREATOR.createFromParcel(parcel);
            } finally { parcel.recycle(); }
            List<Uri> uris = GameRepository.urisFromResult(result);
            assertEquals(Arrays.asList(Uri.fromFile(first), Uri.fromFile(second)), uris);

            // Activity.finish() used to throw FileUriExposedException for this selection.
            picker = (TvFilePickerActivity) getInstrumentation().startActivitySync(
                    new Intent(target, TvFilePickerActivity.class)
                            .putExtra(TvFilePickerActivity.EXTRA_MODE, TvFilePickerActivity.MODE_NES_FILES)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            TvFilePickerActivity active = picker;
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method finish = TvFilePickerActivity.class.getDeclaredMethod("finishWithFiles", List.class);
                    finish.setAccessible(true);
                    finish.invoke(active, selected);
                } catch (Exception e) { throw new AssertionError("Returning multiple files failed", e); }
            });
            assertTrue(picker.isFinishing());

            // Keep the test catalogue separate from the user's library.
            Context isolated = new ContextWrapper(target) {
                @Override public Context getApplicationContext() { return this; }
                @Override public File getFilesDir() { return workspace; }
            };
            GameRepository repository = new GameRepository(isolated);
            GameRepository.ImportResult imported = repository.importUris(uris);
            assertEquals(2, imported.added);
            assertEquals(0, imported.skipped);
            assertEquals(2, new GameRepository(isolated).games().size());
            assertEquals(2, repository.importUris(uris).duplicates);
        } finally {
            if (picker != null && !picker.isFinishing()) {
                TvFilePickerActivity active = picker;
                getInstrumentation().runOnMainSync(active::finish);
            }
            delete(workspace);
        }
    }

    public void testSingleLocalAndProviderSelections() {
        File file = new File(getInstrumentation().getTargetContext().getCacheDir(), "single.nes");
        assertEquals(Arrays.asList(Uri.fromFile(file)), GameRepository.urisFromResult(
                TvFilePickerActivity.localFilesResult(Arrays.asList(file))));
        Uri first = Uri.parse("content://documents/first.nes");
        Uri second = Uri.parse("content://documents/second.nes");
        ClipData clips = ClipData.newRawUri("Games", first);
        clips.addItem(new ClipData.Item(second));
        Intent provider = new Intent().setData(first);
        provider.setClipData(clips);
        assertEquals(Arrays.asList(first, second), GameRepository.urisFromResult(provider));
    }

    private File fixture(File directory, String name) throws Exception {
        File file = new File(directory, name);
        try (InputStream input = getInstrumentation().getContext().getAssets().open(name);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return file;
    }

    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
