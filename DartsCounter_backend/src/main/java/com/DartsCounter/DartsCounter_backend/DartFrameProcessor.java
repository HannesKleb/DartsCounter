package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;

/**
 * Wertet die Bilder einer Kamera aus und meldet neue Pfeile als Pixelspalte der Spitze.
 *
 * Die Kameras schauen flach über die Scheibe. Ein Pfeil erscheint im Bild als länglicher Fleck (Schaft und Flight),
 * der an der Scheibenkante beginnt. Gemessen wird die Spitze: der Punkt des Flecks, der der Scheibe am nächsten ist
 * (bei kopfüber montierten Kameras der oberste Punkt). Die Spitze bleibt ruhig, auch wenn der Flight noch wackelt.
 */
class DartFrameProcessor {
    // Grenzen für ein 1920 px breites Bild, werden für kleinere Bilder mitskaliert
    static final double REFERENCE_WIDTH = 1920;
    static final double MIN_AREA = 2700;      // dünner Schaft reicht
    static final double MAX_AREA = 160000;    // größer: Hand oder Lichtwechsel
    static final double MIN_HEIGHT_SHARE = 0.06; // mindestens 6 % der Bildhöhe lang
    static final double MAX_WIDTH_SHARE = 0.2;   // breiter: Hand
    static final double MIN_ELONGATION = 1.5;    // höher als breit
    // die Spitze liegt an der Scheibenkante (etwa Bildmitte); was erst im äußersten Fünftel beginnt, ist der Raum
    static final double MAX_TIP_SHARE = 0.8;
    // ändert sich mehr als dieser Anteil des Bildes, war es ein Lichtwechsel (z. B. Weißabgleich der Kamera)
    static final double GLOBAL_CHANGE_SHARE = 0.15;
    // ändert sich mehr als dieser Anteil, ist eine Hand oder ein Arm im Bild (Pfeile allein: 1-2 %)
    static final double BIG_MOTION_SHARE = 0.035;

    private final int fps;
    private final int stableFrames;
    private final int processWidth;
    private final boolean tipAtTop;

    private BackgroundSubtractorMOG2 backSub = Video.createBackgroundSubtractorMOG2();
    private DartTracker tracker;
    private Size blurSize;
    private Mat openKernel;
    private Mat closeKernel;
    private final Mat small = new Mat();
    private final Mat mask = new Mat();
    private final Mat tmp = new Mat();
    private int resets;

    /** fertig verarbeitete Maske des letzten Bildes (für Debug-Bilder) */
    final Mat cleaned = new Mat();
    /** zuletzt ausgewertetes (ggf. verkleinertes) Bild und die darin gefundenen Spitzen, für die Diagnose */
    Mat lastWork;
    volatile List<DartTracker.Tip> lastTips = List.of();
    volatile boolean lastBigMotion;

    DartFrameProcessor(int fps, int stableFrames, int processWidth, boolean tipAtTop) {
        this.fps = fps;
        this.stableFrames = stableFrames;
        this.processWidth = processWidth;
        this.tipAtTop = tipAtTop;
    }

    int resets() {
        return resets;
    }

