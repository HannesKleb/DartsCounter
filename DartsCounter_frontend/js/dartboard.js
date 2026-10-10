/********* Dartscheibe: Geometrie, Felder, Werte *********/

// Zahlen im Uhrzeigersinn, beginnend oben
const BOARD_NUMBERS = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];

// Radien in mm, passend zu resources/dartboard.svg
const BOARD_RADIUS = {
    innerBull: 6.35,
    outerBull: 15.9,
    tripleInner: 99,
    tripleOuter: 107,
    doubleInner: 170,
    doubleOuter: 178,
    board: 225.5
};

// Feld-Codes: "S20" Single, "D20" Double, "T20" Triple, "SB" Bull (25), "DB" Bullseye (50), "M" daneben
function fieldValue(field) {
    if (field === "M") return 0;
    if (field === "SB") return 25;
    if (field === "DB") return 50;
    const number = parseInt(field.slice(1), 10);
    const factor = { S: 1, D: 2, T: 3 }[field[0]];
    return number * factor;
}

function fieldNumber(field) {
    if (field === "M") return 0;
    if (field === "SB" || field === "DB") return 25;
    return parseInt(field.slice(1), 10);
}

function fieldMultiplier(field) {
    if (field === "M") return 0;
    if (field === "SB") return 1;
    if (field === "DB") return 2;
    return { S: 1, D: 2, T: 3 }[field[0]];
}

function isDouble(field) {
    return field === "DB" || field[0] === "D";
}

function fieldLabel(field) {
    if (field === "M") return "0";
    if (field === "SB") return "25";
    if (field === "DB") return "Bull";
    return field[0] === "S" ? field.slice(1) : field;
}

// Abstand (mm) und Winkel (0° = oben, im Uhrzeigersinn) -> Feld
function fieldFromPolar(radiusMm, angleDeg) {
    const r = radiusMm;
    if (r <= BOARD_RADIUS.innerBull) return "DB";
    if (r <= BOARD_RADIUS.outerBull) return "SB";
    if (r > BOARD_RADIUS.doubleOuter) return "M";

    const angle = ((angleDeg % 360) + 360) % 360;
    const number = BOARD_NUMBERS[Math.floor(((angle + 9) % 360) / 18)];

    if (r >= BOARD_RADIUS.doubleInner) return `D${number}`;
    if (r >= BOARD_RADIUS.tripleInner && r <= BOARD_RADIUS.tripleOuter) return `T${number}`;
    return `S${number}`;
}

// Kartesisch in mm (x nach rechts, y nach unten wie im SVG) -> Feld
function fieldFromXY(x, y) {
    const radius = Math.sqrt(x * x + y * y);
    const angle = Math.atan2(x, -y) * (180 / Math.PI);
    return fieldFromPolar(radius, angle);
}

// Mittelpunkt eines Feldes in mm, als Zielpunkt für Bots
function fieldCenter(field) {
    if (field === "DB" || field === "M") return { x: 0, y: 0 };
    if (field === "SB") return { x: 0, y: -(BOARD_RADIUS.innerBull + BOARD_RADIUS.outerBull) / 2 };

    const number = fieldNumber(field);
    const angle = BOARD_NUMBERS.indexOf(number) * 18 * (Math.PI / 180);
    const radius = {
        S: (BOARD_RADIUS.tripleOuter + BOARD_RADIUS.doubleInner) / 2,
        D: (BOARD_RADIUS.doubleInner + BOARD_RADIUS.doubleOuter) / 2,
        T: (BOARD_RADIUS.tripleInner + BOARD_RADIUS.tripleOuter) / 2
    }[field[0]];

    return { x: radius * Math.sin(angle), y: -radius * Math.cos(angle) };
}

/********* Bot-Wurf: Zielpunkt + Streuung *********/

const BOT_SPREAD_MM = { easy: 45, medium: 24, hard: 13 };

function randomNormal() {
    // Box-Muller
    let u = 0;
    let v = 0;
    while (u === 0) u = Math.random();
    while (v === 0) v = Math.random();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

function simulateThrow(targetField, level) {
    const spread = BOT_SPREAD_MM[level] ?? BOT_SPREAD_MM.medium;
    const center = fieldCenter(targetField);
    return fieldFromXY(center.x + randomNormal() * spread, center.y + randomNormal() * spread);
}

/********* Checkout-Wege *********/

const PREFERRED_DOUBLES = ["D20", "D16", "D18", "D10", "D8", "D12", "D14", "D6", "D4", "D2",
    "D19", "D17", "D15", "D13", "D11", "D9", "D7", "D5", "D3", "D1", "DB"];

const SETUP_FIELDS = [
    ...[20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1].map((n) => `T${n}`),
    ...[20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1].map((n) => `S${n}`),
    "SB", "DB",
    ...PREFERRED_DOUBLES.filter((d) => d !== "DB")
];

function canFinishWith(field, outMode) {
    if (outMode === "single") return true;
    if (outMode === "master") return isDouble(field) || field[0] === "T";
    return isDouble(field);
}

// Sucht einen Finish-Weg für "score" mit höchstens "darts" Darts, z. B. ["T20", "T20", "D20"]
// Bei mehreren Möglichkeiten gewinnt der Weg mit beliebten Feldern (T20, T19 ... und D20, D16 ...)
function findCheckout(score, darts, outMode = "double") {
    if (score <= 0 || darts <= 0) return null;

    const finishers = SETUP_FIELDS.filter((f) => canFinishWith(f, outMode));
    const finishCost = (f) => {
        const index = PREFERRED_DOUBLES.indexOf(f);
        return index >= 0 ? index * 2 : setupCost(f);
    };

    for (let count = 1; count <= darts; count++) {
        let best = null;
        let bestCost = Infinity;
        for (const finish of finishers) {
            const rest = score - fieldValue(finish);
            if (rest < 0) continue;
            const setups = count === 1 ? (rest === 0 ? [[]] : []) : allSetups(rest, count - 1);
            for (const setup of setups) {
                const cost = finishCost(finish) + setup.reduce((sum, f) => sum + setupCost(f), 0);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = [...setup, finish];
                }
            }
        }
        if (best) return best;
    }
    return null;
}

// je kleiner, desto lieber wird das Feld zum Vorbereiten geworfen
function setupCost(field) {
    if (field === "SB") return 12;
    if (field === "DB") return 15;
    const n = fieldNumber(field);
    if (field[0] === "T") return (20 - n) * 1.5;
    if (field[0] === "S") return 4 + (20 - n) * 0.3;
    return 25;
}

// alle Kombinationen aus genau "count" Darts mit Summe "score"
function allSetups(score, count) {
    if (count === 0) return score === 0 ? [[]] : [];
    const result = [];
    for (const field of SETUP_FIELDS) {
        const rest = score - fieldValue(field);
        if (rest < 0) continue;
        for (const path of allSetups(rest, count - 1)) result.push([field, ...path]);
    }
    return result;
}
