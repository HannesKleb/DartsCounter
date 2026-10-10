package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Berechnet aus den Pixelpositionen mehrerer Kameras den Treffpunkt auf der Scheibe.
 *
 * Koordinaten: Scheibenebene, Ursprung = Bullseye, x nach rechts, y nach oben (in cm).
 * Winkel: 0° = oben (20), gegen den Uhrzeigersinn - wie im Frontend und in ScoreCommunication.
 */
@Service
public class Triangulation {

    static final double DARTBOARD_RADIUS_CM = 22.5;

    public record Point(double x, double y) {}

    public record Polar(double radiusNorm, double angleDeg) {}

    private final double[] positionsDeg;
    private final double distanceCm;
    private final double focalLengthPx;
    private final boolean invertX;

    public Triangulation(@Value("${darts.camera.positions-deg}") double[] positionsDeg,
                         @Value("${darts.camera.distance-cm}") double distanceCm,
                         @Value("${darts.camera.fov-deg}") double fovDeg,
                         @Value("${darts.camera.width-px}") double widthPx,
                         @Value("${darts.camera.invert-x:false}") boolean invertX) {
        this.positionsDeg = positionsDeg;
        this.distanceCm = distanceCm;
        this.focalLengthPx = widthPx / (2 * Math.tan(Math.toRadians(fovDeg) / 2));
        this.invertX = invertX;
    }

    // Richtungsvektor zu einem Winkel (0° = oben, gegen den Uhrzeigersinn)
    private static Point direction(double angleRad) {
        return new Point(-Math.sin(angleRad), Math.cos(angleRad));
    }

    private Point cameraPosition(int cam) {
        Point d = direction(Math.toRadians(positionsDeg[cam]));
        return new Point(d.x() * distanceCm, d.y() * distanceCm);
    }

    /**
     * Blickrichtung des Strahls durch die Pixelspalte.
     * Die Kamera schaut zur Mitte (Position + 180°). Ein Pixel rechts der Mitte dreht den Strahl
     * im Uhrzeigersinn (Kamera aufrecht, Bild-oben zeigt zum Spieler). Ist eine Kamera anders
     * herum montiert, darts.camera.invert-x=true setzen.
     */
    double rayAngleRad(int cam, double pixelX) {
        double offset = Math.atan2(pixelX, focalLengthPx);
        if (invertX) offset = -offset;
        return Math.toRadians(positionsDeg[cam] + 180) - offset;
    }

    // Schnittpunkt zweier Kamerastrahlen; null, wenn parallel oder hinter einer Kamera
    Point intersect(int cam1, double pixel1, int cam2, double pixel2) {
        Point p1 = cameraPosition(cam1), p2 = cameraPosition(cam2);
        Point d1 = direction(rayAngleRad(cam1, pixel1)), d2 = direction(rayAngleRad(cam2, pixel2));

        double det = d1.x() * d2.y() - d1.y() * d2.x();
        if (Math.abs(det) < 1e-6) return null;

        double dx = p2.x() - p1.x(), dy = p2.y() - p1.y();
        double t = (dx * d2.y() - dy * d2.x()) / det;
        double s = (dx * d1.y() - dy * d1.x()) / det;
        if (t <= 0 || s <= 0) return null;

        return new Point(p1.x() + t * d1.x(), p1.y() + t * d1.y());
    }

    /**
     * @param pixels Kamera-Nummer (0, 1, 2 = Reihenfolge in darts.cameras) -> Pixel relativ zur Mitte
     * @return Mittelwert aller paarweisen Schnittpunkte oder null
     */
    public Point triangulate(Map<Integer, Double> pixels) {
        List<Integer> cams = new ArrayList<>(pixels.keySet());
        double sumX = 0, sumY = 0;
        int count = 0;
        for (int i = 0; i < cams.size(); i++) {
            for (int j = i + 1; j < cams.size(); j++) {
                Point p = intersect(cams.get(i), pixels.get(cams.get(i)), cams.get(j), pixels.get(cams.get(j)));
                if (p != null) {
                    sumX += p.x();
                    sumY += p.y();
                    count++;
                }
            }
        }
        return count == 0 ? null : new Point(sumX / count, sumY / count);
    }

    public static Polar toPolar(Point p) {
        double radius = Math.hypot(p.x(), p.y()) / DARTBOARD_RADIUS_CM;
        double angle = Math.toDegrees(Math.atan2(-p.x(), p.y()));
        if (angle < 0) angle += 360;
        return new Polar(radius, angle);
    }
}
