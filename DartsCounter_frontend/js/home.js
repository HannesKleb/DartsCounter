/********* Hauptseite: Modus, Optionen, Spieler, Start *********/

const MAX_PLAYERS = 8;
let lobby = [];          // Spieler für das nächste Spiel
let knownPlayers = [];
let newPlayerIndex = null; // nur der neu hinzugefügte Spieler wird animiert

document.addEventListener("DOMContentLoaded", initHome);

async function initHome() {
    lobby = readLobby();
    restoreModeChoice();

    document.getElementById("add-player").addEventListener("click", addHuman);
    document.getElementById("player-input").addEventListener("keydown", (event) => {
        if (event.key === "Enter") addHuman();
    });
    document.getElementById("add-bot").addEventListener("click", addBot);
    document.getElementById("start-btn").addEventListener("click", start);
    document.querySelectorAll(".mode-options").forEach((form) => {
        form.addEventListener("submit", (event) => event.preventDefault());
    });

    const settings = await loadSettings();
    document.getElementById("bot-level").value = settings.botLevel;

    renderLobby();
    knownPlayers = await loadKnownPlayers();
    renderKnownPlayers();
    renderRunningGame();
}

/********* Laufendes Spiel *********/

async function renderRunningGame() {
    const game = await Store.get("currentGame", null);
    const box = document.getElementById("running-game");
    if (!game || !MODES[game.mode]) {
        box.hidden = true;
        return;
    }
    box.replaceChildren(
        h("div", {},
            h("strong", { text: `Laufendes Spiel: ${MODES[game.mode].label}` }),
            h("div", { text: game.players.map((p) => p.name).join(", ") })),
        h("div", { class: "running-game-buttons" },
            h("button", {
                class: "primary-btn", text: "Fortsetzen",
                onclick: () => bar(`../gameModes/${MODES[game.mode].page}`)
            }),
            h("button", {
                text: "Verwerfen",
                onclick: async () => {
                    if (!confirm("Laufendes Spiel verwerfen?")) return;
                    await Store.remove("currentGame");
                    renderRunningGame();
                }
            }))
    );
    box.hidden = false;
}

/********* Modus und Optionen *********/

function selectedMode() {
    const active = document.querySelector(".mode-selection button.active");
    return active ? active.dataset.target.replace("mode-", "") : null;
}

function readOptions(mode) {
    const form = document.getElementById(`mode-${mode}`);
    const options = { ...MODES[mode].defaults };
    for (const el of form.elements) {
        if (!el.name) continue;
        if (el.type === "checkbox") options[el.name] = el.checked;
        else if (el.type === "number") {
            const value = parseInt(el.value, 10);
            const min = parseInt(el.min, 10);
            const max = parseInt(el.max, 10);
            options[el.name] = Number.isNaN(value) ? options[el.name] : Math.min(max, Math.max(min, value));
        } else if (typeof options[el.name] === "number") options[el.name] = parseInt(el.value, 10);
        else options[el.name] = el.value;
    }
    return options;
}

function writeOptions(mode, options) {
    const form = document.getElementById(`mode-${mode}`);
    if (!form) return;
    for (const el of form.elements) {
        if (!el.name || options[el.name] === undefined) continue;
        if (el.type === "checkbox") el.checked = Boolean(options[el.name]);
        else el.value = options[el.name];
    }
}

// letzte Auswahl merken (nur in diesem Browser)
function restoreModeChoice() {
    try {
        const saved = JSON.parse(localStorage.getItem("lastModeChoice") ?? "null");
        if (!saved) {
            showMode("mode-X01", "mode"); // erster Besuch: X01 vorauswählen, damit nie ein leeres Feld zu sehen ist
            return;
        }
        for (const [mode, options] of Object.entries(saved.options ?? {})) writeOptions(mode, options);
        if (saved.mode && MODES[saved.mode]) showMode(`mode-${saved.mode}`, "mode");
    } catch {
        // egal, dann Standardwerte
    }
}

function rememberModeChoice(mode, options) {
    try {
        const saved = JSON.parse(localStorage.getItem("lastModeChoice") ?? "{}") ?? {};
        saved.mode = mode;
        saved.options = { ...(saved.options ?? {}), [mode]: options };
        localStorage.setItem("lastModeChoice", JSON.stringify(saved));
    } catch {
        // egal
    }
}

/********* Spieler *********/

function readLobby() {
    try {
        const saved = JSON.parse(localStorage.getItem("lobby") ?? "[]");
        return Array.isArray(saved) ? saved.filter((p) => p && typeof p.name === "string") : [];
    } catch {
        return [];
    }
}

function saveLobby() {
    try {
        localStorage.setItem("lobby", JSON.stringify(lobby));
    } catch {
        // egal
    }
}

