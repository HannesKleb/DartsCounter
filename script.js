/********* main-page *********/
function showMode(id) {
    const allModes = document.querySelectorAll('.selection > div');
    allModes.forEach(mode => {
        mode.style.display = 'none';
    });
    const selectedMode = document.getElementById(id);
    if (selectedMode) {
        selectedMode.style.display = 'block';
    }

    const allButtons = document.querySelectorAll('.selection-btn button');
    allButtons.forEach(btn => btn.classList.remove('active'));

    const activeBtn = Array.from(allButtons).find(btn => btn.getAttribute('onclick').includes(id));
    if (activeBtn) {
        activeBtn.classList.add('active');
    }
    

}
function start() {
    window.location.href = "X01.html";
}
function bar(link) {
    window.location.href = link;
}

async function add(addPlayer){

    if (addPlayer == true){
        alert("addPlayer")
    }
    else{
        if ((await fetchJSONData('./test.json')).playerCount <= 8){
            let nextBot = (await fetchJSONData("saves/games/setUpGame.json")).playerCount + 1
            console.log(nextBot)

            const aktuellerStand = await fetchJSONData("saves/games/setUpGame.json");
            aktuellerStand.playerCount += 1; // Wert erhöhen

            await saveJSONData("saves/games/setUpGame.json", aktuellerStand);
        }
    }
}

async function randomBotName(path) {
    let listLen = (await fetchJSONData(path)).names.length
    let randomIndex = Math.floor(Math.random() * listLen)
    return (await fetchJSONData(path)).names[randomIndex]
}

async function fetchJSONData(path) {
    const response = await fetch(path);
    const contents = await response.json();
    return contents; 
}

async function saveJSONData(path, data) {
    const response = await fetch(path, {
        method: 'PUT', // Oder 'PUT', um die Datei zu aktualisieren
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data) // Das Objekt wird wieder zu Text
    });

    if (!response.ok) {
        throw new Error('Speichern fehlgeschlagen');
    }

    return await response.json(); 
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
    window.location.href = "index.html";
}

/********* cricket-page *********/

/********* aroundTheClock-page *********/

/********* 121-page *********/

/********* freiesSpiel-page *********/