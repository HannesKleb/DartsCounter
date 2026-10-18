package com.DartsCounter.DartsCounter_backend;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opencv.core.CvType;
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
    private final AtomicInteger activeCameras = new AtomicInteger();
    private final Map<Integer, CameraDiagnostics> diagnostics = new ConcurrentHashMap<>();
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

    public List<CameraDiagnostics.Status> diagnostics() {
        return diagnostics.values().stream().map(CameraDiagnostics::status)
                .sorted(java.util.Comparator.comparingInt(CameraDiagnostics.Status::camera)).toList();
    }

    public CameraDiagnostics diagnostics(int camera) {
        return diagnostics.get(camera);
    }

    /**
     * Läuft pro Kamera in einem eigenen Thread.
     * @param camera Kamera-Nummer (Reihenfolge in dartscounter.cameras)
     * @param source Kamera-Index ("0") oder Pfad/URL einer Videodatei (zum Testen ohne Kamera)
     */
    @Async
    public void detect(int camera, String source) {
        activeCameras.incrementAndGet();
        CameraDiagnostics diag = new CameraDiagnostics(camera, source);
        diagnostics.put(camera, diag);
        try {
            run(camera, source, diag);
        } catch (RuntimeException e) {
            diag.state("Fehler: " + e.getMessage());
            System.out.println("Kamera " + (camera + 1) + ": Fehler " + e);
        } finally {
            // laufen keine Kameras mehr (z. B. alle nicht erreichbar), darf ein neuer Start sie wieder öffnen
            if (activeCameras.decrementAndGet() == 0) running.set(false);
        }
    }

    private void run(int camera, String source, CameraDiagnostics diag) {
        boolean live = source.matches("\\d+");
        diag.state("wird geöffnet");
        VideoCapture capture = live ? new VideoCapture(Integer.parseInt(source)) : new VideoCapture(source);
        if (!capture.isOpened()) {
            System.out.println("Kamera " + (camera + 1) + " nicht erreichbar: " + source);
            diag.state("nicht erreichbar");
            return;
        }
        diag.state("läuft");
        // MJPG vor der Auflösung setzen: drei unkomprimierte Kameras passen nicht durch die USB-Bandbreite des Pi
        capture.set(Videoio.CAP_PROP_FOURCC, VideoWriter.fourcc('M', 'J', 'P', 'G'));
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, widthPx);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, heightPx);
        capture.set(Videoio.CAP_PROP_FPS, fps); // weniger Bilder pro Sekunde entlasten den Pi
        // Schnell entpacken: die rohen JPEG-Daten holen und gleich in halber Größe entpacken (etwa doppelt so schnell),
        // wenn ohnehin auf halber Breite gerechnet wird. Liefert die Kamera keine Rohdaten, normal weiter.
        boolean fastDecode = live && processWidth * 2 <= widthPx && capture.set(Videoio.CAP_PROP_CONVERT_RGB, 0);
        System.out.println("Kamera " + (camera + 1) + ": " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x"
                + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT) + (fastDecode ? ", schnelles Entpacken" : ""));

        if (saveFrames) {
            new File("subFrames").mkdirs();
        }

        DartFrameProcessor processor = new DartFrameProcessor(fps, stableFrames, processWidth, tipAtTop);
        Mat frame = new Mat();
        Mat raw = new Mat();
        int frameCount = 0;
        // Viele Kameras ignorieren die eingestellte Bildrate und liefern 30 Bilder/s. Ausgewertet werden nur so viele,
        // wie in dartscounter.camera.fps steht; die übrigen werden nur abgeholt (grab), nicht entpackt.
        long intervalNs = 1_000_000_000L / Math.max(1, fps);
        long nextFrameNs = System.nanoTime();
        long fpsWindowStart = System.nanoTime();
        int fpsWindowFrames = 0;
        double measuredFps = 0;
        double fileFps = live ? 0 : capture.get(Videoio.CAP_PROP_FPS);
        int fileStep = fileFps > fps ? (int) Math.round(fileFps / fps) : 1;
        try {
            while (running.get()) {
                if (live) {
                    if (!capture.grab()) break;
                    long now = System.nanoTime();
                    if (now < nextFrameNs) continue; // zu früh: überspringen
                    nextFrameNs = Math.max(nextFrameNs + intervalNs, now - intervalNs);
                    if (fastDecode) {
                        if (!capture.retrieve(raw)) break;
                        if (raw.rows() == 1 && raw.type() == CvType.CV_8UC1) {
                            Mat decoded = Imgcodecs.imdecode(raw, Imgcodecs.IMREAD_REDUCED_COLOR_2);
                            if (decoded.empty()) continue; // kaputtes JPEG: Bild überspringen
                            decoded.copyTo(frame);
                            decoded.release();
                        } else {
                            // Kamera liefert schon entpackte Bilder: normaler Weg
                            fastDecode = false;
                            capture.set(Videoio.CAP_PROP_CONVERT_RGB, 1);
                            if (raw.channels() == 3) raw.copyTo(frame); else continue;
                        }
                    } else if (!capture.retrieve(frame)) {
                        break;
                    }
                } else {
                    // Videodatei: so viele Bilder überspringen, dass die Bildrate passt
                    for (int i = 1; i < fileStep; i++) capture.grab();
                    if (!capture.read(frame)) break; // Videodatei zu Ende
                    // Videodatei in Echtzeit abspielen, damit sich alles wie mit einer echten Kamera verhält
                    long wait = nextFrameNs - System.nanoTime();
                    if (wait > 0) {
                        try {
                            Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    nextFrameNs += intervalNs;
                }
                // Weißabgleich nach der Anlaufzeit festhalten: sonst springt das Bild und alles gilt als Bewegung
                if (live && lockWhiteBalance && frameCount == fps * 2) capture.set(Videoio.CAP_PROP_AUTO_WB, 0);

                // Spalten immer in der vollen Kamera-Auflösung melden (die Kalibrierung rechnet damit)
                double toFullPx = frame.cols() < widthPx ? (double) widthPx / frame.cols() : 1;
                for (double x : processor.process(frame)) {
                    collector.report(camera, x * toFullPx);
                    diag.reported(x * toFullPx);
                }
                // Diagnose: tatsächliche Bildrate und alle 4 Bilder ein Vorschaubild
                fpsWindowFrames++;
                long now = System.nanoTime();
                if (now - fpsWindowStart >= 2_000_000_000L) {
                    measuredFps = fpsWindowFrames * 1e9 / (now - fpsWindowStart);
                    fpsWindowStart = now;
                    fpsWindowFrames = 0;
                }
                double toCameraPx = processor.lastWork == null ? 1 : (double) frame.cols() / processor.lastWork.cols() * toFullPx;
                diag.frame(processor, measuredFps, frameCount + 1, frameCount % 4 == 0, toCameraPx);

                if (saveFrames && frameCount % 2 == 0) {
                    Imgcodecs.imwrite("subFrames/mask_frame_" + frameCount + "_" + camera + ".png", processor.cleaned);
                }
                frameCount++;
            }
        } finally {
            capture.release();
            frame.release();
            raw.release();
            processor.release();
        }
        diag.state("beendet");
        System.out.println("Kamera " + (camera + 1) + ": beendet");
    }
}
