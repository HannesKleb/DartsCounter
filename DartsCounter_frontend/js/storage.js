/********* Speicher: Backend auf dem Pi, Browser als Ersatz *********/

// Backend-Adresse: in den Einstellungen änderbar. Sonst
// - über HTTPS (Cloudflare Tunnel): gleiche Domain, der Tunnel leitet /api und /services ans Backend
// - im Heimnetz (http:): gleicher Rechner auf Port 8080
function getBackendUrl() {
    const custom = customBackendUrl();
    if (custom) return custom;
    if (window.location.protocol === "https:") return window.location.origin;
    const host = window.location.hostname || "localhost";
    return `http://${host}:8080`;
}

function customBackendUrl() {
    try {
        const custom = localStorage.getItem("backendUrl");
        return custom ? custom.replace(/\/+$/, "") : null;
    } catch {
        return null; // localStorage nicht verfügbar
    }
}

// Beschreibung für die Einstellungen
function backendUrlSource() {
    if (customBackendUrl()) return "eigene Adresse aus den Einstellungen";
    if (window.location.protocol === "https:") return "automatisch: gleiche Domain (HTTPS, Cloudflare Tunnel)";
    return "automatisch: dieser Rechner, Port 8080 (Heimnetz)";
}

function setBackendUrl(url) {
    try {
        if (url) localStorage.setItem("backendUrl", url.trim());
        else localStorage.removeItem("backendUrl");
    } catch {
        // localStorage nicht verfügbar
    }
}

// Anmelde-Token (wird von accounts.js gesetzt); über den Tunnel braucht /api/data eine Anmeldung
const AUTH_TOKEN_KEY = "authToken";

function readAuthToken() {
    try {
        return localStorage.getItem(AUTH_TOKEN_KEY);
    } catch {
        return null;
    }
}

function dataHeaders(extra = {}) {
    const token = readAuthToken();
    return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
}

const BACKEND_TIMEOUT_MS = 2500;    // länger wird für Daten nicht gewartet
const AUTH_TIMEOUT_MS = 10000;      // Anmeldung: Code-Hash auf dem Pi und Tunnel brauchen länger
const OFFLINE_RETRY_MS = 30000;     // so lange nach einem Fehlschlag nicht erneut versuchen

// per Doppelklick geöffnet (file://) und keine Adresse eingetragen: kein Backend, sofort lokal speichern
function backendDisabled() {
    if (window.location.protocol !== "file:") return false;
    try {
        return !localStorage.getItem("backendUrl");
    } catch {
        return true;
    }
}

function isMarkedOffline() {
    try {
        const at = parseInt(sessionStorage.getItem("backendOfflineAt") ?? "", 10);
        return !Number.isNaN(at) && Date.now() - at < OFFLINE_RETRY_MS;
    } catch {
        return false;
    }
}

function markOffline(offline) {
    try {
        if (offline) sessionStorage.setItem("backendOfflineAt", String(Date.now()));
        else sessionStorage.removeItem("backendOfflineAt");
    } catch {
        // egal
    }
}

// Beim Seitenwechsel bricht der Browser laufende Anfragen ab, das heißt nicht, dass das Backend weg ist
let pageUnloading = false;
window.addEventListener("pagehide", () => { pageUnloading = true; });
window.addEventListener("pageshow", () => { pageUnloading = false; });

// fetch mit Zeitlimit; nach einem Fehlschlag wird das Backend eine Weile übersprungen
async function backendFetch(path, options = {}, { force = false, timeoutMs = BACKEND_TIMEOUT_MS } = {}) {
    if (!force && (backendDisabled() || isMarkedOffline())) {
        throw new Error("Backend wird übersprungen (zuletzt nicht erreichbar)");
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(`${getBackendUrl()}${path}`, { ...options, signal: controller.signal });
        markOffline(false);
        return response;
    } catch (error) {
        if (!pageUnloading) markOffline(true);
        throw error;
    } finally {
        clearTimeout(timer);
    }
}

