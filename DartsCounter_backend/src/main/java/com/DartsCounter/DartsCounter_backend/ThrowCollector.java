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
 * ersten Meldung kommt, gehört zum selben Pfeil. Danach geht der Wurf an die Kalibrierung (wenn sie läuft)
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
    private long nextId = 1;
    private volatile Hit lastHit;

    public ThrowCollector(CalibrationService calibration, @Value("${dartscounter.detection.window-ms:400}") long windowMs) {
        this.calibration = calibration;
        this.windowMs = windowMs;
    }

    // von jedem Kamera-Thread aufgerufen
    public synchronized void report(int camera, double pixelX) {
        if (group == null) {
            group = new TreeMap<>();
            timer.schedule(this::flush, windowMs, TimeUnit.MILLISECONDS);
        }
        group.put(camera, pixelX);
    }

    /** Wurf abschließen (sonst nach Ablauf des Zeitfensters automatisch). */
    public void flush() {
        Map<Integer, Double> pixels;
        synchronized (this) {
            if (group == null) return;
            pixels = group;
            group = null;
        }
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
