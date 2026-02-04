import java.util.ArrayList;
import java.util.List;

// =========================
// Konstanten
// =========================
final class Constants {
    public static final double DARTBOARD_RADIUS_CM = 22.5; // äußerster Rand der Scheibe
}

// =========================
// Datenklassen
// =========================
class Point {
    public double x;
    public double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
}

class Camera {
    public Point position;     // Position in cm
    public double angleDeg;    // Blickrichtung (0 = Richtung Dartzentrum)
    public double focalLengthPx;

    public Camera(Point position, double angleDeg, double focalLengthPx) {
        this.position = position;
        this.angleDeg = angleDeg;
        this.focalLengthPx = focalLengthPx;
    }
}

// =========================
// Winkel aus Pixelkoordinaten
// =========================
class Geometry {
    public static double angleFromPixelDeg(Camera camera, double pixelX) {
        double angleRad = Math.toRadians(camera.angleDeg) + Math.atan2(pixelX, camera.focalLengthPx);
        return Math.toDegrees(angleRad);
    }

    public static double angleFromPixelRad(Camera camera, double pixelX) {
        return Math.toRadians(camera.angleDeg) + Math.atan2(pixelX, camera.focalLengthPx);
    }

    // =========================
    // Schnittpunkt zweier Kamerastrahlen
    // =========================
    public static Point intersect(Camera cam1, double angle1Rad, Camera cam2, double angle2Rad) {
        double x1 = cam1.position.x;
        double y1 = cam1.position.y;
        double x2 = cam2.position.x;
        double y2 = cam2.position.y;

        double dx1 = Math.sin(angle1Rad);
        double dy1 = Math.cos(angle1Rad);
        double dx2 = Math.sin(angle2Rad);
        double dy2 = Math.cos(angle2Rad);

        double det = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(det) < 1e-6) return null; // parallele Strahlen

        double t = ((x2 - x1) * dy2 - (y2 - y1) * dx2) / det;
        return new Point(x1 + t * dx1, y1 + t * dy1);
    }

    // =========================
    // Triangulation
    // =========================
    public static Point triangulateTwo(Camera cam1, double pixel1, Camera cam2, double pixel2) {
        double a1 = angleFromPixelRad(cam1, pixel1);
        double a2 = angleFromPixelRad(cam2, pixel2);
        return intersect(cam1, a1, cam2, a2);
    }

    public static Point triangulateThree(Camera cam1, double pixel1,
                                         Camera cam2, double pixel2,
                                         Camera cam3, double pixel3) {
        List<Point> points = new ArrayList<>();
        Point p1 = triangulateTwo(cam1, pixel1, cam2, pixel2);
        Point p2 = triangulateTwo(cam2, pixel2, cam3, pixel3);
        Point p3 = triangulateTwo(cam3, pixel3, cam1, pixel1);

        if (p1 != null) points.add(p1);
        if (p2 != null) points.add(p2);
        if (p3 != null) points.add(p3);

        if (points.isEmpty()) return null;

        double x = 0, y = 0;
        for (Point p : points) {
            x += p.x;
            y += p.y;
        }
        x /= points.size();
        y /= points.size();
        return new Point(x, y);
    }

    // =========================
    // Dartboard Koordinaten
    // =========================
    public static double dartRadiusCm(Point p) {
        return Math.sqrt(p.x * p.x + p.y * p.y);
    }

    public static double dartRadiusNormalized(Point p) {
        double r = dartRadiusCm(p) / Constants.DARTBOARD_RADIUS_CM;
        return Math.min(Math.max(r, 0), 1);
    }

    public static double dartAngleDeg(Point p) {
        return Math.toDegrees(Math.atan2(p.x, p.y));
    }

    // =========================
    // Kamerapositionen automatisch setzen
    // =========================
    public static List<Camera> createCamerasAroundBoard(double radiusCm, double fovDeg, int imageWidthPx) {
        List<Camera> cameras = new ArrayList<>();
        int[] anglesDeg = {0, 120, -120};
        double focalLengthPx = imageWidthPx / (2.0 * Math.tan(Math.toRadians(fovDeg) / 2.0));

        for (int angle : anglesDeg) {
            double rad = Math.toRadians(angle);
            double x = radiusCm * Math.sin(rad);
            double y = -radiusCm * Math.cos(rad);
            cameras.add(new Camera(new Point(x, y), angle, focalLengthPx));
        }
        return cameras;
    }
}

// =========================
// Main / Beispiel
// =========================
public class DartTracking {
    public static void main(String[] args) {
        double radiusCm = 50;
        double fovDeg = 60;
        int imageWidthPx = 1920;

        double pixel1 = 0;
        double pixel2 = 0;
        double pixel3 = 0;

        List<Camera> cams = Geometry.createCamerasAroundBoard(radiusCm, fovDeg, imageWidthPx);
        Camera cam1 = cams.get(0);
        Camera cam2 = cams.get(1);
        Camera cam3 = cams.get(2);

        System.out.println("Winkel jeder Kamera basierend auf Pixeln:");
        System.out.printf("Kamera 1: Pixel %.2f → Winkel %.2f°\n", pixel1, Geometry.angleFromPixelDeg(cam1, pixel1));
        System.out.printf("Kamera 2: Pixel %.2f → Winkel %.2f°\n", pixel2, Geometry.angleFromPixelDeg(cam2, pixel2));
        System.out.printf("Kamera 3: Pixel %.2f → Winkel %.2f°\n", pixel3, Geometry.angleFromPixelDeg(cam3, pixel3));

        Point hit3 = Geometry.triangulateThree(cam1, pixel1, cam2, pixel2, cam3, pixel3);
        if (hit3 != null) {
            System.out.println("\n🎯 Treffer mit 3 Kameras");
            System.out.printf("X: %.2f cm, Y: %.2f cm\n", hit3.x, hit3.y);
            System.out.printf("Normierter Radius: %.3f\n", Geometry.dartRadiusNormalized(hit3));
            System.out.printf("Scheibenwinkel: %.2f°\n", Geometry.dartAngleDeg(hit3));
        }

        Point hit2 = Geometry.triangulateTwo(cam1, pixel1, cam2, pixel2);
        if (hit2 != null) {
            System.out.println("\n🎯 Treffer mit 2 Kameras");
            System.out.printf("X: %.2f cm, Y: %.2f cm\n", hit2.x, hit2.y);
            System.out.printf("Normierter Radius: %.3f\n", Geometry.dartRadiusNormalized(hit2));
            System.out.printf("Scheibenwinkel: %.2f°\n", Geometry.dartAngleDeg(hit2));
        }
    }
}