/********* Abgleich: lokal gespeicherte Daten nachträglich hochladen *********/
// Ist das Backend aus oder verlangt es eine Anmeldung (401), bleibt ein Schlüssel als "ausstehend"
// markiert (localStorage "store.pending"). Sobald es wieder geht, wird abgeglichen:
// - "games" (nach id) und "players" (nach Name) werden mit dem Server zusammengeführt, nichts fällt weg.
//   Bewusst gelöschte Einträge merkt sich "store.removed.<key>", damit sie nicht zurückkommen.
// - "settings" und "currentGame": der lokale Stand gewinnt.
// - Schlüssel in "store.replace" (Alles löschen, Backup einspielen) ersetzen den Server-Stand.

const PENDING_KEY = "store.pending";
const REPLACE_KEY = "store.replace";
const MERGE_KEYS = {
    games: (item) => item?.id,
    players: (item) => String(item?.name ?? "").toLowerCase()
};

function readList(storageKey) {
    try {
        const list = JSON.parse(localStorage.getItem(storageKey) ?? "[]");
        return Array.isArray(list) ? list : [];
    } catch {
        return [];
    }
}

function writeList(storageKey, list) {
    try {
        if (list.length) localStorage.setItem(storageKey, JSON.stringify(list));
        else localStorage.removeItem(storageKey);
    } catch {
        // egal
    }
}

function pendingKeys() {
    return readList(PENDING_KEY);
}

function isPending(key) {
    return pendingKeys().includes(key);
}

function markPending(key, replace = false) {
    writeList(PENDING_KEY, [...new Set([...pendingKeys(), key])]);
    const replaceKeys = readList(REPLACE_KEY).filter((k) => k !== key);
    writeList(REPLACE_KEY, replace ? [...replaceKeys, key] : replaceKeys);
}

function clearPending(key) {
    writeList(PENDING_KEY, pendingKeys().filter((k) => k !== key));
    writeList(REPLACE_KEY, readList(REPLACE_KEY).filter((k) => k !== key));
    writeList(`store.removed.${key}`, []);
}

function hasLocal(key) {
    try {
        return localStorage.getItem(`store.${key}`) !== null;
    } catch {
        return false;
    }
}

// gelöschte Einträge merken (Unterschied zwischen altem und neuem lokalen Stand)
function rememberRemoved(key, oldList, newList) {
    const identity = MERGE_KEYS[key];
    if (!identity || !Array.isArray(oldList) || !Array.isArray(newList)) return;
    const kept = new Set(newList.map(identity));
    const removed = oldList.map(identity).filter((id) => id && !kept.has(id));
    if (removed.length) writeList(`store.removed.${key}`, [...new Set([...readList(`store.removed.${key}`), ...removed])]);
}

// bei gleicher id: mehr Würfe, dann das später beendete Spiel
function newerGame(a, b) {
    const dartsA = a?.darts?.length ?? 0;
    const dartsB = b?.darts?.length ?? 0;
    if (dartsA !== dartsB) return dartsA > dartsB ? a : b;
    return (a?.finishedAt ?? "") >= (b?.finishedAt ?? "") ? a : b;
}

// lokalen Stand mit dem Server zusammenführen (nur für ausstehende Schlüssel)
function mergeValues(key, local, server) {
    if (readList(REPLACE_KEY).includes(key)) return local;
    const identity = MERGE_KEYS[key];
    if (!identity) return local; // settings, currentGame: lokal gewinnt
    const removed = new Set(readList(`store.removed.${key}`));
    const byId = new Map();
    for (const item of Array.isArray(server) ? server : []) {
        if (!removed.has(identity(item))) byId.set(identity(item), item);
    }
    for (const item of Array.isArray(local) ? local : []) {
        const id = identity(item);
        const existing = byId.get(id);
        if (!existing) byId.set(id, item);
        else if (key === "games") byId.set(id, newerGame(item, existing));
        else byId.set(id, item); // Spieler: lokaler Eintrag (gleicher Name)
    }
    return [...byId.values()];
}

// 401 von /api/data: Anmeldung fehlt oder ist abgelaufen
function handleUnauthorized() {
    const hadToken = Boolean(readAuthToken());
    if (hadToken) {
        try {
            localStorage.removeItem(AUTH_TOKEN_KEY);
        } catch {
            // egal
        }
        document.dispatchEvent(new CustomEvent("authexpired"));
    }
    try {
        sessionStorage.setItem("loginRequired", "1");
    } catch {
        // egal
    }
    Store.offline = true;
    showOfflineBanner(true, hadToken ? "expired" : "login");
}

