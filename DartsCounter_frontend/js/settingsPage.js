/********* Einstellungen *********/

let currentSettings = { ...DEFAULT_SETTINGS };

document.addEventListener("DOMContentLoaded", async () => {
    let custom = "";
    try {
        custom = localStorage.getItem("backendUrl") ?? "";
    } catch {
        // egal
    }
    document.getElementById("backend-url").value = custom;
    document.getElementById("backend-save").addEventListener("click", saveBackend);

    currentSettings = await loadSettings();
    document.getElementById("input-mode").value = currentSettings.inputMode;
    document.getElementById("bot-level").value = currentSettings.botLevel;
    document.getElementById("bot-delay").value = String(currentSettings.botDelayMs);
    for (const id of ["input-mode", "bot-level", "bot-delay"]) {
        document.getElementById(id).addEventListener("change", saveSettings);
    }

    document.getElementById("export-btn").addEventListener("click", exportData);
    document.getElementById("import-file").addEventListener("change", importData);
    document.getElementById("delete-games").addEventListener("click", deleteAllGames);
    document.getElementById("delete-players").addEventListener("click", deleteAllPlayers);

    updateBackendStatus();
    renderAccountSection();
    document.addEventListener("authchange", renderAccountSection);
});

function renderAccountSection() {
    const user = Auth.current();
    document.getElementById("account-status").textContent = user
        ? `Angemeldet als ${user.name} (${ACCOUNT_ROLES[user.role]}).`
        : "Nicht angemeldet. Spielen geht auch ohne Konto.";
    const actions = [];
    if (user?.role === "admin") actions.push(h("button", { class: "input-btn", text: "Konten verwalten", onclick: () => bar("accounts.html") }));
    if (user) {
        actions.push(h("button", {
            class: "input-btn", text: "Abmelden",
            onclick: () => {
                Auth.logout();
                renderUserMenu();
                renderAccountSection();
            }
        }));
    } else {
        actions.push(h("button", { class: "input-btn primary-btn", text: "Anmelden", onclick: () => bar("login.html") }));
    }
    document.getElementById("account-actions").replaceChildren(...actions);
}

async function updateBackendStatus() {
    const status = document.getElementById("backend-status");
    status.textContent = "Teste Verbindung …";
    const ok = await testBackend();
    status.textContent = ok ? `Verbunden mit ${getBackendUrl()}` : `Keine Verbindung zu ${getBackendUrl()}`;
    status.classList.toggle("error", !ok);
}

function saveBackend() {
    setBackendUrl(document.getElementById("backend-url").value);
    updateBackendStatus();
}

async function saveSettings() {
    currentSettings = {
        ...currentSettings,
        inputMode: document.getElementById("input-mode").value,
        botLevel: document.getElementById("bot-level").value,
        botDelayMs: parseInt(document.getElementById("bot-delay").value, 10)
    };
    await Store.set("settings", currentSettings);
    document.getElementById("settings-status").textContent = Store.offline ? "Nur lokal gespeichert" : "Gespeichert";
}

async function exportData() {
    const [players, games, settings] = await Promise.all([
        Store.get("players", []), Store.get("games", []), Store.get("settings", {})
    ]);
    const data = { app: "DartsCounter", exportedAt: new Date().toISOString(), players, games, settings };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const link = h("a", {
        href: URL.createObjectURL(blob),
        download: `dartscounter-backup-${new Date().toISOString().slice(0, 10)}.json`
    });
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
}

async function importData(event) {
    const file = event.target.files[0];
    event.target.value = "";
    if (!file) return;
    const status = document.getElementById("data-status");
    try {
        const data = JSON.parse(await file.text());
        if (data.app !== "DartsCounter" || !Array.isArray(data.games) || !Array.isArray(data.players)) {
            throw new Error("Keine DartsCounter-Sicherung");
        }
        if (!confirm(`Backup einspielen? Aktuelle Spieler und Spiele werden ersetzt (${data.games.length} Spiele).`)) return;
        await Store.set("players", data.players);
        await Store.set("games", data.games);
        if (data.settings) await Store.set("settings", data.settings);
        status.textContent = "Backup eingespielt.";
    } catch (error) {
        status.textContent = `Fehler: ${error.message}`;
    }
}

async function deleteAllGames() {
    if (!confirm("Wirklich ALLE gespeicherten Spiele löschen? Das kann nicht rückgängig gemacht werden.")) return;
    await Store.set("games", []);
    document.getElementById("data-status").textContent = "Alle Spiele gelöscht.";
}

async function deleteAllPlayers() {
    if (!confirm("Wirklich alle Spieler löschen? Gespielte Spiele bleiben erhalten.")) return;
    await Store.set("players", []);
    document.getElementById("data-status").textContent = "Alle Spieler gelöscht.";
}
