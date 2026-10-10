/********* Spielregeln aller Modi *********/
// Ein Spiel speichert nur die Liste aller geworfenen Darts (z. B. ["T20", "S5", "D16"]).
// Der aktuelle Stand wird jedes Mal aus dieser Liste neu berechnet ("replay").
// Dadurch ist Rückgängig einfach: letzten Dart entfernen und neu berechnen.

const MODES = {
    X01: {
        label: "X01",
        page: "X01.html",
        defaults: { start: 501, in: "single", out: "double", legs: 1 }
    },
    CRICKET: {
        label: "Cricket",
        page: "cricket.html",
        defaults: { variant: "standard", rounds: 20, legs: 1 }
    },
    ATC: {
        label: "Around the Clock",
        page: "aroundTheClock.html",
        defaults: { bull: true, multiSkip: false, legs: 1 }
    },
    G121: {
        label: "121",
        page: "g121.html",
        defaults: { start: 121, attempts: 10, onFail: "stay" }
    },
    FREE: {
        label: "Freies Spiel",
        page: "freiesSpiel.html",
        defaults: { rounds: 10 }
    }
};

const CRICKET_NUMBERS = [20, 19, 18, 17, 16, 15, 25];

function describeOptions(mode, o) {
    switch (mode) {
        case "X01":
            return `${o.start} · ${o.in === "double" ? "Double-In" : "Single-In"} · ` +
                `${{ single: "Single-Out", double: "Double-Out", master: "Master-Out" }[o.out]} · First to ${o.legs}`;
        case "CRICKET":
            return `${o.variant === "cutthroat" ? "Cut Throat" : "Standard"} · ` +
                `${o.rounds > 0 ? `max. ${o.rounds} Runden` : "ohne Rundenlimit"} · First to ${o.legs}`;
        case "ATC":
            return `1–20${o.bull ? " + Bull" : ""}${o.multiSkip ? " · Double/Triple springen weiter" : ""} · First to ${o.legs}`;
        case "G121":
            return `Start ${o.start} · ${o.attempts} Versuche à 9 Darts · bei Fehlschlag ${o.onFail === "down" ? "−1" : "gleiches Ziel"}`;
        case "FREE":
            return `${o.rounds} Runden`;
        default:
            return "";
    }
}

/********* Regeln pro Modus *********/

