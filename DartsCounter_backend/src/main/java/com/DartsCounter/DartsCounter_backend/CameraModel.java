package com.DartsCounter.DartsCounter_backend;

import java.util.List;

/**
 * Wie eine Kamera die Scheibe sieht: Punkt (x, y) auf der Scheibe -> Pixelspalte im Bild.
 *
 * Eine Kamera, die flach über die Scheibe schaut, bildet die Scheibenebene auf eine Linie ab.
 * Das ist eine 1D-Projektion: p = (a·x + b·y + c) / (d·x + e·y + 1).
 * Die 5 Zahlen enthalten Position, Blickrichtung, Brennweite und Bildmitte der Kamera auf einmal
 * und lassen sich aus Kalibrierwürfen direkt ausrechnen (lineare Ausgleichsrechnung, keine Startwerte nötig).
 */
public record CameraModel(double a, double b, double c, double d, double e) {

    // Kalibrierwurf: geklickte Position (mm) und erkannte Pixelspalte
    public record Sample(double x, double y, double pixel) {}

    // Maßstäbe, damit die Rechnung mit ähnlich großen Zahlen arbeitet (mm ~ 100, Pixel ~ 1000)
    private static final double SCALE_MM = 200;
    private static final double SCALE_PX = 1000;

    // 5 Unbekannte pro Kamera; mit mehr Würfen mitteln sich Klick- und Erkennungsfehler heraus
    // (Simulation: 8 Würfe ca. 1,7 mm, 15 Würfe ca. 1,0 mm, 20 Würfe ca. 0,85 mm mittlerer Fehler)
    public static final int MIN_SAMPLES = 8;
    public static final int RECOMMENDED_SAMPLES = 15;

    // gleiche Kamera, aber das Bild um offsetPx verschoben (z. B. Kamera leicht verdreht montiert)
    public CameraModel shifted(double offsetPx) {
        return new CameraModel(a + offsetPx * d, b + offsetPx * e, c + offsetPx, d, e);
    }

    public double pixel(double x, double y) {
        return (a * x + b * y + c) / (d * x + e * y + 1);
    }

    /**
     * Alle Scheibenpunkte, die die Kamera in Pixelspalte p sieht, liegen auf einer Geraden
     * A·x + B·y + C = 0. Zurückgegeben wird {A, B, C}, normiert auf Länge 1 von (A, B).
     */
    public double[] lineFor(double p) {
        double A = a - p * d;
        double B = b - p * e;
        double C = c - p;
        double n = Math.hypot(A, B);
        return new double[] {A / n, B / n, C / n};
    }

    /** Modell aus mindestens MIN_SAMPLES Würfen; null, wenn die Würfe zu ähnlich liegen. */
    public static CameraModel fit(List<Sample> samples) {
        if (samples.size() < MIN_SAMPLES) return null;
        return solve(samples);
    }

    // Ausgleichsrechnung ohne Mindestanzahl (ab 5 Würfen lösbar)
    private static CameraModel solve(List<Sample> samples) {
        if (samples.size() < 5) return null;
        // Gleichung je Wurf: a·x + b·y + c − p·x·d − p·y·e = p  (in skalierten Einheiten)
        double[][] ata = new double[5][5];
        double[] atb = new double[5];
        for (Sample s : samples) {
            double x = s.x() / SCALE_MM, y = s.y() / SCALE_MM, p = s.pixel() / SCALE_PX;
            double[] row = {x, y, 1, -p * x, -p * y};
            for (int i = 0; i < 5; i++) {
                atb[i] += row[i] * p;
                for (int j = 0; j < 5; j++) ata[i][j] += row[i] * row[j];
            }
        }
        double[] k = solve(ata, atb);
        if (k == null) return null;
        // zurück in mm und Pixel
        return new CameraModel(SCALE_PX * k[0] / SCALE_MM, SCALE_PX * k[1] / SCALE_MM, SCALE_PX * k[2],
                k[3] / SCALE_MM, k[4] / SCALE_MM);
    }

    /** Ergebnis einer Kalibrierung mit Ausreißer-Prüfung: Modell und die Würfe, die dafür benutzt wurden */
    public record Fit(CameraModel model, List<Sample> used, int discarded) {}

