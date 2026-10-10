/********* Gemeinsame Funktionen für alle Seiten *********/

function bar(link) {
    window.location.href = link;
}

// Tabs: blendet alle Panels der Gruppe aus und zeigt das gewählte
function showMode(id, group) {
    const groupEl = document.querySelector(`[data-group="${group}"]`);
    if (!groupEl) {
        console.warn(`showMode: Gruppe "${group}" nicht gefunden.`);
        return;
    }

    groupEl.querySelectorAll("[data-panel]").forEach((panel) => {
        panel.style.display = panel.id === id ? "block" : "none";
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
    window.location.href = `../gameModes/${MODES[mode].page}`;
}