let syncRunning = null;
let syncAgain = false;

const Store = {
    offline: false,

    async get(key, fallback) {
        // erst Ausstehendes hochladen, dann lesen
        if (isPending(key)) await this.sync();
        try {
            const response = await backendFetch(`/api/data/${key}`, { cache: "no-store", headers: dataHeaders() });
            if (response.status === 401) {
                handleUnauthorized();
                return readLocal(key, fallback);
            }
            if (response.status !== 204 && !response.ok) throw new Error(`Response status: ${response.status}`);
            const server = response.status === 204 ? null : await response.json();
            this.setOnline(true);

            // lokale Kopie, die noch aussteht, nie überschreiben
            if (isPending(key)) {
                const merged = hasLocal(key) ? mergeValues(key, readLocal(key, null), server) : null;
                if (merged !== null) writeLocal(key, merged);
                return merged ?? fallback;
            }
            if (server === null) {
                removeLocal(key);
                return fallback;
            }
            writeLocal(key, server);
            return server;
        } catch (error) {
            console.warn(`Backend nicht erreichbar (${key}):`, error.message);
            this.setOnline(false);
            return readLocal(key, fallback);
        }
    },

    // replace: true ersetzt den Server-Stand (Alles löschen, Backup einspielen)
    async set(key, value, { replace = false } = {}) {
        if (!replace) rememberRemoved(key, readLocal(key, null), value);
        // lokale Kopie immer, damit bei Ausfall nichts verloren geht
        writeLocal(key, value);
        markPending(key, replace);
        await this.sync();
    },

    async remove(key) {
        removeLocal(key);
        markPending(key, true);
        await this.sync();
    },

    // alle ausstehenden Schlüssel abgleichen; läuft nie doppelt (spätere Aufrufe warten mit)
    sync({ force = false } = {}) {
        if (syncRunning) {
            syncAgain = true;
            return syncRunning;
        }
        syncRunning = (async () => {
            do {
                syncAgain = false;
                await syncPending(force);
            } while (syncAgain && pendingKeys().length);
        })().finally(() => {
            syncRunning = null;
            document.dispatchEvent(new CustomEvent("syncchange"));
        });
        return syncRunning;
    },

    pendingCount() {
        return pendingKeys().length;
    },

    setOnline(online) {
        this.offline = !online;
        if (online) {
            try {
                sessionStorage.removeItem("loginRequired");
            } catch {
                // egal
            }
        }
        showOfflineBanner(!online);
    }
};

function localSnapshot(key) {
    try {
        return localStorage.getItem(`store.${key}`); // null = lokal gelöscht
    } catch {
        return null;
    }
}

async function syncPending(force) {
    for (const key of pendingKeys()) {
        try {
            // Stand merken, um später zu sehen, ob sich lokal währenddessen etwas geändert hat
            const snapshot = localSnapshot(key);
            let value = snapshot === null ? null : JSON.parse(snapshot);

            if (value !== null && MERGE_KEYS[key] && !readList(REPLACE_KEY).includes(key)) {
                const response = await backendFetch(`/api/data/${key}`, { cache: "no-store", headers: dataHeaders() }, { force });
                if (response.status === 401) return handleUnauthorized();
                if (response.status !== 204 && !response.ok) throw new Error(`Response status: ${response.status}`);
                const server = response.status === 204 ? null : await response.json();
                value = mergeValues(key, value, server);
            }

            const response = value === null
                ? await backendFetch(`/api/data/${key}`, { method: "DELETE", headers: dataHeaders() }, { force })
                : await backendFetch(`/api/data/${key}`, {
                    method: "PUT",
                    headers: dataHeaders({ "Content-Type": "application/json" }),
                    body: JSON.stringify(value)
                }, { force });
            if (response.status === 401) return handleUnauthorized();
            if (!response.ok) throw new Error(`Response status: ${response.status}`);
            Store.setOnline(true);

            // Hat sich lokal etwas geändert (z. B. neuer Wurf), bleibt der Schlüssel ausstehend
            // und wird in der nächsten Runde erneut abgeglichen.
            if (localSnapshot(key) === snapshot) {
                if (value !== null) writeLocal(key, value);
                clearPending(key);
            } else {
                syncAgain = true;
            }
        } catch (error) {
            console.warn(`Abgleich fehlgeschlagen (${key}):`, error.message);
            Store.setOnline(false);
            return; // Backend weg: später erneut
        }
    }
}

