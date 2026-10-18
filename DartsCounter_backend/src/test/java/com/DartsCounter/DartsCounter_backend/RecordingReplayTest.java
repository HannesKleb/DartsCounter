package com.DartsCounter.DartsCounter_backend;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import nu.pattern.OpenCV;

/**
 * Werkzeug zum Auswerten echter Aufnahmen: spielt Videos der Kameras durch die Erkennung und zeigt,
 * wann welche Kamera welchen Pfeil meldet. Läuft nur, wenn ein Ordner angegeben wird:
 *   ./mvnw test -Dtest=RecordingReplayTest -DvideoDir=/pfad/zu/den/videos -Dfps=20
 * Mit -Dstep=2 wird nur jedes zweite Bild ausgewertet (wie auf dem Pi mit dartscounter.camera.fps=10).
 * Erwartet video0.avi, video2.avi, video4.avi (MJPEG). Aus den .mkv-Aufnahmen vom Pi:
 *   ffmpeg -i video0.mkv -vf fps=20 -c:v mjpeg -q:v 2 video0.avi
 */
@EnabledIfSystemProperty(named = "videoDir", matches = ".+")
class RecordingReplayTest {

    @Test
    void replay() {
        OpenCV.loadLocally();
        String dir = System.getProperty("videoDir");
        int fps = Integer.parseInt(System.getProperty("fps", "20"));
        int step = Integer.parseInt(System.getProperty("step", "1"));
        String[] files = {"video0.avi", "video2.avi", "video4.avi"};
        for (int cam = 0; cam < files.length; cam++) {
            File file = new File(dir, files[cam]);
            if (!file.exists()) continue;
            VideoCapture cap = new VideoCapture(file.getPath());
            DartFrameProcessor processor = new DartFrameProcessor(fps / step, 3, Integer.parseInt(System.getProperty("width", "640")), true);
            Mat frame = new Mat();
            int f = 0;
            StringBuilder reports = new StringBuilder();
            while (cap.read(frame)) {
                if (f % step == 0) {
                    for (double x : processor.process(frame)) reports.append(String.format(" [%.1fs x=%.0f]", (double) f / fps, x));
                }
                f++;
            }
            cap.release();
            System.out.println("Kamera " + (cam + 1) + ": " + f + " Bilder, " + processor.resets()
                    + "x Hintergrund neu gelernt, Meldungen:" + reports);
        }
    }
}
