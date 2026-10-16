package com.sva.launcher;

import org.json.JSONObject;

final class GameRecord {
    final String id;
    String title;
    final String romFile;
    String artworkFile;
    final long importedAt;
    long lastPlayedAt;

    GameRecord(String id, String title, String romFile, String artworkFile,
               long importedAt, long lastPlayedAt) {
        this.id = id;
        this.title = title;
        this.romFile = romFile;
        this.artworkFile = artworkFile;
        this.importedAt = importedAt;
        this.lastPlayedAt = lastPlayedAt;
    }

    JSONObject toJson() throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("romFile", romFile)
                .put("artworkFile", artworkFile == null ? JSONObject.NULL : artworkFile)
                .put("importedAt", importedAt).put("lastPlayedAt", lastPlayedAt);
    }

    static GameRecord fromJson(JSONObject value) {
        if (value == null) return null;
        String id = value.optString("id", "");
        String title = value.optString("title", "");
        String rom = value.optString("romFile", "");
        // Catalogue names are private-storage basenames, never arbitrary paths.
        if (!id.matches("[a-fA-F0-9]{64}") || title.trim().isEmpty() || !rom.equals(id + ".nes")) return null;
        String art = value.isNull("artworkFile") ? null : value.optString("artworkFile", null);
        if (art != null && !art.equals(id + ".img")) return null;
        return new GameRecord(id, title, rom,
                art,
                value.optLong("importedAt", 0L), value.optLong("lastPlayedAt", 0L));
    }
}
