package com.DartsCounter.DartsCounter_backend;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sevices")
public class ServiceController {
    @Autowired
    private CameraScanner cameraScanner;
    
    @GetMapping("/scan-cameras")
    public List<Integer> scanCameras() {
        return cameraScanner.scanCameras(10); // Scannt Indizes 0 bis 9
    }

    @Autowired
    private ArrowDetection arrowDetection;

    @PostMapping("/setRunning/{bool}")
    public Boolean setRunning(@PathVariable("bool") Boolean bool){
        arrowDetection.setRunning(bool);
        if(bool){
            arrowDetection.BackgroundSubtractor(0);
        }
        return bool;
    }

    @PostMapping("/post/{id}")
    public String postTest(@PathVariable("id") String id){
        return id;
    }
}
