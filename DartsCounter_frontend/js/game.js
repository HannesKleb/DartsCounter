/********* Spielseite (alle Modi) *********/

const PAGE_MODE = document.body.dataset.mode;

let game = null;
let state = null;
let settings = { ...DEFAULT_SETTINGS };
let modifier = null;    // "D" oder "T" für den nächsten Tastatur-Dart
let botTimer = null;
let saveQueue = Promise.resolve();
let previousMain = [];      // letzte angezeigte Hauptzahl pro Spieler (Animation bei Änderung)
let previousCurrent = null; // wer zuletzt dran war (Animation beim Spielerwechsel)
let newDart = null;         // zuletzt eingegebener Dart {player, index} für die Einblend-Animation
let boardSvg = null;
let aim = null;             // {x, y, pointerId, lens} solange auf der Scheibe gedrückt wird

const LENS_SPAN_MM = 70;    // Ausschnitt der Lupe beim Antippen (Breite in mm)

document.addEventListener("DOMContentLoaded", () => runPage(initGamePage));

async function initGamePage() {
    settings = await loadSettings();
    game = await Store.get("currentGame", null);

    if (!game || game.mode !== PAGE_MODE) {
        showNoGame();
        return;
    }

    document.getElementById("game-title").textContent = MODES[game.mode].label;
    document.getElementById("game-options").textContent = describeOptions(game.mode, game.options);

    initKeyboard();
    initBoard();
    showMode(settings.inputMode === "board" ? "score-board-id" : "score-button-id", "board");

    render();
}

function showNoGame() {
    const content = document.querySelector(".content");
    content.replaceChildren(
        h("div", { class: "empty-state" },
            h("p", { text: "Hier läuft gerade kein Spiel." }),
            window.location.protocol === "file:"
                ? h("p", {
                    class: "hint",
                    text: "Per Doppelklick geöffnet? Firefox trennt dabei den Speicher zwischen den Seiten. " +
                        "Bitte Chrome/Edge benutzen oder die Seite über einen Webserver öffnen (z. B. VS Code Live Server)."
                })
                : null,
            h("button", { class: "primary-btn", text: "Zur Hauptseite", onclick: () => bar("../mainPages/index.html") }))
    );
}

/********* Darts eingeben *********/

// point = Auftreffpunkt in mm (Scheibe, Bot) oder null (Tastatur), nur zum Einzeichnen
function addDart(field, point = null) {
    if (!state || state.finished) return;
    newDart = { player: state.current, index: state.turn.length };
    const points = dartPoints();
    points.push(point ? { x: round1(point.x), y: round1(point.y) } : null);
    game.points = points;
    game.darts.push(field);
    afterChange();
}

function addHumanDart(field, point = null) {
    if (!state || state.finished || game.players[state.current].bot) return;
    addDart(field, point);
}

function popDart() {
    game.darts.pop();
    game.points = dartPoints().slice(0, game.darts.length);
}

// Punkte passend zu game.darts (ältere Spiele haben keine)
function dartPoints() {
    const points = Array.isArray(game.points) ? game.points.slice(0, game.darts.length) : [];
    while (points.length < game.darts.length) points.push(null);
    return points;
}

function round1(value) {
    return Math.round(value * 10) / 10;
}

// Rückgängig: entfernt den letzten eigenen Dart (Bot-Darts danach werden mit entfernt)
function undo() {
    if (game.darts.length === 0) return;
    clearTimeout(botTimer);
    botTimer = null;

    const owners = dartOwners(game);
    const hasHuman = game.players.some((p) => !p.bot);
    if (hasHuman) {
        while (owners.length > 0 && game.players[owners.at(-1)].bot) {
            owners.pop();
            popDart();
        }
    }
    popDart();
    afterChange();
}

// welcher Spieler hat welchen Dart geworfen
function dartOwners(g) {
    const s = replayGame({ ...g, darts: [] });
    return g.darts.map((dart) => {
        const owner = s.current;
        applyDart(s, dart);
        return owner;
    });
}

