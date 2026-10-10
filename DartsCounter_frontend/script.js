
async function getGame() {
    const url = "../saves/games/game.json";
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        const result = await response.json();
        return result;
    }   catch (error) {
        console.error(error.message);
    }
    
}

/********* main-page *********/

function showMode(id, group) {
  const groupEl = document.querySelector(`[data-group="${group}"]`);
  if (!groupEl) {
    console.warn(`showMode: Gruppe "${group}" nicht gefunden.`);
    return;
  }

  // Alle Panels der Gruppe ausblenden
  const panels = groupEl.querySelectorAll('[data-panel]');
  panels.forEach((p) => (p.style.display = 'none'));

  // Gewähltes Panel anzeigen
  const selected = document.getElementById(id);
  if (selected) {
    selected.style.display = 'block';
  } else {
    console.warn(`showMode: Panel mit id "${id}" nicht gefunden.`);
  }

  // Buttons der Gruppe zurücksetzen
  const buttons = groupEl.querySelectorAll('button[data-target]');
  buttons.forEach((btn) => btn.classList.remove('active'));

  // Aktiven Button markieren
  const activeBtn = groupEl.querySelector(`button[data-target="${id}"]`);
  if (activeBtn) activeBtn.classList.add('active');
}

const MAX_PLAYERS = 8;

// Spielerliste wird im Browser gespeichert, bis das Backend Speichern kann
function loadPlayers() {
    try {
        return JSON.parse(localStorage.getItem("players")) ?? [];
    } catch {
        return [];
    }
}

function savePlayers(players) {
    try {
        localStorage.setItem("players", JSON.stringify(players));
    } catch (error) {
        console.error(error.message);
    }
}

function buildPlayerList() {
    const playerListEl = document.querySelector(".player-list");
    if (!playerListEl) return;

    playerListEl.innerHTML = ""; // reset

    loadPlayers().forEach((playerName, index) => {
        const playerDiv = document.createElement("div");
        playerDiv.classList.add("player-item");

        const indexEl = document.createElement("span");
        indexEl.classList.add("player-index");
        indexEl.textContent = `#${index + 1}`;

        // textContent statt innerHTML, damit Namen kein HTML einschleusen
        const nameEl = document.createElement("span");
        nameEl.classList.add("player-name");
        nameEl.textContent = playerName;

        const deleteBtn = document.createElement("button");
        deleteBtn.classList.add("delete-btn");
        deleteBtn.textContent = "Löschen";
        deleteBtn.addEventListener("click", () => removePlayer(index));

        playerDiv.append(indexEl, nameEl, deleteBtn);
        playerListEl.appendChild(playerDiv);
    });
}

function removePlayer(index) {
    const players = loadPlayers();
    players.splice(index, 1);
    savePlayers(players);
    buildPlayerList();
}

async function getRandomBotName(players) {
    try {
        const response = await fetch("../saves/botNames.json");
        if (!response.ok) throw new Error(`Response status: ${response.status}`);
        const { names } = await response.json();
        const free = names.filter((name) => !players.includes(name));
        if (free.length > 0) return free[Math.floor(Math.random() * free.length)];
    } catch (error) {
        console.error(error.message);
    }
    return `Bot#${Math.floor(Math.random() * 900) + 100}`;
}

// isPlayer = true: Name aus dem Eingabefeld, false: Bot
async function add(isPlayer) {
    const players = loadPlayers();
    if (players.length >= MAX_PLAYERS) {
        alert(`Maximal ${MAX_PLAYERS} Spieler.`);
        return;
    }

    let name;
    if (isPlayer) {
        const input = document.getElementById("player-input");
        name = input?.value.trim();
        if (!name) {
            input?.focus();
            return;
        }
        if (players.includes(name)) {
            alert("Name ist schon vergeben.");
            return;
        }
        input.value = "";
    } else {
        name = await getRandomBotName(players);
    }

    players.push(name);
    savePlayers(players);
    buildPlayerList();
}

