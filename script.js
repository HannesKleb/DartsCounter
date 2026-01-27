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