    /**
     * Wie fit(), wirft aber einzelne Würfe raus, die gar nicht passen (falsch angeklickt oder falscher Pfeil erkannt).
     * Verfahren (RANSAC): viele Modelle aus zufälligen kleinen Gruppen von Würfen rechnen und das nehmen, zu dem die
     * meisten Würfe passen (höchstens 12 Pixel daneben). Mit diesen Würfen wird das Modell neu berechnet.
     * Passen weniger als 70 % der Würfe, wird nichts verworfen (dann sind die Daten insgesamt zu ungenau).
     */
    public static Fit fitRobust(List<Sample> samples) {
        CameraModel all = fit(samples);
        if (all == null) return null;
        final double inlierPx = 12;
        java.util.Random random = new java.util.Random(42); // gleiche Daten, gleiches Ergebnis
        int n = samples.size(), subset = 6;
        List<Sample> best = samples;
        int bestCount = countWithin(all, samples, inlierPx);
        for (int iter = 0; iter < 400; iter++) {
            List<Sample> pick = new java.util.ArrayList<>(samples);
            java.util.Collections.shuffle(pick, random);
            CameraModel m = solve(pick.subList(0, subset));
            if (m == null) continue;
            int count = countWithin(m, samples, inlierPx);
            if (count > bestCount) {
                bestCount = count;
                List<Sample> inliers = new java.util.ArrayList<>();
                for (Sample s : samples) if (Math.abs(m.pixel(s.x(), s.y()) - s.pixel()) <= inlierPx) inliers.add(s);
                best = inliers;
            }
        }
        if (best.size() < MIN_SAMPLES || best.size() < 0.7 * n) return new Fit(all, samples, 0);
        CameraModel model = fit(best);
        return model == null ? new Fit(all, samples, 0) : new Fit(model, best, n - best.size());
    }

    private static int countWithin(CameraModel m, List<Sample> samples, double px) {
        int c = 0;
        for (Sample s : samples) if (Math.abs(m.pixel(s.x(), s.y()) - s.pixel()) <= px) c++;
        return c;
    }

    /**
     * Startwert vor der ersten Kalibrierung, aus der geplanten Montage.
     * positionDeg: wo die Kamera um die Scheibe sitzt (0° = oben, im Uhrzeigersinn), distanceMm bis zum Bullseye.
     * Die Kamera schaut zur Mitte, Pixel nach rechts drehen den Blick im Uhrzeigersinn.
     */
    public static CameraModel nominal(double positionDeg, double distanceMm, double fovDeg, double widthPx) {
        double phi = Math.toRadians(positionDeg);
        double cx = distanceMm * Math.sin(phi), cy = -distanceMm * Math.cos(phi); // Kameraposition
        double ux = -cx / distanceMm, uy = -cy / distanceMm;                       // Blick zur Mitte
        double rx = -uy, ry = ux;                                                  // rechts im Bild
        double f = widthPx / (2 * Math.tan(Math.toRadians(fovDeg) / 2));
        double c0 = widthPx / 2;
        double k = -(ux * cx + uy * cy); // = Abstand
        double rc = rx * cx + ry * cy;
        return new CameraModel((c0 * ux + f * rx) / k, (c0 * uy + f * ry) / k, c0 - f * rc / k, ux / k, uy / k);
    }

    // Gauß-Verfahren mit Pivotsuche; null, wenn das System (fast) singulär ist
    private static double[] solve(double[][] m, double[] v) {
        int n = v.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) a[i] = m[i].clone();
        double[] b = v.clone();
        double scale = 0;
        for (double[] row : a) for (double x : row) scale = Math.max(scale, Math.abs(x));
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            if (Math.abs(a[pivot][col]) < 1e-10 * scale) return null;
            double[] tmpRow = a[col]; a[col] = a[pivot]; a[pivot] = tmpRow;
            double tmp = b[col]; b[col] = b[pivot]; b[pivot] = tmp;
            for (int r = col + 1; r < n; r++) {
                double factor = a[r][col] / a[col][col];
                for (int c = col; c < n; c++) a[r][c] -= factor * a[col][c];
                b[r] -= factor * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double sum = b[r];
            for (int c = r + 1; c < n; c++) sum -= a[r][c] * x[c];
            x[r] = sum / a[r][r];
        }
        return x;
    }
}
