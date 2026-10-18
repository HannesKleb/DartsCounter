/********* Startseite: drehbare 3D-Scheibe und eigene Kennzahlen *********/

const EDGE_STRIPS = 72;      // Rand der Scheibe aus schmalen Streifen
const LAYER_GAP = 0.18;      // Abstand der Schichten bei "Aufbau zeigen" (Anteil der Scheibengröße)

const view = { rx: 8, ry: 18, exploded: false };
let drag = null;
let coastFrame = null;

document.addEventListener("DOMContentLoaded", () => runPage(initStart));

async function initStart() {
    buildEdge();
    initBoardControls();
    applyView(false);
    await fillStats();
}

/********* Scheibe *********/

// Licht kommt von oben rechts: Streifen auf der Seite heller
function buildEdge() {
    const board = document.getElementById("board-3d");
    const strips = [];
    for (let i = 0; i < EDGE_STRIPS; i++) {
        const angle = (i * 360) / EDGE_STRIPS;
        const lit = Math.round(30 + 22 * Math.cos(((angle - 20) * Math.PI) / 180));
        strips.push(h("div", {
            class: "board-edge",
            style: `transform: translateZ(calc(var(--depth) / -2)) rotateZ(${angle}deg) translateY(calc(var(--size) / -2 + 1px)) rotateX(90deg);` +
                `background: linear-gradient(rgb(${lit + 8},${lit + 8},${lit + 9}), rgb(${lit},${lit},${lit + 1}) 50%, rgb(${lit - 10},${lit - 10},${lit - 9}));`
        }));
    }
    board.prepend(...strips);
}

function applyView(animate) {
    const board = document.getElementById("board-3d");
    board.classList.toggle("no-transition", !animate);
    board.style.transform = `rotateX(${view.rx}deg) rotateY(${view.ry}deg)`;

    // Glanzlicht wandert mit, als hinge die Lampe fest über der Scheibe
    const ry = ((view.ry % 360) + 360) % 360;
    const gx = 50 - Math.sin((ry * Math.PI) / 180) * 45;
    const gy = 10 + view.rx * 0.9;
    document.getElementById("board-sheen").style.background =
        `radial-gradient(circle at ${gx}% ${gy}%, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0) 45%)`;

    document.querySelectorAll(".board-layer").forEach((layer, i) => {
        layer.style.transform = view.exploded
            ? `translateZ(calc(var(--size) * ${LAYER_GAP * i}))`
            : `translateZ(${i * 0.5}px)`;
        layer.classList.toggle("labelled", view.exploded && layer.dataset.label !== "");
    });
    document.getElementById("board-stage").classList.toggle("still", !!drag || coastFrame !== null || view.exploded);
}

function initBoardControls() {
    const stage = document.getElementById("board-stage");
    // Beschriftungen gestaffelt, damit sie sich bei "Aufbau zeigen" nicht überdecken
    document.querySelectorAll(".board-layer").forEach((layer, i) => {
        if (layer.dataset.label) layer.append(h("span", { class: "board-label", text: layer.dataset.label, style: `top: ${42 - i * 12}%` }));
    });

    stage.addEventListener("pointerdown", (event) => {
        stopCoast();
        drag = { x: event.clientX, y: event.clientY, vx: 0, vy: 0 };
        stage.setPointerCapture(event.pointerId);
        stage.classList.add("dragging");
        applyView(false);
    });
    stage.addEventListener("pointermove", (event) => {
        if (!drag) return;
        const dx = (event.clientX - drag.x) * 0.35;
        const dy = -(event.clientY - drag.y) * 0.35;
        drag = { x: event.clientX, y: event.clientY, vx: dx, vy: dy };
        view.ry += dx;
        view.rx = clamp(view.rx + dy, -60, 60);
        applyView(false);
    });
    const release = () => {
        if (!drag) return;
        const { vx, vy } = drag;
        drag = null;
        stage.classList.remove("dragging");
        if (!prefersReducedMotion() && (Math.abs(vx) > 0.4 || Math.abs(vy) > 0.4)) coast(vx, vy);
        else applyView(true);
    };
    stage.addEventListener("pointerup", release);
    stage.addEventListener("pointercancel", release);

    document.getElementById("explode-btn").addEventListener("click", (event) => {
        view.exploded = !view.exploded;
        const base = Math.round(view.ry / 360) * 360;
        view.rx = view.exploded ? 22 : 8;
        view.ry = base + (view.exploded ? 48 : 18);
        event.currentTarget.textContent = view.exploded ? "Zusammensetzen" : "Aufbau zeigen";
        applyView(true);
    });
    document.getElementById("spin-btn").addEventListener("click", () => {
        stopCoast();
        view.ry += 360;
        applyView(true);
    });
}

