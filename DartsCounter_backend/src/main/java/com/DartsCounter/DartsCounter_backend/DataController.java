package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
// CORS steht in WebConfig
public class DataController {
    private final DataStore dataStore;

    public DataController(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    // Verbindungstest fürs Frontend
    @GetMapping("/hallo")
    public Map<String, String> sagHallo() {
        return Collections.singletonMap("nachricht", "Verbindung steht!");
    }

    @GetMapping(value = "/data/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getData(@PathVariable("key") String key) throws IOException {
        if (!dataStore.isValidKey(key)) {
            return ResponseEntity.badRequest().build();
        }
        return dataStore.read(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build()); // noch nichts gespeichert
    }

    @PutMapping(value = "/data/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> putData(@PathVariable("key") String key, @RequestBody String json) throws IOException {
        if (!dataStore.isValidKey(key) || !dataStore.isValidJson(json)) {
            return ResponseEntity.badRequest().build();
        }
        dataStore.write(key, json);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/data/{key}")
    public ResponseEntity<Void> deleteData(@PathVariable("key") String key) throws IOException {
        if (!dataStore.isValidKey(key)) {
            return ResponseEntity.badRequest().build();
        }
        dataStore.delete(key);
        return ResponseEntity.noContent().build();
    }
}
