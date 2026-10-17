/********* Konten und Anmeldung *********/
// Ein Konto hat nur: Spielername, Rolle (admin/spieler) und einen Zugangscode.
// Keine E-Mail, kein echter Name, kein selbst gewähltes Passwort.
//
// DEMO-MODUS: Solange es kein Backend für Konten gibt, liegen die Konten nur in diesem Browser
// (localStorage). Das ist NICHT sicher (jeder mit Zugriff auf den Browser kann sie ändern).
// Für echte Konten übernimmt später das Backend diese Schnittstelle, geplant:
//   POST   /api/auth/login              {name, code} -> {token, account}
//   POST   /api/auth/setup              {name}       -> {account, code}   (nur wenn noch kein Konto existiert)
//   GET    /api/accounts                              -> [account]          (nur Admin)
//   POST   /api/accounts                {name, role} -> {account, code}   (nur Admin)
//   POST   /api/accounts/{id}/code                    -> {code}            (nur Admin)
//   PATCH  /api/accounts/{id}           {role}       -> account           (nur Admin)
//   DELETE /api/accounts/{id}                                              (nur Admin)
// Das Backend speichert nur einen Hash des Codes und begrenzt Fehlversuche.

const ACCOUNT_ROLES = { admin: "Admin", player: "Spieler" };
const NAME_MAX = 16;

// Zeichen ohne Verwechslungsgefahr (kein 0/O, 1/I/L)
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

function generateAccessCode() {
    const bytes = new Uint8Array(12);
    crypto.getRandomValues(bytes);
    const chars = [...bytes].map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
    return `${chars.slice(0, 4)}-${chars.slice(4, 8)}-${chars.slice(8, 12)}`;
}

// Eingabe tolerant machen: Kleinbuchstaben, Leerzeichen, fehlende Bindestriche
function normalizeCode(code) {
    const raw = String(code).toUpperCase().replace(/[^A-Z0-9]/g, "");
    return raw.length === 12 ? `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}` : raw;
}

function validateName(name) {
    const trimmed = String(name ?? "").trim();
    if (!trimmed) return "Bitte einen Spielernamen eingeben.";
    if (trimmed.length > NAME_MAX) return `Höchstens ${NAME_MAX} Zeichen.`;
    return null;
}

/********* SHA-256 (für Codes im Demo-Modus) *********/
// crypto.subtle gibt es nur über HTTPS/localhost, deshalb eine eigene kleine Umsetzung.

function sha256Hex(text) {
    const K = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    ];
    const H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    const bytes = [...new TextEncoder().encode(text)];
    const bitLength = bytes.length * 8;
    bytes.push(0x80);
    while (bytes.length % 64 !== 56) bytes.push(0);
    for (let i = 7; i >= 0; i--) bytes.push(Math.floor(bitLength / 2 ** (i * 8)) & 0xff);

    const rotr = (x, n) => (x >>> n) | (x << (32 - n));
    const w = new Array(64);
    for (let offset = 0; offset < bytes.length; offset += 64) {
        for (let i = 0; i < 16; i++) {
            const j = offset + i * 4;
            w[i] = (bytes[j] << 24) | (bytes[j + 1] << 16) | (bytes[j + 2] << 8) | bytes[j + 3];
        }
        for (let i = 16; i < 64; i++) {
            const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
            const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
            w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
        }
        let [a, b, c, d, e, f, g, h] = H;
        for (let i = 0; i < 64; i++) {
            const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
            const ch = (e & f) ^ (~e & g);
            const t1 = (h + S1 + ch + K[i] + w[i]) | 0;
            const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
            const maj = (a & b) ^ (a & c) ^ (b & c);
            const t2 = (S0 + maj) | 0;
            h = g; g = f; f = e; e = (d + t1) | 0;
            d = c; c = b; b = a; a = (t1 + t2) | 0;
        }
        H[0] = (H[0] + a) | 0; H[1] = (H[1] + b) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0;
        H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0;
    }
    return H.map((x) => (x >>> 0).toString(16).padStart(8, "0")).join("");
}

function hashCode(code, salt) {
    return sha256Hex(`${salt}:${normalizeCode(code)}`);
}

