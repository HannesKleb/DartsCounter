package com.DartsCounter.DartsCounter_backend;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Liest die Kameras und gibt jedes Bild an einen DartFrameProcessor; neue Pfeile gehen an den ThrowCollector.
@Service
public class ArrowDetection {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ThrowCollector collector;
    private final int widthPx;
    private final int heightPx;
    private final int fps;
    private final int processWidth;
    private final int stableFrames;
    private final boolean tipAtTop;
    private final boolean lockWhiteBalance;
    private final boolean saveFrames;

    public ArrowDetection(ThrowCollector collector,
                          @Value("${dartscounter.camera.width-px}") int widthPx,
                          @Value("${dartscounter.camera.height-px}") int heightPx,
                          @Value("${dartscounter.camera.fps:15}") int fps,
                          @Value("${dartscounter.detection.process-width:640}") int processWidth,
                          @Value("${dartscounter.detection.stable-frames:3}") int stableFrames,
                          @Value("${dartscounter.camera.tip:top}") String tip,
                          @Value("${dartscounter.camera.lock-white-balance:true}") boolean lockWhiteBalance,
                          @Value("${dartscounter.debug.save-frames:false}") boolean saveFrames) {
        this.collector = collector;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.fps = fps;
        this.processWidth = processWidth;
        this.stableFrames = stableFrames;
        this.tipAtTop = !"bottom".equalsIgnoreCase(tip);
        this.lockWhiteBalance = lockWhiteBalance;
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
        boolean live = source.matches("\\d+");
        VideoCapture capture = live ? new VideoCapture(Integer.parseInt(source)) : new VideoCapture(source);
        if (!capture.isOpened()) {
            System.out.println("Kamera " + (camera + 1) + " nicht erreichbar: " + source);
            return;
        }
        // MJPG vor der Auflösung setzen: drei unkomprimierte Kameras passen nicht durch die USB-Bandbreite des Pi
        capture.set(Videoio.CAP_PROP_FOURCC, VideoWriter.fourcc('M', 'J', 'P', 'G'));
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, widthPx);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, heightPx);
        capture.set(Videoio.CAP_PROP_FPS, fps); // weniger Bilder pro Sekunde entlasten den Pi
        System.out.println("Kamera " + (camera + 1) + ": " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x"
                + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));

        if (saveFrames) {
            new File("subFrames").mkdirs();
        }

        DartFrameProcessor processor = new DartFrameProcessor(fps, stableFrames, processWidth, tipAtTop);
        Mat frame = new Mat();
        int frameCount = 0;
        // Viele Kameras ignorieren die eingestellte Bildrate und liefern 30 Bilder/s. Ausgewertet werden nur so viele,
        // wie in dartscounter.camera.fps steht; die übrigen werden nur abgeholt (grab), nicht entpackt.
        long intervalNs = 1_000_000_000L / Math.max(1, fps);
        long nextFrameNs = System.nanoTime();
        try {
            while (running.get()) {
                if (live) {
                    if (!capture.grab()) break;
                    long now = System.nanoTime();
                    if (now < nextFrameNs) continue; // zu früh: überspringen
                    nextFrameNs = Math.max(nextFrameNs + intervalNs, now - intervalNs);
                    if (!capture.retrieve(frame)) break;
                } else if (!capture.read(frame)) {
                    break; // Videodatei zu Ende
                }
                // Weißabgleich nach der Anlaufzeit festhalten: sonst springt das Bild und alles gilt als Bewegung
                if (live && lockWhiteBalance && frameCount == fps * 2) capture.set(Videoio.CAP_PROP_AUTO_WB, 0);

                for (double x : processor.process(frame)) collector.report(camera, x);

                if (saveFrames && frameCount % 2 == 0) {
                    Imgcodecs.imwrite("subFrames/mask_frame_" + frameCount + "_" + camera + ".png", processor.cleaned);
                }
                frameCount++;
            }
        } finally {
            capture.release();
            frame.release();
            processor.release();
        }
        System.out.println("Kamera " + (camera + 1) + ": beendet");
    }
}
