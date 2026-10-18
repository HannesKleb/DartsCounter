package com.DartsCounter.DartsCounter_backend;

import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Zustand einer Kamera für die Diagnose-Seite: läuft sie, wie viele Bilder pro Sekunde, was wurde erkannt,
 * und ein Bild mit eingezeichneter Erkennung (rot = Veränderung, grün = erkannte Pfeilspitze).
 */
public class CameraDiagnostics {

    public record Status(int camera, String source, String state, double fps, long frames, long reports,
                         int tipsNow, boolean motionNow, int backgroundResets, Integer lastReportX, String lastReportAgo) {}

    private final int camera;
    private final String source;
    private volatile String state = "nicht gestartet";
    private volatile double fps;
    private volatile long frames;
    private volatile long reports;
    private volatile int tipsNow;
    private volatile boolean motionNow;
    private volatile int resets;
    private volatile Integer lastReportX;
    private volatile long lastReportAt;

    private final Mat image = new Mat();
    private final Mat mask = new Mat();
    private List<DartTracker.Tip> tips = List.of();
    private double scale = 1;

    CameraDiagnostics(int camera, String source) {
        this.camera = camera;
        this.source = source;
    }

    void state(String state) {
        this.state = state;
    }

    void frame(DartFrameProcessor p, double measuredFps, long frameCount, boolean copyImage, double toCameraPx) {
        fps = measuredFps;
        frames = frameCount;
        tipsNow = p.lastTips.size();
        motionNow = p.lastBigMotion;
        resets = p.resets();
        if (copyImage && p.lastWork != null) {
            synchronized (this) {
                p.lastWork.copyTo(image);
                p.cleaned.copyTo(mask);
                tips = p.lastTips;
                scale = toCameraPx;
            }
        }
    }

    void reported(double x) {
        reports++;
        lastReportX = (int) Math.round(x);
        lastReportAt = System.currentTimeMillis();
    }

    public Status status() {
        String ago = lastReportAt == 0 ? null : ((System.currentTimeMillis() - lastReportAt) / 1000) + " s";
        return new Status(camera + 1, source, state, Math.round(fps * 10) / 10.0, frames, reports, tipsNow, motionNow,
                resets, lastReportX, ago);
    }

    /** JPEG mit eingezeichneter Erkennung oder null, wenn noch kein Bild da ist */
    public synchronized byte[] jpeg() {
        if (image.empty()) return null;
        Mat out = image.clone();
        if (!mask.empty() && mask.size().equals(image.size())) {
            Mat red = new Mat(image.size(), image.type(), new Scalar(0, 0, 255));
            Mat tinted = new Mat();
            Core.addWeighted(image, 0.5, red, 0.5, 0, tinted);
            tinted.copyTo(out, mask);
            red.release();
            tinted.release();
        }
        for (DartTracker.Tip t : tips) {
            Imgproc.circle(out, new Point(t.x(), t.y()), 8, new Scalar(0, 255, 0), 2);
            Imgproc.putText(out, "x=" + Math.round(t.x() * scale), new Point(t.x() + 10, t.y() - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1);
        }
        // OpenCV kann keine Umlaute zeichnen
        String text = ("Kamera " + (camera + 1) + "  " + Math.round(fps) + " Bilder/s  " + state)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        Imgproc.putText(out, text,
                new Point(10, 20), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(255, 255, 255), 2);
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".jpg", out, buf, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80));
        out.release();
        byte[] bytes = buf.toArray();
        buf.release();
        return bytes;
    }
}
