package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/**
 * Kalibrierung über Testwürfe: Ein Wurf wird erkannt (Pixelspalte je Kamera), danach klickt man im
 * Frontend die echte Stelle auf der Scheibe an. Aus mehreren solchen Paaren bekommt jede Kamera ihr Modell.
 * Gespeichert in calibration.json im Datenordner (nur Zahlen, keine persönlichen Daten).
 */
@Service
public class CalibrationService {
    static final double MAX_BOARD_MM = 230; // Klicks außerhalb der Scheibe sind Fehler

    // ein Kalibrierwurf: geklickte Position und was jede Kamera gesehen hat
    public record ThrowSample(double x, double y, Map<Integer, Double> pixels) {}

    public record CameraInfo(int camera, int samples, Double rmsPx, boolean calibrated) {}

    public record Status(boolean active, boolean calibrated, String createdAt, Double errorMm, Double maxErrorMm,
                         int samples, int minSamples, int recommendedSamples, List<Integer> pendingCameras, List<CameraInfo> cameras) {}

    // Inhalt von calibration.json
    record Saved(String createdAt, Double errorMm, Double maxErrorMm,
                 Map<Integer, CameraModel> models, Map<Integer, Double> rmsPx, List<ThrowSample> samples) {}

    private final Path file;
    private final int cameraCount;
    private final Map<Integer, CameraModel> nominal = new HashMap<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private Saved saved;                                   // letzte fertige Kalibrierung oder null
    private boolean active;
    private final List<ThrowSample> samples = new ArrayList<>();
    private Map<Integer, Double> pending;                  // erkannter Wurf, wartet auf Klick

    public CalibrationService(@Value("${dartscounter.data-dir:${user.home}/dartscounter-data}") String dataDir,
                              @Value("${dartscounter.cameras}") String[] cameras,
                              @Value("${dartscounter.camera.positions-deg}") double[] positionsDeg,
                              @Value("${dartscounter.camera.distance-mm}") double distanceMm,
                              @Value("${dartscounter.camera.fov-deg}") double fovDeg,
                              @Value("${dartscounter.camera.width-px}") double widthPx) {
        this.file = Path.of(dataDir).resolve("calibration.json");
        this.cameraCount = cameras.length;
        for (int cam = 0; cam < cameraCount && cam < positionsDeg.length; cam++) {
            nominal.put(cam, CameraModel.nominal(positionsDeg[cam], distanceMm, fovDeg, widthPx));
        }
        this.saved = load();
    }

    /********* Ablauf *********/

    public synchronized Status start() {
        active = true;
        samples.clear();
        pending = null;
        return status();
    }

    public synchronized Status cancel() {
        active = false;
        samples.clear();
        pending = null;
        return status();
    }

    /** Vom ThrowCollector: true, wenn der Wurf für die Kalibrierung gebraucht wird. */
    public synchronized boolean offerThrow(Map<Integer, Double> pixels) {
        if (!active) return false;
        pending = new TreeMap<>(pixels); // neuer Wurf ersetzt einen noch nicht angeklickten
        return true;
    }

