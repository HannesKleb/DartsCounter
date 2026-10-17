package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DartTrackerTest {

    // 3 ruhige Bilder nötig, keine Anlaufzeit, 4 Bilder Ruhe nach Hand, 3 leere Bilder = Pfeile weg
    private final DartTracker t = new DartTracker(3, 0, 4, 3, 10, 10);

    private static List<DartTracker.Tip> tips(double... xs) {
        List<DartTracker.Tip> l = new ArrayList<>();
        for (double x : xs) l.add(new DartTracker.Tip(x, 240));
        return l;
    }

    private List<Double> feed(int frames, double... xs) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < frames; i++) out.addAll(t.update(tips(xs), false));
        return out;
    }

    @Test
    void flickerIsIgnored() {
        assertTrue(t.update(tips(300), false).isEmpty());
        assertTrue(t.update(tips(500), false).isEmpty());
        assertTrue(t.update(tips(120), false).isEmpty());
        assertTrue(t.update(tips(), false).isEmpty());
    }

    @Test
    void stableDartReportedOnce() {
        assertEquals(List.of(300.0), feed(10, 300));
    }

    @Test
    void secondDartWhileFirstStillVisible() {
        feed(5, 300);
        assertEquals(List.of(500.0), feed(5, 300, 500));
    }

    @Test
    void handBlocksAndGapAfterwardsIsIgnored() {
        feed(5, 300);
        feed(3); // Pfeil wird in den Hintergrund gelernt
        for (int i = 0; i < 5; i++) assertTrue(t.update(tips(), true).isEmpty()); // Hand zieht den Pfeil
        // Lücke, wo der Pfeil war, für kurze Zeit sichtbar: nicht melden
        assertTrue(feed(4, 300).isEmpty());
    }

    @Test
    void dartRightAfterArmMovementCounts() {
        assertTrue(t.update(tips(), true).isEmpty()); // Arm beim Werfen kurz im Bild
        assertEquals(List.of(450.0), feed(4, 450));    // neuer Pfeil an neuer Stelle: sofort
    }

    @Test
    void sameSpotCountsAgainAfterDartsGone() {
        feed(5, 300);
        feed(3);
        assertEquals(List.of(300.0), feed(5, 300));
    }

    @Test
    void warmupIgnored() {
        DartTracker warm = new DartTracker(2, 5, 4, 3, 10, 10);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.addAll(warm.update(tips(300), false));
        assertTrue(out.isEmpty());
        for (int i = 0; i < 3; i++) out.addAll(warm.update(tips(300), false));
        assertEquals(List.of(300.0), out);
    }
}