function afterChange() {
    modifier = null;
    updateModifierButtons();
    save();
    render();
}

function save() {
    const snapshot = JSON.parse(JSON.stringify(game));
    saveQueue = saveQueue.then(() => Store.set("currentGame", snapshot));
}

/********* Tastatur *********/

function initKeyboard() {
    document.querySelectorAll("[data-number]").forEach((btn) => {
        btn.addEventListener("click", () => pressNumber(parseInt(btn.dataset.number, 10)));
    });
    document.getElementById("double-btn").addEventListener("click", () => toggleModifier("D"));
    document.getElementById("triple-btn").addEventListener("click", () => toggleModifier("T"));
    document.getElementById("undo-btn").addEventListener("click", undo);
}

function toggleModifier(type) {
    modifier = modifier === type ? null : type;
    updateModifierButtons();
}

function updateModifierButtons() {
    document.getElementById("double-btn")?.classList.toggle("active", modifier === "D");
    document.getElementById("triple-btn")?.classList.toggle("active", modifier === "T");
}

function pressNumber(number) {
    let field;
    if (number === 0) field = "M";
    else if (number === 25) {
        if (modifier === "T") {
            toast("Triple-Bull gibt es nicht");
            return;
        }
        field = modifier === "D" ? "DB" : "SB";
    } else {
        field = `${modifier ?? "S"}${number}`;
    }
    addHumanDart(field);
}

/********* Scheibe *********/

function initBoard() {
    const container = document.getElementById("board");
    if (!container) return;
    boardSvg = createBoardSvg();
    boardSvg.append(svgEl("g", { class: "board-marks" }), svgEl("g", { class: "board-aim" }));

    const lensSvg = createBoardSvg();
    lensSvg.removeAttribute("role");
    lensSvg.append(svgEl("g", { class: "board-marks" }));
    const lens = h("div", { class: "board-lens", hidden: true, "aria-hidden": "true" },
        lensSvg, h("div", { class: "calib-lens-cross" }), h("div", { class: "board-lens-label" }));
    container.replaceChildren(boardSvg, lens);

    // Drücken zeigt die Stelle (am Handy mit Lupe), Ziehen korrigiert, Loslassen zählt den Dart
    boardSvg.addEventListener("pointerdown", (event) => {
        if (event.button > 0 || !state || state.finished || game.players[state.current].bot) return;
        event.preventDefault();
        boardSvg.setPointerCapture(event.pointerId);
        aim = { pointerId: event.pointerId, lens: event.pointerType !== "mouse" };
        moveAim(event);
    });
    boardSvg.addEventListener("pointermove", (event) => {
        if (aim?.pointerId === event.pointerId) moveAim(event);
    });
    boardSvg.addEventListener("pointerup", (event) => {
        if (aim?.pointerId !== event.pointerId) return;
        moveAim(event);
        const point = { x: aim.x, y: aim.y };
        aim = null;
        renderAim();
        const field = fieldFromXY(point.x, point.y);
        const target = boardSvg.querySelector(`.board-field[data-field="${field}"]`);
        if (target) replayAnimation(target, "hit");
        addHumanDart(field, point);
    });
    boardSvg.addEventListener("pointercancel", () => {
        aim = null;
        renderAim();
    });
}

function moveAim(event) {
    const p = boardPointFromClient(boardSvg, event.clientX, event.clientY);
    Object.assign(aim, clampToBoard(p.x, p.y));
    renderAim();
}