    public synchronized Status addPoint(double x, double y) {
        requireActive();
        if (pending == null) throw new ApiException(HttpStatus.CONFLICT, "Noch kein Wurf erkannt.");
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.hypot(x, y) > MAX_BOARD_MM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Die Position liegt nicht auf der Scheibe.");
        }
        samples.add(new ThrowSample(x, y, pending));
        pending = null;
        return status();
    }

    // erkannten Wurf verwerfen (z. B. weil nur ein Pfeil herausgezogen wurde)
    public synchronized Status skip() {
        requireActive();
        pending = null;
        return status();
    }

    public synchronized Status undo() {
        requireActive();
        if (!samples.isEmpty()) samples.remove(samples.size() - 1);
        return status();
    }

    public synchronized Status finish() {
        requireActive();
        Map<Integer, CameraModel> models = new TreeMap<>();
        Map<Integer, Double> rms = new TreeMap<>();
        List<String> missing = new ArrayList<>();

        for (int cam = 0; cam < cameraCount; cam++) {
            List<CameraModel.Sample> camSamples = samplesFor(cam);
            if (camSamples.size() < CameraModel.MIN_SAMPLES) {
                missing.add("Kamera " + (cam + 1) + ": " + camSamples.size());
                continue;
            }
            CameraModel model = CameraModel.fit(camSamples);
            if (model == null) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Die Würfe liegen zu dicht beieinander. Bitte über die ganze Scheibe verteilt werfen.");
            }
            models.put(cam, model);
            rms.put(cam, rmsPx(model, camSamples));
        }
        if (models.size() < 2) {
            throw new ApiException(HttpStatus.CONFLICT, "Zu wenige Würfe. Mindestens zwei Kameras brauchen je "
                    + CameraModel.MIN_SAMPLES + " erkannte Würfe (" + String.join(", ", missing) + ").");
        }

        // Probe: jeden Kalibrierwurf mit den neuen Modellen berechnen und mit dem Klick vergleichen
        double sum = 0, max = 0;
        int n = 0;
        for (ThrowSample s : samples) {
            Triangulator.Point p = Triangulator.locate(s.pixels(), models);
            if (p == null) continue;
            double err = Math.hypot(p.x() - s.x(), p.y() - s.y());
            sum += err;
            max = Math.max(max, err);
            n++;
        }
        saved = new Saved(Instant.now().toString(), n == 0 ? null : round(sum / n), n == 0 ? null : round(max),
                models, rms, List.copyOf(samples));
        save(saved);
        active = false;
        samples.clear();
        pending = null;
        return status();
    }

    /********* Abfragen *********/

    public synchronized Status status() {
        List<ThrowSample> current = active ? samples : (saved != null ? saved.samples() : List.of());
        List<CameraInfo> cams = new ArrayList<>();
        for (int cam = 0; cam < cameraCount; cam++) {
            final int c = cam;
            int count = (int) current.stream().filter(s -> s.pixels().containsKey(c)).count();
            Double rms = !active && saved != null ? saved.rmsPx().get(cam) : null;
            boolean calibrated = saved != null && saved.models().containsKey(cam);
            cams.add(new CameraInfo(cam, count, rms, calibrated));
        }
        return new Status(active, saved != null,
                saved != null ? saved.createdAt() : null,
                saved != null ? saved.errorMm() : null,
                saved != null ? saved.maxErrorMm() : null,
                current.size(), CameraModel.MIN_SAMPLES, CameraModel.RECOMMENDED_SAMPLES,
                pending == null ? null : List.copyOf(pending.keySet()), cams);
    }

    /** Modelle für das Spiel: kalibriert, sonst die grobe Schätzung aus der Konfiguration. */
    public synchronized Map<Integer, CameraModel> models() {
        return saved != null ? saved.models() : nominal;
    }

    public synchronized boolean isCalibrated() {
        return saved != null;
    }

    /********* intern *********/

    private void requireActive() {
        if (!active) throw new ApiException(HttpStatus.CONFLICT, "Es läuft keine Kalibrierung.");
    }

    private List<CameraModel.Sample> samplesFor(int cam) {
        List<CameraModel.Sample> list = new ArrayList<>();
        for (ThrowSample s : samples) {
            Double px = s.pixels().get(cam);
            if (px != null) list.add(new CameraModel.Sample(s.x(), s.y(), px));
        }
        return list;
    }

    private static double rmsPx(CameraModel model, List<CameraModel.Sample> list) {
        double sum = 0;
        for (CameraModel.Sample s : list) {
            double d = model.pixel(s.x(), s.y()) - s.pixel();
            sum += d * d;
        }
        return round(Math.sqrt(sum / list.size()));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private Saved load() {
        try {
            if (!Files.exists(file)) return null;
            return jsonMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), Saved.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Kann " + file + " nicht lesen", e);
        }
    }

    private void save(Saved value) {
        try {
            DataStore.writeAtomically(file, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Kann " + file + " nicht schreiben", e);
        }
    }

    // für Tests: Kalibrierung vergessen
    synchronized void reset() {
        saved = null;
        cancel();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