function renderKnownPlayers() {
    const list = document.getElementById("known-players");
    // bekannte Spieler und Spielernamen der Konten als Vorschläge
    const names = new Set(knownPlayers.map((p) => p.name));
    const render = () => list.replaceChildren(...[...names].map((name) => h("option", { value: name })));
    render();
    if (typeof Auth !== "undefined") {
        Auth.accountNames()
            .then((accountNames) => {
                accountNames.forEach((name) => names.add(name));
                render();
            })
            .catch(() => {});
    }
}

function renderLobby() {
    const listEl = document.querySelector(".player-list");
    if (lobby.length === 0) {
        listEl.replaceChildren(h("div", { class: "hint", text: "Noch keine Spieler ausgewählt." }));
        return;
    }
    listEl.replaceChildren(...lobby.map((player, index) =>
        h("div", { class: `player-item${index === newPlayerIndex ? " list-in" : ""}` },
            h("span", { class: "player-index", text: index + 1 }),
            avatar(player),
            h("span", { class: "player-name" },
                player.name,
                player.bot ? h("span", { class: "hint", text: ` · ${BOT_LEVELS[player.level] ?? "Mittel"}` }) : null),
            h("span", { class: "player-actions" },
                h("button", { class: "icon-btn", "aria-label": `${player.name} nach oben`, title: "Nach oben schieben", disabled: index === 0, onclick: () => movePlayer(index) },
                    icon("M12 19V5M5 12l7-7 7 7")),
                h("button", { class: "icon-btn delete-btn", "aria-label": `${player.name} entfernen`, title: "Entfernen", onclick: () => removePlayer(index) },
                    icon("M6 6l12 12M18 6L6 18"))))));
    newPlayerIndex = null;
}

function movePlayer(index) {
    [lobby[index - 1], lobby[index]] = [lobby[index], lobby[index - 1]];
    saveLobby();
    renderLobby();
}

function removePlayer(index) {
    lobby.splice(index, 1);
    saveLobby();
    renderLobby();
}

function canAddPlayer() {
    if (lobby.length >= MAX_PLAYERS) {
        alert(`Maximal ${MAX_PLAYERS} Spieler.`);
        return false;
    }
    return true;
}

function addHuman() {
    const input = document.getElementById("player-input");
    const name = input.value.trim();
    if (!name) {
        input.focus();
        return;
    }
    if (!canAddPlayer()) return;
    if (lobby.some((p) => p.name.toLowerCase() === name.toLowerCase())) {
        alert("Dieser Name ist schon dabei.");
        return;
    }
    lobby.push({ name, bot: false });
    newPlayerIndex = lobby.length - 1;
    input.value = "";
    saveLobby();
    renderLobby();
}

async function addBot() {
    if (!canAddPlayer()) return;
    const level = document.getElementById("bot-level").value;
    const name = await getRandomBotName(lobby.map((p) => p.name));
    lobby.push({ name, bot: true, level });
    newPlayerIndex = lobby.length - 1;
    saveLobby();
    renderLobby();
}

async function getRandomBotName(taken) {
    try {
        const response = await fetch("../saves/botNames.json");
        if (!response.ok) throw new Error(`Response status: ${response.status}`);
        const { names } = await response.json();
        const free = names.filter((name) => !taken.includes(name));
        if (free.length > 0) return free[Math.floor(Math.random() * free.length)];
    } catch (error) {
        console.error(error.message);
    }
    let name;
    do {
        name = `Bot#${Math.floor(Math.random() * 900) + 100}`;
    } while (taken.includes(name));
    return name;
}

/********* Start *********/

async function start() {
    const button = document.getElementById("start-btn");
    if (button.disabled) return;
    button.disabled = true;
    button.textContent = "Startet …";
    try {
        await startGame();
    } finally {
        button.disabled = false;
        button.textContent = "Start";
    }
}

async function startGame() {
    const mode = selectedMode();
    if (!mode) {
        alert("Bitte zuerst einen Spielmodus wählen.");
        return;
    }
    if (lobby.length === 0) {
        alert("Bitte mindestens einen Spieler hinzufügen.");
        return;
    }

    const running = await Store.get("currentGame", null);
    if (running && !confirm("Es läuft noch ein Spiel. Trotzdem ein neues starten? Das alte wird verworfen.")) return;

    const options = readOptions(mode);
    rememberModeChoice(mode, options);

    // neue Namen in die Spielerliste übernehmen
    const known = new Set(knownPlayers.map((p) => p.name));
    const newPlayers = lobby.filter((p) => !p.bot && !known.has(p.name));
    if (newPlayers.length > 0) {
        await Store.set("players", [...knownPlayers, ...newPlayers.map((p) => ({ name: p.name }))]);
    }

    await startNewGame(mode, options, lobby.map((p) => ({ ...p })));
}
