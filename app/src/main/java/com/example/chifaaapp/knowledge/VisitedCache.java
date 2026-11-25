package com.example.chifaaapp.knowledge;

import android.util.AtomicFile;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Accès sérialisés par le repository. Le dossier appartient à un seul compte et serveur. */
final class VisitedCache {
    private final File directory;
    private final Gson gson = new Gson();

    VisitedCache(File directory) { this.directory = directory; }

    static String today() {
        java.text.SimpleDateFormat utc = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        return utc.format(new Date());
    }

    private AtomicFile file(String id) {
        if (id == null || !id.matches("KB-[0-9]{4}")) throw new IllegalArgumentException();
        return new AtomicFile(new File(directory, id));
    }

    void put(KnowledgeApi.Item item) throws IOException {
        AtomicFile file = file(item.contentId);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(gson.toJson(item).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException e) {
            file.failWrite(stream);
            throw e;
        }
    }

    KnowledgeApi.Item get(String id) {
        AtomicFile file = file(id);
        try (Reader reader = new InputStreamReader(file.openRead(), StandardCharsets.UTF_8)) {
            KnowledgeApi.Item item = gson.fromJson(reader, KnowledgeApi.Item.class);
            if (item != null && id.equals(item.contentId) && item.readable(today())) return item;
        } catch (IOException | RuntimeException ignored) {
            // Fichier absent, incomplet ou obsolète.
        }
        file.delete();
        return null;
    }

    List<String> ids() {
        String[] ids = directory.list((dir, name) -> name.matches("KB-[0-9]{4}"));
        if (ids == null) return Collections.emptyList();
        Arrays.sort(ids);
        return Arrays.asList(ids);
    }

    void remove(String id) { file(id).delete(); }

    void clear() {
        File[] files = directory.listFiles();
        if (files != null) for (File entry : files) entry.delete();
        directory.delete();
    }
}