const RULES = {
    X01: {
        startLeg(s) {
            const o = s.game.options;
            s.ps = s.game.players.map(() => ({ score: o.start, opened: o.in !== "double" }));
        },
        dart(s, p, dart) {
            const o = s.game.options;
            const ps = s.ps[p];
            if (s.turn.length === 1) {
                ps.turnStartScore = ps.score;
                ps.turnStartOpened = ps.opened;
            }

            let value = fieldValue(dart);
            if (!ps.opened) {
                if (isDouble(dart)) ps.opened = true;
                else value = 0;
            }

            const rest = ps.score - value;
            const bust = rest < 0 ||
                (rest === 1 && o.out !== "single") ||
                (rest === 0 && !canFinishWith(dart, o.out));

            if (bust) {
                ps.score = ps.turnStartScore;
                ps.opened = ps.turnStartOpened;
                return { bust: true };
            }
            ps.score = rest;
            return rest === 0 ? { legWon: true } : {};
        },
        turnInfo(s, p, result) {
            const ps = s.ps[p];
            return {
                points: result.bust ? 0 : ps.turnStartScore - ps.score,
                startScore: ps.turnStartScore,
                checkout: result.legWon ? ps.turnStartScore : undefined
            };
        }
    },

    CRICKET: {
        startLeg(s) {
            s.ps = s.game.players.map(() => ({
                marks: Object.fromEntries(CRICKET_NUMBERS.map((n) => [n, 0])),
                points: 0,
                turnMarks: 0
            }));
        },
        dart(s, p, dart) {
            const cutthroat = s.game.options.variant === "cutthroat";
            const ps = s.ps[p];
            if (s.turn.length === 1) ps.turnMarks = 0;

            const number = fieldNumber(dart);
            if (CRICKET_NUMBERS.includes(number)) {
                const hits = fieldMultiplier(dart);
                const before = ps.marks[number];
                const closing = Math.min(hits, 3 - before);
                const extra = hits - closing;
                ps.marks[number] = before + closing;

                const openOpponents = s.ps.filter((other, i) => i !== p && other.marks[number] < 3);
                let scoring = 0;
                if (extra > 0 && openOpponents.length > 0) {
                    scoring = extra;
                    if (cutthroat) openOpponents.forEach((other) => { other.points += extra * number; });
                    else ps.points += extra * number;
                }
                ps.turnMarks += closing + scoring;
            }

            const allClosed = CRICKET_NUMBERS.every((n) => ps.marks[n] >= 3);
            if (!allClosed) return {};
            const others = s.ps.filter((_, i) => i !== p).map((other) => other.points);
            const leads = cutthroat
                ? others.every((points) => ps.points <= points)
                : others.every((points) => ps.points >= points);
            return leads ? { legWon: true } : {};
        },
        turnInfo(s, p) {
            return { marks: s.ps[p].turnMarks };
        },
        roundLimit(s) {
            return s.game.options.rounds > 0 ? s.game.options.rounds : Infinity;
        },
        leader(s) {
            const cutthroat = s.game.options.variant === "cutthroat";
            const totalMarks = (ps) => CRICKET_NUMBERS.reduce((sum, n) => sum + ps.marks[n], 0);
            return pickLeader(s.ps, (a, b) =>
                (cutthroat ? b.points - a.points : a.points - b.points) || totalMarks(a) - totalMarks(b));
        }
    },

    ATC: {
        startLeg(s) {
            s.targets = [...Array(20).keys()].map((i) => i + 1);
            if (s.game.options.bull) s.targets.push(25);
            s.ps = s.game.players.map(() => ({ index: 0, turnHits: 0 }));
        },
        dart(s, p, dart) {
            const ps = s.ps[p];
            if (s.turn.length === 1) ps.turnHits = 0;

            const target = s.targets[ps.index];
            if (fieldNumber(dart) !== target) return {};

            const step = s.game.options.multiSkip && target !== 25 ? fieldMultiplier(dart) : 1;
            ps.index = Math.min(ps.index + step, s.targets.length);
            ps.turnHits++;
            return ps.index >= s.targets.length ? { legWon: true } : {};
        },
        turnInfo(s, p) {
            return { hits: s.ps[p].turnHits };
        }
    },

    G121: {
        startLeg(s) {
            const start = s.game.options.start;
            s.ps = s.game.players.map(() => ({
                target: start,
                best: 0,
                remaining: start,
                turnInAttempt: 0,
                attemptsDone: 0,
                successes: 0
            }));
        },
        dart(s, p, dart) {
            const ps = s.ps[p];
            if (s.turn.length === 1) ps.turnStartRemaining = ps.remaining;

            const rest = ps.remaining - fieldValue(dart);
            if (rest < 0 || rest === 1 || (rest === 0 && !isDouble(dart))) {
                ps.remaining = ps.turnStartRemaining;
                return { bust: true };
            }
            ps.remaining = rest;
            return rest === 0 ? { turnOver: true, success: true } : {};
        },
        turnInfo(s, p, result) {
            const ps = s.ps[p];
            return {
                target: ps.target,
                points: result.bust ? 0 : ps.turnStartRemaining - ps.remaining,
                checkout: result.success ? ps.target : undefined
            };
        },
        endTurn(s, p, record) {
            const o = s.game.options;
            const ps = s.ps[p];
            if (record.checkout !== undefined) {
                ps.successes++;
                ps.best = Math.max(ps.best, ps.target);
                ps.target++;
            } else {
                ps.turnInAttempt++;
                if (ps.turnInAttempt < 3) return;
                if (o.onFail === "down") ps.target = Math.max(o.start, ps.target - 1);
            }
            ps.attemptsDone++;
            ps.turnInAttempt = 0;
            ps.remaining = ps.target;
        },
        isPlayerDone(s, p) {
            return s.ps[p].attemptsDone >= s.game.options.attempts;
        },
        leader(s) {
            return pickLeader(s.ps, (a, b) => a.best - b.best || a.successes - b.successes);
        }
    },

    FREE: {
        startLeg(s) {
            s.ps = s.game.players.map(() => ({ total: 0 }));
        },
        dart(s, p, dart) {
            s.ps[p].total += fieldValue(dart);
            return {};
        },
        turnInfo(s, p, result, darts) {
            return { points: darts.reduce((sum, d) => sum + fieldValue(d), 0) };
        },
        roundLimit(s) {
            return s.game.options.rounds;
        },
        leader(s) {
            return pickLeader(s.ps, (a, b) => a.total - b.total);
        }
    }
};

