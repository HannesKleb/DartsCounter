package com.DartsCounter.DartsCounter_backend;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sammelt die Meldungen der Kameras. Melden mindestens zwei Kameras innerhalb des Zeitfensters
 * einen Pfeil, wird trianguliert und das Feld berechnet.
 */
@Service
public class HitService {

    public record Hit(long id, String field, int score, double radius, double angle, long timestamp) {}

    private record Detection(double pixelX, long timestamp) {}

    private final Triangulation triangulation;
    private final ScoreCommunication scoreCommunication;
    private final long matchWindowMs;

    private final Map<Integer, Detection> pending = new HashMap<>();
    private long nextId = 1;
    private volatile Hit lastHit;

    public HitService(Triangulation triangulation, ScoreCommunication scoreCommunication,
                      @Value("${darts.detection.match-window-ms}") long matchWindowMs) {
        this.triangulation = triangulation;
        this.scoreCommunication = scoreCommunication;
        this.matchWindowMs = matchWindowMs;
    }

    // Wird von jedem Kamera-Thread aufgerufen, daher synchronized
    public synchronized void report(int cam, double pixelX) {
        long now = System.currentTimeMillis();
        pending.values().removeIf(d -> now - d.timestamp() > matchWindowMs);
        pending.put(cam, new Detection(pixelX, now));
        if (pending.size() < 2) return;

        Map<Integer, Double> pixels = new HashMap<>();
        pending.forEach((c, d) -> pixels.put(c, d.pixelX()));
        pending.clear();

        Triangulation.Point point = triangulation.triangulate(pixels);
        if (point == null) {
            System.out.println("Triangulation fehlgeschlagen: " + pixels);
            return;
        }
        Triangulation.Polar polar = Triangulation.toPolar(point);
        registerHit(polar.radiusNorm(), polar.angleDeg());
    }

    public synchronized Hit registerHit(double radius, double angle) {
        String field = scoreCommunication.coordsToField(radius, angle);
        Hit hit = new Hit(nextId++, field, scoreCommunication.fieldToScore(field), radius, angle, System.currentTimeMillis());
        lastHit = hit;
        System.out.println("Treffer: " + hit);
        return hit;
    }

    public Hit getLastHit() {
        return lastHit;
    }
}
