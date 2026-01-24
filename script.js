/********* VARIABLES *********/

//buttons
/*const X01Btn = document.getElementById("X01-btn");
const cricketBtn = document.getElementById("cricket-btn");
const aroundTheClockBtn = document.getElementById("around-the-clock-btn");
const g121Btn = document.getElementById("g121-btn");
const freieSpielBtn = document.getElementById("freies-spiel-btn");*/

function showMode(id) {
    const allModes = document.querySelectorAll('.mode > div');
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

