/********* Konten verwalten (nur Admin) *********/

document.addEventListener("DOMContentLoaded", () => runPage(async () => {
    if (!(await Auth.isAdmin())) {
        document.getElementById("accounts-content").replaceChildren(
            h("div", { class: "empty-state" },
                h("p", { text: "Diese Seite ist nur für Admins." }),
                h("button", { class: "primary-btn", text: "Anmelden", onclick: () => bar("login.html") })));
        return;
    }
    document.getElementById("create-form").addEventListener("submit", createFromForm);
    renderAccounts();
}));

async function createFromForm(event) {
    event.preventDefault();
    const nameInput = document.getElementById("new-account-name");
    const error = document.getElementById("create-error");
    error.textContent = "";
    try {
        const { account, code } = await Accounts.create({
            name: nameInput.value,
            role: document.getElementById("new-account-role").value
        });
        nameInput.value = "";
        showCard(account, code);
        renderAccounts();
    } catch (err) {
        error.textContent = err.message;
    }
}

function showCard(account, code) {
    const slot = document.getElementById("new-card");
    const card = accessCard(account, code, () => slot.replaceChildren());
    card.classList.add("panel-in");
    slot.replaceChildren(card);
    card.scrollIntoView({ block: "nearest", behavior: prefersReducedMotion() ? "auto" : "smooth" });
}

async function renderAccounts() {
    const listEl = document.getElementById("account-list");
    const error = document.getElementById("list-error");
    let accounts;
    try {
        accounts = await Accounts.list();
    } catch (err) {
        error.textContent = err.message;
        return;
    }
    const me = await Auth.current();

    listEl.replaceChildren(...accounts.map((account) => {
        const roleSelect = h("select", { "aria-label": `Rolle von ${account.name}` },
            Object.entries(ACCOUNT_ROLES).map(([value, label]) =>
                h("option", { value, text: label, selected: value === account.role })));
        roleSelect.addEventListener("change", () => run(() => Accounts.setRole(account.id, roleSelect.value)));

        return h("div", { class: "player-item account-row" },
            h("span", { class: "player-name" },
                account.name,
                account.id === me?.accountId ? h("span", { class: "badge", text: "Du" }) : null),
            h("span", { class: "hint", text: `seit ${formatDate(account.createdAt).split(",")[0]}` }),
            h("span", { class: "player-actions" },
                roleSelect,
                h("button", {
                    text: "Neuer Code",
                    onclick: () => {
                        if (!confirm(`Neuen Code für ${account.name} erzeugen? Der alte Code funktioniert danach nicht mehr.`)) return;
                        run(async () => showCard(account, await Accounts.resetCode(account.id)));
                    }
                }),
                h("button", {
                    class: "delete-btn", text: "Löschen",
                    disabled: account.id === me?.accountId,
                    onclick: () => {
                        if (!confirm(`Konto ${account.name} löschen?`)) return;
                        run(() => Accounts.remove(account.id));
                    }
                })));
    }));
}

async function run(action) {
    const error = document.getElementById("list-error");
    error.textContent = "";
    try {
        await action();
    } catch (err) {
        error.textContent = err.message;
    }
    renderAccounts();
}