function start() {
    const X01Buttons = document.getElementById('X01-btn').classList;
    if (X01Buttons.contains(`active`)) {
        window.location.href ="../gameModes/X01.html";
    }

    const cricketButtons = document.getElementById('cricket-btn').classList;
    if (cricketButtons.contains(`active`)) {
        window.location.href ="../gameModes/cricket.html";
    }

    const aroundTheClockButtons = document.getElementById('around-the-clock-btn').classList;
    if (aroundTheClockButtons.contains(`active`)) {
        window.location.href ="../gameModes/aroundTheClock.html";
    }

    const g121Buttons = document.getElementById('g121-btn').classList;
    if (g121Buttons.contains(`active`)) {
        window.location.href ="../gameModes/g121.html";
    }

    const freiesSpielButtons = document.getElementById('freies-spiel-btn').classList;
    if (freiesSpielButtons.contains(`active`)) {
        window.location.href ="../gameModes/freiesSpiel.html";
    }
}

function bar(link) {
    window.location.href = link;
}

/************************************ games *****************************************/
function backBtn() {
    window.location.href = "../mainPages/index.html";
}

function getGameMode() {
    const path = window.location.pathname.toLowerCase();

    if (path.includes("x01")) return "X01";
    if (path.includes("freies")) return "FREE";

    return "UNKNOWN";
}

const GAME_MODE = getGameMode();

function togglePressed(id) {
    // alle anderen deaktivieren
    document
        .querySelectorAll('.last-score-field')
        .forEach(btn => {
            btn.classList.remove('active');
            btn.setAttribute("aria-pressed", "false");
        });

    // aktuellen aktivieren
    const btn = document.getElementById(id);
    btn.classList.add("active");
    btn.setAttribute("aria-pressed", "true");

    return { pressed: true, btn, id };
}

function getActiveDartButton() {
    return document.querySelector('.last-score-field.active');
}

function clearActiveDartButton() {
    document
        .querySelectorAll('.last-score-field.active')
        .forEach(btn => {
            btn.classList.remove('active');
            btn.setAttribute("aria-pressed", "false");
        });
}


    function getCenterNormCoords(element, clientX, clientY) {
        const rect = element.getBoundingClientRect();
        const x = clientX - rect.left;  //Abstand zum äußersten Rand des Bildes
        const y = clientY - rect.top;

        const cx = rect.width / 2;  //Mittelpunkte
        const cy = rect.height / 2; 

        const dx = x - cx;  //Abstand zum Mittelpunkt
        const dy = y - cy;
        const r = Math.sqrt(dx*dx + dy*dy); //Abstand zum Mittelpunkt mit beiden Koordinaten
        const rNorm = r / (Math.min(rect.width, rect.height) / 2); // 

        // Winkel: 0° = oben (20), im Uhrzeigersinn positiv, wie in dart_tracking.py
        let angleDeg = Math.atan2(dx, -dy) * (180 / Math.PI);
        if (angleDeg < 0) angleDeg += 360;

        return { r, rNorm, angleDeg };
    }

    // Aktualisiert das Overlay mit lesbarem Output
    function updateOutput(polar) {
        const line = document.getElementById('coord-line');
        if (!line) return;
    }

    // Maus-Klick Handler
    function handleBoardClick(event) {
        const img = event.currentTarget;
        const polar = getCenterNormCoords(img, event.clientX, event.clientY);

        const activeBtn = getActiveDartButton();

        if (!activeBtn) {
            console.log("Kein Dart ausgewählt");
            return;
        }

        console.log("Aktiver Button:", activeBtn.id);
        console.log(
            `[Dartboard] rNorm=${polar.rNorm.toFixed(3)} | angle=${polar.angleDeg.toFixed(1)}°`
        );

        // hier später: Punkte berechnen & ins Feld schreiben

        clearActiveDartButton();
    }


    // Touch Handler
    function handleBoardTouch(event) {
        event.preventDefault();

        const touch = event.touches[0] || event.changedTouches[0];
        if (!touch) return;

        const img = event.currentTarget;
        const polar = getCenterNormCoords(img, touch.clientX, touch.clientY);

        const activeBtn = getActiveDartButton();

        if (!activeBtn) {
            console.log("Kein Dart ausgewählt");
            return;
        }

        console.log("Aktiver Button:", activeBtn.id);
        console.log(
            `[Dartboard] rNorm=${polar.rNorm.toFixed(3)} | angle=${polar.angleDeg.toFixed(1)}°`
        );

        clearActiveDartButton();
    }

 
    // Listener ans Dartboard hängen
    function initDartboardListeners() {
        const boardImg = document.querySelector('#score-board-id img');
        if (!boardImg) {
            // nur auf Spielseiten gibt es eine Scheibe
            if (GAME_MODE !== "UNKNOWN") {
                console.warn('Dartboard-Bild nicht gefunden (Selector: #score-board-id img)');
            }
            return;
        }
        boardImg.style.cursor = 'crosshair'; // besseres Feedback

        // Entfernen, falls bereits vorhanden (idempotent)
        boardImg.removeEventListener('click', handleBoardClick);
        boardImg.removeEventListener('touchstart', handleBoardTouch);

        boardImg.addEventListener('click', handleBoardClick);
        boardImg.addEventListener('touchstart', handleBoardTouch, { passive: false });
    }

    // Wenn DOM bereit ist, initialisieren
    document.addEventListener('DOMContentLoaded', () => {
        initDartboardListeners();
    });