/********* Demo-Speicher im Browser *********/

const DEMO_KEY = "demo.accounts";
const SESSION_KEY = "session";

function readDemoAccounts() {
    try {
        const list = JSON.parse(localStorage.getItem(DEMO_KEY) ?? "[]");
        return Array.isArray(list) ? list : [];
    } catch {
        return [];
    }
}

function writeDemoAccounts(list) {
    localStorage.setItem(DEMO_KEY, JSON.stringify(list));
}

// nach außen nur Name, Rolle, Datum, nie den Hash
function publicAccount(account) {
    return { id: account.id, name: account.name, role: account.role, createdAt: account.createdAt };
}

function newSalt() {
    const bytes = new Uint8Array(8);
    crypto.getRandomValues(bytes);
    return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function nameTaken(list, name, exceptId = null) {
    return list.some((a) => a.id !== exceptId && a.name.toLowerCase() === name.toLowerCase());
}

const Accounts = {
    demoMode: true,

    async list() {
        Auth.requireAdmin();
        return readDemoAccounts()
            .map(publicAccount)
            .sort((a, b) => a.name.localeCompare(b.name, "de"));
    },

    async create({ name, role = "player" }) {
        Auth.requireAdmin();
        return createAccount(name, role);
    },

    async resetCode(id) {
        Auth.requireAdmin();
        const list = readDemoAccounts();
        const account = list.find((a) => a.id === id);
        if (!account) throw new Error("Konto nicht gefunden.");
        const code = generateAccessCode();
        account.salt = newSalt();
        account.codeHash = hashCode(code, account.salt);
        writeDemoAccounts(list);
        return code;
    },

    async setRole(id, role) {
        Auth.requireAdmin();
        if (!ACCOUNT_ROLES[role]) throw new Error("Unbekannte Rolle.");
        const list = readDemoAccounts();
        const account = list.find((a) => a.id === id);
        if (!account) throw new Error("Konto nicht gefunden.");
        if (account.role === "admin" && role !== "admin" && list.filter((a) => a.role === "admin").length === 1) {
            throw new Error("Es muss mindestens ein Admin bleiben.");
        }
        account.role = role;
        writeDemoAccounts(list);
        return publicAccount(account);
    },

    async remove(id) {
        Auth.requireAdmin();
        const list = readDemoAccounts();
        const account = list.find((a) => a.id === id);
        if (!account) return;
        if (account.role === "admin" && list.filter((a) => a.role === "admin").length === 1) {
            throw new Error("Der letzte Admin kann nicht gelöscht werden.");
        }
        if (Auth.current()?.accountId === id) throw new Error("Du kannst dein eigenes Konto nicht löschen.");
        writeDemoAccounts(list.filter((a) => a.id !== id));
    }
};

function createAccount(name, role) {
    const error = validateName(name);
    if (error) throw new Error(error);
    if (!ACCOUNT_ROLES[role]) throw new Error("Unbekannte Rolle.");
    const list = readDemoAccounts();
    const trimmed = name.trim();
    if (nameTaken(list, trimmed)) throw new Error("Diesen Spielernamen gibt es schon.");

    const code = generateAccessCode();
    const salt = newSalt();
    const account = {
        id: newAccountId(),
        name: trimmed,
        role,
        createdAt: new Date().toISOString(),
        salt,
        codeHash: hashCode(code, salt)
    };
    list.push(account);
    writeDemoAccounts(list);
    return { account: publicAccount(account), code };
}

function newAccountId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

const Auth = {
    // Erster Start: noch kein Konto vorhanden
    async setupNeeded() {
        return readDemoAccounts().length === 0;
    },

    async setupAdmin(name) {
        if (!(await this.setupNeeded())) throw new Error("Es gibt schon Konten.");
        const result = createAccount(name, "admin");
        this.startSession(result.account);
        return result;
    },

    async login(name, code) {
        const list = readDemoAccounts();
        const account = list.find((a) => a.name.toLowerCase() === String(name).trim().toLowerCase());
        // gleiche Meldung für falschen Namen und falschen Code
        if (!account || hashCode(code, account.salt) !== account.codeHash) {
            throw new Error("Spielername oder Zugangscode stimmt nicht.");
        }
        this.startSession(publicAccount(account));
        return publicAccount(account);
    },

    logout() {
        try {
            localStorage.removeItem(SESSION_KEY);
        } catch {
            // egal
        }
    },

    current() {
        try {
            const session = JSON.parse(localStorage.getItem(SESSION_KEY) ?? "null");
            if (!session) return null;
            // Konto könnte inzwischen gelöscht worden sein
            const account = readDemoAccounts().find((a) => a.id === session.accountId);
            if (!account) return null;
            return { accountId: account.id, name: account.name, role: account.role };
        } catch {
            return null;
        }
    },

    isAdmin() {
        return this.current()?.role === "admin";
    },

    requireAdmin() {
        if (!this.isAdmin()) throw new Error("Nur für Admins.");
    },

    startSession(account) {
        localStorage.setItem(SESSION_KEY, JSON.stringify({ accountId: account.id, since: new Date().toISOString() }));
    },

    // nur Spielernamen (für Vorschläge bei der Spielerauswahl), keine weiteren Daten
    accountNames() {
        return readDemoAccounts().map((a) => a.name);
    }
};

/********* Zugangskarte (zum Weitergeben) *********/

function accessCard(account, code, onDone) {
    const copyBtn = h("button", { class: "input-btn", text: "Code kopieren" });
    copyBtn.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(`Darts Counter\nSpielername: ${account.name}\nZugangscode: ${code}`);
            copyBtn.textContent = "Kopiert";
        } catch {
            // ohne HTTPS gibt es keine Zwischenablage: Code markieren, dann Strg+C
            const range = document.createRange();
            range.selectNodeContents(card.querySelector(".access-code"));
            getSelection().removeAllRanges();
            getSelection().addRange(range);
            copyBtn.textContent = "Markiert, jetzt Strg+C";
        }
    });

    const card = h("div", { class: "access-card" },
        h("div", { class: "access-card-print" },
            h("div", { class: "access-card-title", text: "Darts Counter" }),
            h("div", { class: "access-label", text: "Spielername" }),
            h("div", { class: "access-name", text: account.name }),
            h("div", { class: "access-label", text: "Zugangscode" }),
            h("div", { class: "access-code", text: code })),
        h("p", { class: "hint", text: "Der Code wird nur jetzt angezeigt. Gib ihn weiter oder drucke die Karte aus." }),
        h("div", { class: "settings-row" },
            copyBtn,
            h("button", { class: "input-btn", text: "Karte drucken", onclick: () => printAccessCard(card) }),
            h("button", { class: "primary-btn input-btn", text: "Fertig", onclick: onDone })));
    return card;
}