// beim Laden der Seite und regelmäßig nachholen, was noch aussteht
document.addEventListener("DOMContentLoaded", () => {
    if (pendingKeys().length) Store.sync({ force: true });
});
setInterval(() => {
    if (pendingKeys().length && !syncRunning) Store.sync();
}, 30000);
window.addEventListener("online", () => {
    if (pendingKeys().length) Store.sync({ force: true });
});

// Kamera-Endpunkte (/services): von außen nur für Admins; Meldung aus {"message"} als Fehler
async function servicesFetch(path, options = {}, { timeoutMs = BACKEND_TIMEOUT_MS } = {}) {
    const response = await backendFetch(`/services${path}`, {
        ...options,
        headers: dataHeaders(options.headers ?? {})
    }, { force: true, timeoutMs });
    if (response.status === 401 || response.status === 403) {
        let message = response.status === 401 ? "Bitte anmelden." : "Nur für Admins.";
        try {
            message = (await response.json())?.message ?? message;
        } catch {
            // keine JSON-Antwort
        }
        if (response.status === 401) handleUnauthorized();
        throw new Error(message);
    }
    return response;
}

async function testBackend() {
    try {
        // Verbindungstest probiert es immer, auch wenn das Backend zuletzt weg war
        const response = await backendFetch("/api/hallo", { cache: "no-store" }, { force: true });
        return response.ok;
    } catch {
        return false;
    }
}

function readLocal(key, fallback) {
    try {
        const raw = localStorage.getItem(`store.${key}`);
        return raw === null ? fallback : JSON.parse(raw);
    } catch {
        return fallback;
    }
}

function writeLocal(key, value) {
    try {
        localStorage.setItem(`store.${key}`, JSON.stringify(value));
    } catch {
        // voll oder gesperrt: Backend ist dann die einzige Kopie
    }
}

function removeLocal(key) {
    try {
        localStorage.removeItem(`store.${key}`);
    } catch {
        // egal
    }
}

function loginPageUrl() {
    return window.location.pathname.includes("/gameModes/") ? "../mainPages/login.html" : "login.html";
}

function showOfflineBanner(show, reason = "offline") {
    let banner = document.getElementById("offline-banner");
    if (!show) {
        banner?.remove();
        return;
    }
    // eine Anmelde-Meldung nicht durch "nicht erreichbar" ersetzen, "abgelaufen" nicht durch "Anmeldung nötig"
    if (banner && reason === "offline" && banner.dataset.reason !== "offline") return;
    if (banner && reason === "login" && banner.dataset.reason === "expired") return;
    if (!banner) {
        banner = document.createElement("div");
        banner.id = "offline-banner";
        banner.className = "offline-banner";
        document.body.prepend(banner);
    }
    banner.dataset.reason = reason;
    const texts = {
        offline: "Backend nicht erreichbar. Daten werden nur in diesem Browser gespeichert und später hochgeladen.",
        login: "Von unterwegs nur mit Anmeldung. Bis dahin bleiben Daten in diesem Browser und werden danach hochgeladen.",
        expired: "Anmeldung abgelaufen. Bitte neu anmelden, bis dahin bleiben Daten in diesem Browser."
    };
    const text = document.createElement("span");
    text.textContent = texts[reason] ?? texts.offline;
    banner.replaceChildren(text);
    if (reason !== "offline" && !window.location.pathname.endsWith("/login.html")) {
        const button = document.createElement("button");
        button.className = "input-btn banner-btn";
        button.textContent = "Anmelden";
        button.addEventListener("click", () => { window.location.href = loginPageUrl(); });
        banner.append(button);
    }
}

/********* Einstellungen *********/

const DEFAULT_SETTINGS = {
    inputMode: "keyboard",   // "keyboard" oder "board"
    botLevel: "medium",      // "easy", "medium", "hard"
    botDelayMs: 700
};

async function loadSettings() {
    const settings = await Store.get("settings", {});
    return { ...DEFAULT_SETTINGS, ...settings };
}
