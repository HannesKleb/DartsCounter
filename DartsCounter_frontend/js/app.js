/********* Gemeinsame Funktionen für alle Seiten *********/

// Apple-Geräte nutzen die Systemschrift San Francisco, alle anderen Inter (siehe style.css)
if (/Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent)) {
    document.documentElement.classList.add("apple");
}

function bar(link) {
    navigate(link);
}

/********* Animationen *********/

function prefersReducedMotion() {
    return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

// Seite weich ausblenden, dann wechseln
function navigate(url) {
    if (prefersReducedMotion()) {
        window.location.href = url;
        return;
    }
    document.body.classList.add("leaving");
    setTimeout(() => { window.location.href = url; }, 180);
}

// beim Laden: Kopf, Inhalt und Leiste nacheinander einblenden
document.addEventListener("DOMContentLoaded", () => {
    const items = [
        document.querySelector(".header"),
        ...document.querySelectorAll(".content > *"),
        document.querySelector(".bar")
    ].filter(Boolean);
    items.forEach((el, i) => {
        el.classList.add("reveal");
        el.style.setProperty("--i", Math.min(i, 8));
    });
    document.body.classList.add("entering");
    setTimeout(() => document.body.classList.remove("entering"), 1600);
});

// "Zurück" im Browser zeigt die Seite aus dem Cache: Ausblenden rückgängig machen
window.addEventListener("pageshow", () => document.body.classList.remove("leaving"));

// Elemente einer Liste gestaffelt einblenden
function animateList(elements) {
    [...elements].forEach((el, i) => {
        el.classList.add("list-in");
        el.style.setProperty("--i", Math.min(i, 10));
    });
}

function replayAnimation(el, className) {
    el.classList.remove(className);
    void el.offsetWidth; // Neustart der CSS-Animation erzwingen
    el.classList.add(className);
}

// Tabs: blendet alle Panels der Gruppe aus und zeigt das gewählte
function showMode(id, group) {
    const groupEl = document.querySelector(`[data-group="${group}"]`);
    if (!groupEl) {
        console.warn(`showMode: Gruppe "${group}" nicht gefunden.`);
        return;
    }

    groupEl.querySelectorAll("[data-panel]").forEach((panel) => {
        const show = panel.id === id;
        const wasHidden = panel.style.display === "none";
        panel.style.display = show ? "block" : "none";
        if (show && wasHidden) replayAnimation(panel, "panel-in");
    });
    groupEl.querySelectorAll("button[data-target]").forEach((btn) => {
        btn.classList.toggle("active", btn.dataset.target === id);
    });

    groupEl.dispatchEvent(new CustomEvent("modechange", { detail: id }));
}

// Kleiner Helfer, um HTML-Elemente zu bauen (Text immer als textContent, kein HTML-Einschleusen)
function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
        if (value === undefined || value === null || value === false) continue;
        if (key === "class") el.className = value;
        else if (key === "text") el.textContent = value;
        else if (key.startsWith("on")) el.addEventListener(key.slice(2), value);
        else if (key === "style") el.style.cssText = value;
        else el.setAttribute(key, value === true ? "" : value);
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined || child === false) continue;
        el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return el;
}

function newId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

function formatDate(iso) {
    if (!iso) return "";
    return new Date(iso).toLocaleString("de-DE", {
        day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
    });
}

const BOT_LEVELS = { easy: "Leicht", medium: "Mittel", hard: "Schwer" };

function playerLabel(player) {
    return player.bot ? `${player.name} (Bot, ${BOT_LEVELS[player.level] ?? "Mittel"})` : player.name;
}

/********* Daten-Zugriff *********/

async function loadGames() {
    const games = await Store.get("games", []);
    return Array.isArray(games) ? games : [];
}

async function loadKnownPlayers() {
    const players = await Store.get("players", []);
    return Array.isArray(players) ? players : [];
}

// Namen aus Spielen und Spielerliste, ohne Bots, alphabetisch
function allPlayerNames(knownPlayers, games) {
    const names = new Set(knownPlayers.map((p) => p.name));
    for (const game of games) {
        game.players.filter((p) => !p.bot).forEach((p) => names.add(p.name));
    }
    return [...names].sort((a, b) => a.localeCompare(b, "de"));
}

async function saveFinishedGame(game) {
    const games = await loadGames();
    const index = games.findIndex((g) => g.id === game.id);
    if (index >= 0) games[index] = game;
    else games.push(game);
    await Store.set("games", games);
}

// Neues Spiel anlegen und zur Spielseite wechseln
async function startNewGame(mode, options, players) {
    const game = {
        id: newId(),
        mode,
        options,
        players,
        startedAt: new Date().toISOString(),
        darts: []
    };
    await Store.set("currentGame", game);
    navigate(`../gameModes/${MODES[mode].page}`);
}
