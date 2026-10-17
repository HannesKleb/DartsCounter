package com.DartsCounter.DartsCounter_backend;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * Sammelt die Meldungen der Kamera-Threads zu einem Wurf: Alles, was innerhalb des Zeitfensters nach der
 * ersten Meldung kommt, gehört zum selben Pfeil. Meldet eine Kamera im selben Fenster einen zweiten Pfeil,
 * ist der erste Wurf sofort abgeschlossen. Danach geht der Wurf an die Kalibrierung (wenn sie läuft)
 * oder wird trianguliert und als Treffer gespeichert.
 */
@Service
public class ThrowCollector {

    public record Hit(long id, String field, int score, double x, double y, boolean calibrated, long timestamp) {}

    private final CalibrationService calibration;
    private final long windowMs;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "throw-collector");
        t.setDaemon(true);
        return t;
    });

    private Map<Integer, Double> group;   // aktueller Wurf, Kamera -> Pixelspalte
    private long generation;              // damit ein alter Zeitgeber keinen neuen Wurf zu früh abschließt
    private long nextId = 1;
    private volatile Hit lastHit;

    public ThrowCollector(CalibrationService calibration, @Value("${dartscounter.detection.window-ms:1200}") long windowMs) {
        this.calibration = calibration;
        this.windowMs = windowMs;
    }

    // von jedem Kamera-Thread aufgerufen
    public void report(int camera, double pixelX) {
        Map<Integer, Double> finished = null;
        synchronized (this) {
            if (group != null && group.containsKey(camera)) {
                // dieselbe Kamera sieht schon den nächsten Pfeil: alten Wurf abschließen
                finished = group;
                group = null;
            }
            if (group == null) {
                group = new TreeMap<>();
                long gen = ++generation;
                timer.schedule(() -> flush(gen), windowMs, TimeUnit.MILLISECONDS);
            }
            group.put(camera, pixelX);
        }
        if (finished != null) finish(finished);
    }

    /** Wurf sofort abschließen (sonst nach Ablauf des Zeitfensters automatisch). */
    public void flush() {
        flush(-1);
    }

    private void flush(long gen) {
        Map<Integer, Double> pixels;
        synchronized (this) {
            if (group == null || (gen >= 0 && gen != generation)) return;
            pixels = group;
            group = null;
        }
        finish(pixels);
    }

    private void finish(Map<Integer, Double> pixels) {
        if (calibration.offerThrow(pixels)) return;

        Triangulator.Point p = Triangulator.locate(pixels, calibration.models());
        if (p == null) return; // nur eine Kamera hat etwas gesehen
        String field = BoardGeometry.fieldFromXY(p.x(), p.y());
        synchronized (this) {
            lastHit = new Hit(nextId++, field, BoardGeometry.fieldValue(field),
                    Math.round(p.x() * 10) / 10.0, Math.round(p.y() * 10) / 10.0,
                    calibration.isCalibrated(), System.currentTimeMillis());
        }
    }

    public Hit lastHit() {
        return lastHit;
    }

    @PreDestroy
    void shutdown() {
        timer.shutdownNow();
    }
}
