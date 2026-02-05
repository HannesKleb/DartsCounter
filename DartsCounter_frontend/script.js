
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

async function getPlayersFromGame() {
    const game = await getGame();
    if (!game) return [];

    return Object.keys(game); 
}
async function buildPlayerList() {
    const playerListEl = document.querySelector(".player-list");
    if (!playerListEl) return;

    playerListEl.innerHTML = ""; // reset

    const players = await getPlayersFromGame();

    players.forEach((playerName, index) => {
        const playerDiv = document.createElement("div");
        playerDiv.classList.add("player-item");

        playerDiv.innerHTML = `
            <span class="player-index">#${index + 1}</span>
            <span class="player-name">${playerName}</span>
            <button class="delete-btn">Löschen</button>
        `;

        playerListEl.appendChild(playerDiv);
    });
}


function addplayer() {
    

    for (let i = 0; i < playerList.length; i++) {
        console.log(playerList[i]);
    }

    return playerList
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

        let angleDeg = ((Math.atan2(-dy, dx) * (180 / Math.PI))-90); //Winkel
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
    }

 
    // Listener ans Dartboard hängen
    function initDartboardListeners() {
        const boardImg = document.querySelector('#score-board-id img');
        if (!boardImg) {
            console.warn('Dartboard-Bild nicht gefunden (Selector: #score-board-id img)');
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




async function getDart(player, turn, dart) {
  const game = await getGame();
  const dartData = game?.[player]?.[turn]?.[dart].points;

  return dartData;
}

async function showDart(player, dartKey, playerIndex) {
    const game = await getGame();
    const playerData = game?.[player];
    if (!playerData) return;

    const lastTurnKey = getLastTurnKey(playerData);
    if (!lastTurnKey) return;

    const dartIdMap = {
        dart1: `first-throw${playerIndex}`,
        dart2: `second-throw${playerIndex}`,
        dart3: `third-throw${playerIndex}`
    };

    const dartId = dartIdMap[dartKey];
    const points = playerData[lastTurnKey]?.[dartKey]?.points;

    const el = document.getElementById(dartId);
    if (el) el.textContent = points ?? "-";
}


async function showDarts(player, playerIndex) {
    const game = await getGame();
    const playerData = game?.[player];
    if (!playerData) return;

    const lastTurnKey = getLastTurnKey(playerData);
    if (!lastTurnKey) return;

    for (const dartKey of ["dart1", "dart2", "dart3"]) {
        const dartIdMap = {
            dart1: `first-throw${playerIndex}`,
            dart2: `second-throw${playerIndex}`,
            dart3: `third-throw${playerIndex}`
        };

        const dartId = dartIdMap[dartKey];
        const points = playerData[lastTurnKey]?.[dartKey]?.points;
        const el = document.getElementById(dartId);
        if (el) el.textContent = points ?? "-";
    }
}

showDarts("Player1", 1);
showDarts("Player2", 2);

async function getPlayerLastTurnScore(player) {
    const game = await getGame();
    const playerData = game?.[player];
    if (!playerData) return 0;

    const lastTurnKey = getLastTurnKey(playerData);
    if (!lastTurnKey) return 0;

    const lastTurn = playerData[lastTurnKey];

    let sum = 0;
    for (const dartKey of ["dart1", "dart2", "dart3"]) {
        const points = lastTurn?.[dartKey]?.points;
        if (typeof points === "number") {
            sum += points;
        }
    }

    return sum;
}

async function getPlayerTotalPoints(player) {
    const game = await getGame();
    const playerData = game?.[player];
    if (!playerData) return 0;

    let sum = 0;
    for (const turn of Object.values(playerData)) {
        for (const dart of Object.values(turn)) {
            if (typeof dart.points === "number") {
                sum += dart.points;
            }
        }
    }
    return sum;
}
async function updateScoreLeft(player, playerIndex) {
    const total = await getPlayerTotalPoints(player);
    const el = document.getElementById(`score-left${playerIndex}`);
    if (!el) return;

    if (GAME_MODE === "X01") {
        const START_SCORE = 501;
        el.textContent = START_SCORE - total;
    }

    if (GAME_MODE === "FREE") {
        el.textContent = total;
    }
}
async function updateLastTurnScore(player, playerIndex) {
    const game = await getGame();
    const playerData = game?.[player];
    if (!playerData) return;

    const lastTurnKey = getLastTurnKey(playerData);
    if (!lastTurnKey) return;

    let sum = 0;
    for (const dartKey of ["dart1", "dart2", "dart3"]) {
        const points = playerData[lastTurnKey]?.[dartKey]?.points;
        if (typeof points === "number") sum += points;
    }

    const el = document.getElementById(`score-thrown${playerIndex}`);
    if (el) el.textContent = sum;
}
refreshPlayer("Player1", 1);
refreshPlayer("Player2", 2);

function refreshPlayer(player, index) {
    updateScoreLeft(player, index);
    updateLastTurnScore(player, index);
    showDarts(player, index);
}

console.log(GAME_MODE);

document.addEventListener("DOMContentLoaded", () => {
    buildPlayerList();
});

/********* X01-page ********/

/********* cricket-page *********/

/********* aroundTheClock-page *********/

/********* 121-page *********/

/********* freiesSpiel-page *********/
