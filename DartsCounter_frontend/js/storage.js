/********* Speicher: Backend auf dem Pi, Browser als Ersatz *********/

// Backend-Adresse: in den Einstellungen änderbar, sonst gleicher Rechner auf Port 8080
function getBackendUrl() {
    try {
        const custom = localStorage.getItem("backendUrl");
        if (custom) return custom.replace(/\/+$/, "");
    } catch {
        // localStorage nicht verfügbar
    }
    const host = window.location.hostname || "localhost";
    return `http://${host}:8080`;
}

function setBackendUrl(url) {
    try {
        if (url) localStorage.setItem("backendUrl", url.trim());
        else localStorage.removeItem("backendUrl");
    } catch {
        // localStorage nicht verfügbar
    }
}

const BACKEND_TIMEOUT_MS = 2500;    // länger wird nicht gewartet
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
async function backendFetch(path, options = {}, { force = false } = {}) {
    if (!force && (backendDisabled() || isMarkedOffline())) {
        throw new Error("Backend wird übersprungen (zuletzt nicht erreichbar)");
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), BACKEND_TIMEOUT_MS);
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

const Store = {
    offline: false,

    async get(key, fallback) {
        try {
            const response = await backendFetch(`/api/data/${key}`, { cache: "no-store" });
            if (response.status === 204) {
                this.setOnline(true);
                return fallback;
            }
            if (!response.ok) throw new Error(`Response status: ${response.status}`);
            const value = await response.json();
            this.setOnline(true);
            writeLocal(key, value);
            return value;
        } catch (error) {
            console.warn(`Backend nicht erreichbar (${key}):`, error.message);
            this.setOnline(false);
            return readLocal(key, fallback);
        }
    },

    async set(key, value) {
        // lokale Kopie immer, damit bei Ausfall nichts verloren geht
        writeLocal(key, value);
        try {
            const response = await backendFetch(`/api/data/${key}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(value)
            });
            if (!response.ok) throw new Error(`Response status: ${response.status}`);
            this.setOnline(true);
        } catch (error) {
            console.warn(`Speichern im Backend fehlgeschlagen (${key}):`, error.message);
            this.setOnline(false);
        }
    },

    async remove(key) {
        removeLocal(key);
        try {
            const response = await backendFetch(`/api/data/${key}`, { method: "DELETE" });
            if (!response.ok) throw new Error(`Response status: ${response.status}`);
            this.setOnline(true);
        } catch (error) {
            console.warn(`Löschen im Backend fehlgeschlagen (${key}):`, error.message);
            this.setOnline(false);
        }
    },

    setOnline(online) {
        this.offline = !online;
        showOfflineBanner(!online);
    }
};

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

function showOfflineBanner(show) {
    let banner = document.getElementById("offline-banner");
    if (!show) {
        banner?.remove();
        return;
    }
    if (banner) return;
    banner = document.createElement("div");
    banner.id = "offline-banner";
    banner.className = "offline-banner";
    banner.textContent = "Backend nicht erreichbar. Daten werden nur in diesem Browser gespeichert.";
    document.body.prepend(banner);
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
