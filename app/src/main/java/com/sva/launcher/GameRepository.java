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
    static final class ImportResult {
        int added, duplicates, skipped;
        String message() {
            return added + " added · " + duplicates + " duplicate" + (duplicates == 1 ? "" : "s")
                    + " · " + skipped + " skipped";
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
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            walkTree(tree, rootId, result);
        } catch (Exception e) { result.skipped++; }
        return result;
    }

    private void walkTree(Uri tree, String parentId, ImportResult result) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = context.getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) walkTree(tree, id, result);
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
            if (input == null) throw new IllegalStateException("Could not read file");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            byte[] header = new byte[4];
            int headerCount = 0, read;
            while ((read = input.read(buffer)) != -1) {
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
                throw new IllegalArgumentException("Not an iNES file");
            String id = hex(digest.digest());
            if (find(id) != null) { result.duplicates++; temporary.delete(); return; }
            destination = new File(roms, id + ".nes");
            if (!temporary.renameTo(destination)) throw new IllegalStateException("Could not finish import");
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
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IllegalStateException("Could not read artwork");
            byte[] buffer = new byte[32 * 1024]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
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
        if (data.getData() != null) values.add(data.getData());
        ClipData clips = data.getClipData();
        if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) {
            Uri uri = clips.getItemAt(i).getUri(); if (!values.contains(uri)) values.add(uri);
        }
        return values;
    }

    private static byte[] readFully(InputStream input) throws Exception {
        try (InputStream source = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = source.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }
}
