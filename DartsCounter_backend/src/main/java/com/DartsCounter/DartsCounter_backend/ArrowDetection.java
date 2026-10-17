package com.DartsCounter.DartsCounter_backend;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
public class ArrowDetection {
    // Grenzen, ab wann eine Kontur als Pfeil zählt
    private static final double MAX_RATIO = 5;
    private static final double MIN_RATIO = 1.4;
    private static final double MAX_AREA = 160000;
    private static final double MIN_AREA = 14000;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ThrowCollector collector;
    private final int widthPx;
    private final int heightPx;
    private final boolean saveFrames;

    public ArrowDetection(ThrowCollector collector,
                          @Value("${dartscounter.camera.width-px}") int widthPx,
                          @Value("${dartscounter.camera.height-px}") int heightPx,
                          @Value("${dartscounter.debug.save-frames:false}") boolean saveFrames) {
        this.collector = collector;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.saveFrames = saveFrames;
    }

    /** @return true, wenn neu gestartet; false, wenn die Erkennung schon lief */
    public boolean start() {
        return running.compareAndSet(false, true);
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Läuft pro Kamera in einem eigenen Thread.
     * @param camera Kamera-Nummer (Reihenfolge in dartscounter.cameras)
     * @param source Kamera-Index ("0") oder Pfad/URL einer Videodatei (zum Testen ohne Kamera)
     */
    @Async
    public void detect(int camera, String source) {
        VideoCapture capture = source.matches("\\d+") ? new VideoCapture(Integer.parseInt(source)) : new VideoCapture(source);
        if (!capture.isOpened()) {
            System.out.println("Kamera " + (camera + 1) + " nicht erreichbar: " + source);
            return;
        }
        // MJPG vor der Auflösung setzen: drei unkomprimierte 1080p-Kameras passen nicht durch die USB-Bandbreite des Pi
        capture.set(Videoio.CAP_PROP_FOURCC, VideoWriter.fourcc('M', 'J', 'P', 'G'));
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, widthPx);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, heightPx);
        System.out.println("Kamera " + (camera + 1) + ": " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x"
                + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));

        if (saveFrames) {
            new File("subFrames/arrow").mkdirs();
            new File("subFrames/empty").mkdirs();
        }

        BackgroundSubtractor backSub = Video.createBackgroundSubtractorMOG2();
        Mat frame = new Mat();
        Mat fgMask = new Mat();
        Mat fgMaskBlur = new Mat();
        Mat fgCleaned = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10));
        Size blurSize = new Size(7, 7);
        int frameCount = 0;
        boolean dartVisible = false;

        try {
            while (running.get() && capture.read(frame)) {
                backSub.apply(frame, fgMask);
                if (frameCount % 2 == 0) {
                    // enhance IMG
                    Imgproc.blur(fgMask, fgMaskBlur, blurSize);
                    Imgproc.morphologyEx(fgMaskBlur, fgCleaned, Imgproc.MORPH_OPEN, kernel);

                    Double dartX = showsDarts(fgCleaned);
                    // nur melden, wenn der Pfeil neu auftaucht, nicht in jedem Frame
                    if (dartX != null && !dartVisible) collector.report(camera, dartX);
                    dartVisible = dartX != null;

                    if (saveFrames) {
                        String folder = dartX != null ? "arrow" : "empty";
                        Imgcodecs.imwrite("subFrames/" + folder + "/mask_frame_" + frameCount + "_" + camera + ".png", fgCleaned);
                    }
                }
                frameCount++;
            }
        } finally {
            capture.release();
            frame.release();
            fgMask.release();
            fgMaskBlur.release();
            fgCleaned.release();
            kernel.release();
        }
        System.out.println("Kamera " + (camera + 1) + ": beendet");
    }

    /** @return Pixelspalte des Pfeils (Mitte der zwei obersten Ecken) oder null, wenn keiner zu sehen ist */
    public Double showsDarts(Mat image) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(image, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        // größte Kontur, die wie ein Pfeil aussieht
        double bestArea = 0;
        RotatedRect best = null;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            RotatedRect rect = Imgproc.minAreaRect(contour2f);
            contour2f.release();
            contour.release();

            double minSide = Math.min(rect.size.width, rect.size.height);
            if (minSide <= 0) continue; // Division durch 0 vermeiden
            double ratio = Math.max(rect.size.width, rect.size.height) / minSide;
            if (MIN_RATIO < ratio && ratio < MAX_RATIO && MIN_AREA < area && area < MAX_AREA && area > bestArea) {
                bestArea = area;
                best = rect;
            }
        }
        if (best == null) return null;

        Point[] vertices = new Point[4];
        best.points(vertices);
        Arrays.sort(vertices, Comparator.comparingDouble(p -> p.y));
        return (vertices[0].x + vertices[1].x) / 2;
    }
}
