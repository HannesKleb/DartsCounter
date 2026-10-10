package com.DartsCounter.DartsCounter_backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final HitService hitService;
    private final double[] centerX;
    private final int widthPx;
    private final int heightPx;
    private final boolean saveFrames;
    private final boolean logFrames;
    private final String framesDir;

    public ArrowDetection(HitService hitService,
                          @Value("${darts.camera.center-x}") double[] centerX,
                          @Value("${darts.camera.width-px}") int widthPx,
                          @Value("${darts.camera.height-px}") int heightPx,
                          @Value("${darts.debug.save-frames}") boolean saveFrames,
                          @Value("${darts.debug.log-frames}") boolean logFrames,
                          @Value("${darts.debug.frames-dir}") String framesDir) {
        this.hitService = hitService;
        this.centerX = centerX;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.saveFrames = saveFrames;
        this.logFrames = logFrames;
        this.framesDir = framesDir;
    }

    /** @return true, wenn die Erkennung neu gestartet wurde; false, wenn sie schon lief */
    public boolean start() {
        return isRunning.compareAndSet(false, true);
    }

    public void stop() {
        isRunning.set(false);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Läuft pro Kamera in einem eigenen Thread.
     * @param cam      Kamera-Nummer (Position in darts.cameras: 0, 1, 2)
     * @param camIndex OpenCV-Index der Kamera
     */
    @Async
    public void detect(int cam, int camIndex) {
        BackgroundSubtractor backSub = Video.createBackgroundSubtractorMOG2();

        VideoCapture camera = new VideoCapture(camIndex);
        if (!camera.isOpened()) {
            System.out.println("Camera " + camIndex + " not accessible");
            return;
        }
        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, widthPx);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, heightPx);
        System.out.println("Camera " + camIndex + " Resolution: "
                + camera.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x" + camera.get(Videoio.CAP_PROP_FRAME_HEIGHT));

        if (saveFrames) {
            try {
                Files.createDirectories(Path.of(framesDir, "arrow"));
                Files.createDirectories(Path.of(framesDir, "empty"));
            } catch (IOException e) {
                System.out.println("Ordner für Frames konnte nicht angelegt werden: " + e.getMessage());
            }
        }

        Mat frame = new Mat();
        Mat fgMask = new Mat();
        Mat fgMaskBlur = new Mat();
        Mat fgCleaned = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10));
        Size blurSize = new Size(7, 7);

        int frameCount = 0;
        boolean dartVisible = false;

        System.out.println("Camera " + camIndex + ": start capturing frames");

        try {
            while (isRunning.get() && camera.read(frame)) {
                backSub.apply(frame, fgMask);
                if (logFrames) System.out.println("Camera " + camIndex + " Frame: " + frameCount);

                if (frameCount % 2 == 0) {
                    // enhance IMG
                    Imgproc.blur(fgMask, fgMaskBlur, blurSize);
                    Imgproc.morphologyEx(fgMaskBlur, fgCleaned, Imgproc.MORPH_OPEN, kernel);

                    Double dartPos = showsDarts(fgCleaned);

                    // nur beim Erscheinen des Pfeils melden, nicht in jedem Frame erneut
                    if (dartPos != null && !dartVisible) {
                        hitService.report(cam, dartPos - centerX[cam]);
                    }
                    dartVisible = dartPos != null;

                    if (saveFrames) {
                        String folder = dartPos != null ? "arrow" : "empty";
                        Imgcodecs.imwrite(Path.of(framesDir, folder, "mask_frame_" + frameCount + "_" + camIndex + ".png").toString(), fgCleaned);
                    }
                }
                frameCount++;
            }
        } finally {
            camera.release();
            frame.release();
            fgMask.release();
            fgMaskBlur.release();
            fgCleaned.release();
            kernel.release();
        }
        System.out.println("Camera " + camIndex + ": finished");
    }

    /** @return x-Position des Pfeils im Bild (Pixel) oder null, wenn kein Pfeil zu sehen ist */
    public Double showsDarts(Mat image) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(image, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        // größte Kontur, die wie ein Pfeil aussieht
        double bestArea = 0;
        RotatedRect bestRect = null;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            MatOfPoint2f contourf2 = new MatOfPoint2f(contour.toArray());
            RotatedRect rotatedRect = Imgproc.minAreaRect(contourf2);
            contourf2.release();
            contour.release();

            double width = rotatedRect.size.width;
            double height = rotatedRect.size.height;
            if (Math.min(width, height) <= 0) continue;
            double ratio = Math.max(width, height) / Math.min(width, height);

            if (MIN_RATIO < ratio && ratio < MAX_RATIO && MIN_AREA < area && area < MAX_AREA && area > bestArea) {
                bestArea = area;
                bestRect = rotatedRect;
            }
        }

        if (bestRect == null) return null;

        // Mitte der zwei obersten Ecken (kleinstes y)
        Point[] vertices = new Point[4];
        bestRect.points(vertices);
        Arrays.sort(vertices, Comparator.comparingDouble(p -> p.y));
        double tipX = (vertices[0].x + vertices[1].x) / 2;

        if (logFrames) System.out.println("Dart: area=" + bestArea + " posX=" + tipX);
        return tipX;
    }
}
