/********* Alle Spiele *********/

let allGames = [];

document.addEventListener("DOMContentLoaded", async () => {
    showMode("X01-games", "games");
    document.getElementById("X01-games").replaceChildren(...skeletonRows());

    allGames = await loadGames();
    document.querySelector('[data-group="games"]').addEventListener("modechange", renderGames);
    renderGames();
});

function renderGames() {
    const panel = document.querySelector('[data-group="games"] [data-panel]:not([style*="none"])');
    if (!panel) return;
    const mode = panel.dataset.mode;

    const games = allGames
        .filter((g) => g.mode === mode)
        .sort((a, b) => (b.finishedAt ?? "").localeCompare(a.finishedAt ?? ""));

    if (games.length === 0) {
        panel.replaceChildren(h("div", { class: "hint", text: "Noch keine Spiele in diesem Modus." }));
        return;
    }
    panel.replaceChildren(...games.map(gameCard));
    animateList(panel.children);
}

function gameCard(game) {
    const s = replayGame(game);
    const winner = s.winner === null ? "Unentschieden" : `Sieger: ${game.players[s.winner].name}`;

    const details = h("div", { class: "game-details", hidden: true },
        h("div", { class: "hint", text: describeOptions(game.mode, game.options) }),
        game.players.map((player, p) => h("div", { class: "result-item" },
            h("strong", { text: playerLabel(player) }),
            h("span", { text: gameStatsLine(game.mode, playerGameStats(s, p)) }))),
        h("button", { class: "danger-btn", text: "Spiel löschen", onclick: () => deleteGame(game.id) }));

    return h("div", { class: "game-card" },
        h("button", {
            class: "game-card-head",
            onclick: () => { details.hidden = !details.hidden; }
        },
            h("span", { text: formatDate(game.finishedAt ?? game.startedAt) }),
            h("span", { text: game.players.map((p) => p.name).join(", ") }),
            h("strong", { text: winner })),
        details);
}

async function deleteGame(id) {
    if (!confirm("Dieses Spiel löschen? Es fehlt danach auch in der Statistik.")) return;
    allGames = allGames.filter((g) => g.id !== id);
    await Store.set("games", allGames);
    renderGames();
}
