package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Ganzer Ablauf über HTTP mit simulierten Kameras: Würfe melden, Stelle "anklicken", fertig, dann spielen
@SpringBootTest
class CalibrationApiTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("dartscounter.data-dir", () -> dataDir.toString());
        registry.add("dartscounter.detection.simulate", () -> "true");
        registry.add("dartscounter.detection.window-ms", () -> "60000"); // Test schließt Würfe selbst ab
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    CalibrationService calibration;

    MockMvc mvc;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        calibration.reset();
    }

    private JsonNode call(String path, Object body, int expected) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    // simulierte Kameras sehen einen Pfeil bei (x, y)
    private void throwAt(double x, double y, Random random) throws Exception {
        for (int cam = 0; cam < 3; cam++) {
            double px = CalibrationMathTest.REAL[cam].pixel(x, y) + random.nextGaussian();
            call("/services/test/detection", Map.of("camera", cam, "pixelX", px), 202);
        }
        call("/services/test/flush", null, 204);
    }

    @Test
    void fullCalibrationThenPlay() throws Exception {
        Random random = new Random(7);
        call("/services/calibration/start", null, 200);

        // ohne erkannten Wurf kein Klick möglich
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/services/calibration/point").contentType(MediaType.APPLICATION_JSON).content("{\"x\":0,\"y\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Noch kein Wurf erkannt."));

        for (int i = 0; i < 10; i++) {
            double[] p = CalibrationMathTest.randomBoardPoint(random);
            throwAt(p[0], p[1], random);
            mvc.perform(get("/services/calibration")).andExpect(jsonPath("$.pendingCameras.length()").value(3));
            JsonNode status = call("/services/calibration/point", Map.of("x", p[0], "y", p[1]), 200);
            assertEquals(i + 1, status.get("samples").asInt());
        }

        // Fehlwurf (z. B. Pfeil herausgezogen) verwerfen
        throwAt(0, 0, random);
        call("/services/calibration/skip", null, 200);

        JsonNode done = call("/services/calibration/finish", null, 200);
        assertTrue(done.get("calibrated").asBoolean());
        assertTrue(done.get("errorMm").asDouble() < 3, "Fehler: " + done.get("errorMm"));
        assertTrue(Files.exists(dataDir.resolve("calibration.json")));

        // jetzt normal spielen: Wurf in die T20
        throwAt(0, -103, random);
        mvc.perform(get("/services/lastHit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field").value("T20"))
                .andExpect(jsonPath("$.score").value(60))
                .andExpect(jsonPath("$.calibrated").value(true));
    }

    @Test
    void finishNeedsEnoughThrows() throws Exception {
        Random random = new Random(8);
        call("/services/calibration/start", null, 200);
        for (int i = 0; i < 3; i++) {
            double[] p = CalibrationMathTest.randomBoardPoint(random);
            throwAt(p[0], p[1], random);
            call("/services/calibration/point", Map.of("x", p[0], "y", p[1]), 200);
        }
        call("/services/calibration/undo", null, 200);
        JsonNode error = call("/services/calibration/finish", null, 409);
        assertTrue(error.get("message").asString().startsWith("Zu wenige Würfe"));
        mvc.perform(get("/services/calibration")).andExpect(jsonPath("$.samples").value(2)).andExpect(jsonPath("$.active").value(true));

        call("/services/calibration/cancel", null, 200);
        call("/services/calibration/skip", null, 409); // keine Kalibrierung aktiv
    }

    @Test
    void lateCamerasAreMergedIntoSameThrow() throws Exception {
        call("/services/calibration/start", null, 200);
        // Kamera 1 meldet zuerst, die anderen erst nach Ablauf des Zeitfensters (langsamer Pi)
        call("/services/test/detection", Map.of("camera", 0, "pixelX", 300.0), 202);
        call("/services/test/flush", null, 204);
        call("/services/test/detection", Map.of("camera", 1, "pixelX", 310.0), 202);
        call("/services/test/detection", Map.of("camera", 2, "pixelX", 320.0), 202);
        call("/services/test/flush", null, 204);
        mvc.perform(get("/services/calibration")).andExpect(jsonPath("$.pendingCameras.length()").value(3));

        // meldet dieselbe Kamera wieder, ist es ein neuer Wurf
        call("/services/test/detection", Map.of("camera", 0, "pixelX", 100.0), 202);
        call("/services/test/flush", null, 204);
        mvc.perform(get("/services/calibration")).andExpect(jsonPath("$.pendingCameras.length()").value(1));
    }

    @Test
    void diagnosticsWithoutRunningCameras() throws Exception {
        mvc.perform(get("/services/debug/cameras")).andExpect(status().isOk());
        mvc.perform(get("/services/debug/camera/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/services/debug")).andExpect(status().isOk());
    }

    @Test
    void clickOutsideBoardRejected() throws Exception {
        call("/services/calibration/start", null, 200);
        throwAt(10, 10, new Random(9));
        call("/services/calibration/point", Map.of("x", 400, "y", 0), 400);
    }
}