    /** @return Spalten (im Kamerabild) der Pfeile, die mit diesem Bild neu erkannt wurden */
    List<Double> process(Mat frame) {
        Mat work = frame;
        double toCameraPx = 1;
        if (frame.cols() > processWidth) {
            toCameraPx = (double) frame.cols() / processWidth;
            Imgproc.resize(frame, small, new Size(processWidth, Math.round(frame.rows() / toCameraPx)), 0, 0, Imgproc.INTER_AREA);
            work = small;
        }
        if (tracker == null) init(work, toCameraPx);
        lastWork = work;

        backSub.apply(work, mask);
        // Schatten (Grauwert 127) und unsichere Pixel verwerfen, nur echte Veränderung (255) behalten
        Imgproc.threshold(mask, mask, 200, 255, Imgproc.THRESH_BINARY);

        double changed = (double) Core.countNonZero(mask) / mask.total();
        if (changed > GLOBAL_CHANGE_SHARE) {
            // Lichtwechsel: Hintergrund neu lernen statt lauter Fehlalarme
            backSub = Video.createBackgroundSubtractorMOG2();
            tracker.reset(fps);
            resets++;
            mask.copyTo(cleaned);
            lastTips = List.of();
            lastBigMotion = true;
            return List.of();
        }

        Imgproc.blur(mask, tmp, blurSize);
        Imgproc.morphologyEx(tmp, cleaned, Imgproc.MORPH_OPEN, openKernel);
        // Schaft und Flight senkrecht zu einem Fleck verbinden
        Imgproc.morphologyEx(cleaned, cleaned, Imgproc.MORPH_CLOSE, closeKernel);
        Imgproc.threshold(cleaned, cleaned, 127, 255, Imgproc.THRESH_BINARY);

        Detection d = findTips(cleaned, tipAtTop);
        lastTips = d.tips();
        lastBigMotion = d.bigMotion() || changed > BIG_MOTION_SHARE;
        List<Double> found = tracker.update(d.tips(), d.bigMotion() || changed > BIG_MOTION_SHARE);
        if (toCameraPx == 1) return found;
        List<Double> scaled = new ArrayList<>(found.size());
        for (double x : found) scaled.add(x * toCameraPx);
        return scaled;
    }

    private void init(Mat work, double toCameraPx) {
        double k = work.cols() / REFERENCE_WIDTH;
        long blur = Math.max(5, Math.round(7 * k));
        blurSize = new Size(blur, blur);
        long side = Math.max(5, Math.round(10 * k));
        openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(side, side));
        closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, Math.max(9, Math.round(work.rows() * 0.05))));
        // Toleranzen: 2 % der Breite, 4 % der Höhe; Anlaufzeit 2 s, nach Hand 1,5 s Ruhe an alten Stellen,
        // 1/3 s leer = Pfeile weg, alte Stellen 60 s merken
        tracker = new DartTracker(stableFrames, fps * 2, Math.round(fps * 1.5f), Math.max(3, fps / 3),
                work.cols() * 0.02, work.rows() * 0.04, fps * 60);
    }

    record Detection(List<DartTracker.Tip> tips, boolean bigMotion) {}

    /** Sucht in einer binären Maske alle Flecken, die wie ein Pfeil aussehen, und gibt ihre Spitzen zurück. */
    static Detection findTips(Mat binary, boolean tipAtTop) {
        double k = binary.cols() / REFERENCE_WIDTH;
        double minArea = MIN_AREA * k * k, maxArea = MAX_AREA * k * k;
        double minHeight = binary.rows() * MIN_HEIGHT_SHARE, maxWidth = binary.cols() * MAX_WIDTH_SHARE;

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
        hierarchy.release();

        List<DartTracker.Tip> tips = new ArrayList<>();
        boolean bigMotion = false;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            Rect box = Imgproc.boundingRect(contour);
            if (area > maxArea || (box.width > maxWidth && area > minArea)) {
                bigMotion = true; // Hand oder Arm
            } else if (area >= minArea && box.height >= minHeight && box.height >= MIN_ELONGATION * box.width) {
                DartTracker.Tip t = tip(contour.toArray(), tipAtTop);
                double share = tipAtTop ? t.y() / binary.rows() : 1 - t.y() / binary.rows();
                if (share <= MAX_TIP_SHARE) tips.add(t);
            }
            contour.release();
        }
        return new Detection(tips, bigMotion);
    }

    // Mittelwert der Punkte, die höchstens 3 px vom äußersten Ende (Scheibenseite) entfernt sind
    private static DartTracker.Tip tip(Point[] points, boolean tipAtTop) {
        double edge = tipAtTop ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Point p : points) edge = tipAtTop ? Math.min(edge, p.y) : Math.max(edge, p.y);
        double sum = 0;
        int n = 0;
        for (Point p : points) {
            if (Math.abs(p.y - edge) <= 3) {
                sum += p.x;
                n++;
            }
        }
        return new DartTracker.Tip(sum / n, edge);
    }

    void release() {
        small.release();
        mask.release();
        tmp.release();
        cleaned.release();
        if (openKernel != null) openKernel.release();
        if (closeKernel != null) closeKernel.release();
    }
}