function printAccessCard(card) {
    card.classList.add("printing");
    document.body.classList.add("print-access-card");
    window.addEventListener("afterprint", () => {
        card.classList.remove("printing");
        document.body.classList.remove("print-access-card");
    }, { once: true });
    window.print();
}

/********* Anzeige oben rechts: angemeldet als ... *********/

function renderUserMenu() {
    const header = document.querySelector(".header");
    if (!header) return;
    header.querySelector(".user-menu")?.remove();

    const user = Auth.current();
    const menu = h("div", { class: "user-menu" });
    if (user) {
        const items = [
            h("span", { class: "user-pill", title: ACCOUNT_ROLES[user.role] },
                h("span", { text: user.name }),
                user.role === "admin" ? h("span", { class: "badge", text: "Admin" }) : null),
            user.role === "admin"
                ? h("button", { class: "input-btn", text: "Konten", onclick: () => bar("accounts.html") })
                : null,
            h("button", {
                class: "input-btn", text: "Abmelden",
                onclick: () => {
                    Auth.logout();
                    renderUserMenu();
                    document.dispatchEvent(new CustomEvent("authchange"));
                }
            })
        ];
        menu.append(...items.filter(Boolean));
    } else {
        menu.append(h("button", { class: "input-btn", text: "Anmelden", onclick: () => bar("login.html") }));
    }
    header.append(menu);
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.body.dataset.noUserMenu === undefined) renderUserMenu();
});