// Fadenkreuz und Lupe an der gedrückten Stelle
function renderAim() {
    const group = boardSvg.querySelector(".board-aim");
    const lens = document.querySelector(".board-lens");
    if (!aim) {
        group.replaceChildren();
        lens.hidden = true;
        return;
    }
    const { x, y } = aim;
    group.replaceChildren(
        svgEl("circle", { cx: x, cy: y, r: 7, class: "calib-marker-ring" }),
        svgEl("line", { x1: x - 13, y1: y, x2: x - 4, y2: y, class: "calib-marker-line" }),
        svgEl("line", { x1: x + 4, y1: y, x2: x + 13, y2: y, class: "calib-marker-line" }),
        svgEl("line", { x1: x, y1: y - 13, x2: x, y2: y - 4, class: "calib-marker-line" }),
        svgEl("line", { x1: x, y1: y + 4, x2: x, y2: y + 13, class: "calib-marker-line" }));
    group.setAttribute("class", "board-aim calib-marker");

    if (!aim.lens) return;
    const lensSvg = lens.querySelector("svg");
    lensSvg.setAttribute("viewBox", `${x - LENS_SPAN_MM / 2} ${y - LENS_SPAN_MM / 2} ${LENS_SPAN_MM} ${LENS_SPAN_MM}`);
    lens.querySelector(".board-lens-label").textContent = fieldLabel(fieldFromXY(x, y));
    lens.hidden = false;

    // über dem Finger, oben kein Platz: darunter
    const wrap = lens.parentElement.getBoundingClientRect();
    const screen = boardPointToClient(boardSvg, x, y);
    const size = lens.offsetWidth || 130;
    const gap = 60;
    let left = screen.x - wrap.left - size / 2;
    let top = screen.y - wrap.top - size - gap;
    if (screen.y - size - gap < 8) top = screen.y - wrap.top + gap;
    left = Math.max(-8, Math.min(left, wrap.width - size + 8));
    lens.style.transform = `translate(${Math.round(left)}px, ${Math.round(top)}px)`;
}

// Darts der laufenden (oder gerade beendeten) Aufnahme genau an ihrer Stelle einzeichnen
function renderBoardMarks() {
    if (!boardSvg) return;
    const turnDarts = state.turn.length > 0 ? state.turn.length : state.turns.at(-1)?.darts.length ?? 0;
    const points = dartPoints();
    const first = game.darts.length - turnDarts;
    const marks = [];
    for (let i = first; i < game.darts.length; i++) {
        const field = game.darts[i];
        const point = points[i] ?? (field === "M" ? null : fieldCenter(field));
        if (!point) continue;
        const isNew = i === game.darts.length - 1 && newDart !== null;
        marks.push(svgEl("g", { class: `board-mark${isNew ? " mark-in" : ""}`, transform: `translate(${point.x} ${point.y})` }));
        marks.at(-1).append(
            svgEl("circle", { r: 11, class: "board-mark-dot" }),
            svgEl("text", { class: "board-mark-num", y: 0.5 }));
        marks.at(-1).lastChild.textContent = i - first + 1;
    }
    document.querySelectorAll(".board-marks").forEach((group, n) => {
        group.replaceChildren(...(n === 0 ? marks : marks.map((m) => m.cloneNode(true))));
    });
}

/********* Anzeige *********/

function render() {
    state = replayGame(game);
    renderInfo();
    renderBoardMarks();
    renderPlayers();
    if (game.mode === "CRICKET") renderCricketBoard();
    renderResult();
    scheduleBot();
}

function renderInfo() {
    const info = document.getElementById("game-info");
    const parts = [];
    if (state.legsToWin > 1) parts.push(`Leg ${state.leg}`);
    if (game.mode === "FREE") parts.push(`Runde ${Math.min(state.round, game.options.rounds)}/${game.options.rounds}`);
    else if (game.mode === "CRICKET" && game.options.rounds > 0) parts.push(`Runde ${Math.min(state.round, game.options.rounds)}/${game.options.rounds}`);
    else if (game.mode !== "G121") parts.push(`Runde ${state.round}`);
    if (!state.finished) parts.push(`${game.players[state.current].name} ist dran`);
    info.textContent = parts.join(", ");
}

