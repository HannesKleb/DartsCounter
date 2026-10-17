/********* Gemeinsame Funktionen für alle Seiten *********/

function bar(link) {
    navigate(link);
}

/********* Animationen *********/

function prefersReducedMotion() {
    return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

// Seitenwechsel sofort; die Überblendung macht der Browser selbst (View Transitions, siehe style.css)
function navigate(url) {
    window.location.href = url;
}

// Elemente einer Liste einblenden (alle gleichzeitig, keine Wartezeit durch Staffelung)
function animateList(elements) {
    [...elements].forEach((el) => el.classList.add("list-in"));
}

/********* Ladebildschirm *********/

// Jede Seite zeigt beim Öffnen den Ladebildschirm, bis sie pageReady() aufruft.
// Er erscheint erst nach kurzer Zeit (schnelle Seiten flackern nicht) und bleibt dann kurz sichtbar.
const LOADER_DELAY_MS = 200;
const LOADER_MIN_MS = 300;
const LOADER_SLOW_MS = 2000;
const LOADER_MAX_MS = 15000; // Sicherheitsnetz, falls eine Seite pageReady() nie erreicht

const loader = { el: null, timers: [], shownAt: 0 };

function showLoader(text = "Lädt …") {
    clearLoaderTimers();
    if (!loader.el) {
        loader.el = h("div", { class: "page-loader", role: "status", "aria-live": "polite" },
            h("div", { class: "page-loader-box" },
                loaderBoardSvg(),
                h("span", { class: "page-loader-text" })));
        document.body.append(loader.el);
    }
    loader.el.querySelector(".page-loader-text").textContent = text;
    loader.timers.push(setTimeout(() => {
        loader.el.classList.add("visible");
        loader.shownAt = Date.now();
    }, LOADER_DELAY_MS));
    loader.timers.push(setTimeout(() => {
        loader.el.querySelector(".page-loader-text").textContent = "Verbinde mit dem Pi …";
    }, LOADER_SLOW_MS));
    loader.timers.push(setTimeout(hideLoader, LOADER_MAX_MS));
}

function hideLoader() {
    clearLoaderTimers();
    if (!loader.el?.classList.contains("visible")) return;
    const wait = Math.max(0, LOADER_MIN_MS - (Date.now() - loader.shownAt));
    loader.timers.push(setTimeout(() => loader.el.classList.remove("visible"), wait));
}

function clearLoaderTimers() {
    loader.timers.forEach(clearTimeout);
    loader.timers = [];
}

// Seite ist fertig aufgebaut
function pageReady() {
    hideLoader();
}

// Aufbau einer Seite: Ladebildschirm bis zum Ende, auch bei Fehlern
async function runPage(init) {
    try {
        await init();
    } finally {
        pageReady();
    }
}

function loaderBoardSvg() {
    const svg = svgElement("svg", { viewBox: "0 0 56 56", class: "page-loader-board", "aria-hidden": "true" });
    svg.append(
        svgElement("circle", { cx: 28, cy: 28, r: 24, class: "ring" }),
        svgElement("circle", { cx: 28, cy: 28, r: 24, class: "arc" }),
        svgElement("circle", { cx: 28, cy: 28, r: 5, class: "bull" }));
    return svg;
}

function svgElement(tag, attrs) {
    const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value);
    return el;
}

// Button zeigt einen Kreisel, solange die Aktion läuft
async function withButtonLoading(button, action) {
    if (!button) return action();
    button.classList.add("loading");
    button.setAttribute("aria-busy", "true");
    try {
        return await action();
    } finally {
        button.classList.remove("loading");
        button.removeAttribute("aria-busy");
    }
}

showLoader();
// zurück-Taste (bfcache): Seite ist schon fertig
window.addEventListener("pageshow", (event) => { if (event.persisted) hideLoader(); });

// Platzhalter, solange Daten geladen werden
function skeletonRows(count = 3) {
    return [...Array(count)].map(() => h("div", { class: "skeleton" }));
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
        panel.style.display = show ? "" : "none"; // "" = Darstellung aus style.css (z. B. Raster)
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

// Strich-Icon (24er Raster) für Knöpfe ohne Text; Pfade wie in den HTML-Seiten
function icon(pathData) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", pathData);
    svg.append(path);
    return svg;
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

// Kreis mit den ersten zwei Buchstaben vor dem Namen (Bots gestrichelt mit "Bot")
function avatar(player) {
    return h("span", {
        class: `avatar${player.bot ? " bot" : ""}`,
        "aria-hidden": "true",
        text: player.bot ? "Bot" : player.name.slice(0, 2).toUpperCase()
    });
}

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
    showLoader("Spiel wird gestartet …");
    await Store.set("currentGame", game);
    navigate(`../gameModes/${MODES[mode].page}`);
}
