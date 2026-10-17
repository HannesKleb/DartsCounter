/********* Spieler-Seite: bekannte Spieler verwalten *********/

let players = [];
let games = [];

document.addEventListener("DOMContentLoaded", async () => {
    document.getElementById("new-player-btn").addEventListener("click", addKnownPlayer);
    document.getElementById("new-player").addEventListener("keydown", (event) => {
        if (event.key === "Enter") addKnownPlayer();
    });
    document.getElementById("known-player-list").replaceChildren(...skeletonRows());
    [players, games] = await Promise.all([loadKnownPlayers(), loadGames()]);
    renderPlayers();
    animateList(document.querySelectorAll("#known-player-list .player-item"));
});

function renderPlayers() {
    const listEl = document.getElementById("known-player-list");
    if (players.length === 0) {
        listEl.replaceChildren(h("div", { class: "hint", text: "Noch keine Spieler. Spieler werden auch beim Spielstart automatisch gespeichert." }));
        return;
    }
    const sorted = [...players].sort((a, b) => a.name.localeCompare(b.name, "de"));
    listEl.replaceChildren(...sorted.map((player) => {
        const own = games.filter((g) => g.players.some((p) => p.name === player.name && !p.bot));
        const wins = own.filter((g) => g.winner !== null && g.winner !== undefined && g.players[g.winner]?.name === player.name).length;
        return h("div", { class: "player-item" },
            avatar(player),
            h("span", { class: "player-name", text: player.name }),
            h("span", { class: "hint", text: `${own.length} ${own.length === 1 ? "Spiel" : "Spiele"}, ${wins} ${wins === 1 ? "Sieg" : "Siege"}` }),
            h("span", { class: "player-actions" },
                h("button", { text: "Umbenennen", onclick: () => renamePlayer(player.name) }),
                h("button", { class: "delete-btn", text: "Löschen", onclick: () => deletePlayer(player.name) })));
    }));
}

async function addKnownPlayer() {
    const input = document.getElementById("new-player");
    const name = input.value.trim();
    if (!name) return;
    if (players.some((p) => p.name.toLowerCase() === name.toLowerCase())) {
        alert("Diesen Spieler gibt es schon.");
        return;
    }
    players.push({ name });
    input.value = "";
    await Store.set("players", players);
    renderPlayers();
}

// Umbenennen ändert auch alle gespeicherten Spiele, damit die Statistik zusammenbleibt
async function renamePlayer(oldName) {
    const newName = prompt("Neuer Name:", oldName)?.trim().slice(0, 16);
    if (!newName || newName === oldName) return;
    if (players.some((p) => p.name.toLowerCase() === newName.toLowerCase())) {
        alert("Diesen Namen gibt es schon.");
        return;
    }
    players = players.map((p) => (p.name === oldName ? { ...p, name: newName } : p));
    let changed = false;
    for (const game of games) {
        for (const p of game.players) {
            if (!p.bot && p.name === oldName) {
                p.name = newName;
                changed = true;
            }
        }
    }
    await Store.set("players", players);
    if (changed) await Store.set("games", games);
    renderPlayers();
}

async function deletePlayer(name) {
    if (!confirm(`${name} löschen? Gespielte Spiele bleiben erhalten.`)) return;
    players = players.filter((p) => p.name !== name);
    await Store.set("players", players);
    renderPlayers();
}
