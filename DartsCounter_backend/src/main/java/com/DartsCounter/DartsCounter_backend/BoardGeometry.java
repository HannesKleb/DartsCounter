package com.DartsCounter.DartsCounter_backend;

// Felder der Dartscheibe. Koordinaten wie im Frontend (js/dartboard.js): Millimeter, Ursprung Bullseye,
// x nach rechts, y nach unten, Winkel 0° = oben, im Uhrzeigersinn. Feld-Codes ebenfalls wie im Frontend:
// "S20", "D20", "T20", "SB" (25), "DB" (Bullseye), "M" (daneben).
public final class BoardGeometry {
    // Maße einer Standard-Steeldartscheibe in mm (Innenkante der Drähte)
    static final double INNER_BULL = 6.35;
    static final double OUTER_BULL = 15.9;
    static final double TRIPLE_INNER = 99;
    static final double TRIPLE_OUTER = 107;
    static final double DOUBLE_INNER = 162;
    static final double DOUBLE_OUTER = 170;

    // Zahlen im Uhrzeigersinn, beginnend oben
    private static final int[] NUMBERS = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};

    private BoardGeometry() {
    }

    public static String fieldFromXY(double x, double y) {
        double r = Math.hypot(x, y);
        if (r <= INNER_BULL) return "DB";
        if (r <= OUTER_BULL) return "SB";
        if (r > DOUBLE_OUTER) return "M";

        double angle = Math.toDegrees(Math.atan2(x, -y));
        angle = ((angle % 360) + 360) % 360;
        int number = NUMBERS[(int) (((angle + 9) % 360) / 18)];

        if (r >= DOUBLE_INNER) return "D" + number;
        if (r >= TRIPLE_INNER && r <= TRIPLE_OUTER) return "T" + number;
        return "S" + number;
    }

    public static int fieldValue(String field) {
        switch (field) {
            case "M": return 0;
            case "SB": return 25;
            case "DB": return 50;
            default:
                int number = Integer.parseInt(field.substring(1));
                return number * switch (field.charAt(0)) {
                    case 'D' -> 2;
                    case 'T' -> 3;
                    default -> 1;
                };
        }
    }
}
