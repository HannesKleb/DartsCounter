package com.DartsCounter.DartsCounter_backend;

import org.springframework.stereotype.Service;

@Service
public class ScoreCommunication {

    // Radien normiert auf 22,5 cm (Maße einer Standard-Steeldartscheibe)
    static final double INNER_BULL = 0.0282;   // 6,35 mm
    static final double OUTER_BULL = 0.0707;   // 15,9 mm
    static final double TRIPLE_IN  = 0.44;     // 99 mm
    static final double TRIPLE_OUT = 0.4756;   // 107 mm
    static final double DOUBLE_IN  = 0.72;     // 162 mm
    static final double DOUBLE_OUT = 0.7556;   // 170 mm

    // Zahlen ab oben (20) gegen den Uhrzeigersinn, je 18°
    private static final int[] NUMBERS = {20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6, 13, 4, 18, 1};

    /**
     * @param radius normierter Radius (0 = Mitte, 1 = 22,5 cm)
     * @param angle  Winkel in Grad, 0 = oben (20), gegen den Uhrzeigersinn (wie im Frontend)
     * @return Feld, z. B. "IB", "OB", "S20", "D5", "T19" oder "MISS"
     */
    public String coordsToField(double radius, double angle) {
        if (radius < INNER_BULL) return "IB";
        if (radius < OUTER_BULL) return "OB";
        if (radius >= DOUBLE_OUT) return "MISS";

        double a = ((angle % 360) + 360) % 360;
        int number = NUMBERS[(int) (((a + 9) % 360) / 18)];

        if (radius >= TRIPLE_IN && radius < TRIPLE_OUT) return "T" + number;
        if (radius >= DOUBLE_IN) return "D" + number;
        return "S" + number;
    }

    // Punktwert eines Feldes, z. B. "T20" -> 60
    public int fieldToScore(String field) {
        switch (field) {
            case "IB": return 50;
            case "OB": return 25;
            case "MISS": return 0;
        }
        int number = Integer.parseInt(field.substring(1));
        switch (field.charAt(0)) {
            case 'D': return 2 * number;
            case 'T': return 3 * number;
            default: return number;
        }
    }
}