function getLastTurnKey(playerData) {
    return Object.keys(playerData)
        .sort((a, b) => {
            const aNum = parseInt(a.replace("turn", ""), 10);
            const bNum = parseInt(b.replace("turn", ""), 10);
            return aNum - bNum;
        })
        .at(-1); // letzter Turn
}




const DART_KEYS = ["dart1", "dart2", "dart3"];
const X01_START_SCORE = 501;

function sumPoints(darts) {
    return darts.reduce(
        (sum, dart) => sum + (typeof dart?.points === "number" ? dart.points : 0),
        0
    );
}

function showDarts(lastTurn, playerIndex) {
    const dartIds = [`first-throw${playerIndex}`, `second-throw${playerIndex}`, `third-throw${playerIndex}`];

    DART_KEYS.forEach((dartKey, i) => {
        const el = document.getElementById(dartIds[i]);
        if (el) el.textContent = lastTurn?.[dartKey]?.points ?? "-";
    });
}

function updateScoreLeft(playerData, playerIndex) {
    const el = document.getElementById(`score-left${playerIndex}`);
    if (!el) return;

    const allDarts = Object.values(playerData).flatMap((turn) => Object.values(turn));
    const total = sumPoints(allDarts);

    if (GAME_MODE === "X01") el.textContent = X01_START_SCORE - total;
    if (GAME_MODE === "FREE") el.textContent = total;
}

function updateLastTurnScore(lastTurn, playerIndex) {
    const el = document.getElementById(`score-thrown${playerIndex}`);
    if (el) el.textContent = sumPoints(DART_KEYS.map((key) => lastTurn?.[key]));
}

function refreshPlayer(game, player, index) {
    const playerData = game?.[player];
    if (!playerData) return;

    const lastTurnKey = getLastTurnKey(playerData);
    const lastTurn = lastTurnKey ? playerData[lastTurnKey] : undefined;

    updateScoreLeft(playerData, index);
    updateLastTurnScore(lastTurn, index);
    showDarts(lastTurn, index);
}

// game.json nur einmal laden und nur auf Spielseiten anzeigen
async function refreshGame() {
    const game = await getGame();
    if (!game) return;

    Object.keys(game).forEach((player, i) => refreshPlayer(game, player, i + 1));
}

document.addEventListener("DOMContentLoaded", () => {
    buildPlayerList();
    if (GAME_MODE !== "UNKNOWN") refreshGame();
});

/********* X01-page ********/

/********* cricket-page *********/

/********* aroundTheClock-page *********/

/********* 121-page *********/

/********* freiesSpiel-page *********/
