package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ScoringTests {

    private final ScoreCommunication score = new ScoreCommunication();
    private final Triangulation triangulation = new Triangulation(new double[] {0, 120, 240}, 50, 60, 1920, false);

    @Test
    void fields() {
        assertEquals("IB", score.coordsToField(0.01, 0));
        assertEquals("OB", score.coordsToField(0.05, 0));
        assertEquals("S20", score.coordsToField(0.3, 0));
        assertEquals("S20", score.coordsToField(0.3, 355));
        assertEquals("T20", score.coordsToField(0.45, 0));
        assertEquals("D20", score.coordsToField(0.74, 0));
        assertEquals("MISS", score.coordsToField(0.8, 0));
        assertEquals("S6", score.coordsToField(0.3, 270));   // rechts
        assertEquals("S11", score.coordsToField(0.3, 90));   // links
        assertEquals("T3", score.coordsToField(0.46, 180));  // unten
        assertEquals("S5", score.coordsToField(0.3, 18));
        assertEquals(60, score.fieldToScore("T20"));
        assertEquals(50, score.fieldToScore("IB"));
        assertEquals(36, score.fieldToScore("D18"));
    }

    @Test
    void centerPixelsHitBull() {
        Triangulation.Point p = triangulation.triangulate(Map.of(0, 0.0, 1, 0.0, 2, 0.0));
        assertNotNull(p);
        assertEquals(0, p.x(), 1e-9);
        assertEquals(0, p.y(), 1e-9);
    }

    @Test
    void triangulatesKnownPoint() {
        // Pfeil bei x = 5 cm (rechts), y = 10 cm (oben): Pixel pro Kamera zurückrechnen
        double tx = 5, ty = 10;
        double f = 1920 / (2 * Math.tan(Math.toRadians(30)));
        Map<Integer, Double> pixels = new java.util.HashMap<>();
        double[] positions = {0, 120, 240};
        for (int cam = 0; cam < 3; cam++) {
            double a = Math.toRadians(positions[cam]);
            double cx = -Math.sin(a) * 50, cy = Math.cos(a) * 50;
            double heading = Math.atan2(-(tx - cx), ty - cy);       // gleiche Konvention wie Triangulation
            double offset = Math.toRadians(positions[cam] + 180) - heading;
            offset = Math.atan2(Math.sin(offset), Math.cos(offset)); // auf -pi..pi
            pixels.put(cam, Math.tan(offset) * f);
        }
        Triangulation.Point p = triangulation.triangulate(pixels);
        assertNotNull(p);
        assertEquals(tx, p.x(), 1e-6);
        assertEquals(ty, p.y(), 1e-6);

        Triangulation.Polar polar = Triangulation.toPolar(p);
        assertEquals("S1", score.coordsToField(polar.radiusNorm(), polar.angleDeg())); // rechts oben neben der 20
    }
}