function renderPlayers() {
    const container = document.getElementById("players");
    container.replaceChildren(...game.players.map((player, p) => playerCard(player, p)));
    previousCurrent = state.finished ? null : state.current;
    newDart = null;
}

function playerCard(player, p) {
    const isCurrent = !state.finished && state.current === p;
    const view = playerView(p);
    const shownDarts = isCurrent ? state.turn : lastTurnOf(p)?.darts ?? [];
    const changed = previousMain[p] !== undefined && previousMain[p] !== view.main;
    previousMain[p] = view.main;
    const turnStart = isCurrent && previousCurrent !== null && previousCurrent !== p;
    const animateDartIndex = newDart?.player === p ? newDart.index : -1;

    return h("div", { class: `scoring-stats${isCurrent ? " current" : ""}${turnStart ? " turn-start" : ""}` },
        h("div", { class: "big-scoring-stats" },
            h("div", { class: "player-name-line" },
                h("span", { text: player.name }),
                player.bot ? h("span", { class: "badge", text: "Bot" }) : null,
                state.legsToWin > 1 ? h("span", { class: "badge", text: `Legs ${state.legsWon[p]}` }) : null),
            h("div", { class: "score-left" }, h("span", { class: changed ? "score-change" : "", text: view.main })),
            view.mainLabel ? h("div", { class: "score-label", text: view.mainLabel }) : null,
            h("div", { class: "last-score" },
                [0, 1, 2].map((i) => h("div", {
                    class: `last-score-field${i === animateDartIndex ? " dart-in" : ""}`,
                    text: shownDarts[i] !== undefined ? fieldLabel(shownDarts[i]) : "-"
                })))),
        h("div", { class: "small-scoring-stats" },
            view.finish ? h("div", { class: "finish", text: `Finish: ${view.finish}` }) : null,
            view.lines.map((line) => h("div", { text: line }))));
}

function lastTurnOf(p) {
    for (let i = state.turns.length - 1; i >= 0; i--) {
        if (state.turns[i].player === p) return state.turns[i];
    }
    return null;
}

// Was auf der Karte eines Spielers steht, je nach Modus
function playerView(p) {
    const ps = state.ps[p];
    const st = playerGameStats(state, p);
    const last = lastTurnOf(p);
    const isCurrent = !state.finished && state.current === p;
    const dartsLeft = isCurrent ? 3 - state.turn.length : 3;

    switch (game.mode) {
        case "X01": {
            const lines = [
                `Letzte Aufnahme: ${last ? (last.bust ? "Bust" : last.points) : "-"}`,
                `Average: ${fmt.dec(st.average)}`
            ];
            if (!ps.opened) lines.push("Noch kein Double getroffen");
            const path = ps.opened && ps.score <= 170 ? findCheckout(ps.score, dartsLeft, game.options.out) : null;
            return { main: ps.score, finish: path?.map(fieldLabel).join(" "), lines };
        }
        case "CRICKET":
            return {
                main: ps.points,
                mainLabel: "Punkte",
                lines: [`Marks pro Runde: ${fmt.dec(st.mpr)}`]
            };
        case "ATC": {
            const target = state.targets[ps.index];
            return {
                main: target === undefined ? "Fertig" : target === 25 ? "Bull" : target,
                mainLabel: "Nächstes Ziel",
                lines: [`Geschafft: ${ps.index}/${state.targets.length}`, `Trefferquote: ${fmt.pct(st.hitRate)}`]
            };
        }
        case "G121": {
            const done = ps.attemptsDone >= game.options.attempts;
            const lines = done
                ? ["Alle Versuche gemacht"]
                : [`Ziel ${ps.target}, Versuch ${ps.attemptsDone + 1}/${game.options.attempts}`,
                    `Aufnahme ${ps.turnInAttempt + 1}/3`];
            lines.push(`Geschafft: ${ps.successes}, bestes Ziel: ${ps.best || "-"}`);
            const path = done ? null : findCheckout(ps.remaining, dartsLeft, "double");
            return { main: done ? "Fertig" : ps.remaining, finish: path?.map(fieldLabel).join(" "), lines };
        }
        case "FREE":
            return {
                main: ps.total,
                mainLabel: "Punkte",
                lines: [`Letzte Aufnahme: ${last ? last.points : "-"}`, `Pro Aufnahme: ${fmt.dec(st.perTurn)}`]
            };
        default:
            return { main: "", lines: [] };
    }
}

