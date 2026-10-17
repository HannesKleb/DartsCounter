package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import nu.pattern.OpenCV;

// Erkennung ohne Kamera: künstliche Masken (schwarz = Hintergrund, weiß = Veränderung) und künstliche Bildfolgen
class DartFrameProcessorTest {

    @BeforeAll
    static void loadOpenCV() {
        OpenCV.loadLocally();
    }

    private static Mat mask() {
        return Mat.zeros(480, 640, CvType.CV_8UC1);
    }

    private static void fillRotatedRect(Mat image, Point center, Size size, double angle) {
        Point[] corners = new Point[4];
        new RotatedRect(center, size, angle).points(corners);
        Imgproc.fillConvexPoly(image, new MatOfPoint(corners), new Scalar(255));
    }

    private static DartFrameProcessor.Detection find(Mat m) {
        return DartFrameProcessor.findTips(m, true);
    }

    @Test
    void thinShaftIsADart() {
        // wie in den echten Aufnahmen: dünner Schaft, beginnt an der Scheibenkante (oben)
        Mat m = mask();
        fillRotatedRect(m, new Point(320, 300), new Size(8, 120), 0);
        List<DartTracker.Tip> tips = find(m).tips();
        assertEquals(1, tips.size());
        assertEquals(320, tips.get(0).x(), 2);
        assertEquals(240, tips.get(0).y(), 2);
    }

    @Test
    void tiltedDartMeasuresTipNotMiddle() {
        Mat m = mask();
        fillRotatedRect(m, new Point(200, 320), new Size(10, 170), 30); // schräg stehender Pfeil
        DartTracker.Tip tip = find(m).tips().get(0);
        // die Spitze liegt oben, deutlich seitlich von der Mitte des Flecks
        assertTrue(Math.abs(tip.x() - 200) > 25, "Spitze bei " + tip.x());
        assertEquals(320 - 85 * Math.cos(Math.toRadians(30)), tip.y(), 6);
    }

    @Test
    void severalDartsAreAllFound() {
        Mat m = mask();
        fillRotatedRect(m, new Point(100, 300), new Size(8, 120), 0);
        fillRotatedRect(m, new Point(400, 310), new Size(30, 140), 0);
        assertEquals(2, find(m).tips().size());
    }

    @Test
    void noiseFlatShapesAndHandsAreNoDarts() {
        assertTrue(find(mask()).tips().isEmpty());

        Mat tiny = mask();
        fillRotatedRect(tiny, new Point(300, 300), new Size(5, 20), 0);   // zu klein
        assertTrue(find(tiny).tips().isEmpty());

        Mat flat = mask();
        fillRotatedRect(flat, new Point(300, 300), new Size(120, 40), 0); // breiter als hoch
        assertTrue(find(flat).tips().isEmpty());

        Mat hand = mask();
        fillRotatedRect(hand, new Point(300, 330), new Size(200, 180), 0); // große Fläche: Hand
        DartFrameProcessor.Detection d = find(hand);
        assertTrue(d.tips().isEmpty());
        assertTrue(d.bigMotion());
    }

    @Test
    void bottomTipForUprightCameras() {
        Mat m = mask();
        fillRotatedRect(m, new Point(320, 180), new Size(8, 120), 0);
        DartTracker.Tip tip = DartFrameProcessor.findTips(m, false).tips().get(0);
        assertEquals(240, tip.y(), 2);
    }

    // künstliches Kamerabild: grauer Hintergrund, Scheibenkante bei y=240, Pfeile hängen nach unten
    private static Mat scene(int... dartXs) {
        Mat img = new Mat(480, 640, CvType.CV_8UC3, new Scalar(90, 110, 130));
        Imgproc.rectangle(img, new Point(0, 0), new Point(640, 240), new Scalar(20, 20, 20), -1);
        for (int x : dartXs) {
            Imgproc.rectangle(img, new Point(x - 3, 240), new Point(x + 3, 330), new Scalar(200, 200, 200), -1);
            Imgproc.rectangle(img, new Point(x - 15, 330), new Point(x + 15, 380), new Scalar(40, 40, 230), -1);
        }
        return img;
    }

    @Test
    void sequenceReportsEachDartOnce() {
        DartFrameProcessor p = new DartFrameProcessor(10, 3, 640, true);
        StringBuilder reports = new StringBuilder();
        int frame = 0;
        // 3 s leer, dann Pfeil 1, nach 2 s Pfeil 2 dazu
        for (; frame < 30; frame++) assertTrue(p.process(scene()).isEmpty());
        for (; frame < 50; frame++) p.process(scene(200)).forEach(x -> reports.append(Math.round(x)).append(' '));
        for (; frame < 70; frame++) p.process(scene(200, 450)).forEach(x -> reports.append(Math.round(x)).append(' '));
        assertEquals("200 450 ", reports.toString());
    }

    @Test
    void lightingJumpDoesNotCreateThrows() {
        DartFrameProcessor p = new DartFrameProcessor(10, 3, 640, true);
        for (int i = 0; i < 30; i++) p.process(scene());
        Mat bluish = scene();
        Imgproc.cvtColor(bluish, bluish, Imgproc.COLOR_BGR2RGB); // Farben springen wie beim Weißabgleich
        boolean any = false;
        for (int i = 0; i < 30; i++) any |= !p.process(bluish).isEmpty();
        assertFalse(any);
        assertTrue(p.resets() >= 1);
    }
}
