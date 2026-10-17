package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

// Rechnung ohne Kameras: "echte" Kameras sind absichtlich anders montiert als in der Konfiguration
class CalibrationMathTest {

    // so sitzen die Kameras wirklich: Position, Abstand und Sichtfeld weichen vom Plan ab,
    // außerdem sind sie leicht verdreht (Bild um einige Dutzend Pixel verschoben)
    static final CameraModel[] REAL = {
            CameraModel.nominal(4, 470, 62, 1920).shifted(35),
            CameraModel.nominal(117, 530, 58, 1920).shifted(-50),
            CameraModel.nominal(246, 505, 61, 1920).shifted(20)
    };
    // so stehen sie in application.properties
    static final Map<Integer, CameraModel> PLANNED = Map.of(
            0, CameraModel.nominal(0, 500, 60, 1920),
            1, CameraModel.nominal(120, 500, 60, 1920),
            2, CameraModel.nominal(240, 500, 60, 1920));

    static double[] randomBoardPoint(Random random) {
        double r = 165 * Math.sqrt(random.nextDouble()), a = random.nextDouble() * 2 * Math.PI;
        return new double[] {r * Math.sin(a), -r * Math.cos(a)};
    }

    static Map<Integer, Double> seen(double x, double y, Random random, double noisePx) {
        Map<Integer, Double> pixels = new HashMap<>();
        for (int cam = 0; cam < REAL.length; cam++) pixels.put(cam, REAL[cam].pixel(x, y) + random.nextGaussian() * noisePx);
        return pixels;
    }

    @Test
    void fields() {
        assertEquals("DB", BoardGeometry.fieldFromXY(0, 3));
        assertEquals("SB", BoardGeometry.fieldFromXY(0, -10));
        assertEquals("T20", BoardGeometry.fieldFromXY(0, -103));
        assertEquals("D20", BoardGeometry.fieldFromXY(0, -166));
        assertEquals("S6", BoardGeometry.fieldFromXY(140, 0));    // rechts
        assertEquals("S3", BoardGeometry.fieldFromXY(0, 140));    // unten (y nach unten)
        assertEquals("T11", BoardGeometry.fieldFromXY(-103, 0));  // links
        assertEquals("M", BoardGeometry.fieldFromXY(0, 175));
        assertEquals(60, BoardGeometry.fieldValue("T20"));
        assertEquals(50, BoardGeometry.fieldValue("DB"));
    }

    @Test
    void exactModelHitsExactly() {
        Map<Integer, CameraModel> real = Map.of(0, REAL[0], 1, REAL[1], 2, REAL[2]);
        Triangulator.Point p = Triangulator.locate(seen(40, -103, new Random(1), 0), real);
        assertEquals(40, p.x(), 1e-6);
        assertEquals(-103, p.y(), 1e-6);
        // nur eine Kamera reicht nicht
        assertNull(Triangulator.locate(Map.of(0, 960.0), real));
    }

    @Test
    void calibrationCorrectsMounting() {
        Random random = new Random(42);
        // 15 Kalibrierwürfe (Empfehlung), Klick ca. 1 mm daneben, Erkennung ca. 1,5 Pixel ungenau
        List<List<CameraModel.Sample>> perCam = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < CameraModel.RECOMMENDED_SAMPLES; i++) {
            double[] p = randomBoardPoint(random);
            Map<Integer, Double> pixels = seen(p[0], p[1], random, 1.5);
            double clickX = p[0] + random.nextGaussian() * 1.0, clickY = p[1] + random.nextGaussian() * 1.0;
            pixels.forEach((cam, px) -> perCam.get(cam).add(new CameraModel.Sample(clickX, clickY, px)));
        }
        Map<Integer, CameraModel> fitted = new HashMap<>();
        for (int cam = 0; cam < 3; cam++) fitted.put(cam, CameraModel.fit(perCam.get(cam)));

        // 1000 neue Würfe: Fehler mit Plan-Werten vs. nach Kalibrierung
        double planned = 0, calibrated = 0;
        int rightField = 0;
        for (int i = 0; i < 1000; i++) {
            double[] p = randomBoardPoint(random);
            Map<Integer, Double> pixels = seen(p[0], p[1], random, 1.5);
            Triangulator.Point a = Triangulator.locate(pixels, PLANNED);
            Triangulator.Point b = Triangulator.locate(pixels, fitted);
            planned += Math.hypot(a.x() - p[0], a.y() - p[1]);
            calibrated += Math.hypot(b.x() - p[0], b.y() - p[1]);
            if (BoardGeometry.fieldFromXY(b.x(), b.y()).equals(BoardGeometry.fieldFromXY(p[0], p[1]))) rightField++;
        }
        planned /= 1000;
        calibrated /= 1000;
        System.out.printf("Mittlerer Fehler: ohne Kalibrierung %.1f mm, mit Kalibrierung %.1f mm, richtiges Feld %d/1000%n",
                planned, calibrated, rightField);
        assertTrue(planned > 10, "Plan-Werte sollten deutlich daneben liegen: " + planned);
        assertTrue(calibrated < 2, "Kalibrierung zu ungenau: " + calibrated);
        assertTrue(rightField > 930, "zu oft falsches Feld: " + rightField);
    }

    @Test
    void wrongClickIsDiscarded() {
        Random random = new Random(11);
        List<CameraModel.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double[] p = randomBoardPoint(random);
            samples.add(new CameraModel.Sample(p[0], p[1], REAL[0].pixel(p[0], p[1]) + random.nextGaussian()));
        }
        // ein Klick 6 cm daneben
        double[] p = randomBoardPoint(random);
        samples.add(new CameraModel.Sample(p[0] + 60, p[1], REAL[0].pixel(p[0], p[1])));

        CameraModel.Fit fit = CameraModel.fitRobust(samples);
        assertEquals(1, fit.discarded());
        assertEquals(REAL[0].pixel(20, -50), fit.model().pixel(20, -50), 3);
    }

    @Test
    void tooFewOrSamePointsGiveNoModel() {
        List<CameraModel.Sample> few = new ArrayList<>();
        for (int i = 0; i < CameraModel.MIN_SAMPLES - 1; i++) few.add(new CameraModel.Sample(i * 10, 0, REAL[0].pixel(i * 10, 0)));
        assertNull(CameraModel.fit(few));

        List<CameraModel.Sample> same = new ArrayList<>();
        for (int i = 0; i < 8; i++) same.add(new CameraModel.Sample(50, 50, REAL[0].pixel(50, 50)));
        assertNull(CameraModel.fit(same));

        List<CameraModel.Sample> good = new ArrayList<>();
        Random random = new Random(3);
        for (int i = 0; i < CameraModel.MIN_SAMPLES; i++) {
            double[] p = randomBoardPoint(random);
            good.add(new CameraModel.Sample(p[0], p[1], REAL[0].pixel(p[0], p[1])));
        }
        CameraModel model = CameraModel.fit(good);
        assertNotNull(model);
        assertEquals(REAL[0].pixel(10, -20), model.pixel(10, -20), 1e-3);
    }
}
