package com.DartsCounter.DartsCounter_backend;

import java.util.Map;

// Treffpunkt aus den Pixelspalten mehrerer Kameras: Schnittpunkt der Sichtgeraden (bei 3 Kameras ausgeglichen)
public final class Triangulator {

    public record Point(double x, double y) {}

    private Triangulator() {
    }

    /**
     * @param pixels Kamera-Nummer -> Pixelspalte
     * @param models Kamera-Nummer -> Modell
     * @return Punkt in mm oder null (weniger als 2 Kameras oder Geraden fast parallel)
     */
    public static Point locate(Map<Integer, Double> pixels, Map<Integer, CameraModel> models) {
        // minimiert die Summe der quadrierten Abstände zu allen Geraden
        double sxx = 0, sxy = 0, syy = 0, sx = 0, sy = 0;
        int count = 0;
        for (var entry : pixels.entrySet()) {
            CameraModel model = models.get(entry.getKey());
            if (model == null) continue;
            double[] line = model.lineFor(entry.getValue());
            double A = line[0], B = line[1], C = line[2];
            sxx += A * A; sxy += A * B; syy += B * B;
            sx -= A * C; sy -= B * C;
            count++;
        }
        if (count < 2) return null;
        double det = sxx * syy - sxy * sxy;
        if (det < 1e-6 * count * count) return null;
        return new Point((syy * sx - sxy * sy) / det, (sxx * sy - sxy * sx) / det);
    }
}
