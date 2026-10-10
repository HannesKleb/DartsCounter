/********* Statistik-Seite *********/

let statGames = [];
let knownForStats = [];

document.addEventListener("DOMContentLoaded", async () => {
    [statGames, knownForStats] = await Promise.all([loadGames(), loadKnownPlayers()]);
    document.querySelector('[data-group="stats"]').addEventListener("modechange", renderStats);
    document.getElementById("show-bots").addEventListener("change", renderStats);
    showMode("X01-stats", "stats");
});

function renderStats() {
    const panel = document.querySelector('[data-group="stats"] [data-panel][style*="block"]');
    if (!panel) return;
    const mode = panel.dataset.mode;
    const showBots = document.getElementById("show-bots").checked;

    const modeGames = statGames.filter((g) => g.mode === mode);
    const names = new Set();
    for (const game of modeGames) {
        game.players.filter((p) => showBots || !p.bot).forEach((p) => names.add(p.name));
    }

    if (names.size === 0) {
        panel.replaceChildren(h("div", { class: "hint", text: "Noch keine Spiele in diesem Modus." }));
        return;
    }

    const columns = [...STAT_COLUMNS.common, ...STAT_COLUMNS[mode]];
    const rows = [...names]
        .map((name) => ({ name, stats: aggregatePlayerStats(modeGames, mode, name) }))
        .sort((a, b) => b.stats.wins - a.stats.wins || b.stats.games - a.stats.games);

    panel.replaceChildren(
        h("div", { class: "table-scroll" },
            h("table", { class: "stats-table" },
                h("thead", {}, h("tr", {}, h("th", { text: "Spieler" }), columns.map(([title]) => h("th", { text: title })))),
                h("tbody", {}, rows.map((row) => h("tr", {},
                    h("th", { text: row.name }),
                    columns.map(([, value]) => h("td", { text: value(row.stats) }))))))));
}
