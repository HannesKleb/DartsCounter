package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;

import org.opencv.videoio.VideoCapture;  
import org.opencv.videoio.Videoio;
import org.springframework.stereotype.Service;

@Service
public class CameraScanner {
    public List<Integer> scanCameras(int maxToTry) {
        List<Integer> availableCameras = new ArrayList<>();

        for (int i = 0; i < maxToTry; i++) {
            VideoCapture cap = new VideoCapture(i);
            
            if (cap.isOpened()) {
                // Kamera-Informationen auslesen
                double width = cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
                double height = cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
                double fps = cap.get(Videoio.CAP_PROP_FPS);
                String backend = cap.getBackendName();

                System.out.printf("Kamera gefunden! Index: %d | Auflösung: %.0fx%.0f | FPS: %.1f | Backend: %s%n", 
                                   i, width, height, fps, backend);
                
                availableCameras.add(i);
                
                // Wichtig: Kamera sofort wieder freigeben
                cap.release();
            }
        }
        return availableCameras;
    }
}
