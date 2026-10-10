package com.DartsCounter.DartsCounter_backend;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/services")
public class ServiceController {
    private final CameraScanner cameraScanner;
    private final ArrowDetection arrowDetection;
    private final HitService hitService;
    private final int[] cameras;

    public ServiceController(CameraScanner cameraScanner, ArrowDetection arrowDetection, HitService hitService,
                             @Value("${darts.cameras}") int[] cameras) {
        this.cameraScanner = cameraScanner;
        this.arrowDetection = arrowDetection;
        this.hitService = hitService;
        this.cameras = cameras;
    }

    @GetMapping("/scan-cameras")
    public List<Integer> scanCameras() {
        return cameraScanner.scanCameras(10); // Scannt Indizes 0 bis 9
    }

    @PostMapping("/setRunning/{bool}")
    public Boolean setRunning(@PathVariable("bool") Boolean bool) {
        if (!bool) {
            arrowDetection.stop();
        } else if (arrowDetection.start()) { // nur starten, wenn noch nicht aktiv
            for (int cam = 0; cam < cameras.length; cam++) {
                arrowDetection.detect(cam, cameras[cam]);
            }
        }
        return arrowDetection.isRunning();
    }

    // Letzter erkannter Treffer; das Frontend erkennt neue Treffer an der id
    @GetMapping("/lastHit")
    public ResponseEntity<HitService.Hit> lastHit() {
        HitService.Hit hit = hitService.getLastHit();
        return hit == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(hit);
    }

    // Manuelle Eingabe (z. B. Klick aufs Dartboard im Frontend): Winkel in Grad, Radius normiert
    @PostMapping("/scoring/coords/{playerId}/{dart}/{angle}/{radius}")
    public Map<String, Object> recieveCoords(@PathVariable("playerId") int playerId, @PathVariable("dart") int dart,
                                             @PathVariable("angle") double angle, @PathVariable("radius") double radius) {
        HitService.Hit hit = hitService.registerHit(radius, angle);
        return Map.of("playerId", playerId, "dart", dart, "field", hit.field(), "score", hit.score());
    }
}