function renderCricketBoard() {
    const container = document.getElementById("cricket-board");
    const markSymbol = ["", "/", "X", "Ⓧ"];
    const header = h("tr", {}, h("th", { text: "" }), game.players.map((p) => h("th", { text: p.name })));
    const rows = CRICKET_NUMBERS.map((n) => {
        const closedByAll = state.ps.every((ps) => ps.marks[n] >= 3);
        return h("tr", { class: closedByAll ? "closed" : "" },
            h("th", { text: n === 25 ? "Bull" : n }),
            state.ps.map((ps, p) => h("td", {
                class: !state.finished && state.current === p ? "current" : "",
                text: markSymbol[ps.marks[n]]
            })));
    });
    container.replaceChildren(h("table", { class: "cricket-table" }, h("thead", {}, header), h("tbody", {}, rows)));
}

/********* Spielende *********/

function renderResult() {
    const overlay = document.getElementById("result-overlay");
    if (!state.finished) {
        overlay.hidden = true;
        return;
    }

    const title = state.winner === null ? "Unentschieden!" : `${game.players[state.winner].name} gewinnt!`;
    overlay.replaceChildren(
        h("div", { class: "result-box" },
            h("h2", { text: title }),
            h("div", { class: "result-list" },
                game.players.map((player, p) => h("div", { class: "result-item" },
                    h("strong", { text: player.name }),
                    h("span", { text: gameStatsLine(game.mode, playerGameStats(state, p)) })))),
            h("div", { class: "result-buttons" },
                h("button", { text: "Rückgängig", onclick: undo }),
                h("button", { text: "Revanche", onclick: () => finishGame(true) }),
                h("button", { class: "primary-btn", text: "Speichern & beenden", onclick: () => finishGame(false) })))
    );
    overlay.hidden = false;
}

async function finishGame(rematch) {
    game.finishedAt = new Date().toISOString();
    game.winner = state.winner;
    game.legsWon = state.legsWon;
    showLoader(rematch ? "Revanche wird gestartet …" : "Spiel wird gespeichert …");
    await saveQueue;
    await saveFinishedGame(game);
    await Store.remove("currentGame");

    if (rematch) {
        // gleiche Spieler, Startreihenfolge eins weiter
        const players = [...game.players.slice(1), game.players[0]];
        await startNewGame(game.mode, game.options, players);
    } else {
        bar("../mainPages/index.html");
    }
}

async function abortGame() {
    if (!confirm("Spiel wirklich abbrechen? Es wird nicht gespeichert.")) return;
    clearTimeout(botTimer);
    await saveQueue;
    await Store.remove("currentGame");
    bar("../mainPages/index.html");
}

function backBtn() {
    // Spiel bleibt gespeichert und kann auf der Hauptseite fortgesetzt werden
    bar("../mainPages/index.html");
}

/********* Bots *********/

function scheduleBot() {
    if (botTimer || state.finished) return;
    const player = game.players[state.current];
    if (!player.bot) return;

    botTimer = setTimeout(() => {
        botTimer = null;
        const hit = simulateThrowPoint(botTarget(state), player.level ?? settings.botLevel);
        addDart(hit.field, hit);
    }, settings.botDelayMs);
}

/********* Hinweise *********/

let toastTimer = null;
function toast(text) {
    const el = document.getElementById("toast");
    if (!el) return;
    el.textContent = text;
    el.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("visible"), 1800);
}