// Index des Besten nach "compare" (> 0 heißt a ist besser), bei Gleichstand null
function pickLeader(list, compare) {
    let best = 0;
    for (let i = 1; i < list.length; i++) {
        if (compare(list[i], list[best]) > 0) best = i;
    }
    const tie = list.some((item, i) => i !== best && compare(item, list[best]) === 0);
    return tie ? null : best;
}

/********* Ablauf: Aufnahmen, Spielerwechsel, Legs *********/

function replayGame(game) {
    const s = {
        game,
        rules: RULES[game.mode],
        n: game.players.length,
        legsToWin: game.options.legs ?? 1,
        leg: 1,
        legStarter: 0,
        current: 0,
        round: 1,
        turn: [],
        turns: [],
        legsWon: game.players.map(() => 0),
        finished: false,
        winner: null
    };
    s.rules.startLeg(s);
    for (const dart of game.darts) applyDart(s, dart);
    return s;
}

function applyDart(s, dart) {
    if (s.finished) return;
    s.turn.push(dart);
    const result = s.rules.dart(s, s.current, dart) || {};
    if (result.legWon || result.bust || result.turnOver || s.turn.length === 3) {
        finishTurn(s, result);
    }
}

function finishTurn(s, result) {
    const p = s.current;
    const darts = s.turn;
    const record = {
        player: p,
        leg: s.leg,
        round: s.round,
        darts,
        bust: Boolean(result.bust),
        legWon: Boolean(result.legWon),
        ...(s.rules.turnInfo?.(s, p, result, darts) ?? {})
    };
    s.turns.push(record);
    s.turn = [];
    s.rules.endTurn?.(s, p, record);

    if (result.legWon) {
        winLeg(s, p);
        return;
    }

    // nächster Spieler, fertige Spieler (121) überspringen
    const isDone = (i) => s.rules.isPlayerDone?.(s, i) ?? false;
    let next = p;
    for (let step = 0; step < s.n; step++) {
        next = (next + 1) % s.n;
        if (next === s.legStarter) s.round++;
        if (!isDone(next)) break;
    }
    if (isDone(next)) {
        endLegByLeader(s);
        return;
    }
    s.current = next;

    const limit = s.rules.roundLimit?.(s) ?? Infinity;
    if (s.round > limit) endLegByLeader(s);
}

function endLegByLeader(s) {
    const leader = s.rules.leader(s);
    if (leader === null) {
        // Gleichstand
        if (s.legsToWin <= 1) {
            s.finished = true;
            s.winner = null;
            return;
        }
        startNextLeg(s);
        return;
    }
    winLeg(s, leader);
}

function winLeg(s, p) {
    s.legsWon[p]++;
    if (s.legsWon[p] >= s.legsToWin) {
        s.finished = true;
        s.winner = p;
        return;
    }
    startNextLeg(s);
}

function startNextLeg(s) {
    s.leg++;
    s.legStarter = (s.legStarter + 1) % s.n;
    s.current = s.legStarter;
    s.round = 1;
    s.rules.startLeg(s);
}

/********* Bot: welches Feld wird angepeilt *********/

function botTarget(s) {
    const p = s.current;
    const o = s.game.options;
    const ps = s.ps[p];
    const dartsLeft = 3 - s.turn.length;

    switch (s.game.mode) {
        case "X01": {
            if (!ps.opened) return "D20";
            const path = ps.score <= 170 ? findCheckout(ps.score, dartsLeft, o.out) : null;
            if (path) return path[0];
            // nicht auf 1 stellen bzw. auf einen Rest ohne Finish
            if (ps.score < 60) return ps.score > 40 ? `S${Math.min(20, ps.score - 32)}` : `S${ps.score % 2 ? 1 : 2}`;
            return "T20";
        }
        case "G121": {
            const path = findCheckout(ps.remaining, dartsLeft, "double");
            if (path) return path[0];
            return ps.remaining > 60 ? "T20" : `S${Math.max(1, Math.min(20, ps.remaining - 40))}`;
        }
        case "CRICKET": {
            const open = CRICKET_NUMBERS.filter((n) => ps.marks[n] < 3);
            const scoring = CRICKET_NUMBERS.filter((n) => ps.marks[n] >= 3 &&
                s.ps.some((other, i) => i !== p && other.marks[n] < 3));
            const number = (o.variant === "cutthroat" ? open[0] : (open[0] ?? scoring[0])) ?? 20;
            return number === 25 ? "DB" : `T${number}`;
        }
        case "ATC": {
            const target = s.targets[ps.index];
            if (target === 25) return "SB";
            return o.multiSkip ? `T${target}` : `S${target}`;
        }
        default:
            return "T20";
    }
}
