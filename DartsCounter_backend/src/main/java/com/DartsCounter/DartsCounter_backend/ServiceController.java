package com.DartsCounter.DartsCounter_backend;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Kameras, Treffer und Kalibrierung. Von außen nur für Admins (AccessGuard), im Heimnetz frei.
@RestController
@RequestMapping("/services")
public class ServiceController {
    record Point(Double x, Double y) {}

    record Detection(Integer camera, Double pixelX) {}

    private final CameraScanner cameraScanner;
    private final ArrowDetection arrowDetection;
    private final ThrowCollector collector;
    private final CalibrationService calibration;
    private final String[] cameras;
    private final boolean simulate;

    public ServiceController(CameraScanner cameraScanner, ArrowDetection arrowDetection, ThrowCollector collector,
                             CalibrationService calibration,
                             @Value("${dartscounter.cameras}") String[] cameras,
                             @Value("${dartscounter.detection.simulate:false}") boolean simulate) {
        this.cameraScanner = cameraScanner;
        this.arrowDetection = arrowDetection;
        this.collector = collector;
        this.calibration = calibration;
        this.cameras = cameras;
        this.simulate = simulate;
    }

    @GetMapping("/scan-cameras")
    public List<Integer> scanCameras() {
        return cameraScanner.scanCameras(10); // Scannt Indizes 0 bis 9
    }

    @PostMapping("/setRunning/{bool}")
    public Boolean setRunning(@PathVariable("bool") Boolean bool) {
        if (bool) startDetection();
        else arrowDetection.stop();
        return arrowDetection.isRunning();
    }

    // letzter erkannter Treffer; neue Treffer erkennt das Frontend an der id
    @GetMapping("/lastHit")
    public ResponseEntity<ThrowCollector.Hit> lastHit() {
        ThrowCollector.Hit hit = collector.lastHit();
        return hit == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(hit);
    }

    /********* Kalibrierung *********/

    @GetMapping("/calibration")
    public CalibrationService.Status calibrationStatus() {
        return calibration.status();
    }

    // startet auch die Kameras, falls sie noch nicht laufen
    @PostMapping("/calibration/start")
    public CalibrationService.Status startCalibration() {
        CalibrationService.Status status = calibration.start();
        if (!simulate) startDetection();
        return status;
    }

    // echte Position des zuletzt erkannten Pfeils, in mm wie im Frontend (x rechts, y unten)
    @PostMapping("/calibration/point")
    public CalibrationService.Status calibrationPoint(@RequestBody Point point) {
        if (point.x() == null || point.y() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "x und y fehlen.");
        return calibration.addPoint(point.x(), point.y());
    }

    @PostMapping("/calibration/skip")
    public CalibrationService.Status skipThrow() {
        return calibration.skip();
    }

    @PostMapping("/calibration/undo")
    public CalibrationService.Status undoPoint() {
        return calibration.undo();
    }

    @PostMapping("/calibration/finish")
    public CalibrationService.Status finishCalibration() {
        return calibration.finish();
    }

    @PostMapping("/calibration/cancel")
    public CalibrationService.Status cancelCalibration() {
        return calibration.cancel();
    }

    /********* Testen ohne Kameras *********/

    // nur mit dartscounter.detection.simulate=true: so tun, als hätte eine Kamera einen Pfeil gesehen
    @PostMapping("/test/detection")
    public ResponseEntity<Map<String, String>> simulateDetection(@RequestBody Detection detection) {
        if (!simulate) throw new ApiException(HttpStatus.NOT_FOUND, "Simulation ist ausgeschaltet.");
        if (detection.camera() == null || detection.pixelX() == null
                || detection.camera() < 0 || detection.camera() >= cameras.length) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "camera oder pixelX ungültig.");
        }
        collector.report(detection.camera(), detection.pixelX());
        return ResponseEntity.accepted().body(Map.of("message", "Erkennung gemeldet."));
    }

    // Wurf sofort abschließen statt auf das Zeitfenster zu warten
    @PostMapping("/test/flush")
    public ResponseEntity<Void> simulateFlush() {
        if (!simulate) throw new ApiException(HttpStatus.NOT_FOUND, "Simulation ist ausgeschaltet.");
        collector.flush();
        return ResponseEntity.noContent().build();
    }

    private void startDetection() {
        if (!arrowDetection.start()) return; // läuft schon
        for (int cam = 0; cam < cameras.length; cam++) arrowDetection.detect(cam, cameras[cam].trim());
    }
}
