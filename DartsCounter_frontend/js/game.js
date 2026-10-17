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

document.addEventListener("DOMContentLoaded", initGamePage);

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

function addDart(field) {
    if (!state || state.finished) return;
    newDart = { player: state.current, index: state.turn.length };
    game.darts.push(field);
    afterChange();
}

function addHumanDart(field) {
    if (!state || state.finished || game.players[state.current].bot) return;
    addDart(field);
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
            game.darts.pop();
        }
    }
    game.darts.pop();
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
    document.getElementById("single-btn").addEventListener("click", () => toggleModifier(null));
    document.getElementById("double-btn").addEventListener("click", () => toggleModifier("D"));
    document.getElementById("triple-btn").addEventListener("click", () => toggleModifier("T"));
    document.getElementById("undo-btn").addEventListener("click", undo);
}

function toggleModifier(type) {
    modifier = modifier === type ? null : type;
    updateModifierButtons();
}

// Single/Double/Triple: genau einer ist aktiv; die Zahlentasten zeigen den Wert mit (siehe style.css)
function updateModifierButtons() {
    for (const [id, type] of [["single-btn", null], ["double-btn", "D"], ["triple-btn", "T"]]) {
        const btn = document.getElementById(id);
        btn?.classList.toggle("active", modifier === type);
        btn?.setAttribute("aria-pressed", String(modifier === type));
    }
    const keys = document.getElementById("score-button-id");
    if (keys) keys.dataset.mod = modifier ?? "";
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
    const svg = createBoardSvg();
    container.replaceChildren(svg);

    svg.addEventListener("click", (event) => {
        // Feld direkt vom angetippten Ring-Stück, sonst über die Position berechnen
        const target = event.target.closest("[data-field]");
        const field = target ? target.dataset.field : fieldFromScreen(svg, event.clientX, event.clientY);
        if (target?.classList.contains("board-field")) replayAnimation(target, "hit");
        addHumanDart(field);
    });
}

function fieldFromScreen(element, clientX, clientY) {
    const rect = element.getBoundingClientRect();
    const scale = BOARD_RADIUS.board / (Math.min(rect.width, rect.height) / 2); // mm pro Pixel
    const x = (clientX - rect.left - rect.width / 2) * scale;
    const y = (clientY - rect.top - rect.height / 2) * scale;
    return fieldFromXY(x, y);
}

/********* Anzeige *********/

function render() {
    state = replayGame(game);
    renderInfo();
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
                    class: `last-score-field${shownDarts[i] === undefined ? " empty" : ""}${i === animateDartIndex ? " dart-in" : ""}`,
                    text: shownDarts[i] !== undefined ? fieldLabel(shownDarts[i]) : ""
                })))),
        h("div", { class: "small-scoring-stats" },
            view.finish ? h("div", { class: "finish" },
                h("span", { class: "finish-label", text: "Finish" }),
                view.finish.map((field) => h("span", {
                    class: `finish-chip${field[0] === "D" ? " double" : ""}`,
                    text: fieldLabel(field)
                }))) : null,
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
            return { main: ps.score, finish: path, lines };
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
            return { main: done ? "Fertig" : ps.remaining, finish: path, lines };
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
        addDart(simulateThrow(botTarget(state), player.level ?? settings.botLevel));
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
