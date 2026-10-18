package com.DartsCounter.DartsCounter_backend;
import org.springframework.context.annotation.Configuration;
import nu.pattern.OpenCV;
import jakarta.annotation.PostConstruct;

@Configuration
public class OpenCVConfig {
    @PostConstruct
    public void init() {
        OpenCV.loadLocally(); // loadShared() geht ab Java 12 nicht mehr
        // jede Kamera hat schon einen eigenen Thread; zusätzliche OpenCV-Threads pro Bild bremsen den Pi nur aus
        org.opencv.core.Core.setNumThreads(1);
        System.out.println("OpenCV lib loaded successfully. ver: " + org.opencv.core.Core.VERSION);
    }
}