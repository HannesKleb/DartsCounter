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