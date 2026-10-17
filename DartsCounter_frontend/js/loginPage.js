/********* Anmelden / erster Start *********/

document.addEventListener("DOMContentLoaded", async () => {
    let setup = false;
    try {
        setup = await Auth.setupNeeded();
    } catch (err) {
        console.warn("Konten-Status nicht abrufbar:", err.message);
    }
    if (setup) renderSetup();
    else renderLogin();
});

// über den Tunnel (HTTPS) oder das Backend hat schon eine Anmeldung verlangt
function fromOutside() {
    try {
        if (sessionStorage.getItem("loginRequired")) return true;
    } catch {
        // egal
    }
    return window.location.protocol === "https:";
}

function errorLine() {
    return h("p", { class: "form-error", role: "alert" });
}

function renderLogin() {
    document.getElementById("login-title").textContent = "Anmelden";
    const error = errorLine();
    const nameInput = h("input", { type: "text", id: "login-name", maxlength: NAME_MAX, autocomplete: "username", required: true });
    const codeInput = h("input", {
        type: "text", id: "login-code", autocomplete: "current-password", required: true,
        placeholder: "XXXX-XXXX-XXXX", spellcheck: "false", autocapitalize: "characters"
    });

    const form = h("form", { class: "account-form" },
        h("label", {}, "Spielername", nameInput),
        h("label", {}, "Zugangscode", codeInput),
        h("button", { type: "submit", class: "input-btn primary-btn", text: "Anmelden" }));

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        error.textContent = "";
        try {
            await Auth.login(nameInput.value, codeInput.value);
            bar("index.html");
        } catch (err) {
            error.textContent = err.message;
            codeInput.select();
        }
    });

    document.getElementById("auth-box").replaceChildren(
        h("p", { class: "hint", text: "Den Zugangscode bekommst du vom Admin." }),
        form,
        error,
        h("button", { class: "link-btn", text: "Ohne Anmeldung weiter", onclick: () => bar("index.html") }),
        fromOutside()
            ? h("p", {
                class: "hint",
                text: "Von unterwegs bleiben Spiele ohne Anmeldung nur in diesem Browser. Nach dem Anmelden werden sie hochgeladen."
            })
            : null);
    nameInput.focus();
}

function renderSetup() {
    document.getElementById("login-title").textContent = "Erster Start";
    const error = errorLine();
    const nameInput = h("input", { type: "text", id: "setup-name", maxlength: NAME_MAX, autocomplete: "off", required: true });
    const form = h("form", { class: "account-form" },
        h("label", {}, "Dein Spielername", nameInput),
        h("button", { type: "submit", class: "input-btn primary-btn", text: "Admin-Konto anlegen" }));

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        error.textContent = "";
        try {
            const { account, code } = await Auth.setupAdmin(nameInput.value);
            document.getElementById("auth-box").replaceChildren(
                h("p", { text: "Dein Admin-Konto ist angelegt. Hebe den Code gut auf, damit meldest du dich auf anderen Geräten an." }),
                accessCard(account, code, () => bar("accounts.html")));
        } catch (err) {
            error.textContent = err.message;
        }
    });

    document.getElementById("auth-box").replaceChildren(
        h("p", { text: "Es gibt noch keine Konten. Lege zuerst dein eigenes Admin-Konto an. Danach kannst du Konten für deine Freunde erstellen." }),
        form,
        error,
        h("button", { class: "link-btn", text: "Ohne Konten weiter", onclick: () => bar("index.html") }));
    nameInput.focus();
}
