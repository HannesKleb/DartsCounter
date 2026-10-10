package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Speichert die Daten des Frontends als JSON-Dateien auf dem Pi (ein File pro Schlüssel).
// Standardordner liegt außerhalb des Projekts, damit die Deploy-Skripte ihn nicht löschen.
@Service
public class DataStore {
    // nur diese Schlüssel sind erlaubt, damit niemand beliebige Dateien schreibt
    public static final Set<String> KEYS = Set.of("players", "settings", "games", "currentGame");

    private final Path dataDir;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public DataStore(@Value("${dartscounter.data-dir:${user.home}/dartscounter-data}") String dataDir) {
        this.dataDir = Path.of(dataDir);
    }

    public boolean isValidKey(String key) {
        return KEYS.contains(key);
    }

    public boolean isValidJson(String json) {
        try {
            jsonMapper.readTree(json);
            return true;
        } catch (JacksonException e) {
            return false;
        }
    }

    public synchronized Optional<String> read(String key) throws IOException {
        Path file = fileFor(key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    public synchronized void write(String key, String json) throws IOException {
        Files.createDirectories(dataDir);
        // erst temporär schreiben und dann ersetzen, damit bei Stromausfall keine halbe Datei bleibt
        Path tmp = dataDir.resolve(key + ".json.tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, fileFor(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized void delete(String key) throws IOException {
        Files.deleteIfExists(fileFor(key));
    }

    private Path fileFor(String key) {
        return dataDir.resolve(key + ".json");
    }
}
