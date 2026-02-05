package com.DartsCounter.DartsCounter_backend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
// Erlaubt dem VS Code Live Server den Zugriff
@CrossOrigin(origins = {"http://127.0.0.1:5500", "http://localhost:5500"})
public class DataController {

    @GetMapping("/hallo")
    public Map<String, String> sagHallo() {
        return Collections.singletonMap("nachricht", "Verbindung steht!");
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> daten = new HashMap<>();
        daten.put("benutzer", "Admin");
        daten.put("online", true);
        daten.put("besucher", 42);
        return daten;
    }
}