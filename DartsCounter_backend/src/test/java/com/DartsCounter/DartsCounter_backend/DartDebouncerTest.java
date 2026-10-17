package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DartDebouncerTest {

    // 3 ruhige Bilder nötig, 3 leere Bilder setzen zurück, keine Anlaufzeit, Toleranz 10 px
    private final DartDebouncer d = new DartDebouncer(3, 3, 0, 10);

    @Test
    void flickerIsIgnored() {
        assertNull(d.update(300.0));
        assertNull(d.update(null));
        assertNull(d.update(500.0));
        assertNull(d.update(120.0)); // springt herum: Rauschen
        assertNull(d.update(null));
    }

    @Test
    void stableDartReportedOnce() {
        assertNull(d.update(300.0));
        assertNull(d.update(302.0));
        assertEquals(301, d.update(301.0), 2);
        assertNull(d.update(301.0)); // steckt weiter: nicht noch einmal
        assertNull(d.update(300.0));
    }

    @Test
    void secondDartElsewhereIsReported() {
        d.update(300.0); d.update(300.0); d.update(300.0);
        assertNull(d.update(600.0));
        assertNull(d.update(601.0));
        assertEquals(600, d.update(600.0), 2);
    }

    @Test
    void pullingOutSameDartIsNotReported() {
        d.update(300.0); d.update(300.0); d.update(300.0);
        d.update(null); // kurz nichts zu sehen
        assertNull(d.update(299.0));
        assertNull(d.update(300.0));
        assertNull(d.update(300.0)); // Herausziehen an derselben Stelle
    }

    @Test
    void afterQuietPeriodSameSpotCountsAgain() {
        d.update(300.0); d.update(300.0); d.update(300.0);
        d.update(null); d.update(null); d.update(null);
        d.update(300.0); d.update(300.0);
        assertEquals(300, d.update(300.0), 1);
    }

    @Test
    void warmupIgnored() {
        DartDebouncer warm = new DartDebouncer(2, 3, 5, 10);
        for (int i = 0; i < 5; i++) assertNull(warm.update(300.0));
        assertNull(warm.update(300.0));
        assertEquals(300, warm.update(300.0), 1);
    }
}
