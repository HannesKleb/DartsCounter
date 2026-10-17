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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
public class ArrowDetection {
    // Grenzen, ab wann eine Kontur als Pfeil zählt. Flächen gelten für ein 1920 px breites Bild
    // und werden für kleinere Bilder mitskaliert.
    private static final double MAX_RATIO = 5;
    private static final double MIN_RATIO = 1.4;
    private static final double MAX_AREA = 160000;
    private static final double MIN_AREA = 14000;
    private static final double REFERENCE_WIDTH = 1920;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ThrowCollector collector;
    private final int widthPx;
    private final int heightPx;
    private final int fps;
    private final int processWidth;
    private final int stableFrames;
    private final boolean saveFrames;

    @Autowired
    public ArrowDetection(ThrowCollector collector,
                          @Value("${dartscounter.camera.width-px}") int widthPx,
                          @Value("${dartscounter.camera.height-px}") int heightPx,
                          @Value("${dartscounter.camera.fps:15}") int fps,
                          @Value("${dartscounter.detection.process-width:640}") int processWidth,
                          @Value("${dartscounter.detection.stable-frames:3}") int stableFrames,
                          @Value("${dartscounter.debug.save-frames:false}") boolean saveFrames) {
        this.collector = collector;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.fps = fps;
        this.processWidth = processWidth;
        this.stableFrames = stableFrames;
        this.saveFrames = saveFrames;
    }

    // für Tests
    ArrowDetection(ThrowCollector collector, int widthPx, int heightPx, boolean saveFrames) {
        this(collector, widthPx, heightPx, 15, widthPx, 3, saveFrames);
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
        capture.set(Videoio.CAP_PROP_FPS, fps); // weniger Bilder pro Sekunde entlasten den Pi
        System.out.println("Kamera " + (camera + 1) + ": " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x"
                + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));

        if (saveFrames) {
            new File("subFrames/arrow").mkdirs();
            new File("subFrames/empty").mkdirs();
        }

        BackgroundSubtractor backSub = Video.createBackgroundSubtractorMOG2();
        Mat frame = new Mat();
        Mat small = new Mat();
        Mat fgMask = new Mat();
        Mat fgMaskBlur = new Mat();
        Mat fgCleaned = new Mat();
        Mat kernel = null;
        Size blurSize = null;
        double toCameraPx = 1; // verkleinerte Spalte -> Spalte im Kamerabild
        int frameCount = 0;
        DartDebouncer debouncer = null;

        try {
            while (running.get() && capture.read(frame)) {
                // auf einem verkleinerten Bild rechnen: viel weniger Arbeit für den Pi, Spalte wird zurückgerechnet
                Mat work = frame;
                if (frame.cols() > processWidth) {
                    toCameraPx = (double) frame.cols() / processWidth;
                    Imgproc.resize(frame, small, new Size(processWidth, Math.round(frame.rows() / toCameraPx)), 0, 0, Imgproc.INTER_AREA);
                    work = small;
                }
                if (kernel == null) {
                    // Filtergrößen wie bei 1920 px, passend verkleinert, aber nie so klein, dass Rauschen durchkommt
                    double k = work.cols() / REFERENCE_WIDTH;
                    long blur = Math.max(5, Math.round(7 * k));
                    blurSize = new Size(blur, blur);
                    long side = Math.max(5, Math.round(10 * k));
                    kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(side, side));
                    // 2 s Anlaufzeit für die Hintergrund-Erkennung, Toleranz 2 % der Bildbreite
                    debouncer = new DartDebouncer(stableFrames, Math.max(3, fps / 3), fps * 2, frame.cols() * 0.02);
                }
                backSub.apply(work, fgMask);
                // Schatten (Grauwert 127) und unsichere Pixel verwerfen, nur echte Bewegung (255) behalten
                Imgproc.threshold(fgMask, fgMask, 200, 255, Imgproc.THRESH_BINARY);
                Imgproc.blur(fgMask, fgMaskBlur, blurSize);
                Imgproc.morphologyEx(fgMaskBlur, fgCleaned, Imgproc.MORPH_OPEN, kernel);
                Imgproc.threshold(fgCleaned, fgCleaned, 127, 255, Imgproc.THRESH_BINARY);

                Double dartX = showsDarts(fgCleaned);
                if (dartX != null) dartX *= toCameraPx;
                // erst melden, wenn der Pfeil ein paar Bilder lang ruhig an derselben Stelle steckt
                Double report = debouncer.update(dartX);
                if (report != null) collector.report(camera, report);

                if (saveFrames && frameCount % 2 == 0) {
                    String folder = dartX != null ? "arrow" : "empty";
                    Imgcodecs.imwrite("subFrames/" + folder + "/mask_frame_" + frameCount + "_" + camera + ".png", fgCleaned);
                }
                frameCount++;
            }
        } finally {
            capture.release();
            frame.release();
            small.release();
            fgMask.release();
            fgMaskBlur.release();
            fgCleaned.release();
            if (kernel != null) kernel.release();
        }
        System.out.println("Kamera " + (camera + 1) + ": beendet");
    }

    /** @return Pixelspalte des Pfeils (Mitte der zwei obersten Ecken) oder null, wenn keiner zu sehen ist */
    public Double showsDarts(Mat image) {
        double k = image.cols() / REFERENCE_WIDTH;
        double minArea = MIN_AREA * k * k, maxArea = MAX_AREA * k * k;
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
            if (MIN_RATIO < ratio && ratio < MAX_RATIO && minArea < area && area < maxArea && area > bestArea) {
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
