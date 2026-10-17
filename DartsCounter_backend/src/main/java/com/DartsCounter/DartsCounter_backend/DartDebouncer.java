package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Entprellt die Erkennung einer Kamera, damit Rauschen, Schatten und Lichtflackern keine Würfe auslösen.
 * - Gemeldet wird erst, wenn ein Pfeil mehrere Bilder hintereinander ruhig an derselben Stelle zu sehen ist.
 * - Ein schon gemeldeter Pfeil wird nicht noch einmal gemeldet (auch nicht beim Herausziehen),
 *   ein weiterer Pfeil an einer anderen Stelle schon.
 * - Erst wenn eine Weile gar nichts mehr zu sehen ist, beginnt alles von vorn.
 * - Die ersten Bilder nach dem Start werden ignoriert, solange die Hintergrund-Erkennung noch lernt.
 */
class DartDebouncer {
    private final int stableFrames;
    private final int clearFrames;
    private final int warmupFrames;
    private final double tolerancePx;

    private int frames;
    private Double candidate;
    private int candidateCount;
    private int emptyCount;
    private final List<Double> reported = new ArrayList<>();

    DartDebouncer(int stableFrames, int clearFrames, int warmupFrames, double tolerancePx) {
        this.stableFrames = stableFrames;
        this.clearFrames = clearFrames;
        this.warmupFrames = warmupFrames;
        this.tolerancePx = tolerancePx;
    }

    /** @param x erkannte Spalte in diesem Bild oder null; @return Spalte, wenn jetzt ein neuer Pfeil gemeldet werden soll */
    Double update(Double x) {
        frames++;
        if (frames <= warmupFrames) return null;

        if (x == null) {
            candidate = null;
            candidateCount = 0;
            if (!reported.isEmpty() && ++emptyCount >= clearFrames) reported.clear();
            return null;
        }
        emptyCount = 0;

        if (candidate != null && Math.abs(x - candidate) <= tolerancePx) {
            candidateCount++;
            candidate += (x - candidate) / candidateCount; // gleitender Mittelwert
        } else {
            candidate = x;
            candidateCount = 1;
        }
        if (candidateCount < stableFrames) return null;

        double stable = candidate;
        boolean known = reported.stream().anyMatch(r -> Math.abs(r - stable) <= tolerancePx);
        if (known) return null;
        reported.add(stable);
        return stable;
    }
}
