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


function addplayer() {
    const playerList = [
        `Player 1`,
        `Player 2`,
    ]

    for (let i = 0; i < playerList.length; i++) {
        console.log(playerList[i]);
    }

    return playerList
}
addplayer()

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

    // Konsole + Overlay
    console.log(`[Dartboard] Klick @ rNorm=${polar.rNorm.toFixed(3)} | angle=${polar.angleDeg.toFixed(1)}°`);
    updateOutput(polar);
}

// Touch Handler
function handleBoardTouch(event) {
    event.preventDefault();
    const touch = event.touches[0] || event.changedTouches[0];
    if (!touch) return;

    const img = event.currentTarget;
    const polar = getCenterNormCoords(img, touch.clientX, touch.clientY);

    console.log(`[Dartboard] Touch @ rNorm=${polar.rNorm.toFixed(3)} | angle=${polar.angleDeg.toFixed(1)}°`);
    updateOutput(polar);
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










/********* X01-page *********/
function scoreLeft() {
    document.getElementById("score-left1").textContent = "501";
}
function backBtn() {
    window.location.href = "../mainPages/index.html";
}


/********* cricket-page *********/

/********* aroundTheClock-page *********/

/********* 121-page *********/

/********* freiesSpiel-page *********/