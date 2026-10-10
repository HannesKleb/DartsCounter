/********* Statistiken aus gespeicherten Spielen *********/

function dartsOf(s, p) {
    const done = s.turns.filter((t) => t.player === p).reduce((sum, t) => sum + t.darts.length, 0);
    return done + (s.current === p ? s.turn.length : 0);
}

// Kennzahlen eines Spielers in einem Spiel
function playerGameStats(s, p) {
    const turns = s.turns.filter((t) => t.player === p);
    const darts = dartsOf(s, p);
    const stats = {
        darts,
        turns: turns.length,
        legsWon: s.legsWon[p],
        won: s.finished && s.winner === p
    };

    switch (s.game.mode) {
        case "X01": {
            const points = turns.reduce((sum, t) => sum + t.points, 0);
            stats.points = points;
            stats.average = darts ? (points / darts) * 3 : 0;
            stats.highest = Math.max(0, ...turns.map((t) => t.points));
            stats.count180 = turns.filter((t) => t.points === 180).length;
            stats.count140 = turns.filter((t) => t.points >= 140).length;
            stats.count100 = turns.filter((t) => t.points >= 100).length;
            stats.bestCheckout = Math.max(0, ...turns.map((t) => t.checkout ?? 0));
            break;
        }
        case "CRICKET": {
            const marks = turns.reduce((sum, t) => sum + t.marks, 0);
            stats.marks = marks;
            stats.mpr = turns.length ? marks / turns.length : 0;
            stats.bestMarks = Math.max(0, ...turns.map((t) => t.marks));
            stats.points = s.ps[p].points;
            break;
        }
        case "ATC": {
            const hits = turns.reduce((sum, t) => sum + t.hits, 0);
            stats.hits = hits;
            stats.hitRate = darts ? hits / darts : 0;
            break;
        }
        case "G121": {
            const ps = s.ps[p];
            stats.best = ps.best;
            stats.successes = ps.successes;
            stats.attempts = ps.attemptsDone;
            stats.successRate = ps.attemptsDone ? ps.successes / ps.attemptsDone : 0;
            break;
        }
        case "FREE": {
            stats.total = s.ps[p].total;
            stats.perTurn = turns.length ? stats.total / turns.length : 0;
            stats.highest = Math.max(0, ...turns.map((t) => t.points));
            break;
        }
    }
    return stats;
}

// Zusammenfassung über viele Spiele für einen Spielernamen in einem Modus
function aggregatePlayerStats(games, mode, name) {
    const result = { games: 0, wins: 0, darts: 0, turns: 0 };
    const sums = { points: 0, marks: 0, hits: 0, successes: 0, attempts: 0, total: 0 };
    const max = { highest: 0, bestCheckout: 0, bestMarks: 0, best: 0, total: 0, count180: 0, count140: 0, count100: 0 };

    for (const game of games) {
        if (game.mode !== mode) continue;
        const p = game.players.findIndex((player) => player.name === name);
        if (p < 0) continue;

        const s = replayGame(game);
        const st = playerGameStats(s, p);
        result.games++;
        if (st.won) result.wins++;
        result.darts += st.darts;
        result.turns += st.turns;
        for (const key of Object.keys(sums)) sums[key] += st[key] ?? 0;
        for (const key of ["highest", "bestCheckout", "bestMarks", "best", "total"]) {
            max[key] = Math.max(max[key], st[key] ?? 0);
        }
        max.count180 += st.count180 ?? 0;
        max.count140 += st.count140 ?? 0;
        max.count100 += st.count100 ?? 0;
    }

    result.winRate = result.games ? result.wins / result.games : 0;
    switch (mode) {
        case "X01":
            result.average = result.darts ? (sums.points / result.darts) * 3 : 0;
            result.highest = max.highest;
            result.bestCheckout = max.bestCheckout;
            result.count180 = max.count180;
            result.count140 = max.count140;
            result.count100 = max.count100;
            break;
        case "CRICKET":
            result.mpr = result.turns ? sums.marks / result.turns : 0;
            result.bestMarks = max.bestMarks;
            break;
        case "ATC":
            result.hitRate = result.darts ? sums.hits / result.darts : 0;
            break;
        case "G121":
            result.best = max.best;
            result.successRate = sums.attempts ? sums.successes / sums.attempts : 0;
            break;
        case "FREE":
            result.perTurn = result.turns ? sums.total / result.turns : 0;
            result.bestTotal = max.total;
            result.highest = max.highest;
            break;
    }
    return result;
}

// Spalten für Tabellen: [Überschrift, Wert-Funktion]
const fmt = {
    num: (v) => (v ?? 0).toLocaleString("de-DE"),
    dec: (v) => (v ?? 0).toLocaleString("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    pct: (v) => `${Math.round((v ?? 0) * 100)} %`
};

const STAT_COLUMNS = {
    common: [
        ["Spiele", (r) => fmt.num(r.games)],
        ["Siege", (r) => fmt.num(r.wins)],
        ["Siegquote", (r) => fmt.pct(r.winRate)],
        ["Darts", (r) => fmt.num(r.darts)]
    ],
    X01: [
        ["Average (3 Darts)", (r) => fmt.dec(r.average)],
        ["Höchste Aufnahme", (r) => fmt.num(r.highest)],
        ["Höchstes Checkout", (r) => fmt.num(r.bestCheckout)],
        ["180er", (r) => fmt.num(r.count180)],
        ["140+", (r) => fmt.num(r.count140)],
        ["100+", (r) => fmt.num(r.count100)]
    ],
    CRICKET: [
        ["Marks pro Runde", (r) => fmt.dec(r.mpr)],
        ["Beste Aufnahme (Marks)", (r) => fmt.num(r.bestMarks)]
    ],
    ATC: [
        ["Trefferquote", (r) => fmt.pct(r.hitRate)]
    ],
    G121: [
        ["Höchstes geschafftes Ziel", (r) => (r.best ? fmt.num(r.best) : "–")],
        ["Erfolgsquote", (r) => fmt.pct(r.successRate)]
    ],
    FREE: [
        ["Punkte pro Aufnahme", (r) => fmt.dec(r.perTurn)],
        ["Beste Gesamtpunkte", (r) => fmt.num(r.bestTotal)],
        ["Höchste Aufnahme", (r) => fmt.num(r.highest)]
    ]
};

// Kurzinfo pro Spieler in einem einzelnen Spiel (Alle Spiele, Spielende)
function gameStatsLine(mode, st) {
    switch (mode) {
        case "X01":
            return `Avg ${fmt.dec(st.average)} · Höchste ${st.highest} · Checkout ${st.bestCheckout || "–"} · Legs ${st.legsWon}`;
        case "CRICKET":
            return `MPR ${fmt.dec(st.mpr)} · Punkte ${st.points} · Legs ${st.legsWon}`;
        case "ATC":
            return `Trefferquote ${fmt.pct(st.hitRate)} · ${st.darts} Darts · Legs ${st.legsWon}`;
        case "G121":
            return `Bestes Ziel ${st.best || "–"} · ${st.successes}/${st.attempts} geschafft`;
        case "FREE":
            return `${st.total} Punkte · ${fmt.dec(st.perTurn)} pro Aufnahme`;
        default:
            return "";
    }
}
