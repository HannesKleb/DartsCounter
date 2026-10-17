package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Verfolgt die Pfeilspitzen einer Kamera über mehrere Bilder und entscheidet, wann ein neuer Pfeil gemeldet wird.
 * - Gemeldet wird erst, wenn eine Spitze mehrere Bilder hintereinander ruhig an derselben Stelle ist
 *   (Rauschen und Flackern springen herum).
 * - Mehrere Pfeile werden getrennt verfolgt; ein zweiter Pfeil geht nicht unter, nur weil der erste noch sichtbar ist.
 * - Derselbe Pfeil wird nur einmal gemeldet.
 * - Solange sich etwas Großes bewegt (Hand, Arm), wird nichts gemeldet. Kurz danach werden Stellen ignoriert, an
 *   denen vorher schon ein Pfeil gemeldet wurde: Dort bleibt nach dem Herausziehen eine Lücke, die sonst wie ein
 *   neuer Pfeil aussähe. Ein frisch geworfener Pfeil an einer neuen Stelle zählt dagegen sofort.
 * - Nach dem Start oder einem Neu-Lernen des Hintergrunds wird eine Anlaufzeit abgewartet.
 */
class DartTracker {

    record Tip(double x, double y) {}

    private static final class Track {
        double x, y;
        int count = 1;
        int missed;
        boolean reported;

        Track(Tip t) {
            x = t.x();
            y = t.y();
        }
    }

    private final int stableFrames;
    private final int busyFrames;
    private final int historyFrames;
    private final int clearFrames;
    private final double tolX;
    private final double tolY;

    private int frame;
    private int quietFrom;      // vorher wird nichts gemeldet (Anlaufzeit)
    private int lastBigMotion = Integer.MIN_VALUE / 2;
    private int emptyCount;
    private final List<double[]> history = new ArrayList<>(); // {x, Bild} früher gemeldeter Pfeile
    private final List<Track> tracks = new ArrayList<>();
    private final List<Double> reported = new ArrayList<>();

    DartTracker(int stableFrames, int warmupFrames, int busyFrames, int clearFrames, double tolX, double tolY) {
        this(stableFrames, warmupFrames, busyFrames, clearFrames, tolX, tolY, 60 * 30);
    }

    DartTracker(int stableFrames, int warmupFrames, int busyFrames, int clearFrames, double tolX, double tolY,
                int historyFrames) {
        this.stableFrames = stableFrames;
        this.busyFrames = busyFrames;
        this.historyFrames = historyFrames;
        this.clearFrames = clearFrames;
        this.tolX = tolX;
        this.tolY = tolY;
        this.quietFrom = warmupFrames;
    }

    /** Alles vergessen und eine Anlaufzeit abwarten (z. B. nachdem der Hintergrund neu gelernt wurde). */
    void reset(int warmupFrames) {
        tracks.clear();
        reported.clear();
        quietFrom = Math.max(quietFrom, frame + warmupFrames);
    }

    /**
     * @param tips      Spitzen, die in diesem Bild gefunden wurden
     * @param bigMotion ob sich gerade etwas Großes bewegt (Hand)
     * @return Spalten der Pfeile, die jetzt neu gemeldet werden sollen
     */
    List<Double> update(List<Tip> tips, boolean bigMotion) {
        frame++;
        if (bigMotion) {
            lastBigMotion = frame;
            tracks.clear(); // erst nach der Bewegung neu zählen
            emptyCount = 0;
            return List.of();
        }

        for (Track t : tracks) t.missed++;
        for (Tip tip : tips) {
            Track best = null;
            double bestDist = Double.MAX_VALUE;
            for (Track t : tracks) {
                double dx = Math.abs(t.x - tip.x()), dy = Math.abs(t.y - tip.y());
                if (dx <= tolX && dy <= tolY && dx < bestDist) {
                    best = t;
                    bestDist = dx;
                }
            }
            if (best == null) {
                tracks.add(new Track(tip));
            } else {
                best.count++;
                best.missed = 0;
                best.x += (tip.x() - best.x) / best.count; // gleitender Mittelwert
                best.y += (tip.y() - best.y) / best.count;
            }
        }
        tracks.removeIf(t -> t.missed > 2);

        if (tips.isEmpty()) {
            if (++emptyCount >= clearFrames) reported.clear();
        } else {
            emptyCount = 0;
        }
        if (frame < quietFrom) return List.of();
        history.removeIf(h -> frame - h[1] > historyFrames);
        boolean afterMotion = frame - lastBigMotion <= busyFrames;

        List<Double> out = new ArrayList<>();
        for (Track t : tracks) {
            if (t.reported || t.count < stableFrames) continue;
            t.reported = true;
            double x = t.x;
            if (reported.stream().anyMatch(r -> Math.abs(r - x) <= tolX)) continue; // derselbe Pfeil
            if (afterMotion && history.stream().anyMatch(h -> Math.abs(h[0] - x) <= tolX)) continue; // Lücke
            reported.add(x);
            history.add(new double[] {x, frame});
            out.add(x);
        }
        return out;
    }
}
