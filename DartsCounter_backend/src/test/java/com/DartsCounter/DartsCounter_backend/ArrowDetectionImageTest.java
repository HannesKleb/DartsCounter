package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

// Erkennung auf künstlichen Masken (schwarz = Hintergrund, weiß = Bewegung), ohne Kamera
class ArrowDetectionImageTest {

    @BeforeAll
    static void loadOpenCV() {
        OpenCV.loadLocally();
    }

    private final ArrowDetection detection = new ArrowDetection(null, 1920, 1080, false);

    private static Mat mask() {
        return Mat.zeros(1080, 1920, CvType.CV_8UC1);
    }

    private static void fillRotatedRect(Mat image, Point center, Size size, double angle) {
        Point[] corners = new Point[4];
        new RotatedRect(center, size, angle).points(corners);
        Imgproc.fillConvexPoly(image, new MatOfPoint(corners), new Scalar(255));
    }

    @Test
    void findsDartShapedBlob() {
        Mat image = mask();
        fillRotatedRect(image, new Point(1200, 600), new Size(110, 330), 0); // ca. 36 000 px, Verhältnis 3
        Double x = detection.showsDarts(image);
        assertNotNull(x);
        assertEquals(1200, x, 2);
    }

    @Test
    void ignoresEmptyNoiseAndWrongShapes() {
        assertNull(detection.showsDarts(mask()));

        Mat small = mask();
        fillRotatedRect(small, new Point(500, 500), new Size(40, 120), 0);   // zu klein
        assertNull(detection.showsDarts(small));

        Mat square = mask();
        fillRotatedRect(square, new Point(500, 500), new Size(200, 200), 0); // quadratisch, kein Pfeil
        assertNull(detection.showsDarts(square));

        Mat huge = mask();
        fillRotatedRect(huge, new Point(960, 540), new Size(500, 1000), 0);  // ganzes Bild ändert sich (Licht)
        assertNull(detection.showsDarts(huge));
    }

    @Test
    void smallImagesUseScaledLimits() {
        // gleicher Pfeil wie oben, aber im 640x480-Bild (ein Drittel so breit)
        Mat image = Mat.zeros(480, 640, CvType.CV_8UC1);
        fillRotatedRect(image, new Point(400, 200), new Size(37, 110), 0);
        assertEquals(400, detection.showsDarts(image), 2);

        Mat tiny = Mat.zeros(480, 640, CvType.CV_8UC1);
        fillRotatedRect(tiny, new Point(400, 200), new Size(13, 40), 0); // zu klein für einen Pfeil
        assertNull(detection.showsDarts(tiny));
    }

    @Test
    void picksDartNotBiggerNoise() {
        Mat image = mask();
        fillRotatedRect(image, new Point(1500, 500), new Size(300, 300), 0); // großer Fleck, falsche Form
        fillRotatedRect(image, new Point(700, 600), new Size(100, 300), 0);  // Pfeil
        assertEquals(700, detection.showsDarts(image), 2);
    }
}
