package com.sva.launcher;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class GameRepository {
    static final int MAX_ROM_BYTES = 32 * 1024 * 1024;
    static final int MAX_ARTWORK_BYTES = 12 * 1024 * 1024;
    private static final int MAX_TREE_DEPTH = 16;
    static final class ImportResult {
        int added, duplicates, skipped;
        String message() {
            return AppLanguage.text(R.string.ui_import_counts, "%1$s · %2$s · %3$s",
                    AppLanguage.quantity(R.plurals.import_added, added, "%1$d added", "%1$d added"),
                    AppLanguage.quantity(R.plurals.import_duplicates, duplicates, "%1$d duplicate", "%1$d duplicates"),
                    AppLanguage.quantity(R.plurals.import_skipped, skipped, "%1$d skipped", "%1$d skipped"));
        }
    }

    private final Context context;
    private final File root, roms, artwork, saves, catalog;
    private List<GameRecord> memoryCatalog;
    private long memoryCatalogModified = Long.MIN_VALUE;

    GameRepository(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "games");
        roms = new File(root, "roms");
        artwork = new File(root, "artwork");
        saves = new File(root, "saves");
        catalog = new File(root, "catalog-v1.json");
        roms.mkdirs(); artwork.mkdirs(); saves.mkdirs();
    }

    synchronized List<GameRecord> games() {
        long modified = catalog.isFile() ? catalog.lastModified() : 0L;
        if (memoryCatalog != null && modified == memoryCatalogModified) return new ArrayList<>(memoryCatalog);
        ArrayList<GameRecord> result = new ArrayList<>();
        try {
            if (!catalog.isFile()) {
                memoryCatalog = result;
                memoryCatalogModified = modified;
                return new ArrayList<>(result);
            }
            byte[] bytes = readFully(new FileInputStream(catalog));
            JSONArray values = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                    .optJSONArray("games");
            if (values != null) for (int i = 0; i < values.length(); i++) {
                GameRecord record = GameRecord.fromJson(values.optJSONObject(i));
                if (record != null && romFile(record).isFile()) result.add(record);
            }
        } catch (Exception ignored) { }
        Collections.sort(result, Comparator.comparing(game -> game.title.toLowerCase(Locale.ROOT)));
        memoryCatalog = result;
        memoryCatalogModified = modified;
        return new ArrayList<>(result);
    }

    synchronized GameRecord find(String id) {
        for (GameRecord game : games()) if (game.id.equals(id)) return game;
        return null;
    }

    synchronized ImportResult importUris(List<Uri> uris) {
        ImportResult result = new ImportResult();
        for (Uri uri : uris) importOne(uri, result);
        return result;
    }

    synchronized ImportResult importTree(Uri tree) {
        ImportResult result = new ImportResult();
        try {
            if (tree == null) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_missing_folder, "Missing folder"));
            if ("file".equals(tree.getScheme())) {
                walkFiles(new File(tree.getPath()), result, 0);
                return result;
            }
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            walkTree(tree, rootId, result, 0);
        } catch (Exception e) { result.skipped++; }
        return result;
    }

    private void walkFiles(File folder, ImportResult result, int depth) {
        if (depth > 16) return;
        File[] children = folder.listFiles();
        if (children == null) { result.skipped++; return; }
        for (File child : children) {
            if (child.isDirectory()) walkFiles(child, result, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".nes"))
                importOne(Uri.fromFile(child), result);
        }
    }

    private void walkTree(Uri tree, String parentId, ImportResult result, int depth) throws Exception {
        if (depth > MAX_TREE_DEPTH) { result.skipped++; return; }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = context.getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) walkTree(tree, id, result, depth + 1);
                else if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".nes"))
                    importOne(DocumentsContract.buildDocumentUriUsingTree(tree, id), result);
            }
        }
    }

    private void importOne(Uri uri, ImportResult result) {
        String displayName = displayName(uri);
        if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".nes")) {
            result.skipped++; return;
        }
        File temporary = new File(roms, ".import-" + System.nanoTime());
        File destination = null;
        try (InputStream input = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            if (input == null) throw new IllegalStateException(AppLanguage.text(R.string.ui_could_not_read_file, "Could not read file"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            byte[] header = new byte[4];
            int headerCount = 0, read, total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ROM_BYTES) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_nes_file_is_larger_than_32_mb, "NES file is larger than 32 MB."));
                if (headerCount < 4) {
                    int take = Math.min(4 - headerCount, read);
                    System.arraycopy(buffer, 0, header, headerCount, take);
                    headerCount += take;
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.flush();
            if (headerCount != 4 || header[0] != 'N' || header[1] != 'E' || header[2] != 'S' || header[3] != 0x1a)
                throw new IllegalArgumentException(AppLanguage.text(R.string.ui_not_an_ines_file, "Not an iNES file"));
            String id = hex(digest.digest());
            if (find(id) != null) { result.duplicates++; temporary.delete(); return; }
            destination = new File(roms, id + ".nes");
            if (!temporary.renameTo(destination)) throw new IllegalStateException(AppLanguage.text(R.string.ui_could_not_finish_import, "Could not finish import"));
            List<GameRecord> current = games();
            current.add(new GameRecord(id, cleanTitle(displayName), destination.getName(), null,
                    System.currentTimeMillis(), 0L));
            save(current);
            result.added++;
        } catch (Exception e) {
            temporary.delete();
            if (destination != null && findByRomFile(destination.getName()) == null) destination.delete();
            result.skipped++;
        }
    }

    private GameRecord findByRomFile(String name) {
        for (GameRecord game : games()) if (game.romFile.equals(name)) return game;
        return null;
    }

    synchronized void updateTitle(String id, String title) throws Exception {
        List<GameRecord> current = games();
        for (GameRecord game : current) if (game.id.equals(id)) game.title = title.trim();
        save(current);
    }

    synchronized void updateArtwork(String id, Uri uri) throws Exception {
        List<GameRecord> current = games();
        GameRecord target = null;
        for (GameRecord game : current) if (game.id.equals(id)) target = game;
        if (target == null) return;
        File destination = new File(artwork, id + ".img");
        File temporary = File.createTempFile("art-", ".tmp", artwork);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalStateException(AppLanguage.text(R.string.ui_could_not_read_artwork, "Could not read artwork"));
                copyBounded(input, output, MAX_ARTWORK_BYTES);
            }
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_choose_an_image_file, "Choose an image file."));
            java.nio.file.Files.move(temporary.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { temporary.delete(); }
        target.artworkFile = destination.getName();
        save(current);
    }

    synchronized void markPlayed(String id) throws Exception {
        List<GameRecord> current = games();
        for (GameRecord game : current) if (game.id.equals(id)) game.lastPlayedAt = System.currentTimeMillis();
        save(current);
    }

    synchronized void remove(String id) throws Exception {
        List<GameRecord> current = games();
        ArrayList<GameRecord> kept = new ArrayList<>();
        for (GameRecord game : current) {
            if (!game.id.equals(id)) { kept.add(game); continue; }
            romFile(game).delete(); artworkFile(game).delete(); saveFile(game).delete();
        }
        save(kept);
    }

    File romFile(GameRecord game) { return new File(roms, game.romFile); }
    File artworkFile(GameRecord game) { return game.artworkFile == null ? new File(artwork, "missing") : new File(artwork, game.artworkFile); }
    File saveFile(GameRecord game) { return new File(saves, game.id + ".sav"); }

    private void save(List<GameRecord> records) throws Exception {
        root.mkdirs();
        JSONArray values = new JSONArray();
        for (GameRecord record : records) values.put(record.toJson());
        File temporary = new File(root, "catalog-v1.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(new JSONObject().put("version", 1).put("games", values).toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        java.nio.file.Files.move(temporary.toPath(), catalog.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        memoryCatalog = new ArrayList<>(records);
        Collections.sort(memoryCatalog, Comparator.comparing(game -> game.title.toLowerCase(Locale.ROOT)));
        memoryCatalogModified = catalog.lastModified();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    static String cleanTitle(String name) {
        String value = name == null ? "NES game" : name.replaceFirst("(?i)\\.nes$", "").replace('_', ' ').trim();
        while (value.matches(".*\\s\\[[^]]*]$")) value = value.replaceFirst("\\s\\[[^]]*]$", "").trim();
        while (value.matches("(?i).*\\s\\((usa|world|europe|japan|rev[^)]*|v[0-9][^)]*|beta[^)]*|proto[^)]*)\\)$"))
            value = value.replaceFirst("(?i)\\s\\((usa|world|europe|japan|rev[^)]*|v[0-9][^)]*|beta[^)]*|proto[^)]*)\\)$", "").trim();
        return value.isEmpty() ? "NES game" : value;
    }

    static List<Uri> urisFromResult(android.content.Intent data) {
        ArrayList<Uri> values = new ArrayList<>();
        if (data == null) return values;
        ArrayList<String> localFiles = data.getStringArrayListExtra(TvFilePickerActivity.EXTRA_LOCAL_FILES);
        if (localFiles != null) for (String path : localFiles) {
            if (path == null) continue;
            Uri uri = Uri.fromFile(new File(path));
            if (!values.contains(uri)) values.add(uri);
        }
        if (data.getData() != null) values.add(data.getData());
        ClipData clips = data.getClipData();
        if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) {
            Uri uri = clips.getItemAt(i).getUri(); if (uri != null && !values.contains(uri)) values.add(uri);
        }
        return values;
    }

    static void copyBounded(InputStream input, OutputStream output, int maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        int total = 0, count;
        while ((count = input.read(buffer)) != -1) {
            if (count > maxBytes - total) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_file_exceeds_the_import_size_limit, "File exceeds the import size limit."));
            total += count;
            output.write(buffer, 0, count);
        }
    }

    private static byte[] readFully(InputStream input) throws Exception {
        try (InputStream source = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            copyBounded(source, output, 8 * 1024 * 1024);
            return output.toByteArray();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }
}
