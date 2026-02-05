package com.DartsCounter.DartsCounter_backend;
import org.springframework.context.annotation.Configuration;
import nu.pattern.OpenCV;
import jakarta.annotation.PostConstruct;

@Configuration
public class OpenCVConfig {
    @PostConstruct
    public void init() {
        OpenCV.loadShared();
        System.out.println("OpenCV lib loaded successfully. ver: " + org.opencv.core.Core.VERSION);
    }
}