// Schwung nach dem Loslassen: dreht weiter und bremst langsam ab
function coast(vx, vy) {
    const step = () => {
        vx *= 0.95;
        vy *= 0.95;
        if (Math.abs(vx) < 0.05 && Math.abs(vy) < 0.05) {
            coastFrame = null;
            applyView(true);
            return;
        }
        view.ry += vx;
        view.rx = clamp(view.rx + vy, -60, 60);
        coastFrame = requestAnimationFrame(step);
        applyView(false);
    };
    coastFrame = requestAnimationFrame(step);
}

function stopCoast() {
    if (coastFrame !== null) cancelAnimationFrame(coastFrame);
    coastFrame = null;
}

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

/********* Kennzahlen *********/

async function fillStats() {
    const [games, user, current] = await Promise.all([
        loadGames(),
        Auth.current().catch(() => null),
        Store.get("currentGame", null)
    ]);
    const name = user?.name ?? mostFrequentPlayer(games);

    const hour = new Date().getHours();
    const hello = hour < 11 ? "Guten Morgen" : hour < 18 ? "Hallo" : "Guten Abend";
    document.getElementById("greeting").textContent = name ? `${hello}, ${name}.` : `${hello}.`;

    const finished = games
        .filter((g) => g.finishedAt && (!name || g.players.some((p) => p.name === name)))
        .sort((a, b) => b.finishedAt.localeCompare(a.finishedAt));
    const last = finished[0];
    if (last) document.getElementById("last-game").textContent = describeLastGame(last, name);

    if (name) {
        const x01 = aggregatePlayerStats(games, "X01", name);
        if (x01.games > 0) {
            document.getElementById("stat-average").textContent = x01.average.toLocaleString("de-DE", { maximumFractionDigits: 1, minimumFractionDigits: 1 });
            document.getElementById("stat-checkout").textContent = x01.bestCheckout || "–";
        }
        const field = favouriteField(games, name);
        if (field) document.getElementById("stat-field").textContent = fieldLabel(field);
    }

    // Zweiter Knopf: laufendes Spiel fortsetzen, sonst Revanche des letzten Spiels
    const second = document.getElementById("second-btn");
    if (current && MODES[current.mode]) {
        second.textContent = "Fortsetzen";
        second.onclick = () => bar(`../gameModes/${MODES[current.mode].page}`);
        second.hidden = false;
    } else if (last) {
        second.textContent = "Revanche";
        second.onclick = () => startNewGame(last.mode, last.options, [...last.players.slice(1), last.players[0]]);
        second.hidden = false;
    }
}

function mostFrequentPlayer(games) {
    const counts = new Map();
    for (const game of games) {
        for (const player of game.players) {
            if (!player.bot) counts.set(player.name, (counts.get(player.name) ?? 0) + 1);
        }
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
}

function describeLastGame(game, name) {
    const s = replayGame(game);
    const p = game.players.findIndex((player) => player.name === name);
    const others = game.players.filter((_, i) => i !== p).map((player) => player.name);
    if (others.length === 0) return `Letztes Spiel: ${MODES[game.mode].label}, Training.`;
    const against = `gegen ${others.join(", ")}`;
    if (p < 0 || s.winner === null || s.winner === undefined) return `Letztes Spiel: ${MODES[game.mode].label} ${against}.`;
    const legs = s.legsToWin > 1 ? ` ${s.legsWon[p]}–${Math.max(...s.legsWon.filter((_, i) => i !== p))}` : "";
    return `Letztes Spiel: ${s.winner === p ? "gewonnen" : "verloren"}${legs} ${against}.`;
}

// Feld, das der Spieler am häufigsten trifft (ohne Fehlwürfe)
function favouriteField(games, name) {
    const counts = new Map();
    for (const game of games) {
        const p = game.players.findIndex((player) => player.name === name);
        if (p < 0) continue;
        for (const turn of replayGame(game).turns) {
            if (turn.player !== p) continue;
            for (const field of turn.darts) {
                if (field !== "M") counts.set(field, (counts.get(field) ?? 0) + 1);
            }
        }
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
}
